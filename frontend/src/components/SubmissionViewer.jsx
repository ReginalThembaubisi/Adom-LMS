import React, { useState, useEffect, useRef, Suspense, lazy } from 'react';
import { GraderBadge } from '../utils/graderBadge';
import { getStatusBadgeClasses, getStatusLabel } from '../utils/colors';
import { useLearner } from '../context/LearnerContext';
import { drawStroke, RENDER_SCALE } from '../utils/pdfAnnotations';
const PdfReplay = lazy(() => import('./PdfReplay'));

// Loads pdf.js on demand, same as PdfReplay's own loader (duplicated rather than imported so
// that importing this component doesn't force PdfReplay's module — and its lazy chunk — to
// load eagerly). The module itself is cached by the bundler regardless of how many places
// dynamic-import it, so this costs a second small loader function, not a second pdf.js.
let _pdfjsLib = null;
async function getPdfjs() {
    if (_pdfjsLib) return _pdfjsLib;
    const [lib, workerUrl] = await Promise.all([
        import('pdfjs-dist'),
        import('pdfjs-dist/build/pdf.worker.min.mjs?url'),
    ]);
    lib.GlobalWorkerOptions.workerSrc = workerUrl.default;
    _pdfjsLib = lib;
    return lib;
}

// Read-only counterpart to SubmissionMarker: lets a student view their own submitted
// document alongside its assessment outcome and feedback. PDFs and images render in-app;
// Word files are saved locally, since the only way to preview those in a browser would be
// to hand the file to a third-party viewer.
const SubmissionViewer = ({ submission, onClose }) => {
    const { authFetch } = useLearner();
    const [annotationStrokes, setAnnotationStrokes] = useState(null);
    const [annotationsLoading, setAnnotationsLoading] = useState(submission.hasAnnotations);
    const [documentUrl, setDocumentUrl] = useState(null);
    const [documentType, setDocumentType] = useState(null);
    const [loadError, setLoadError] = useState(false);
    // null when idle, { current, total } while flattening pages, 'error' on failure.
    const [flattenState, setFlattenState] = useState(null);
    const flatteningRef = useRef(false);
    const mountedRef = useRef(true);
    useEffect(() => () => { mountedRef.current = false; }, []);

    const hasAnnotations = submission.hasAnnotations;
    const hasMarkedCopy = submission.hasMarkedCopy && !hasAnnotations;

    // The document is fetched here, with the learner's token, and rendered from an object
    // URL. Nothing else works without handing the file to somebody: an <iframe> pointed at
    // our own endpoint cannot send an Authorization header, and Google's document viewer
    // renders Word files by having Google fetch them — which would send a learner's
    // assessment work to a third party we hold no processing agreement with, on personal
    // information nobody consented to sharing.
    useEffect(() => {
        let cancelled = false;
        let objectUrl = null;

        setDocumentUrl(null);
        setLoadError(false);

        const path = `/api/submissions/${submission.submissionId}/view`
            + (hasMarkedCopy ? '?marked=true' : '');

        authFetch(path)
            .then(res => res.ok ? res.blob() : Promise.reject(new Error('Could not load document')))
            .then(blob => {
                if (cancelled) return;
                objectUrl = URL.createObjectURL(blob);
                setDocumentType(blob.type);
                setDocumentUrl(objectUrl);
            })
            .catch(() => { if (!cancelled) setLoadError(true); });

        return () => {
            cancelled = true;
            // The blob stays in memory until it is revoked, so this matters on a screen
            // a learner opens once per submission.
            if (objectUrl) URL.revokeObjectURL(objectUrl);
        };
    }, [submission.submissionId, hasMarkedCopy, authFetch]);

    // Word files have no in-browser renderer we can use without shipping the file offsite,
    // so they are saved and opened in Word instead. If inline preview turns out to matter,
    // the answer is a server-side PDF conversion, not a third-party viewer.
    //
    // The "marked_" prefix mirrors SubmissionController.viewSubmissionFile, which serves the
    // marked variant under that same filename — so the download matches what the backend
    // would call it, not just whatever the original was named.
    const saveDocument = () => {
        if (!documentUrl) return;
        const link = document.createElement('a');
        link.href = documentUrl;
        link.download = (hasMarkedCopy || hasAnnotations)
            ? `marked_${submission.originalFilename || 'submission'}`
            : (submission.originalFilename || 'submission');
        document.body.appendChild(link);
        link.click();
        link.remove();
    };

    /**
     * Bakes the replayed strokes into a real, downloadable PDF for the hasAnnotations path,
     * where there is no server-side rasterized copy — marks exist only as stroke JSON, replayed
     * on top of the original by PdfReplay. saveDocument() can't be reused here: it downloads
     * documentUrl as-is, and documentUrl is the unmarked original.
     *
     * Deliberately sequential and on the main thread, one page at a time, reusing a single
     * canvas: this renders every page (PdfReplay only renders pages scrolled into view, which
     * is not an option for a file that has to be complete), and a long submission can take
     * several seconds — the progress state exists so the button doesn't just look stuck.
     */
    const downloadFlattenedPdf = async () => {
        if (flatteningRef.current || !documentUrl) return;
        flatteningRef.current = true;
        setFlattenState({ current: 0, total: 0 });

        try {
            const [pdfjs, { jsPDF }] = await Promise.all([getPdfjs(), import('jspdf')]);
            const doc = await pdfjs.getDocument({ url: documentUrl }).promise;
            const numPages = doc.numPages;
            if (!mountedRef.current) return;
            setFlattenState({ current: 0, total: numPages });

            // One canvas, reused and cleared (by reassigning width/height) every page, so peak
            // memory is one page's raster rather than growing with the document's length.
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            let out = null;

            for (let pageNum = 1; pageNum <= numPages; pageNum++) {
                const page = await doc.getPage(pageNum);
                const vp = page.getViewport({ scale: RENDER_SCALE });
                canvas.width = vp.width;
                canvas.height = vp.height;

                await page.render({ canvasContext: ctx, viewport: vp }).promise;
                (annotationStrokes?.[pageNum] || []).forEach(s => drawStroke(ctx, s, RENDER_SCALE));

                // JPEG, not PNG: a scanned or text-heavy page compresses far smaller, which
                // matters once dozens of pages are accumulating in the output document.
                const pageImage = canvas.toDataURL('image/jpeg', 0.85);
                const orientation = vp.width > vp.height ? 'l' : 'p';
                if (!out) {
                    out = new jsPDF({ unit: 'px', format: [vp.width, vp.height], orientation });
                } else {
                    out.addPage([vp.width, vp.height], orientation);
                }
                out.addImage(pageImage, 'JPEG', 0, 0, vp.width, vp.height);

                if (!mountedRef.current) return;
                setFlattenState({ current: pageNum, total: numPages });
            }

            const base = submission.originalFilename || 'submission';
            const name = `marked_${base}`;
            out.save(name.toLowerCase().endsWith('.pdf') ? name : `${name}.pdf`);
            if (mountedRef.current) setFlattenState(null);
        } catch (e) {
            if (mountedRef.current) {
                setFlattenState('error');
                setTimeout(() => { if (mountedRef.current) setFlattenState(null); }, 4000);
            }
        } finally {
            flatteningRef.current = false;
        }
    };

    // Fetch saved stroke data when the submission has vector annotations (new path).
    useEffect(() => {
        if (!submission.hasAnnotations) return;
        let cancelled = false;
        authFetch(`/api/submissions/${submission.submissionId}/annotations`)
            .then(res => res.ok ? res.json() : null)
            .then(data => { if (!cancelled) setAnnotationStrokes(data || {}); })
            .catch(() => { if (!cancelled) setAnnotationStrokes({}); })
            .finally(() => { if (!cancelled) setAnnotationsLoading(false); });
        return () => { cancelled = true; };
    }, [submission.submissionId, submission.hasAnnotations, authFetch]);

    // hasAnnotations → PdfReplay (original PDF + vector strokes replayed client-side)
    // hasMarkedCopy (legacy) → the rasterized marked PDF
    // else → the original file, rendered inline when the browser can, saved when it can't
    const isPdf = hasAnnotations || hasMarkedCopy
        || submission.originalFilename?.toLowerCase().endsWith('.pdf')
        || documentType === 'application/pdf';
    const isImage = !!documentType && documentType.startsWith('image/');

    const status = submission.status;
    const isAssessed = status === 'COMPETENT' || status === 'NOT_YET_COMPETENT';

    return (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full h-[92vh] max-w-7xl flex flex-col overflow-hidden shadow-2xl animate-fade-in text-slate-100">
                <div className="px-6 py-4 bg-slate-950 border-b border-slate-800 flex items-center justify-between">
                    <div>
                        <h3 className="text-base font-extrabold text-[#f8fafc]">My Submission</h3>
                        <p className="text-xs text-[#cbd5e1]">
                            File: <span className="font-semibold text-[#f8fafc]">{submission.originalFilename}</span>
                        </p>
                    </div>
                    <button
                        onClick={onClose}
                        className="bg-[#1e293b] hover:bg-[#334155] text-[#94a3b8] hover:text-[#f8fafc] p-2 rounded-xl transition-colors cursor-pointer"
                    >
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>

                <div className="flex-1 flex overflow-hidden">
                    <div className="w-3/4 bg-slate-950 flex flex-col relative border-r border-slate-800 p-4">
                        <div className="flex-1 bg-slate-900/40 rounded-2xl border border-slate-800 overflow-hidden relative flex flex-col items-center justify-center">
                            {loadError ? (
                                <div className="text-center p-6 space-y-3">
                                    <div className="w-12 h-12 bg-red-500/10 text-red-500 rounded-full flex items-center justify-center mx-auto">
                                        <svg className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                                        </svg>
                                    </div>
                                    <h4 className="text-sm font-bold text-white">This document could not be opened</h4>
                                    <p className="text-xs text-slate-400">Your session may have expired. Sign in again and reopen the submission.</p>
                                </div>
                            ) : !documentUrl ? (
                                <p className="text-xs text-slate-500">Loading document...</p>
                            ) : hasAnnotations ? (
                                // Vector annotations path: render original PDF + replay strokes.
                                // PdfReplay only renders pages scrolled into view, so downloading
                                // *that* would be incomplete — the Download button here runs
                                // downloadFlattenedPdf() instead, which renders every page (off
                                // the visible PdfReplay instance entirely) and bakes the same
                                // strokes into a standalone PDF. See its own comment for why
                                // that's a real render pass, not free — hence the progress state.
                                annotationsLoading ? (
                                    <p className="text-xs text-slate-500">Loading marked copy...</p>
                                ) : (
                                    <>
                                        <Suspense fallback={<p className="text-xs text-slate-500">Loading viewer...</p>}>
                                            <PdfReplay
                                                documentUrl={documentUrl}
                                                strokes={annotationStrokes || {}}
                                            />
                                        </Suspense>
                                        {flattenState === 'error' ? (
                                            <span className="absolute top-3 right-3 bg-rose-600/90 text-white text-xs font-semibold px-3 py-1.5 rounded-lg shadow-lg">
                                                Couldn't prepare download
                                            </span>
                                        ) : flattenState ? (
                                            <span className="absolute top-3 right-3 flex items-center gap-1.5 bg-slate-800/90 text-slate-200 text-xs font-semibold px-3 py-1.5 rounded-lg shadow-lg">
                                                <svg className="w-3.5 h-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                                                </svg>
                                                {flattenState.total
                                                    ? `Preparing ${flattenState.current}/${flattenState.total}…`
                                                    : 'Preparing…'}
                                            </span>
                                        ) : (
                                            <button
                                                onClick={downloadFlattenedPdf}
                                                className="absolute top-3 right-3 flex items-center gap-1.5 bg-[#4A3AFF] hover:bg-[#3d2fd6] text-white text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors cursor-pointer shadow-lg"
                                            >
                                                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                                </svg>
                                                Download
                                            </button>
                                        )}
                                    </>
                                )
                            ) : isPdf ? (
                                // Legacy path (rasterized marked copy) or unmarked original PDF.
                                // #toolbar=0 hides the browser's native PDF viewer chrome (which
                                // otherwise adds its own print/save controls); the Download
                                // button below is the one deliberate way out, and — unlike the
                                // hasAnnotations case above — documentUrl here really is the file
                                // on screen (the marked raster when hasMarkedCopy, the original
                                // otherwise), so saving it is never misleading.
                                <>
                                    <iframe
                                        src={`${documentUrl}#toolbar=0`}
                                        className="w-full h-full border-none"
                                        title="PDF Document Preview"
                                    />
                                    <button
                                        onClick={saveDocument}
                                        className="absolute top-3 right-3 flex items-center gap-1.5 bg-[#4A3AFF] hover:bg-[#3d2fd6] text-white text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors cursor-pointer shadow-lg"
                                    >
                                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                        </svg>
                                        Download
                                    </button>
                                </>
                            ) : isImage ? (
                                <img
                                    src={documentUrl}
                                    alt={submission.originalFilename}
                                    className="max-w-full max-h-full object-contain"
                                />
                            ) : (
                                // Word documents, and anything else the browser cannot render
                                // on its own. Previewing these used to mean handing the file to
                                // Google's document viewer, which fetches it from their servers
                                // — a learner's assessment work leaving our infrastructure for
                                // a processor we have no agreement with. Saving it locally and
                                // opening it in Word keeps the file between the learner and us.
                                <div className="p-6 text-center space-y-4 max-w-md">
                                    <div className="w-12 h-12 bg-blue-500/15 text-blue-400 rounded-full flex items-center justify-center mx-auto">
                                        <svg className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" d="M12 10v6m0 0l-3-3m3 3l3-3M3 17V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
                                        </svg>
                                    </div>
                                    <h4 className="text-sm font-bold text-white">Open this file in Word</h4>
                                    <p className="text-xs text-slate-400">
                                        Word documents can't be previewed in the browser. Save your copy and open it in Word — your assessment work stays between you and your facilitator.
                                    </p>
                                    <button
                                        onClick={saveDocument}
                                        className="bg-[#4A3AFF] hover:bg-[#3d2fd6] text-white text-xs font-semibold px-4 py-2 rounded-xl transition-colors cursor-pointer"
                                    >
                                        Save my submission
                                    </button>
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="w-1/4 bg-slate-900 p-6 flex flex-col justify-between overflow-y-auto">
                        <div className="space-y-6">
                            <div className="space-y-1">
                                <h4 className="text-sm font-bold text-white uppercase tracking-wider text-slate-400">Assessment Status</h4>
                            </div>

                            <div className="space-y-4">
                                <span className={`inline-flex items-center gap-1.5 border text-xs font-semibold px-3 py-1 rounded-full ${getStatusBadgeClasses(status, 'dark')}`}>
                                    {getStatusLabel(status || 'SUBMITTED')}
                                </span>

                                {isAssessed && (
                                    <div>
                                        <GraderBadge role={submission.gradedByRole} name={submission.gradedByName} theme="dark" />
                                    </div>
                                )}

                                {submission.marksAwarded != null && (
                                    <div className="space-y-1.5">
                                        <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">Marks</label>
                                        <p className="text-lg font-bold text-white">{submission.marksAwarded}%</p>
                                    </div>
                                )}

                                {(hasAnnotations || hasMarkedCopy) && (
                                    <p className="text-[10px] text-blue-400">Showing your facilitator's marked-up copy.</p>
                                )}

                                <div className="space-y-1.5">
                                    <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">Feedback</label>
                                    <p className="text-xs text-[#cbd5e1] italic bg-slate-950 border border-slate-800 rounded-xl p-3.5">
                                        {isAssessed ? (submission.feedback || 'No written comments provided.') : 'Not yet assessed.'}
                                    </p>
                                </div>
                            </div>
                        </div>

                        <div className="pt-6 border-t border-slate-800/80 text-center">
                            <span className="text-[10px] text-slate-500 uppercase tracking-widest">
                                {isImage ? 'View only' : 'Your copy'}
                            </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default SubmissionViewer;
