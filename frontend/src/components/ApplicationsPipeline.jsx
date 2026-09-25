import React, { useState, useEffect, useCallback } from 'react';

/**
 * The admissions pipeline: applications from the public website, moved through selection and,
 * once accepted, enrolled as learners on the LMS.
 *
 * Enrolling creates the learner's account (student number, learnership, modules) and carries
 * the documents they uploaded into their document vault, so nothing is typed or uploaded twice.
 */

const APPLICATION_STATUSES = [
    { key: 'SUBMITTED', label: 'New', style: 'bg-sky-100 text-sky-800' },
    { key: 'SCREENING', label: 'Screening', style: 'bg-indigo-100 text-indigo-800' },
    { key: 'SHORTLISTED', label: 'Shortlisted', style: 'bg-violet-100 text-violet-800' },
    { key: 'INTERVIEW', label: 'Interview', style: 'bg-amber-100 text-amber-800' },
    { key: 'ACCEPTED', label: 'Accepted', style: 'bg-emerald-100 text-emerald-800' },
    { key: 'WAITLISTED', label: 'Waitlisted', style: 'bg-orange-100 text-orange-800' },
    { key: 'DECLINED', label: 'Declined', style: 'bg-rose-100 text-rose-800' },
    { key: 'WITHDRAWN', label: 'Withdrawn', style: 'bg-slate-200 text-slate-700' },
    { key: 'ENROLLED', label: 'Enrolled', style: 'bg-emerald-600 text-white' }
];
const STATUS_BY_KEY = Object.fromEntries(APPLICATION_STATUSES.map(s => [s.key, s]));
const SETTABLE = APPLICATION_STATUSES.filter(s => s.key !== 'ENROLLED');

const inputClass = 'bg-white border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10';

const StatusPill = ({ status }) => {
    const s = STATUS_BY_KEY[status] || { label: status, style: 'bg-slate-100 text-slate-700' };
    return <span className={`text-[10px] font-bold uppercase rounded-full px-2 py-0.5 whitespace-nowrap ${s.style}`}>{s.label}</span>;
};

const formatDateTime = (iso) => iso
    ? new Date(iso).toLocaleString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
    : '—';
const formatDate = (iso) => iso
    ? new Date(iso).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })
    : '—';

const ApplicationsPipeline = ({ token, learnerships, initialLearnershipId, onAuthFailure, onError, onInfo }) => {
    const [learnershipId, setLearnershipId] = useState(initialLearnershipId ? String(initialLearnershipId) : '');
    const [statusFilter, setStatusFilter] = useState('');
    const [query, setQuery] = useState('');
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selected, setSelected] = useState(new Set());
    const [bulkStatus, setBulkStatus] = useState('SCREENING');
    const [detail, setDetail] = useState(null);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        setLearnershipId(initialLearnershipId ? String(initialLearnershipId) : '');
    }, [initialLearnershipId]);

    const request = useCallback(async (url, options = {}) => {
        const res = await fetch(url, {
            ...options,
            headers: { Authorization: `Basic ${token}`, ...(options.headers || {}) }
        });
        if (res.status === 401) {
            onAuthFailure();
            throw new Error('Signed out');
        }
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.message || `Request failed (${res.status})`);
        }
        return res;
    }, [token, onAuthFailure]);

    const report = useCallback((e) => { if (e.message !== 'Signed out') onError(e.message); }, [onError]);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (learnershipId) params.set('learnershipId', learnershipId);
            const res = await request(`/api/admin/applications?${params}`);
            setRows(await res.json());
            setSelected(new Set());
        } catch (e) {
            report(e);
        } finally {
            setLoading(false);
        }
    }, [learnershipId, request, report]);

    useEffect(() => { load(); }, [load]);

    const openDetail = async (id) => {
        try {
            const res = await request(`/api/admin/applications/${id}`);
            setDetail(await res.json());
        } catch (e) {
            report(e);
        }
    };

    const q = query.trim().toLowerCase();
    const visible = rows.filter(r =>
        (!statusFilter || r.status === statusFilter) &&
        (!q || [r.reference, r.fullName, r.idNumber, r.email, r.phone, r.town].some(v => (v || '').toLowerCase().includes(q)))
    );
    const counts = rows.reduce((m, r) => ({ ...m, [r.status]: (m[r.status] || 0) + 1 }), {});

    const toggle = (id) => setSelected(s => {
        const next = new Set(s);
        if (next.has(id)) next.delete(id); else next.add(id);
        return next;
    });
    const allVisibleSelected = visible.length > 0 && visible.every(r => selected.has(r.id));
    const toggleAll = () => setSelected(allVisibleSelected ? new Set() : new Set(visible.map(r => r.id)));

    const applyBulk = async () => {
        if (selected.size === 0) return;
        const label = STATUS_BY_KEY[bulkStatus].label;
        if (!window.confirm(`Move ${selected.size} application(s) to ${label}? Applicants are emailed about the change.`)) return;
        setBusy(true);
        try {
            const res = await request('/api/admin/applications/bulk-status', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ids: [...selected], status: bulkStatus })
            });
            const data = await res.json();
            onInfo(`${data.updated} moved to ${label}.` + (data.skipped.length ? ` Skipped: ${data.skipped.join('; ')}` : ''));
            load();
        } catch (e) {
            report(e);
        } finally {
            setBusy(false);
        }
    };

    const exportCsv = async () => {
        try {
            const params = new URLSearchParams();
            if (learnershipId) params.set('learnershipId', learnershipId);
            if (statusFilter) params.set('status', statusFilter);
            const res = await request(`/api/admin/applications/export.csv?${params}`);
            const url = URL.createObjectURL(await res.blob());
            const a = document.createElement('a');
            a.href = url;
            a.download = `applications-${new Date().toISOString().slice(0, 10)}.csv`;
            a.click();
            setTimeout(() => URL.revokeObjectURL(url), 10000);
        } catch (e) {
            report(e);
        }
    };

    if (detail) {
        return (
            <ApplicationDetail
                detail={detail}
                request={request}
                report={report}
                onInfo={onInfo}
                onBack={() => { setDetail(null); load(); }}
                onReload={() => openDetail(detail.summary.id)}
            />
        );
    }

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-end justify-between gap-3 border-b border-slate-300 pb-3">
                <div>
                    <h2 className="text-lg font-bold text-slate-900">Applications</h2>
                    <p className="text-xs text-slate-500">Applications from the website. Move them through selection, then enrol accepted applicants.</p>
                </div>
                <button type="button" onClick={exportCsv} className="text-xs font-semibold text-slate-700 border border-slate-300 rounded-xl px-4 py-2 hover:bg-slate-50">
                    Export CSV
                </button>
            </div>

            <div className="flex flex-wrap gap-3">
                <select value={learnershipId} onChange={e => setLearnershipId(e.target.value)} className={inputClass}>
                    <option value="">All learnerships</option>
                    {learnerships.map(l => <option key={l.id} value={l.id}>{l.name}</option>)}
                </select>
                <input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search name, ID, reference, phone…" className={`${inputClass} flex-1 min-w-[200px]`} />
            </div>

            <div className="flex flex-wrap gap-2">
                <button type="button" onClick={() => setStatusFilter('')}
                    className={`text-xs font-semibold rounded-full px-3 py-1.5 border ${statusFilter === '' ? 'bg-slate-900 text-white border-slate-900' : 'bg-white text-slate-700 border-slate-200'}`}>
                    All {rows.length}
                </button>
                {APPLICATION_STATUSES.map(s => (
                    <button key={s.key} type="button" onClick={() => setStatusFilter(s.key)}
                        className={`text-xs font-semibold rounded-full px-3 py-1.5 border ${statusFilter === s.key ? 'bg-slate-900 text-white border-slate-900' : 'bg-white text-slate-700 border-slate-200'}`}>
                        {s.label} {counts[s.key] || 0}
                    </button>
                ))}
            </div>

            {selected.size > 0 && (
                <div className="flex flex-wrap items-center gap-3 bg-slate-900 text-white rounded-2xl px-4 py-3">
                    <span className="text-xs font-semibold">{selected.size} selected</span>
                    <span className="text-xs text-slate-400">Move to</span>
                    <select value={bulkStatus} onChange={e => setBulkStatus(e.target.value)} className="bg-slate-800 border border-slate-700 rounded-lg px-2 py-1 text-xs">
                        {SETTABLE.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
                    </select>
                    <button type="button" disabled={busy} onClick={applyBulk} className="bg-[#C8F25A] text-slate-900 text-xs font-bold rounded-lg px-3 py-1.5 disabled:opacity-50">
                        Apply
                    </button>
                    <button type="button" onClick={() => setSelected(new Set())} className="text-xs text-slate-400 ml-auto">Clear</button>
                </div>
            )}

            {loading ? (
                <p className="text-sm text-slate-500">Loading…</p>
            ) : visible.length === 0 ? (
                <div className="bg-white border border-slate-200 rounded-2xl p-8 text-center">
                    <p className="text-sm font-semibold text-slate-800">No applications here</p>
                    <p className="text-xs text-slate-500 mt-1">Applications appear as soon as someone applies on the website for an open learnership.</p>
                </div>
            ) : (
                <div className="bg-white border border-slate-200 rounded-2xl overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="text-left text-[11px] uppercase tracking-wider text-slate-400 border-b border-slate-100">
                                <th className="p-3 w-8"><input type="checkbox" checked={allVisibleSelected} onChange={toggleAll} aria-label="Select all" /></th>
                                <th className="p-3">Applicant</th>
                                <th className="p-3">Learnership</th>
                                <th className="p-3">Location</th>
                                <th className="p-3">Docs</th>
                                <th className="p-3">Applied</th>
                                <th className="p-3">Status</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                            {visible.map(r => (
                                <tr key={r.id} className="hover:bg-slate-50 cursor-pointer" onClick={() => openDetail(r.id)}>
                                    <td className="p-3" onClick={e => e.stopPropagation()}>
                                        <input type="checkbox" checked={selected.has(r.id)} onChange={() => toggle(r.id)} aria-label={`Select ${r.fullName}`} />
                                    </td>
                                    <td className="p-3">
                                        <div className="font-semibold text-slate-900">{r.fullName}</div>
                                        <div className="text-xs text-slate-500">{r.reference} · {r.phone}</div>
                                    </td>
                                    <td className="p-3 text-xs text-slate-700">{r.learnershipName}</td>
                                    <td className="p-3 text-xs text-slate-700">{[r.town, r.province].filter(Boolean).join(', ') || '—'}</td>
                                    <td className="p-3 text-xs text-slate-700">{r.documentCount}</td>
                                    <td className="p-3 text-xs text-slate-700 whitespace-nowrap">{formatDate(r.submittedAt)}</td>
                                    <td className="p-3">
                                        <StatusPill status={r.status} />
                                        {r.enrolledLearnerCode && <div className="text-[11px] text-slate-500 mt-1">{r.enrolledLearnerCode}</div>}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

const Field = ({ label, value }) => (
    <div>
        <dt className="text-[11px] font-bold uppercase tracking-wider text-slate-400">{label}</dt>
        <dd className="text-sm text-slate-900 break-words">{value === null || value === undefined || value === '' ? '—' : value}</dd>
    </div>
);

const parseSubjects = (json) => {
    try {
        const list = JSON.parse(json);
        return Array.isArray(list) ? list : [];
    } catch {
        return [];
    }
};

const ApplicationDetail = ({ detail, request, report, onInfo, onBack, onReload }) => {
    const s = detail.summary;
    const [status, setStatus] = useState(s.status === 'ENROLLED' ? 'ACCEPTED' : s.status);
    const [note, setNote] = useState('');
    const [notify, setNotify] = useState(true);
    const [notes, setNotes] = useState(detail.staffNotes || '');
    const [cohort, setCohort] = useState('');
    const [busy, setBusy] = useState(false);
    const enrolled = s.status === 'ENROLLED';
    const subjects = parseSubjects(detail.subjectsJson);

    const run = async (fn) => {
        setBusy(true);
        try { await fn(); } catch (e) { report(e); } finally { setBusy(false); }
    };

    const changeStatus = () => run(async () => {
        await request(`/api/admin/applications/${s.id}/status`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status, note, notifyApplicant: notify })
        });
        onInfo(`${s.fullName} moved to ${STATUS_BY_KEY[status].label}.`);
        setNote('');
        onReload();
    });

    const saveNotes = () => run(async () => {
        await request(`/api/admin/applications/${s.id}/notes`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ staffNotes: notes })
        });
        onInfo('Notes saved.');
    });

    const enrol = () => run(async () => {
        if (!window.confirm(`Enrol ${s.fullName} as a learner on ${s.learnershipName}? This creates their LMS account and emails them their student number.`)) return;
        const res = await request(`/api/admin/applications/${s.id}/enrol`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ cohort: cohort || null })
        });
        const data = await res.json();
        onInfo(`${data.fullName} enrolled: student number ${data.learnerCode}, cohort ${data.cohort}, ${data.modulesEnrolled} modules, ${data.documentsCopied} documents carried across.`);
        onReload();
    });

    const openDocument = (doc) => run(async () => {
        // Opened as a blob so the admin credential stays in a header, never in a URL.
        const res = await request(`/api/admin/applications/${s.id}/documents/${doc.id}/view`);
        const url = URL.createObjectURL(await res.blob());
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
    });

    return (
        <div className="space-y-6">
            <div className="border-b border-slate-300 pb-3">
                <button type="button" onClick={onBack} className="text-xs font-semibold text-blue-600 hover:text-blue-700">← All applications</button>
                <div className="flex flex-wrap items-center gap-3 mt-1">
                    <h2 className="text-lg font-bold text-slate-900">{s.fullName}</h2>
                    <StatusPill status={s.status} />
                </div>
                <p className="text-xs text-slate-500">{s.reference} · {s.learnershipName} · applied {formatDateTime(s.submittedAt)}</p>
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
                <div className="xl:col-span-2 space-y-6">
                    <section className="bg-white border border-slate-200 rounded-2xl p-5">
                        <h3 className="text-sm font-bold text-slate-900 mb-3">Personal details</h3>
                        <dl className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                            <Field label={detail.idType} value={s.idNumber} />
                            <Field label="Date of birth" value={detail.dateOfBirth} />
                            <Field label="Gender" value={detail.gender} />
                            <Field label="Race" value={detail.race} />
                            <Field label="Disability" value={detail.disability === null || detail.disability === undefined ? null : detail.disability ? `Yes${detail.disabilityDetails ? ': ' + detail.disabilityDetails : ''}` : 'No'} />
                            <Field label="Home language" value={detail.homeLanguage} />
                        </dl>
                    </section>

                    <section className="bg-white border border-slate-200 rounded-2xl p-5">
                        <h3 className="text-sm font-bold text-slate-900 mb-3">Contact</h3>
                        <dl className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                            <Field label="Email" value={s.email} />
                            <Field label="Cellphone" value={s.phone} />
                            <Field label="Address" value={[detail.streetAddress, detail.suburb, s.town, detail.postalCode, s.province].filter(Boolean).join(', ')} />
                            <Field label="Next of kin" value={[detail.kinName, detail.kinRelationship && `(${detail.kinRelationship})`].filter(Boolean).join(' ')} />
                            <Field label="Next of kin phone" value={detail.kinPhone} />
                        </dl>
                    </section>

                    <section className="bg-white border border-slate-200 rounded-2xl p-5">
                        <h3 className="text-sm font-bold text-slate-900 mb-3">Education and background</h3>
                        <dl className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                            <Field label="Highest grade" value={s.highestGrade} />
                            <Field label="School" value={detail.schoolName} />
                            <Field label="Matric year" value={detail.matricYear} />
                            <Field label="Currently" value={s.currentActivity} />
                            <Field label="Previous study" value={detail.previousStudy} />
                        </dl>
                        {subjects.length > 0 && (
                            <div className="mt-4 flex flex-wrap gap-2">
                                {subjects.map((sub, i) => (
                                    <span key={i} className="text-xs bg-slate-100 text-slate-700 rounded-full px-2.5 py-1">
                                        {sub.name || sub.subject} {sub.mark !== undefined && `· ${sub.mark}%`}
                                    </span>
                                ))}
                            </div>
                        )}
                        {detail.motivation && (
                            <div className="mt-4">
                                <dt className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Motivation</dt>
                                <p className="text-sm text-slate-800 whitespace-pre-line mt-1">{detail.motivation}</p>
                            </div>
                        )}
                    </section>

                    <section className="bg-white border border-slate-200 rounded-2xl p-5">
                        <h3 className="text-sm font-bold text-slate-900 mb-3">Documents</h3>
                        {detail.documents.length === 0 ? (
                            <p className="text-xs text-slate-500">No documents uploaded.</p>
                        ) : (
                            <ul className="divide-y divide-slate-100">
                                {detail.documents.map(d => (
                                    <li key={d.id} className="flex items-center justify-between py-2">
                                        <div>
                                            <div className="text-sm font-semibold text-slate-900">{d.label}</div>
                                            <div className="text-xs text-slate-500">{d.originalFilename}{d.sizeBytes ? ` · ${Math.max(1, Math.round(d.sizeBytes / 1024))} KB` : ''}</div>
                                        </div>
                                        <button type="button" disabled={busy} onClick={() => openDocument(d)} className="text-xs font-semibold text-blue-600 hover:text-blue-700">Open</button>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>

                    <section className="bg-white border border-slate-200 rounded-2xl p-5">
                        <h3 className="text-sm font-bold text-slate-900 mb-3">History</h3>
                        <ol className="space-y-3">
                            {detail.events.map((e, i) => (
                                <li key={i} className="text-xs">
                                    <div className="flex items-center gap-2">
                                        <StatusPill status={e.toStatus} />
                                        <span className="text-slate-500">{formatDateTime(e.changedAt)} · {e.changedBy}</span>
                                    </div>
                                    {e.note && <p className="text-slate-700 mt-1">{e.note}</p>}
                                </li>
                            ))}
                        </ol>
                    </section>
                </div>

                <div className="space-y-6">
                    {enrolled ? (
                        <section className="bg-emerald-50 border border-emerald-200 rounded-2xl p-5">
                            <h3 className="text-sm font-bold text-emerald-900">Enrolled</h3>
                            <p className="text-xs text-emerald-800 mt-1">
                                Learner {s.enrolledLearnerCode}. Manage them in the Student Directory from here on.
                            </p>
                        </section>
                    ) : (
                        <>
                            {s.status === 'ACCEPTED' && (
                                <section className="bg-white border-2 border-emerald-300 rounded-2xl p-5 space-y-3">
                                    <h3 className="text-sm font-bold text-slate-900">Enrol as learner</h3>
                                    <p className="text-xs text-slate-500">
                                        Creates their LMS account on {s.learnershipName}, enrols them on its modules, copies their
                                        documents into their vault and emails their student number.
                                    </p>
                                    <input value={cohort} onChange={e => setCohort(e.target.value)} placeholder="Cohort (defaults to the intake)" className={`${inputClass} w-full`} />
                                    <button type="button" disabled={busy} onClick={enrol} className="w-full bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-xl py-2.5 disabled:opacity-50">
                                        Enrol {s.fullName.split(' ')[0]}
                                    </button>
                                </section>
                            )}

                            <section className="bg-white border border-slate-200 rounded-2xl p-5 space-y-3">
                                <h3 className="text-sm font-bold text-slate-900">Move application</h3>
                                <select value={status} onChange={e => setStatus(e.target.value)} className={`${inputClass} w-full`}>
                                    {SETTABLE.map(st => <option key={st.key} value={st.key}>{st.label}</option>)}
                                </select>
                                <textarea value={note} onChange={e => setNote(e.target.value)} rows={2} placeholder="Note for the history (staff only)" className={`${inputClass} w-full`} />
                                <label className="flex items-center gap-2 text-xs text-slate-700">
                                    <input type="checkbox" checked={notify} onChange={e => setNotify(e.target.checked)} />
                                    Email the applicant about this change
                                </label>
                                <button type="button" disabled={busy || (status === s.status && !note.trim())} onClick={changeStatus}
                                    className="w-full bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-xl py-2.5 disabled:opacity-50">
                                    {status === s.status ? 'Add note' : `Move to ${STATUS_BY_KEY[status].label}`}
                                </button>
                            </section>
                        </>
                    )}

                    <section className="bg-white border border-slate-200 rounded-2xl p-5 space-y-3">
                        <h3 className="text-sm font-bold text-slate-900">Staff notes</h3>
                        <textarea value={notes} onChange={e => setNotes(e.target.value)} rows={5} placeholder="Interview notes, missing documents, follow-ups… Never shown to the applicant." className={`${inputClass} w-full`} />
                        <button type="button" disabled={busy} onClick={saveNotes} className="text-xs font-semibold text-blue-600 hover:text-blue-700 disabled:opacity-50">Save notes</button>
                    </section>

                    <p className="text-[11px] text-slate-400">POPIA consent given {formatDateTime(detail.popiaConsentAt)}.</p>
                </div>
            </div>
        </div>
    );
};

export default ApplicationsPipeline;
