import React, { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Phase 10: importing a zip of historical PoE folders that predate this system.
 *
 * Nothing here writes a document. Every path through this screen — choosing a file, seeing the
 * preview, even clicking "Confirm import" — stops at a preview until the server's own confirm
 * call returns; cancelling is always safe, by construction, because there is nothing else this
 * screen can do to a learner's records before that call.
 */

const CONFIDENCE_STYLES = {
    EXACT: 'bg-emerald-100 text-emerald-800',
    LIKELY: 'bg-amber-100 text-amber-800',
    SPECIFIED: 'bg-sky-100 text-sky-800',
    UNMATCHED: 'bg-rose-100 text-rose-800'
};

const STATUS_STYLES = {
    PREVIEWED: 'bg-slate-100 text-slate-700',
    CONFIRMED: 'bg-emerald-100 text-emerald-800',
    CANCELLED: 'bg-rose-100 text-rose-800'
};

const LegacyImport = ({ token, onAuthFailure, onError, onInfo }) => {
    const [batches, setBatches] = useState([]);
    const [loading, setLoading] = useState(true);
    const [activeBatch, setActiveBatch] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [singleLearnerMode, setSingleLearnerMode] = useState(false);
    const [learners, setLearners] = useState([]);
    const [learnerId, setLearnerId] = useState('');
    const fileInputRef = useRef(null);

    const authHeaders = useCallback(() => ({ Authorization: `Basic ${token}` }), [token]);

    const loadBatches = useCallback(async () => {
        try {
            const res = await fetch('/api/admin/poe/import', { headers: authHeaders() });
            if (res.status === 401) { onAuthFailure?.(); return; }
            if (!res.ok) { onError?.('Could not load past imports.'); return; }
            setBatches(await res.json());
        } catch {
            onError?.('Could not load past imports.');
        } finally {
            setLoading(false);
        }
    }, [authHeaders, onAuthFailure, onError]);

    useEffect(() => { loadBatches(); }, [loadBatches]);

    useEffect(() => {
        if (!singleLearnerMode || learners.length > 0) return;
        (async () => {
            try {
                const res = await fetch('/api/admin/learners', { headers: authHeaders() });
                if (res.ok) setLearners(await res.json());
            } catch {
                // The dropdown just stays empty; the admin can still retype the id if they know it.
            }
        })();
    }, [singleLearnerMode, learners.length, authHeaders]);

    const handleFileChosen = async (file) => {
        if (!file) return;
        setSubmitting(true);
        try {
            const formData = new FormData();
            formData.append('file', file);
            if (singleLearnerMode && learnerId) {
                formData.append('learnerId', learnerId);
            }
            const res = await fetch('/api/admin/poe/import', {
                method: 'POST', headers: authHeaders(), body: formData
            });
            if (res.status === 401) { onAuthFailure?.(); return; }
            const data = await res.json().catch(() => ({}));
            if (!res.ok) throw new Error(data.message || 'Could not read that zip file.');
            setActiveBatch(data);
            await loadBatches();
        } catch (err) {
            onError?.(err.message || 'Could not read that zip file.');
        } finally {
            setSubmitting(false);
            if (fileInputRef.current) fileInputRef.current.value = '';
        }
    };

    const confirmBatch = async (batchId) => {
        if (!window.confirm('Write every importable, matched file shown below into the matched learners’ document vaults? This cannot be undone.')) {
            return;
        }
        setSubmitting(true);
        try {
            const res = await fetch(`/api/admin/poe/import/${batchId}/confirm`, { method: 'POST', headers: authHeaders() });
            if (res.status === 401) { onAuthFailure?.(); return; }
            const data = await res.json().catch(() => ({}));
            if (!res.ok) throw new Error(data.message || 'Could not confirm this import.');
            setActiveBatch(data);
            onInfo?.(`Imported ${data.importedCount} file(s)${data.failedCount ? `, ${data.failedCount} failed` : ''}.`);
            await loadBatches();
        } catch (err) {
            onError?.(err.message || 'Could not confirm this import.');
        } finally {
            setSubmitting(false);
        }
    };

    const cancelBatch = async (batchId) => {
        setSubmitting(true);
        try {
            const res = await fetch(`/api/admin/poe/import/${batchId}/cancel`, { method: 'POST', headers: authHeaders() });
            if (res.status === 401) { onAuthFailure?.(); return; }
            const data = await res.json().catch(() => ({}));
            if (!res.ok) throw new Error(data.message || 'Could not cancel this import.');
            setActiveBatch(data);
            onInfo?.('Cancelled. Nothing was written.');
            await loadBatches();
        } catch (err) {
            onError?.(err.message || 'Could not cancel this import.');
        } finally {
            setSubmitting(false);
        }
    };

    const openBatch = async (batchId) => {
        try {
            const res = await fetch(`/api/admin/poe/import/${batchId}`, { headers: authHeaders() });
            if (res.ok) setActiveBatch(await res.json());
        } catch {
            onError?.('Could not load that import.');
        }
    };

    return (
        <div className="space-y-6">
            <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-4">
                <div>
                    <h3 className="text-sm font-semibold text-slate-800">Import historical PoE folders</h3>
                    <p className="text-xs text-slate-500 mt-1 leading-relaxed">
                        Upload a zip of one learner's folder, or a parent folder containing many. Nothing
                        is written until you review the preview below and confirm it — cancelling writes
                        nothing at all. Only personal documents (CV, ID, matric, agreement, and anything
                        else) are imported; module content is reported but skipped.
                    </p>
                </div>

                <label className="flex items-center gap-2 text-xs text-slate-700">
                    <input type="checkbox" checked={singleLearnerMode}
                        onChange={(e) => setSingleLearnerMode(e.target.checked)} />
                    This zip is one specific learner's own folder (not a parent of many)
                </label>

                {singleLearnerMode && (
                    <select value={learnerId} onChange={(e) => setLearnerId(e.target.value)}
                        className="text-xs border border-slate-300 rounded-lg px-2.5 py-2 w-full max-w-sm">
                        <option value="">Select the learner...</option>
                        {learners.map(l => (
                            <option key={l.id} value={l.id}>{l.fullName} ({l.learnerCode})</option>
                        ))}
                    </select>
                )}

                <div>
                    <input ref={fileInputRef} type="file" accept=".zip" className="hidden"
                        disabled={submitting}
                        onChange={(e) => handleFileChosen(e.target.files?.[0])} />
                    <button onClick={() => fileInputRef.current?.click()} disabled={submitting}
                        className="px-4 py-2 rounded-lg text-xs font-semibold text-white bg-indigo-600 disabled:opacity-50">
                        {submitting ? 'Working...' : 'Choose zip and preview'}
                    </button>
                </div>
            </div>

            {activeBatch && (
                <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-4">
                    <div className="flex items-center justify-between flex-wrap gap-2">
                        <div>
                            <h4 className="text-sm font-semibold text-slate-800">{activeBatch.originalFilename}</h4>
                            <p className="text-xs text-slate-500 mt-0.5">
                                {activeBatch.entryCount} file(s) found &middot; {activeBatch.importableCount} importable &middot;{' '}
                                {activeBatch.unmatchedCount} with no confident learner match
                            </p>
                        </div>
                        <span className={`text-[11px] font-semibold px-2.5 py-1 rounded-full ${STATUS_STYLES[activeBatch.status] || ''}`}>
                            {activeBatch.status}
                        </span>
                    </div>

                    {activeBatch.status === 'PREVIEWED' && (
                        <div className="flex gap-2">
                            <button onClick={() => confirmBatch(activeBatch.id)} disabled={submitting}
                                className="px-4 py-2 rounded-lg text-xs font-semibold text-white bg-emerald-600 disabled:opacity-50">
                                Confirm import ({activeBatch.importableCount} file(s))
                            </button>
                            <button onClick={() => cancelBatch(activeBatch.id)} disabled={submitting}
                                className="px-4 py-2 rounded-lg text-xs font-semibold text-slate-700 bg-slate-100 disabled:opacity-50">
                                Cancel — write nothing
                            </button>
                        </div>
                    )}

                    <div className="overflow-x-auto">
                        <table className="w-full text-xs">
                            <thead>
                                <tr className="text-left text-slate-500 border-b border-slate-200">
                                    <th className="py-2 pr-3">File</th>
                                    <th className="py-2 pr-3">Learner</th>
                                    <th className="py-2 pr-3">Match</th>
                                    <th className="py-2 pr-3">Document type</th>
                                    <th className="py-2 pr-3">Status</th>
                                    <th className="py-2 pr-3">Note</th>
                                </tr>
                            </thead>
                            <tbody>
                                {(activeBatch.entries || []).map(entry => (
                                    <tr key={entry.id} className="border-b border-slate-100">
                                        <td className="py-2 pr-3 max-w-[220px] truncate" title={entry.zipEntryPath}>{entry.zipEntryPath}</td>
                                        <td className="py-2 pr-3">{entry.matchedLearnerName || <span className="text-slate-400">no match</span>}</td>
                                        <td className="py-2 pr-3">
                                            <span className={`px-2 py-0.5 rounded-full font-semibold ${CONFIDENCE_STYLES[entry.matchConfidence] || ''}`}>
                                                {entry.matchConfidence}
                                            </span>
                                        </td>
                                        <td className="py-2 pr-3">{entry.documentLabel || '—'}</td>
                                        <td className="py-2 pr-3">
                                            {entry.importedDocumentId ? (
                                                <span className="text-emerald-700 font-semibold">Imported</span>
                                            ) : entry.importError ? (
                                                <span className="text-rose-700 font-semibold">Failed</span>
                                            ) : entry.importable ? (
                                                <span className="text-slate-600">Will import</span>
                                            ) : (
                                                <span className="text-slate-400">Not imported</span>
                                            )}
                                        </td>
                                        <td className="py-2 pr-3 text-slate-500 max-w-[260px]">{entry.importError || entry.note || ''}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            <div className="bg-white rounded-xl border border-slate-200 p-5">
                <h4 className="text-sm font-semibold text-slate-800 mb-3">Past imports</h4>
                {loading ? (
                    <p className="text-xs text-slate-400">Loading...</p>
                ) : batches.length === 0 ? (
                    <p className="text-xs text-slate-400">No imports yet.</p>
                ) : (
                    <table className="w-full text-xs">
                        <thead>
                            <tr className="text-left text-slate-500 border-b border-slate-200">
                                <th className="py-2 pr-3">File</th>
                                <th className="py-2 pr-3">Status</th>
                                <th className="py-2 pr-3">Imported</th>
                                <th className="py-2 pr-3">Created</th>
                                <th className="py-2 pr-3" />
                            </tr>
                        </thead>
                        <tbody>
                            {batches.map(b => (
                                <tr key={b.id} className="border-b border-slate-100">
                                    <td className="py-2 pr-3">{b.originalFilename}</td>
                                    <td className="py-2 pr-3">
                                        <span className={`px-2 py-0.5 rounded-full font-semibold ${STATUS_STYLES[b.status] || ''}`}>{b.status}</span>
                                    </td>
                                    <td className="py-2 pr-3">{b.importedCount}/{b.importableCount}</td>
                                    <td className="py-2 pr-3">{new Date(b.createdAt).toLocaleString()}</td>
                                    <td className="py-2 pr-3">
                                        <button onClick={() => openBatch(b.id)} className="text-indigo-600 font-semibold">View</button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>
        </div>
    );
};

export default LegacyImport;
