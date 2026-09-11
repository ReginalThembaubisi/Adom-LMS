import React, { useState, useEffect, useCallback, useRef } from 'react';

/**
 * The SETA export.
 *
 * Async by design: starting an export returns immediately with a QUEUED job, and this panel
 * polls for its progress rather than holding a request open. A whole-learnership export can be
 * hundreds of files fetched one at a time — the kind of thing that times out a browser tab long
 * before it times out the server, which is exactly the failure the brief calls out by name.
 */

const STATUS_STYLES = {
    QUEUED: 'bg-slate-100 text-slate-700',
    RUNNING: 'bg-amber-100 text-amber-800',
    COMPLETED: 'bg-emerald-100 text-emerald-800',
    FAILED: 'bg-rose-100 text-rose-800'
};

const SCOPE_LABELS = {
    LEARNERSHIP: 'Whole learnership',
    COHORT: 'One cohort',
    SECTION: 'One section',
    LEARNER: 'One learner',
    MODERATION_SAMPLE: 'Moderation sample'
};

const CATEGORY_LABELS = {
    FUNDAMENTAL: 'Fundamentals',
    CORE: 'Cores',
    ELECTIVE: 'Electives'
};

const POLL_MS = 5000;

const PoeExports = ({ token, learnerships, categories, onAuthFailure, onError, onInfo }) => {
    const [jobs, setJobs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [scopeType, setScopeType] = useState('LEARNERSHIP');
    const [learnershipId, setLearnershipId] = useState('');
    const [cohort, setCohort] = useState('');
    const [cohortOptions, setCohortOptions] = useState([]);
    const [categoryId, setCategoryId] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const pollRef = useRef(null);

    const authHeaders = useCallback(() => ({ Authorization: `Basic ${token}` }), [token]);

    const loadJobs = useCallback(async () => {
        try {
            const res = await fetch('/api/poe/export', { headers: authHeaders() });
            if (res.status === 401) { onAuthFailure?.(); return; }
            if (!res.ok) { onError?.('Could not load the export list.'); return; }
            setJobs(await res.json());
        } catch (e) {
            onError?.('Could not load the export list.');
        } finally {
            setLoading(false);
        }
    }, [authHeaders, onAuthFailure, onError]);

    useEffect(() => { loadJobs(); }, [loadJobs]);

    // Poll only while something is actually in flight — an export that finished ten minutes
    // ago has no reason to keep this tab making requests every five seconds.
    useEffect(() => {
        const hasActive = jobs.some(j => j.status === 'QUEUED' || j.status === 'RUNNING');
        if (hasActive && !pollRef.current) {
            pollRef.current = setInterval(loadJobs, POLL_MS);
        } else if (!hasActive && pollRef.current) {
            clearInterval(pollRef.current);
            pollRef.current = null;
        }
        return () => {
            if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
        };
    }, [jobs, loadJobs]);

    // The cohort list belongs to whichever learnership is selected.
    useEffect(() => {
        setCohort('');
        setCohortOptions([]);
        if (scopeType !== 'COHORT' || !learnershipId) return;
        (async () => {
            try {
                const res = await fetch(`/api/admin/poe/completeness?learnershipId=${learnershipId}`, {
                    headers: authHeaders()
                });
                if (res.ok) {
                    const data = await res.json();
                    setCohortOptions(data.filters?.cohorts || []);
                }
            } catch (e) {
                // Non-fatal: the admin can still type nothing and the request is rejected
                // server-side with a clear message rather than this silently breaking the form.
            }
        })();
    }, [scopeType, learnershipId, authHeaders]);

    const categoriesForLearnership = categories.filter(c => String(c.learnershipId) === String(learnershipId));

    const startExport = async () => {
        if (!learnershipId) { onError?.('Choose a learnership first.'); return; }
        if (scopeType === 'COHORT' && !cohort) { onError?.('Choose a cohort.'); return; }
        if (scopeType === 'SECTION' && !categoryId) { onError?.('Choose a section.'); return; }

        setSubmitting(true);
        try {
            const body = { scopeType, learnershipId: Number(learnershipId) };
            if (scopeType === 'COHORT') body.cohort = cohort;
            if (scopeType === 'SECTION') body.categoryId = Number(categoryId);

            const res = await fetch('/api/poe/export', {
                method: 'POST',
                headers: { ...authHeaders(), 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            if (res.status === 401) { onAuthFailure?.(); return; }
            if (res.ok) {
                const job = await res.json();
                onInfo?.(`Export queued for ${job.scopeLabel} — ${job.learnerCount} learner(s) in scope.`);
                loadJobs();
            } else {
                const err = await res.json().catch(() => ({}));
                onError?.(err.message || err.error || 'Could not start that export.');
            }
        } catch (e) {
            onError?.('Could not start that export.');
        } finally {
            setSubmitting(false);
        }
    };

    const download = async (job) => {
        try {
            const res = await fetch(`/api/poe/export/${job.id}/download`, { headers: authHeaders() });
            if (res.status === 401) { onAuthFailure?.(); return; }
            if (!res.ok) { onError?.('That export could not be downloaded.'); return; }
            const blob = await res.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `poe_export_${job.id}.zip`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
        } catch (e) {
            onError?.('That export could not be downloaded.');
        }
    };

    return (
        <div className="space-y-6">
            <div className="border-b border-slate-300 pb-3">
                <h2 className="text-lg font-bold text-slate-900">SETA Export</h2>
                <p className="text-xs text-slate-500">
                    Builds the folder structure a SETA submission expects, zipped, with an index
                    and a signatures manifest. Runs in the background — leave this open or come
                    back later, the job keeps going either way.
                </p>
            </div>

            <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm p-5 space-y-4">
                <div className="flex flex-wrap gap-4 items-end">
                    <div>
                        <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">
                            Scope
                        </label>
                        <select
                            value={scopeType}
                            onChange={(e) => setScopeType(e.target.value)}
                            className="border border-slate-300 rounded-xl px-3 py-2 text-xs font-semibold text-slate-700 bg-white"
                        >
                            <option value="LEARNERSHIP">Whole learnership</option>
                            <option value="COHORT">One cohort</option>
                            <option value="SECTION">One section</option>
                        </select>
                    </div>
                    <div>
                        <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">
                            Learnership
                        </label>
                        <select
                            value={learnershipId}
                            onChange={(e) => { setLearnershipId(e.target.value); setCategoryId(''); }}
                            className="border border-slate-300 rounded-xl px-3 py-2 text-xs font-semibold text-slate-700 bg-white"
                        >
                            <option value="">Choose one</option>
                            {learnerships.map(l => <option key={l.id} value={l.id}>{l.name}</option>)}
                        </select>
                    </div>
                    {scopeType === 'COHORT' && (
                        <div>
                            <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">
                                Cohort
                            </label>
                            <select
                                value={cohort}
                                onChange={(e) => setCohort(e.target.value)}
                                disabled={!learnershipId}
                                className="border border-slate-300 rounded-xl px-3 py-2 text-xs font-semibold text-slate-700 bg-white"
                            >
                                <option value="">Choose one</option>
                                {cohortOptions.map(c => <option key={c} value={c}>{c}</option>)}
                            </select>
                        </div>
                    )}
                    {scopeType === 'SECTION' && (
                        <div>
                            <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">
                                Section
                            </label>
                            <select
                                value={categoryId}
                                onChange={(e) => setCategoryId(e.target.value)}
                                disabled={!learnershipId}
                                className="border border-slate-300 rounded-xl px-3 py-2 text-xs font-semibold text-slate-700 bg-white"
                            >
                                <option value="">Choose one</option>
                                {categoriesForLearnership.map(c => (
                                    <option key={c.id} value={c.id}>
                                        {CATEGORY_LABELS[c.categoryType] || c.categoryType}
                                    </option>
                                ))}
                            </select>
                        </div>
                    )}
                    <button
                        type="button"
                        onClick={startExport}
                        disabled={submitting}
                        className="bg-blue-600 hover:bg-blue-700 active:scale-[0.99] disabled:opacity-50 text-white font-semibold text-xs py-2.5 px-5 rounded-xl shadow-xs shadow-blue-500/20 transition-all duration-150"
                    >
                        {submitting ? 'Starting…' : 'Start export'}
                    </button>
                </div>
                {scopeType === 'SECTION' && (
                    <p className="text-[10px] text-slate-500">
                        A section export leaves out personal details and evidence — it only
                        rebuilds the guides, activities and feedback for the chosen section.
                        Use it to re-run one folder rather than the whole learnership.
                    </p>
                )}
            </div>

            <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm p-5">
                <h3 className="text-sm font-bold text-slate-900 mb-3">Exports</h3>
                {loading ? (
                    <p className="text-xs text-slate-500 py-4">Loading…</p>
                ) : jobs.length === 0 ? (
                    <p className="text-xs text-slate-500 py-4">No exports yet.</p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-xs">
                            <thead>
                                <tr className="text-left text-[10px] font-bold text-slate-400 uppercase tracking-wider border-b border-slate-200">
                                    <th className="py-2 pr-4">Scope</th>
                                    <th className="py-2 pr-4">Status</th>
                                    <th className="py-2 pr-4">Learners</th>
                                    <th className="py-2 pr-4">Files</th>
                                    <th className="py-2 pr-4">Requested</th>
                                    <th className="py-2"></th>
                                </tr>
                            </thead>
                            <tbody>
                                {jobs.map(job => (
                                    <tr key={job.id} className="border-b border-slate-100">
                                        <td className="py-2 pr-4">
                                            <span className="font-semibold text-slate-800">{job.scopeLabel}</span>
                                            <span className="block text-[10px] text-slate-400">
                                                {SCOPE_LABELS[job.scopeType] || job.scopeType}
                                            </span>
                                        </td>
                                        <td className="py-2 pr-4">
                                            <span className={`px-2 py-1 rounded-lg font-bold whitespace-nowrap inline-block ${STATUS_STYLES[job.status] || ''}`}>
                                                {job.status}
                                            </span>
                                            {job.status === 'FAILED' && job.error && (
                                                <span className="block text-[10px] text-rose-600 mt-1">{job.error}</span>
                                            )}
                                        </td>
                                        <td className="py-2 pr-4 text-slate-700">{job.learnerCount ?? '—'}</td>
                                        <td className="py-2 pr-4 text-slate-700">{job.fileCount ?? '—'}</td>
                                        <td className="py-2 pr-4 text-slate-500">
                                            {job.createdAt ? new Date(job.createdAt).toLocaleString() : '—'}
                                        </td>
                                        <td className="py-2">
                                            {job.downloadAvailable && (
                                                <button
                                                    type="button"
                                                    onClick={() => download(job)}
                                                    className="text-[10px] font-semibold text-blue-600 hover:underline"
                                                >
                                                    Download
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
};

export default PoeExports;
