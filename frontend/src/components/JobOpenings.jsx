import React, { useState, useEffect, useCallback } from 'react';

/**
 * Job and internship openings for the website's Careers page. Set one to Open and it is listed
 * there straight away; it drops off by itself the day after its closing date. Applications to
 * it arrive in the Applications tab.
 */

const CATEGORIES = [
    { key: 'INTERNSHIP', label: 'Internship' },
    { key: 'GRADUATE_PROGRAMME', label: 'Graduate programme' },
    { key: 'GRADE_12', label: 'Grade 12 holders' },
    { key: 'ENTRY_LEVEL', label: 'Entry-level job' },
    { key: 'GOVERNMENT', label: 'Government job' }
];

const STATUS_STYLES = {
    OPEN: 'bg-emerald-100 text-emerald-800',
    DRAFT: 'bg-slate-100 text-slate-700',
    CLOSED: 'bg-rose-100 text-rose-800'
};

const BLANK = {
    title: '', category: 'INTERNSHIP', division: '', location: '10 Cameron Street, Lindokuhle House, Nelspruit',
    positions: '', closingDate: '', description: '', requirements: '', status: 'DRAFT'
};

const inputClass = 'w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10';
const labelClass = 'block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5';

const formatDate = (date) => {
    if (!date) return '';
    const d = new Date(date + 'T00:00:00');
    return isNaN(d) ? date : d.toLocaleDateString('en-ZA', { day: 'numeric', month: 'long', year: 'numeric' });
};

const JobOpenings = ({ token, onAuthFailure, onError, onInfo, onViewApplications }) => {
    const [openings, setOpenings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [form, setForm] = useState(null);
    const [saving, setSaving] = useState(false);

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
        return res.status === 204 ? null : res.json();
    }, [token, onAuthFailure]);

    const report = useCallback((e) => { if (e.message !== 'Signed out') onError(e.message); }, [onError]);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setOpenings(await request('/api/admin/openings'));
        } catch (e) {
            report(e);
        } finally {
            setLoading(false);
        }
    }, [request, report]);

    useEffect(() => { load(); }, [load]);

    const set = (e) => {
        const { name, value } = e.target;
        setForm(f => ({ ...f, [name]: value }));
    };

    const edit = (o) => setForm({ ...BLANK, ...Object.fromEntries(Object.entries(o).map(([k, v]) => [k, v ?? ''])) });

    const save = async (e) => {
        e.preventDefault();
        setSaving(true);
        try {
            const saved = await request(form.id ? `/api/admin/openings/${form.id}` : '/api/admin/openings', {
                method: form.id ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    ...form,
                    positions: form.positions === '' ? null : Number(form.positions),
                    closingDate: form.closingDate || null
                })
            });
            onInfo(saved.status === 'OPEN' ? `"${saved.title}" is live on the Careers page.` : `"${saved.title}" saved as ${saved.status.toLowerCase()}.`);
            setForm(null);
            load();
        } catch (err) {
            report(err);
        } finally {
            setSaving(false);
        }
    };

    const remove = async (o) => {
        if (!window.confirm(`Delete "${o.title}"? This can't be undone.`)) return;
        try {
            await request(`/api/admin/openings/${o.id}`, { method: 'DELETE' });
            onInfo(`"${o.title}" deleted.`);
            load();
        } catch (err) {
            report(err);
        }
    };

    if (form) {
        return (
            <div className="space-y-6">
                <div className="border-b border-slate-300 pb-3">
                    <button type="button" onClick={() => setForm(null)} className="text-xs font-semibold text-blue-600 hover:text-blue-700">← All openings</button>
                    <h2 className="text-lg font-bold text-slate-900 mt-1">{form.id ? `Edit ${form.title || 'opening'}` : 'New opening'}</h2>
                </div>
                <form onSubmit={save} className="bg-white border border-slate-200/80 rounded-2xl shadow-sm p-6 space-y-4 max-w-3xl">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div className="sm:col-span-2">
                            <label className={labelClass} htmlFor="op-title">Title *</label>
                            <input id="op-title" name="title" value={form.title} onChange={set} required className={inputClass} placeholder="e.g. Software Developer" />
                        </div>
                        <div>
                            <label className={labelClass} htmlFor="op-category">Category *</label>
                            <select id="op-category" name="category" value={form.category} onChange={set} className={inputClass}>
                                {CATEGORIES.map(c => <option key={c.key} value={c.key}>{c.label}</option>)}
                            </select>
                        </div>
                        <div>
                            <label className={labelClass} htmlFor="op-status">Status</label>
                            <select id="op-status" name="status" value={form.status} onChange={set} className={inputClass}>
                                <option value="DRAFT">Draft (not on website)</option>
                                <option value="OPEN">Open (taking applications)</option>
                                <option value="CLOSED">Closed</option>
                            </select>
                        </div>
                        <div>
                            <label className={labelClass} htmlFor="op-division">Division</label>
                            <input id="op-division" name="division" value={form.division} onChange={set} className={inputClass} placeholder="e.g. Software Development" />
                        </div>
                        <div>
                            <label className={labelClass} htmlFor="op-positions">Positions</label>
                            <input id="op-positions" type="number" min="1" name="positions" value={form.positions} onChange={set} className={inputClass} />
                        </div>
                        <div>
                            <label className={labelClass} htmlFor="op-closing">Closing date</label>
                            <input id="op-closing" type="date" name="closingDate" value={form.closingDate} onChange={set} className={inputClass} />
                        </div>
                        <div>
                            <label className={labelClass} htmlFor="op-location">Location</label>
                            <input id="op-location" name="location" value={form.location} onChange={set} className={inputClass} />
                        </div>
                        <div className="sm:col-span-2">
                            <label className={labelClass} htmlFor="op-description">About the role</label>
                            <textarea id="op-description" name="description" value={form.description} onChange={set} rows={5} className={inputClass} placeholder="What the person will do, who they'll work with." />
                        </div>
                        <div className="sm:col-span-2">
                            <label className={labelClass} htmlFor="op-requirements">Requirements (one per line)</label>
                            <textarea id="op-requirements" name="requirements" value={form.requirements} onChange={set} rows={4} className={inputClass} placeholder={'Diploma in IT\nDriver\'s licence'} />
                        </div>
                    </div>
                    <div className="flex gap-3">
                        <button type="submit" disabled={saving} className="bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs py-2.5 px-5 rounded-xl disabled:opacity-50">
                            {saving ? 'Saving…' : form.status === 'OPEN' ? 'Save and publish' : 'Save'}
                        </button>
                        <button type="button" onClick={() => setForm(null)} className="text-xs font-semibold text-slate-600 px-4">Cancel</button>
                    </div>
                </form>
            </div>
        );
    }

    const live = openings.filter(o => o.acceptingApplications).length;

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-end justify-between gap-3 border-b border-slate-300 pb-3">
                <div>
                    <h2 className="text-lg font-bold text-slate-900">Jobs &amp; Internships</h2>
                    <p className="text-xs text-slate-500">Openings on the website's Careers page. {live} taking applications · {openings.length} in total.</p>
                </div>
                <button type="button" onClick={() => setForm({ ...BLANK })} className="bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs py-2.5 px-5 rounded-xl">
                    New opening
                </button>
            </div>

            {loading ? (
                <p className="text-sm text-slate-500">Loading…</p>
            ) : openings.length === 0 ? (
                <div className="bg-white border border-slate-200 rounded-2xl p-8 text-center">
                    <p className="text-sm font-semibold text-slate-800">No openings yet</p>
                    <p className="text-xs text-slate-500 mt-1">Post a job or internship and set it to Open to list it on the Careers page.</p>
                </div>
            ) : (
                <div className="bg-white border border-slate-200 rounded-2xl divide-y divide-slate-100">
                    {openings.map(o => {
                        const counts = o.applicationCounts || {};
                        const waiting = (counts.SUBMITTED || 0) + (counts.SCREENING || 0);
                        return (
                            <div key={o.id} className="p-4 flex flex-wrap items-center gap-4">
                                <div className="flex-1 min-w-[220px]">
                                    <div className="flex items-center gap-2">
                                        <span className="text-sm font-bold text-slate-900">{o.title}</span>
                                        <span className={`text-[10px] font-bold uppercase rounded-full px-2 py-0.5 ${STATUS_STYLES[o.status]}`}>
                                            {o.status === 'OPEN' && !o.acceptingApplications ? 'Open · past closing date' : o.status}
                                        </span>
                                    </div>
                                    <p className="text-xs text-slate-500 mt-0.5">
                                        {[o.categoryLabel, o.division, o.positions && `${o.positions} position${o.positions === 1 ? '' : 's'}`,
                                            o.closingDate ? `closes ${formatDate(o.closingDate)}` : o.status === 'OPEN' && 'open until filled']
                                            .filter(Boolean).join(' · ')}
                                    </p>
                                </div>
                                <button type="button" onClick={() => onViewApplications(o.id)} className="text-left">
                                    <span className="block text-sm font-bold text-slate-900">{o.applicationTotal || 0} applications</span>
                                    <span className="block text-xs text-slate-500">{waiting} waiting for review</span>
                                </button>
                                <div className="flex gap-2">
                                    <button type="button" onClick={() => edit(o)} className="text-xs font-semibold text-blue-600 hover:text-blue-700 px-2 py-1">Edit</button>
                                    {!o.applicationTotal && (
                                        <button type="button" onClick={() => remove(o)} className="text-xs font-semibold text-rose-600 hover:text-rose-700 px-2 py-1">Delete</button>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
};

export default JobOpenings;
