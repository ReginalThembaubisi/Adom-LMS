import React, { useState, useEffect, useCallback } from 'react';

/**
 * Appointment requests from the website's Services page. Staff confirm each one by phone or
 * email, then track it here: New → Confirmed → Completed, or Cancelled.
 */

const STATUSES = [
    { key: 'NEW', label: 'New', style: 'bg-sky-100 text-sky-800' },
    { key: 'CONFIRMED', label: 'Confirmed', style: 'bg-emerald-100 text-emerald-800' },
    { key: 'COMPLETED', label: 'Completed', style: 'bg-slate-200 text-slate-700' },
    { key: 'CANCELLED', label: 'Cancelled', style: 'bg-rose-100 text-rose-800' }
];
const BY_KEY = Object.fromEntries(STATUSES.map(s => [s.key, s]));

const inputClass = 'bg-white border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10';

const formatDate = (d) => {
    if (!d) return 'Any day';
    const x = new Date(d + 'T00:00:00');
    return isNaN(x) ? d : x.toLocaleDateString('en-ZA', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
};
const formatDateTime = (iso) => iso
    ? new Date(iso).toLocaleString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
    : '—';

const AppointmentsPanel = ({ token, onAuthFailure, onError, onInfo }) => {
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState('');
    const [open, setOpen] = useState(null);
    const [notes, setNotes] = useState('');
    const [busy, setBusy] = useState(false);

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
        return res.json();
    }, [token, onAuthFailure]);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setRows(await request('/api/admin/appointments'));
        } catch (e) {
            if (e.message !== 'Signed out') onError(e.message);
        } finally {
            setLoading(false);
        }
    }, [request, onError]);

    useEffect(() => { load(); }, [load]);

    const update = async (row, changes, message) => {
        setBusy(true);
        try {
            const saved = await request(`/api/admin/appointments/${row.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(changes)
            });
            setRows(rs => rs.map(r => (r.id === saved.id ? saved : r)));
            onInfo(message);
        } catch (e) {
            if (e.message !== 'Signed out') onError(e.message);
        } finally {
            setBusy(false);
        }
    };

    const toggle = (row) => {
        if (open === row.id) {
            setOpen(null);
        } else {
            setOpen(row.id);
            setNotes(row.staffNotes || '');
        }
    };

    const counts = rows.reduce((m, r) => ({ ...m, [r.status]: (m[r.status] || 0) + 1 }), {});
    const visible = rows.filter(r => !filter || r.status === filter);

    return (
        <div className="space-y-6">
            <div className="border-b border-slate-300 pb-3">
                <h2 className="text-lg font-bold text-slate-900">Appointments</h2>
                <p className="text-xs text-slate-500">Requests from the Services page. Confirm each by phone or email, then update it here.</p>
            </div>

            <div className="flex flex-wrap gap-2">
                <button type="button" onClick={() => setFilter('')}
                    className={`text-xs font-semibold rounded-full px-3 py-1.5 border ${filter === '' ? 'bg-slate-900 text-white border-slate-900' : 'bg-white text-slate-700 border-slate-200'}`}>
                    All {rows.length}
                </button>
                {STATUSES.map(s => (
                    <button key={s.key} type="button" onClick={() => setFilter(s.key)}
                        className={`text-xs font-semibold rounded-full px-3 py-1.5 border ${filter === s.key ? 'bg-slate-900 text-white border-slate-900' : 'bg-white text-slate-700 border-slate-200'}`}>
                        {s.label} {counts[s.key] || 0}
                    </button>
                ))}
            </div>

            {loading ? (
                <p className="text-sm text-slate-500">Loading…</p>
            ) : visible.length === 0 ? (
                <div className="bg-white border border-slate-200 rounded-2xl p-8 text-center">
                    <p className="text-sm font-semibold text-slate-800">No appointment requests here</p>
                    <p className="text-xs text-slate-500 mt-1">They appear as soon as someone books on the Services page.</p>
                </div>
            ) : (
                <div className="bg-white border border-slate-200 rounded-2xl divide-y divide-slate-100">
                    {visible.map(r => (
                        <div key={r.id} className="p-4 space-y-3">
                            <button type="button" onClick={() => toggle(r)} className="w-full text-left flex flex-wrap items-center gap-4">
                                <div className="flex-1 min-w-[200px]">
                                    <div className="text-sm font-bold text-slate-900">{r.fullName}{r.company && <span className="font-normal text-slate-500"> · {r.company}</span>}</div>
                                    <div className="text-xs text-slate-500">{r.service} · {formatDate(r.preferredDate)}{r.preferredTime ? `, ${r.preferredTime}` : ''}</div>
                                </div>
                                <div className="text-xs text-slate-500">Requested {formatDateTime(r.createdAt)}</div>
                                <span className={`text-[10px] font-bold uppercase rounded-full px-2 py-0.5 ${BY_KEY[r.status].style}`}>{BY_KEY[r.status].label}</span>
                            </button>
                            {open === r.id && (
                                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 bg-slate-50 rounded-xl p-4">
                                    <div className="space-y-2 text-sm">
                                        <div><span className="text-xs font-bold uppercase text-slate-400">Phone</span><div><a href={`tel:${r.phone}`} className="text-blue-600 font-semibold">{r.phone}</a></div></div>
                                        <div><span className="text-xs font-bold uppercase text-slate-400">Email</span><div><a href={`mailto:${r.email}`} className="text-blue-600 font-semibold">{r.email}</a></div></div>
                                        <div><span className="text-xs font-bold uppercase text-slate-400">What they need</span><p className="whitespace-pre-line text-slate-800">{r.message || '—'}</p></div>
                                    </div>
                                    <div className="space-y-3">
                                        <div className="flex flex-wrap gap-2">
                                            {STATUSES.filter(s => s.key !== r.status).map(s => (
                                                <button key={s.key} type="button" disabled={busy}
                                                    onClick={() => update(r, { status: s.key }, `${r.fullName}: ${s.label.toLowerCase()}.`)}
                                                    className="text-xs font-semibold border border-slate-300 rounded-lg px-3 py-1.5 hover:bg-white disabled:opacity-50">
                                                    Mark {s.label.toLowerCase()}
                                                </button>
                                            ))}
                                        </div>
                                        <textarea value={notes} onChange={e => setNotes(e.target.value)} rows={3} aria-label="Staff notes"
                                            placeholder="e.g. Confirmed by phone for Tuesday 10:00" className={`${inputClass} w-full`} />
                                        <button type="button" disabled={busy} onClick={() => update(r, { staffNotes: notes }, 'Notes saved.')}
                                            className="text-xs font-semibold text-blue-600 hover:text-blue-700 disabled:opacity-50">Save notes</button>
                                    </div>
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default AppointmentsPanel;
