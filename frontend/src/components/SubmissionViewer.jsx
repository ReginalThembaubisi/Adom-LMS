import React, { useState, useEffect, Suspense, lazy } from 'react';
import { GraderBadge } from '../utils/graderBadge';
import { getStatusBadgeClasses, getStatusLabel } from '../utils/colors';
const PdfReplay = lazy(() => import('./PdfReplay'));

// Read-only counterpart to SubmissionMarker: lets a student view their own submitted
// document in-app (no download) alongside its assessment outcome and feedback.
const SubmissionViewer = ({ submission, learnerCode, onClose }) => {
    const [annotationStrokes, setAnnotationStrokes] = useState(null);
    const [annotationsLoading, setAnnotationsLoading] = useState(submission.hasAnnotations);

    // Fetch saved stroke data when the submission has vector annotations (new path).
    useEffect(() => {
        if (!submission.hasAnnotations) return;
        let cancelled = false;
        const url = `${window.location.origin}/api/submissions/${submission.submissionId}/annotations?learnerCode=${encodeURIComponent(learnerCode)}`;
        fetch(url)
            .then(res => res.ok ? res.json() : null)
            .then(data => { if (!cancelled) setAnnotationStrokes(data || {}); })
            .catch(() => { if (!cancelled) setAnnotationStrokes({}); })
            .finally(() => { if (!cancelled) setAnnotationsLoading(false); });
        return () => { cancelled = true; };
    }, [submission.submissionId, submission.hasAnnotations, learnerCode]);

    // hasAnnotations → PdfReplay (original PDF + vector strokes replayed client-side)
    // hasMarkedCopy (legacy) → iframe serving the rasterized marked PDF
    // else → iframe serving the original file
    const hasAnnotations = submission.hasAnnotations;
    const hasMarkedCopy = !!submission.markedFilePath && !hasAnnotations;
    const isPdf = hasAnnotations || hasMarkedCopy || submission.originalFilename?.toLowerCase().endsWith('.pdf');
    const isDoc = !hasAnnotations && !hasMarkedCopy && (submission.originalFilename?.toLowerCase().endsWith('.doc') || submission.originalFilename?.toLowerCase().endsWith('.docx'));

    // Always go through our own /view endpoint — even for externally-stored (Cloudinary)
    // files — so the response always carries a Content-Type the browser can render inline.
    // Cloudinary's raw-resource delivery doesn't set one reliably, which left this viewer
    // blank when linked to directly.
    const documentUrl = `${window.location.origin}/api/submissions/${submission.submissionId}/view?learnerCode=${encodeURIComponent(learnerCode)}${hasMarkedCopy ? '&marked=true' : ''}`;
    const googleDocsViewerUrl = `https://docs.google.com/gview?url=${encodeURIComponent(documentUrl)}&embedded=true`;

    const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';

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
                            {hasAnnotations ? (
                                // Vector annotations path: render original PDF + replay strokes.
                                // No re-download of a large rasterized file — strokes load as JSON.
                                annotationsLoading ? (
                                    <p className="text-xs text-slate-500">Loading marked copy...</p>
                                ) : (
                                    <Suspense fallback={<p className="text-xs text-slate-500">Loading viewer...</p>}>
                                        <PdfReplay
                                            documentUrl={documentUrl}
                                            strokes={annotationStrokes || {}}
                                        />
                                    </Suspense>
                                )
                            ) : isPdf ? (
                                // Legacy path (rasterized marked copy) or unmarked original PDF.
                                // #toolbar=0 hides the browser's native PDF viewer chrome (which
                                // otherwise adds its own print/save controls) — viewing stays
                                // strictly in-app, no download affordance.
                                <iframe
                                    src={`${documentUrl}#toolbar=0`}
                                    className="w-full h-full border-none"
                                    title="PDF Document Preview"
                                />
                            ) : isDoc ? (
                                isLocalhost ? (
                                    <div className="p-6 text-center space-y-4 max-w-md">
                                        <div className="w-12 h-12 bg-amber-500/15 text-amber-500 rounded-full flex items-center justify-center mx-auto">
                                            <svg className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                                            </svg>
                                        </div>
                                        <h4 className="text-sm font-bold text-white">Local Word Document Preview Sandbox</h4>
                                        <p className="text-xs text-slate-400">
                                            Google Document Viewer requires a public URL to fetch and render Word documents. In the deployed production site, this will render the document inside the browser automatically.
                                        </p>
                                    </div>
                                ) : (
                                    <iframe
                                        src={googleDocsViewerUrl}
                                        className="w-full h-full border-none"
                                        title="Word Document Preview"
                                    />
                                )
                            ) : (
                                <div className="text-center p-6 space-y-3">
                                    <div className="w-12 h-12 bg-red-500/10 text-red-500 rounded-full flex items-center justify-center mx-auto">
                                        <svg className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                                        </svg>
                                    </div>
                                    <h4 className="text-sm font-bold text-white">Unsupported file format preview</h4>
                                    <p className="text-xs text-slate-400">
                                        This document format cannot be rendered inside the viewer.
                                    </p>
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
                            <span className="text-[10px] text-slate-500 uppercase tracking-widest">View only</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default SubmissionViewer;
