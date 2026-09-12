import React, { useState, useEffect, useCallback } from 'react';
import { fetchStaffBlobUrl } from '../utils/staffAuth';

/**
 * Portfolio completeness.
 *
 * The screen exists to answer two different questions that are easy to confuse, so it never
 * shows a bare number: every figure says whether it counts people or documents. "23 documents
 * outstanding" is a to-do list; "9 learners with something outstanding" is a phone list.
 *
 * "Exempt" is shown, never hidden. A learner who registered before a requirement took effect was
 * never asked for the document — counting them as missing would be an accusation — but a portfolio
 * full of exempt items is not one to send to a SETA, so exempt keeps a learner out of Complete and
 * appears in its own column with its own explanation.
 *
 * The per-learner drill-down also does the Phase 3 document review the admin console never got
 * a screen for: open the file, accept it, or reject it with a note. This is that screen, not a
 * new one — an admin already lands here to see what a learner is missing, and "missing" and
 * "needs a decision" are the same trip. The checklist's own `documentItems` say which document
 * types are required and their state (missing/awaiting review/accepted/rejected/exempt); the
 * actionable id, filename and version for each one come from `GET
 * /api/admin/learners/{id}/documents` (Phase 3's own endpoint, fetched alongside the checklist),
 * matched by document type. A checklist row with no matching document (MISSING or EXEMPT) has
 * nothing to open or decide, so it renders with no actions — the same rule the review endpoint
 * itself enforces (a rejection without a note is refused server-side regardless of what this
 * screen does, so a bypassed or buggy client can't downgrade that rule).
 */

const STATE_STYLES = {
    ACCEPTED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    AWAITING_REVIEW: 'bg-amber-50 text-amber-700 border-amber-200',
    REJECTED: 'bg-rose-50 text-rose-700 border-rose-200',
    MISSING: 'bg-rose-50 text-rose-700 border-rose-200',
    EXEMPT: 'bg-slate-100 text-slate-600 border-slate-300',
    NOT_YET_DUE: 'bg-sky-50 text-sky-700 border-sky-200'
};

const STATE_LABELS = {
    ACCEPTED: 'Accepted',
    AWAITING_REVIEW: 'Awaiting review',
    REJECTED: 'Sent back',
    MISSING: 'Missing',
    EXEMPT: 'Exempt',
    NOT_YET_DUE: 'Not yet due'
};

const LEARNER_STATE_STYLES = {
    OUTSTANDING: 'bg-rose-100 text-rose-800',
    AWAITING_REVIEW: 'bg-amber-100 text-amber-800',
    NOT_FULLY_IN_SCOPE: 'bg-slate-200 text-slate-700',
    COMPLETE: 'bg-emerald-100 text-emerald-800'
};

const LEARNER_STATE_LABELS = {
    OUTSTANDING: 'Something outstanding',
    AWAITING_REVIEW: 'Awaiting review',
    NOT_FULLY_IN_SCOPE: 'Not fully in scope',
    COMPLETE: 'Complete'
};

const PoeCompleteness = ({ token, onAuthFailure, onError }) => {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [learnershipId, setLearnershipId] = useState('');
    const [cohort, setCohort] = useState('');
    const [openLearner, setOpenLearner] = useState(null);
    const [checklist, setChecklist] = useState(null);
    const [requirements, setRequirements] = useState(null);
    const [savingType, setSavingType] = useState(null);

    // Document review, inline in this same drill-down — see the class doc above for why this
    // lives here rather than as a separate screen.
    const [documents, setDocuments] = useState([]);
    const [reviewBusyId, setReviewBusyId] = useState(null);
    const [rejectDraftId, setRejectDraftId] = useState(null);
    const [rejectNote, setRejectNote] = useState('');
    const [reviewError, setReviewError] = useState('');

    const authHeaders = useCallback(() => ({ Authorization: `Basic ${token}` }), [token]);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (learnershipId) params.set('learnershipId', learnershipId);
            if (cohort) params.set('cohort', cohort);
            const res = await fetch(`/api/admin/poe/completeness?${params.toString()}`, {
                headers: authHeaders()
            });
            if (res.status === 401) { onAuthFailure?.(); return; }
            if (!res.ok) { onError?.('Could not load the completeness dashboard.'); return; }
            setData(await res.json());
        } catch (e) {
            onError?.('Could not load the completeness dashboard.');
        } finally {
            setLoading(false);
        }
    }, [learnershipId, cohort, authHeaders, onAuthFailure, onError]);

    useEffect(() => { load(); }, [load]);

    // The cohort list belongs to the learnership. Keeping a cohort selected across a learnership
    // change would silently filter to nothing and read like an empty database.
    useEffect(() => { setCohort(''); }, [learnershipId]);

    const openChecklist = async (learner) => {
        setOpenLearner(learner);
        setChecklist(null);
        setDocuments([]);
        setReviewError('');
        setRejectDraftId(null);
        setRejectNote('');
        try {
            const [checklistRes, documentsRes] = await Promise.all([
                fetch(`/api/admin/poe/completeness/learners/${learner.learnerId}`, { headers: authHeaders() }),
                fetch(`/api/admin/learners/${learner.learnerId}/documents`, { headers: authHeaders() })
            ]);
            if (checklistRes.status === 401 || documentsRes.status === 401) { onAuthFailure?.(); return; }
            if (checklistRes.ok) setChecklist(await checklistRes.json());
            if (documentsRes.ok) setDocuments(await documentsRes.json());
        } catch (e) {
            onError?.('Could not load that learner’s checklist.');
        }
    };

    // Re-fetches both the drill-down's own checklist and the outer table's counts, so a
    // decision made here is reflected the moment the modal closes rather than on the next
    // manual refresh.
    const refreshAfterReview = async () => {
        if (!openLearner) return;
        try {
            const res = await fetch(`/api/admin/poe/completeness/learners/${openLearner.learnerId}`, {
                headers: authHeaders()
            });
            if (res.ok) setChecklist(await res.json());
        } catch {
            // The action itself already succeeded; a stale checklist count is a cosmetic
            // problem, not a reason to report the review as failed.
        }
        load();
    };

    const openDocumentFile = async (doc) => {
        try {
            const { objectUrl } = await fetchStaffBlobUrl(`/api/admin/documents/${doc.id}/view`);
            window.open(objectUrl, '_blank');
        } catch (err) {
            onError?.(err.message || 'Could not open that document.');
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
            setDocuments(prev => prev.map(d => (d.id === documentId ? data : d)));
            setRejectDraftId(null);
            setRejectNote('');
            await refreshAfterReview();
        } catch (err) {
            setReviewError(err.message || 'Could not save that decision.');
        } finally {
            setReviewBusyId(null);
        }
    };

    const acceptDocument = (documentId) => decideDocument(documentId, 'ACCEPTED', null);

    const confirmReject = (documentId) => {
        // The same rule the server enforces — checked here too so the admin sees why the
        // button won't do anything yet, rather than firing a request that comes back 400.
        if (!rejectNote.trim()) return;
        decideDocument(documentId, 'REJECTED', rejectNote.trim());
    };

    const loadRequirements = async () => {
        if (!learnershipId) return;
        try {
            const res = await fetch(`/api/admin/poe/learnerships/${learnershipId}/requirements`, {
                headers: authHeaders()
            });
            if (res.status === 401) { onAuthFailure?.(); return; }
            if (res.ok) setRequirements(await res.json());
        } catch (e) {
            onError?.('Could not load the requirements for that learnership.');
        }
    };

    const saveRequirement = async (body) => {
        setSavingType(body.documentType);
        try {
            const res = await fetch(`/api/admin/poe/learnerships/${learnershipId}/requirements`, {
                method: 'PUT',
                headers: { ...authHeaders(), 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            if (res.status === 401) { onAuthFailure?.(); return; }
            if (res.ok) {
                setRequirements(await res.json());
                load();
            } else {
                onError?.('Could not save that requirement.');
            }
        } catch (e) {
            onError?.('Could not save that requirement.');
        } finally {
            setSavingType(null);
        }
    };

    if (loading && !data) {
        return <div className="text-xs text-slate-500 py-8 text-center">Loading portfolio completeness…</div>;
    }
    if (!data) {
        return <div className="text-xs text-slate-500 py-8 text-center">No completeness data available.</div>;
    }

    const { filters, summary, mostCommonlyMissing, learners, learnershipsWithoutRequirements } = data;
    const docs = summary.documents;
    const subs = summary.submissions;
    const totalExempt = docs.itemsExempt + subs.itemsExempt;

    return (
        <div className="space-y-6">
            <div className="border-b border-slate-300 pb-3">
                <h2 className="text-lg font-bold text-slate-900">Portfolio Completeness</h2>
                <p className="text-xs text-slate-500">
                    Readiness for a SETA submission, per learnership and cohort. People and documents
                    are counted separately — read the label on every number.
                </p>
            </div>

            {/* Filters */}
            <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm p-5 flex flex-wrap items-end gap-4">
                <div>
                    <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">
                        Learnership
                    </label>
                    <select
                        value={learnershipId}
                        onChange={(e) => { setLearnershipId(e.target.value); setRequirements(null); }}
                        className="border border-slate-300 rounded-xl px-3 py-2 text-xs font-semibold text-slate-700 bg-white"
                    >
                        <option value="">All learnerships</option>
                        {filters.learnerships.map(l => (
                            <option key={l.id} value={l.id}>{l.name}</option>
                        ))}
                    </select>
                </div>
                <div>
                    <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">
                        Cohort
                    </label>
                    <select
                        value={cohort}
                        onChange={(e) => setCohort(e.target.value)}
                        className="border border-slate-300 rounded-xl px-3 py-2 text-xs font-semibold text-slate-700 bg-white"
                    >
                        <option value="">All cohorts</option>
                        {filters.cohorts.map(c => <option key={c} value={c}>{c}</option>)}
                    </select>
                </div>
                {learnershipId && (
                    <button
                        type="button"
                        onClick={() => (requirements ? setRequirements(null) : loadRequirements())}
                        className="border border-slate-300 px-4 py-2 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50"
                    >
                        {requirements ? 'Hide required documents' : 'Required documents'}
                    </button>
                )}
                <span className="text-[10px] text-slate-400 ml-auto">
                    As at {new Date(data.generatedAt).toLocaleString()}
                </span>
            </div>

            {filters.mixedLearnerships && (
                <div className="bg-amber-50 border border-amber-200 text-amber-800 rounded-xl p-4 text-xs font-semibold">
                    These figures span more than one learnership. Different qualifications require
                    different evidence, so "how many are complete" across all of them is not a number
                    to act on — pick a learnership before preparing a submission.
                </div>
            )}

            {learnershipsWithoutRequirements?.length > 0 && (
                <div className="bg-rose-50 border border-rose-200 text-rose-800 rounded-xl p-4 text-xs font-semibold">
                    No document requirements are configured for: {learnershipsWithoutRequirements.join(', ')}.
                    Those learners are being measured against the default set, so their figures may
                    overstate what is outstanding.
                </div>
            )}

            {/* People */}
            <div>
                <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">
                    Learners — {summary.learnersInScope} in scope
                </h3>
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard label="Learners complete" value={summary.learnersComplete} tone="emerald" />
                    <StatCard label="Learners with something outstanding" value={summary.learnersWithSomethingOutstanding} tone="rose" />
                    <StatCard label="Learners awaiting review only" value={summary.learnersAwaitingReview} tone="amber" />
                    <StatCard label="Learners not fully in scope" value={summary.learnersNotFullyInScope} tone="slate" />
                </div>
                <p className="text-[10px] text-slate-500 mt-2">
                    These four count people, are mutually exclusive, and add up to the {summary.learnersInScope} in
                    scope. Of those outstanding, {summary.learnersWithDocumentsOutstanding} are short a document
                    and {summary.learnersWithSubmissionsOutstanding} are short a submission — somebody short of
                    both is in both figures.
                </p>
            </div>

            {/* Things */}
            <div>
                <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">
                    Items — documents and submissions, counted as items not people
                </h3>
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard label="Documents outstanding" value={docs.itemsOutstanding} tone="rose" />
                    <StatCard label="Documents awaiting review" value={docs.itemsAwaitingReview} tone="amber" />
                    <StatCard label="Submissions outstanding" value={subs.itemsOutstanding} tone="rose" />
                    <StatCard label="Submissions awaiting marking" value={subs.itemsAwaitingReview} tone="amber" />
                </div>
                {totalExempt > 0 && (
                    <p className="text-[10px] text-slate-500 mt-2">
                        A further {totalExempt} item{totalExempt === 1 ? ' is' : 's are'} exempt — the learner
                        registered before the requirement took effect, or the session closed before they joined,
                        so they were never asked. Exempt items are not counted as outstanding and do not make a
                        learner complete. To start counting a cohort, move a requirement's effective date back
                        under "Required documents" — after the cohort has actually been asked.
                    </p>
                )}
            </div>

            {/* Requirements editor */}
            {requirements && (
                <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm p-5">
                    <h3 className="text-sm font-bold text-slate-900 mb-1">
                        Required documents — {requirements.learnershipName}
                    </h3>
                    <p className="text-[10px] text-slate-500 mb-4">
                        What this learnership asks of its learners, and from when. Clearing the date counts every
                        learner including past cohorts; setting one exempts anybody who registered before it.
                    </p>
                    <div className="overflow-x-auto">
                        <table className="w-full text-xs">
                            <thead>
                                <tr className="text-left text-[10px] font-bold text-slate-400 uppercase tracking-wider border-b border-slate-200">
                                    <th className="py-2 pr-4">Document</th>
                                    <th className="py-2 pr-4">PoE section</th>
                                    <th className="py-2 pr-4">Required</th>
                                    <th className="py-2 pr-4">Required from</th>
                                    <th className="py-2"></th>
                                </tr>
                            </thead>
                            <tbody>
                                {requirements.requirements.map(row => (
                                    <tr key={row.documentType} className="border-b border-slate-100">
                                        <td className="py-2 pr-4 font-semibold text-slate-800">{row.documentLabel}</td>
                                        <td className="py-2 pr-4 text-slate-500">Section {row.poeSection}</td>
                                        <td className="py-2 pr-4">
                                            <button
                                                type="button"
                                                disabled={savingType === row.documentType}
                                                onClick={() => saveRequirement({
                                                    documentType: row.documentType,
                                                    required: !row.required
                                                })}
                                                className={`px-3 py-1 rounded-lg font-bold ${
                                                    row.required
                                                        ? 'bg-emerald-100 text-emerald-800'
                                                        : 'bg-slate-200 text-slate-600'
                                                }`}
                                            >
                                                {row.required ? 'Required' : 'Not required'}
                                            </button>
                                        </td>
                                        <td className="py-2 pr-4">
                                            <input
                                                type="date"
                                                value={row.requiredFrom || ''}
                                                disabled={savingType === row.documentType}
                                                onChange={(e) => saveRequirement({
                                                    documentType: row.documentType,
                                                    requiredFrom: e.target.value || null,
                                                    clearRequiredFrom: !e.target.value
                                                })}
                                                className="border border-slate-300 rounded-lg px-2 py-1 text-xs"
                                            />
                                        </td>
                                        <td className="py-2">
                                            {row.requiredFrom && (
                                                <button
                                                    type="button"
                                                    disabled={savingType === row.documentType}
                                                    onClick={() => saveRequirement({
                                                        documentType: row.documentType,
                                                        clearRequiredFrom: true
                                                    })}
                                                    className="text-[10px] font-semibold text-blue-600 hover:underline"
                                                >
                                                    Count everybody
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* Most commonly missing */}
            {mostCommonlyMissing.length > 0 && (
                <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm p-5">
                    <h3 className="text-sm font-bold text-slate-900 mb-1">Most commonly outstanding</h3>
                    <p className="text-[10px] text-slate-500 mb-3">
                        Counted in learners: how many people are short of each item.
                    </p>
                    <div className="space-y-2">
                        {mostCommonlyMissing.map(item => (
                            <div key={item.key} className="flex items-center justify-between text-xs">
                                <span className="font-semibold text-slate-700">
                                    {item.label}
                                    <span className="ml-2 text-[10px] font-bold text-slate-400 uppercase">
                                        {item.kind === 'DOCUMENT' ? 'Document' : 'Submission'}
                                    </span>
                                </span>
                                <span className="font-bold text-rose-700">
                                    {item.learnersMissingIt} learner{item.learnersMissingIt === 1 ? '' : 's'}
                                </span>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Learner table */}
            <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm p-5">
                <h3 className="text-sm font-bold text-slate-900 mb-3">
                    Learners — worst first
                </h3>
                {learners.length === 0 ? (
                    <p className="text-xs text-slate-500 py-4">No learners match this filter.</p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-xs">
                            <thead>
                                <tr className="text-left text-[10px] font-bold text-slate-400 uppercase tracking-wider border-b border-slate-200">
                                    <th className="py-2 pr-4">Learner</th>
                                    <th className="py-2 pr-4">Cohort</th>
                                    <th className="py-2 pr-4">Learnership</th>
                                    <th className="py-2 pr-4">Status</th>
                                    <th className="py-2 pr-4">Docs outstanding</th>
                                    <th className="py-2 pr-4">Submissions outstanding</th>
                                    <th className="py-2 pr-4">Exempt</th>
                                    <th className="py-2"></th>
                                </tr>
                            </thead>
                            <tbody>
                                {learners.map(row => (
                                    <tr key={row.learnerId} className="border-b border-slate-100 hover:bg-slate-50">
                                        <td className="py-2 pr-4">
                                            <span className="font-semibold text-slate-800">{row.fullName}</span>
                                            <span className="block text-[10px] text-slate-400">{row.learnerCode}</span>
                                        </td>
                                        <td className="py-2 pr-4 text-slate-600">{row.cohort || '—'}</td>
                                        <td className="py-2 pr-4 text-slate-600">{row.learnershipName || 'Unassigned'}</td>
                                        <td className="py-2 pr-4">
                                            <span className={`inline-block whitespace-nowrap px-2 py-1 rounded-lg font-bold ${LEARNER_STATE_STYLES[row.state]}`}>
                                                {LEARNER_STATE_LABELS[row.state]}
                                            </span>
                                        </td>
                                        <td className="py-2 pr-4 font-bold text-slate-700">{row.documents.itemsOutstanding}</td>
                                        <td className="py-2 pr-4 font-bold text-slate-700">{row.submissions.itemsOutstanding}</td>
                                        <td className="py-2 pr-4 text-slate-500">
                                            {row.documents.itemsExempt + row.submissions.itemsExempt}
                                        </td>
                                        <td className="py-2">
                                            <button
                                                type="button"
                                                onClick={() => openChecklist(row)}
                                                className="text-[10px] font-semibold text-blue-600 hover:underline"
                                            >
                                                Checklist
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {/* Drill-down */}
            {openLearner && (
                <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-[100]"
                     onClick={() => { setOpenLearner(null); setChecklist(null); }}>
                    <div className="bg-white rounded-2xl shadow-xl max-w-3xl w-full max-h-[85vh] overflow-y-auto p-6"
                         onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-start justify-between mb-4">
                            <div>
                                <h3 className="text-sm font-bold text-slate-900">{openLearner.fullName}</h3>
                                <p className="text-[10px] text-slate-500">
                                    {openLearner.learnerCode} · {openLearner.learnershipName || 'No learnership'}
                                    {openLearner.cohort ? ` · ${openLearner.cohort}` : ''}
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={() => { setOpenLearner(null); setChecklist(null); }}
                                className="text-slate-400 hover:text-slate-700 font-bold"
                            >
                                ✕
                            </button>
                        </div>
                        {!checklist ? (
                            <p className="text-xs text-slate-500">Loading checklist…</p>
                        ) : (
                            <div className="space-y-5">
                                <DocumentReviewSection
                                    items={checklist.documentItems}
                                    documents={documents}
                                    reviewBusyId={reviewBusyId}
                                    rejectDraftId={rejectDraftId}
                                    rejectNote={rejectNote}
                                    reviewError={reviewError}
                                    onOpen={openDocumentFile}
                                    onAccept={acceptDocument}
                                    onStartReject={(id) => { setRejectDraftId(id); setRejectNote(''); setReviewError(''); }}
                                    onCancelReject={() => { setRejectDraftId(null); setRejectNote(''); }}
                                    onRejectNoteChange={setRejectNote}
                                    onConfirmReject={confirmReject}
                                />
                                <ChecklistSection title="Submissions" items={checklist.submissionItems} />
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

const StatCard = ({ label, value, tone }) => {
    const toneClass = {
        emerald: 'text-emerald-600',
        rose: 'text-rose-600',
        amber: 'text-amber-600',
        slate: 'text-slate-600'
    }[tone] || 'text-slate-600';
    return (
        <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm p-5 h-full flex flex-col justify-between">
            <h3 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider leading-tight">{label}</h3>
            <span className={`text-3xl font-extrabold mt-2 block ${toneClass}`}>{value}</span>
        </div>
    );
};

/**
 * The Documents half of the drill-down: the same checklist rows as before (one per required
 * document type, in the learner's required state), each now enriched — where a document
 * actually exists — with the file itself and the accept/reject actions Phase 3 never got a
 * screen for. A MISSING or EXEMPT row has nothing to open or decide, so it renders exactly as
 * the plain checklist row did.
 */
const DocumentReviewSection = ({
    items, documents, reviewBusyId, rejectDraftId, rejectNote, reviewError,
    onOpen, onAccept, onStartReject, onCancelReject, onRejectNoteChange, onConfirmReject
}) => {
    const currentFor = (type) => documents.find(d => d.documentType === type && d.current) || null;
    const historyFor = (type) => documents
        .filter(d => d.documentType === type && !d.current)
        .sort((a, b) => (b.version || 0) - (a.version || 0));
    // OTHER is never a required type, so it never appears in `items` — every OTHER upload is
    // shown here on its own, the same way the learner's own "other evidence" list works.
    const otherDocuments = documents
        .filter(d => d.documentType === 'OTHER')
        .sort((a, b) => new Date(b.uploadedAt) - new Date(a.uploadedAt));
    // "Other evidence" has no checklist row of its own to borrow a state/label from — it maps
    // its own ReviewStatus (PENDING/ACCEPTED/REJECTED) onto the same vocabulary the checklist
    // rows use, so the badge reads and colours the same way everywhere on this screen.
    const OTHER_STATE = { PENDING: 'AWAITING_REVIEW', ACCEPTED: 'ACCEPTED', REJECTED: 'REJECTED' };

    return (
        <div>
            <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Documents</h4>
            {reviewError && (
                <p className="text-[11px] font-medium text-rose-700 bg-rose-50 border border-rose-200 rounded-lg px-3 py-2 mb-2">
                    {reviewError}
                </p>
            )}
            {items.length === 0 && otherDocuments.length === 0 ? (
                <p className="text-xs text-slate-400">Nothing on this part of the checklist.</p>
            ) : (
                <div className="space-y-2">
                    {items.map((item, i) => (
                        <DocumentRow
                            key={`${item.key}-${i}`}
                            item={item}
                            current={currentFor(item.key)}
                            history={historyFor(item.key)}
                            busy={reviewBusyId}
                            rejectDraftId={rejectDraftId}
                            rejectNote={rejectNote}
                            onOpen={onOpen}
                            onAccept={onAccept}
                            onStartReject={onStartReject}
                            onCancelReject={onCancelReject}
                            onRejectNoteChange={onRejectNoteChange}
                            onConfirmReject={onConfirmReject}
                        />
                    ))}
                    {otherDocuments.map((doc) => (
                        <DocumentRow
                            key={`other-${doc.id}`}
                            item={{ key: 'OTHER', label: doc.originalFilename, state: OTHER_STATE[doc.status] || doc.status, detail: doc.reviewNote }}
                            current={doc}
                            history={[]}
                            busy={reviewBusyId}
                            rejectDraftId={rejectDraftId}
                            rejectNote={rejectNote}
                            onOpen={onOpen}
                            onAccept={onAccept}
                            onStartReject={onStartReject}
                            onCancelReject={onCancelReject}
                            onRejectNoteChange={onRejectNoteChange}
                            onConfirmReject={onConfirmReject}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};

const DocumentRow = ({
    item, current, history, busy, rejectDraftId, rejectNote,
    onOpen, onAccept, onStartReject, onCancelReject, onRejectNoteChange, onConfirmReject
}) => {
    const isRejecting = current && rejectDraftId === current.id;
    const isBusy = current && busy === current.id;

    return (
        <div className={`border rounded-xl px-3 py-2 ${STATE_STYLES[item.state] || 'bg-slate-50 border-slate-200'}`}>
            <div className="flex items-center justify-between gap-3 flex-wrap">
                <div>
                    <span className="text-xs font-semibold">{item.label}</span>
                    {current && (
                        <span className="block text-[10px] opacity-70">
                            {current.originalFilename}{current.version > 1 ? ` · v${current.version}` : ''}
                        </span>
                    )}
                </div>
                <span className="text-[10px] font-bold uppercase tracking-wider">
                    {STATE_LABELS[item.state] || item.state}
                </span>
            </div>
            {item.detail && <p className="text-[10px] mt-1 opacity-80">{item.detail}</p>}

            {current && (
                <div className="flex items-center gap-3 mt-2">
                    <button type="button" onClick={() => onOpen(current)}
                        className="text-[10px] font-semibold text-blue-600 hover:underline">
                        Open
                    </button>
                    <button type="button" disabled={isBusy} onClick={() => onAccept(current.id)}
                        className="text-[10px] font-semibold text-emerald-700 hover:underline disabled:opacity-50">
                        Accept
                    </button>
                    <button type="button" disabled={isBusy} onClick={() => onStartReject(current.id)}
                        className="text-[10px] font-semibold text-rose-700 hover:underline disabled:opacity-50">
                        Reject
                    </button>
                </div>
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
                            onClick={() => onConfirmReject(current.id)}
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

            {history.length > 0 && (
                <details className="mt-2">
                    <summary className="text-[10px] text-slate-500 cursor-pointer">
                        {history.length} earlier version{history.length > 1 ? 's' : ''}
                    </summary>
                    <div className="pt-1.5 space-y-1">
                        {history.map(h => (
                            <div key={h.id} className="flex items-center justify-between text-[10px] text-slate-500">
                                <span className="truncate">v{h.version} · {h.originalFilename} · {h.status}</span>
                                <button type="button" onClick={() => onOpen(h)}
                                    className="font-semibold text-blue-600 hover:underline flex-shrink-0">Open</button>
                            </div>
                        ))}
                    </div>
                </details>
            )}
        </div>
    );
};

const ChecklistSection = ({ title, items }) => (
    <div>
        <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">{title}</h4>
        {items.length === 0 ? (
            <p className="text-xs text-slate-400">Nothing on this part of the checklist.</p>
        ) : (
            <div className="space-y-2">
                {items.map((item, i) => (
                    <div key={`${item.key}-${i}`}
                         className={`border rounded-xl px-3 py-2 ${STATE_STYLES[item.state] || 'bg-slate-50 border-slate-200'}`}>
                        <div className="flex items-center justify-between gap-3">
                            <span className="text-xs font-semibold">{item.label}</span>
                            <span className="text-[10px] font-bold uppercase tracking-wider">
                                {STATE_LABELS[item.state] || item.state}
                            </span>
                        </div>
                        {item.detail && <p className="text-[10px] mt-1 opacity-80">{item.detail}</p>}
                    </div>
                ))}
            </div>
        )}
    </div>
);

export default PoeCompleteness;
