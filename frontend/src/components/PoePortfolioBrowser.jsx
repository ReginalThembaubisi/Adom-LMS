import React, { useState, useEffect, useCallback } from 'react';

/**
 * The per-learner portfolio browser: the same six-section PoE folder structure the SETA export
 * zips, shown on screen instead of downloaded.
 *
 * Everything on this tree comes from one backend call — GET /api/admin/poe/portfolio/learners/{id}
 * — which itself is built from the exact resolution the export uses (see
 * PoeExportService.resolvePortfolio and PoePortfolioBrowserService's class doc on the backend).
 * This component does not decide what belongs where; it only renders what it's given and lets an
 * admin open a file or record a document decision. Sections 4/5 always show their
 * Fundamentals/Cores/Electives subfolders even when empty — the shape of the qualification should
 * be visible whether or not this learner has work in every part of it, exactly as the zip would
 * show it.
 *
 * Section 3 (Assessment Guidelines) is deliberately hidden here — see HIDDEN_SECTION_NUMBERS
 * below — even though the backend still sends it and the export ZIP still includes it.
 *
 * "Open" never links straight to storage: every file is fetched with this screen's own auth
 * header and rendered from a blob URL, through whichever authenticated endpoint already serves
 * that kind of file elsewhere in the app (documents, module files, submissions). Only DOCUMENT
 * rows carry the accept/reject actions Phase 3 built — a guide, a submission or a rendered
 * feedback record has no review workflow of its own.
 */

// Guides are module-level shared reference material — uploaded once by a lecturer under Modules,
// identical for every learner in that module — not something a specific learner sent, submitted,
// or has "on record" personally. A per-learner checklist of guide availability doesn't reflect
// anything about that learner; it reflects whether the lecturer uploaded a guide at all, which
// belongs in Modules Directory. Hidden display-side only: the export ZIP still needs Section 3
// for SETA folder-structure compliance, a separate concern from this quick-browse view.
const HIDDEN_SECTION_NUMBERS = new Set([3]);

const KIND_LABELS = {
    DOCUMENT: 'Document',
    GUIDE: 'Facilitator guide',
    SUBMISSION: 'Submission',
    FEEDBACK: 'Feedback',
    MARKED_COPY: 'Marked copy'
};

const REVIEW_STYLES = {
    PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
    ACCEPTED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    REJECTED: 'bg-rose-50 text-rose-700 border-rose-200'
};

const PoePortfolioBrowser = ({ learnerId, learnerLabel, token, onAuthFailure, onError, onClose }) => {
    const [tree, setTree] = useState(null);
    const [loading, setLoading] = useState(true);
    const [reviewBusyId, setReviewBusyId] = useState(null);
    const [rejectDraftId, setRejectDraftId] = useState(null);
    const [rejectNote, setRejectNote] = useState('');
    const [reviewError, setReviewError] = useState('');
    const [expandedFeedback, setExpandedFeedback] = useState(null);

    const authHeaders = useCallback(() => ({ Authorization: `Basic ${token}` }), [token]);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/admin/poe/portfolio/learners/${learnerId}`, { headers: authHeaders() });
            if (res.status === 401) { onAuthFailure?.(); return; }
            if (!res.ok) { onError?.('Could not load this learner’s portfolio.'); return; }
            setTree(await res.json());
        } catch (e) {
            onError?.('Could not load this learner’s portfolio.');
        } finally {
            setLoading(false);
        }
    }, [learnerId, authHeaders, onAuthFailure, onError]);

    useEffect(() => { load(); }, [load]);

    // Fetch-and-blob, never a direct link — see the class doc above. Every openUrl the backend
    // sends is one of this application's own authenticated view endpoints.
    const openFile = async (file) => {
        try {
            const res = await fetch(file.openUrl, { headers: authHeaders() });
            if (res.status === 401) { onAuthFailure?.(); return; }
            if (!res.ok) throw new Error(`Could not open that file (${res.status})`);
            const objectUrl = URL.createObjectURL(await res.blob());
            window.open(objectUrl, '_blank');
        } catch (err) {
            onError?.(err.message || 'Could not open that file.');
        }
    };

    const decideDocument = async (documentId, status, note) => {
        setReviewBusyId(documentId);
        setReviewError('');
        try {
            const res = await fetch(`/api/admin/documents/${documentId}/review`, {
                method: 'PUT',
                headers: { ...authHeaders(), 'Content-Type': 'application/json' },
                body: JSON.stringify({ status, note: note || null })
            });
            if (res.status === 401) { onAuthFailure?.(); return; }
            const data = await res.json().catch(() => ({}));
            if (!res.ok) throw new Error(data.message || 'Could not save that decision.');
            setRejectDraftId(null);
            setRejectNote('');
            await load();
        } catch (err) {
            setReviewError(err.message || 'Could not save that decision.');
        } finally {
            setReviewBusyId(null);
        }
    };

    const acceptDocument = (documentId) => decideDocument(documentId, 'ACCEPTED', null);
    const confirmReject = (documentId) => {
        if (!rejectNote.trim()) return;
        decideDocument(documentId, 'REJECTED', rejectNote.trim());
    };

    // See HIDDEN_SECTION_NUMBERS above. The header count is derived from this filtered list
    // (not tree.totalFiles) so it never counts files from a section that isn't shown.
    const visibleSections = tree ? tree.sections.filter(section => !HIDDEN_SECTION_NUMBERS.has(section.number)) : [];
    const visibleFileCount = visibleSections.reduce((sum, section) => sum + section.fileCount, 0);

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-[110]" onClick={onClose}>
            <div className="bg-white rounded-2xl shadow-xl max-w-4xl w-full max-h-[90vh] overflow-y-auto p-6"
                 onClick={(e) => e.stopPropagation()}>
                <div className="flex items-start justify-between mb-4 border-b border-slate-200 pb-3">
                    <div>
                        <h3 className="text-sm font-bold text-slate-900">Portfolio — {learnerLabel}</h3>
                        <p className="text-[10px] text-slate-500">
                            {tree ? `${visibleFileCount} file${visibleFileCount === 1 ? '' : 's'} across ${visibleSections.length} sections` : 'Loading…'}
                        </p>
                    </div>
                    <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-700 font-bold">✕</button>
                </div>

                {reviewError && (
                    <p className="text-[11px] font-medium text-rose-700 bg-rose-50 border border-rose-200 rounded-lg px-3 py-2 mb-3">
                        {reviewError}
                    </p>
                )}

                {loading && !tree ? (
                    <p className="text-xs text-slate-500 py-8 text-center">Loading portfolio…</p>
                ) : !tree ? (
                    <p className="text-xs text-slate-500 py-8 text-center">Could not load this learner’s portfolio.</p>
                ) : (
                    <div className="space-y-5">
                        {visibleSections.map(section => (
                            <SectionBlock
                                key={section.number}
                                section={section}
                                reviewBusyId={reviewBusyId}
                                rejectDraftId={rejectDraftId}
                                rejectNote={rejectNote}
                                expandedFeedback={expandedFeedback}
                                onOpen={openFile}
                                onAccept={acceptDocument}
                                onStartReject={(id) => { setRejectDraftId(id); setRejectNote(''); setReviewError(''); }}
                                onCancelReject={() => { setRejectDraftId(null); setRejectNote(''); }}
                                onRejectNoteChange={setRejectNote}
                                onConfirmReject={confirmReject}
                                onToggleFeedback={(key) => setExpandedFeedback(prev => (prev === key ? null : key))}
                            />
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

const SectionBlock = (props) => {
    const { section } = props;
    return (
        <div className="border border-slate-200 rounded-xl p-4">
            <div className="flex items-center justify-between mb-2">
                <h4 className="text-xs font-bold text-slate-800">{section.name}</h4>
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                    {section.fileCount} file{section.fileCount === 1 ? '' : 's'}
                </span>
            </div>
            {section.categories ? (
                <div className="space-y-3">
                    {section.categories.map(category => (
                        <CategoryBlock key={category.name} category={category} {...props} />
                    ))}
                </div>
            ) : (
                <FileList files={section.files} emptyLabel="Nothing on record for this section." {...props} />
            )}
        </div>
    );
};

const CategoryBlock = ({ category, ...rest }) => (
    <div className="bg-slate-50/70 border border-slate-200 rounded-lg p-3">
        <div className="flex items-center justify-between mb-2">
            <h5 className="text-[11px] font-bold text-slate-600">{category.name}</h5>
            <span className="text-[10px] font-semibold text-slate-400">
                {category.fileCount} file{category.fileCount === 1 ? '' : 's'}
            </span>
        </div>
        {category.modules.length === 0 ? (
            <p className="text-[10px] text-slate-400 italic">No modules in this category for this learner.</p>
        ) : (
            <div className="space-y-2">
                {category.modules.map(module => (
                    <div key={module.moduleId} className="bg-white border border-slate-200 rounded-lg p-2.5">
                        <div className="text-[11px] font-semibold text-slate-700 mb-1.5">
                            {module.moduleName}
                            {module.moduleCode ? <span className="text-slate-400 font-normal"> · {module.moduleCode}</span> : null}
                        </div>
                        <FileList files={module.files} emptyLabel="Nothing on record for this module yet." {...rest} />
                    </div>
                ))}
            </div>
        )}
    </div>
);

const FileList = ({ files, emptyLabel, ...rest }) => {
    if (!files || files.length === 0) {
        return <p className="text-[10px] text-slate-400 italic">{emptyLabel}</p>;
    }
    return (
        <div className="space-y-1.5">
            {files.map((file, i) => (
                <FileRow key={`${file.kind}-${file.documentId || file.canonicalFilename}-${i}`} file={file} {...rest} />
            ))}
        </div>
    );
};

const FileRow = ({
    file, reviewBusyId, rejectDraftId, rejectNote, expandedFeedback,
    onOpen, onAccept, onStartReject, onCancelReject, onRejectNoteChange, onConfirmReject, onToggleFeedback
}) => {
    const isDocument = file.kind === 'DOCUMENT';
    const isFeedback = file.kind === 'FEEDBACK';
    const isRejecting = isDocument && rejectDraftId === file.documentId;
    const isBusy = isDocument && reviewBusyId === file.documentId;
    const feedbackKey = `${file.canonicalFilename}`;
    const feedbackOpen = expandedFeedback === feedbackKey;

    return (
        <div className={`border rounded-lg px-2.5 py-2 text-[11px] ${
            isDocument ? (REVIEW_STYLES[file.reviewStatus] || 'bg-slate-50 border-slate-200') : 'bg-slate-50 border-slate-200'
        }`}>
            <div className="flex items-center justify-between gap-3 flex-wrap">
                <div className="min-w-0">
                    <span className="font-semibold text-slate-800 break-all">{file.canonicalFilename}</span>
                    <span className="block text-[10px] text-slate-500 mt-0.5">
                        {KIND_LABELS[file.kind] || file.kind}
                        {file.version != null ? ` · v${file.version}` : ''}
                        {file.at ? ` · ${new Date(file.at).toLocaleDateString()}` : ''}
                        {file.sessionName ? ` · ${file.sessionName}` : ''}
                    </span>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                    {isDocument && (
                        <span className="text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded">
                            {file.reviewStatus}
                        </span>
                    )}
                    {isFeedback && (
                        <span className={`text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded ${
                            file.releaseStatus === 'released' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
                        }`}>
                            {file.releaseStatus}
                        </span>
                    )}
                    {file.openUrl && (
                        <button type="button" onClick={() => onOpen(file)}
                            className="text-[10px] font-semibold text-blue-600 hover:underline">
                            Open
                        </button>
                    )}
                    {isFeedback && (
                        <button type="button" onClick={() => onToggleFeedback(feedbackKey)}
                            className="text-[10px] font-semibold text-blue-600 hover:underline">
                            {feedbackOpen ? 'Hide' : 'View'}
                        </button>
                    )}
                    {isDocument && (
                        <>
                            <button type="button" disabled={isBusy} onClick={() => onAccept(file.documentId)}
                                className="text-[10px] font-semibold text-emerald-700 hover:underline disabled:opacity-50">
                                Accept
                            </button>
                            <button type="button" disabled={isBusy} onClick={() => onStartReject(file.documentId)}
                                className="text-[10px] font-semibold text-rose-700 hover:underline disabled:opacity-50">
                                Reject
                            </button>
                        </>
                    )}
                </div>
            </div>

            {isDocument && file.reviewNote && (
                <p className="text-[10px] mt-1 text-slate-600">Note: {file.reviewNote}</p>
            )}

            {isRejecting && (
                <div className="mt-2 space-y-1.5">
                    <textarea
                        value={rejectNote}
                        onChange={(e) => onRejectNoteChange(e.target.value)}
                        placeholder="Say why — the learner is shown this note as written."
                        rows={2}
                        className="w-full text-[11px] border border-slate-300 rounded-lg px-2 py-1.5"
                        autoFocus
                    />
                    <div className="flex items-center gap-3">
                        <button
                            type="button"
                            disabled={isBusy || !rejectNote.trim()}
                            onClick={() => onConfirmReject(file.documentId)}
                            className="text-[10px] font-semibold text-white bg-rose-600 rounded-lg px-2.5 py-1 disabled:opacity-40"
                        >
                            Confirm rejection
                        </button>
                        <button type="button" onClick={onCancelReject}
                            className="text-[10px] font-semibold text-slate-500 hover:underline">
                            Cancel
                        </button>
                        {!rejectNote.trim() && (
                            <span className="text-[10px] text-slate-400">A note is required to reject.</span>
                        )}
                    </div>
                </div>
            )}

            {isFeedback && feedbackOpen && (
                <pre className="mt-2 text-[10px] whitespace-pre-wrap bg-white border border-slate-200 rounded-lg p-2 text-slate-700">
                    {file.feedbackText}
                </pre>
            )}
        </div>
    );
};

export default PoePortfolioBrowser;
