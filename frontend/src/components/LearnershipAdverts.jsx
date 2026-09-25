import React, { useState, useEffect, useCallback } from 'react';

/**
 * Learnership adverts: what the public website lists for people to apply to.
 *
 * Each learnership is both the LMS container learners are enrolled on and, once its advert is
 * filled in and set to Open, a public listing. Open adverts appear on the website straight away
 * and drop off by themselves the day after their closing date.
 */

const STATUS_STYLES = {
    OPEN: 'bg-emerald-100 text-emerald-800',
    DRAFT: 'bg-slate-100 text-slate-700',
    CLOSED: 'bg-rose-100 text-rose-800'
};

const BLANK = {
    name: '', qualificationCode: '', status: 'DRAFT', slug: '', seta: '', nqfLevel: '',
    durationMonths: '', stipend: '', location: '10 Cameron Street, Lindokuhle House, Nelspruit',
    intake: '', closingDate: '', maxLearners: '', description: '', requirements: ''
};

const inputClass = 'w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10';
const labelClass = 'block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5';

const toNumberOrNull = (v) => (v === '' || v === null || v === undefined ? null : Number(v));

const daysUntil = (date) => {
    if (!date) return null;
    return Math.ceil((new Date(date + 'T23:59:59') - new Date()) / 86400000);
};

const formatDate = (date) => {
    if (!date) return '';
    const d = new Date(date + 'T00:00:00');
    return isNaN(d) ? date : d.toLocaleDateString('en-ZA', { day: 'numeric', month: 'long', year: 'numeric' });
};

const LearnershipAdverts = ({ token, onAuthFailure, onError, onInfo, onViewApplications }) => {
    const [learnerships, setLearnerships] = useState([]);
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

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setLearnerships(await request('/api/admin/learnerships'));
        } catch (e) {
            if (e.message !== 'Signed out') onError(e.message);
        } finally {
            setLoading(false);
        }
    }, [request, onError]);

    useEffect(() => { load(); }, [load]);

    const edit = (l) => setForm({
        ...BLANK,
        ...Object.fromEntries(Object.entries(l).map(([k, v]) => [k, v ?? ''])),
        status: l.status || 'DRAFT'
    });

    const set = (e) => {
        const { name, value } = e.target;
        setForm(f => ({ ...f, [name]: value }));
    };

    const save = async (e) => {
        e.preventDefault();
        setSaving(true);
        const body = {
            ...form,
            nqfLevel: toNumberOrNull(form.nqfLevel),
            durationMonths: toNumberOrNull(form.durationMonths),
            stipend: toNumberOrNull(form.stipend),
            maxLearners: toNumberOrNull(form.maxLearners),
            closingDate: form.closingDate || null,
            slug: form.slug || null
        };
        try {
            const saved = await request(form.id ? `/api/admin/learnerships/${form.id}` : '/api/admin/learnerships', {
                method: form.id ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            onInfo(saved.status === 'OPEN'
                ? `"${saved.name}" is live on the website.`
                : `"${saved.name}" saved as ${saved.status.toLowerCase()}.`);
            setForm(null);
            load();
        } catch (err) {
            if (err.message !== 'Signed out') onError(err.message);
        } finally {
            setSaving(false);
        }
    };

    const remove = async (l) => {
        if (!window.confirm(`Delete "${l.name}"? This can't be undone.`)) return;
        try {
            await request(`/api/admin/learnerships/${l.id}`, { method: 'DELETE' });
            onInfo(`"${l.name}" deleted.`);
            load();
        } catch (err) {
            if (err.message !== 'Signed out') onError(err.message);
        }
    };

    if (form) {
        const days = daysUntil(form.closingDate);
        const facts = [
            form.nqfLevel && `NQF ${form.nqfLevel}`,
            form.durationMonths && `${form.durationMonths} months`,
            form.stipend && `R${Number(form.stipend).toLocaleString('en-ZA')} / month`,
            form.intake
        ].filter(Boolean);
        const reqs = (form.requirements || '').split('\n').map(s => s.trim()).filter(Boolean);

        return (
            <div className="space-y-6">
                <div className="flex items-center justify-between border-b border-slate-300 pb-3">
                    <div>
                        <button type="button" onClick={() => setForm(null)} className="text-xs font-semibold text-blue-600 hover:text-blue-700">
                            ← All learnerships
                        </button>
                        <h2 className="text-lg font-bold text-slate-900 mt-1">{form.id ? `Edit ${form.name || 'learnership'}` : 'New learnership advert'}</h2>
                    </div>
                </div>

                <div className="grid grid-cols-1 xl:grid-cols-5 gap-6">
                    <form onSubmit={save} className="xl:col-span-3 bg-white border border-slate-200/80 rounded-2xl shadow-sm p-6 space-y-4">
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <div className="sm:col-span-2">
                                <label className={labelClass}>Learnership name *</label>
                                <input name="name" value={form.name} onChange={set} className={inputClass} required placeholder="e.g. IT Systems Support NQF4" />
                            </div>
                            <div>
                                <label className={labelClass}>Advert status</label>
                                <select name="status" value={form.status} onChange={set} className={inputClass}>
                                    <option value="DRAFT">Draft (not on website)</option>
                                    <option value="OPEN">Open (taking applications)</option>
                                    <option value="CLOSED">Closed</option>
                                </select>
                            </div>
                            <div>
                                <label className={labelClass}>Closing date</label>
                                <input type="date" name="closingDate" value={form.closingDate} onChange={set} className={inputClass} />
                            </div>
                            <div>
                                <label className={labelClass}>Qualification code</label>
                                <input name="qualificationCode" value={form.qualificationCode} onChange={set} className={inputClass} placeholder="e.g. 48573" />
                            </div>
                            <div>
                                <label className={labelClass}>SETA</label>
                                <input name="seta" value={form.seta} onChange={set} className={inputClass} placeholder="e.g. MICT SETA" />
                            </div>
                            <div>
                                <label className={labelClass}>NQF level</label>
                                <select name="nqfLevel" value={form.nqfLevel} onChange={set} className={inputClass}>
                                    <option value="">—</option>
                                    {[1, 2, 3, 4, 5, 6, 7, 8].map(n => <option key={n} value={n}>{n}</option>)}
                                </select>
                            </div>
                            <div>
                                <label className={labelClass}>Duration (months)</label>
                                <input type="number" min="1" name="durationMonths" value={form.durationMonths} onChange={set} className={inputClass} />
                            </div>
                            <div>
                                <label className={labelClass}>Monthly stipend (R)</label>
                                <input type="number" min="0" name="stipend" value={form.stipend} onChange={set} className={inputClass} />
                            </div>
                            <div>
                                <label className={labelClass}>Places available</label>
                                <input type="number" min="1" name="maxLearners" value={form.maxLearners} onChange={set} className={inputClass} />
                            </div>
                            <div>
                                <label className={labelClass}>Intake / cohort</label>
                                <input name="intake" value={form.intake} onChange={set} className={inputClass} placeholder="e.g. 2027 Intake A" />
                            </div>
                            <div>
                                <label className={labelClass}>Web address</label>
                                <input name="slug" value={form.slug} onChange={set} className={inputClass} placeholder="made from the name if blank" />
                            </div>
                            <div className="sm:col-span-2">
                                <label className={labelClass}>Location</label>
                                <input name="location" value={form.location} onChange={set} className={inputClass} />
                            </div>
                            <div className="sm:col-span-2">
                                <label className={labelClass}>About this learnership</label>
                                <textarea name="description" value={form.description} onChange={set} rows={5} className={inputClass} placeholder="What learners do, where they're placed, what they leave with." />
                            </div>
                            <div className="sm:col-span-2">
                                <label className={labelClass}>Requirements (one per line)</label>
                                <textarea name="requirements" value={form.requirements} onChange={set} rows={4} className={inputClass} placeholder={'Grade 12\nAged 18 to 35\nUnemployed'} />
                            </div>
                        </div>
                        <p className="text-xs text-slate-500">
                            New intakes use this learnership's intake as the default cohort when applicants are enrolled.
                        </p>
                        <div className="flex gap-3">
                            <button type="submit" disabled={saving} className="bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs py-2.5 px-5 rounded-xl disabled:opacity-50">
                                {saving ? 'Saving…' : form.status === 'OPEN' ? 'Save and publish' : 'Save'}
                            </button>
                            <button type="button" onClick={() => setForm(null)} className="text-xs font-semibold text-slate-600 px-4">Cancel</button>
                        </div>
                    </form>

                    <div className="xl:col-span-2">
                        <div className="sticky top-6 space-y-2">
                            <span className={labelClass}>Website preview</span>
                            <div className="bg-[#0E0F1A] text-[#F4F3EE] rounded-2xl p-6 space-y-3">
                                <div className="flex items-center justify-between text-[11px] font-bold uppercase tracking-wider text-[#22D3C5]">
                                    <span>{form.seta || 'Learnership'}</span>
                                    <span className="text-[#A9ABBE]">{form.qualificationCode}</span>
                                </div>
                                <h3 className="text-xl font-bold">{form.name || 'Learnership name'}</h3>
                                {facts.length > 0 && (
                                    <div className="flex flex-wrap gap-2">
                                        {facts.map(f => <span key={f} className="text-[11px] border border-[#2A2D42] rounded-full px-2.5 py-1">{f}</span>)}
                                    </div>
                                )}
                                {form.description && <p className="text-sm text-[#C9CAD6] whitespace-pre-line">{form.description}</p>}
                                {reqs.length > 0 && (
                                    <ul className="text-sm text-[#C9CAD6] list-disc pl-5 space-y-0.5">
                                        {reqs.map(r => <li key={r}>{r}</li>)}
                                    </ul>
                                )}
                                <div className="flex items-center justify-between pt-2">
                                    <span className={`text-xs ${days !== null && days <= 7 ? 'text-[#FF4D4D]' : 'text-[#C9CAD6]'}`}>
                                        {days === null ? 'Open until filled' : days < 0 ? 'Closed' : days === 0 ? 'Closes today' : `Closes ${formatDate(form.closingDate)}`}
                                    </span>
                                    <span className="bg-[#22D3C5] text-[#0E0F1A] text-xs font-bold rounded-full px-4 py-2">Apply</span>
                                </div>
                            </div>
                            {form.status !== 'OPEN' && (
                                <p className="text-xs text-slate-500">Only visible on the website once the status is Open.</p>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    const openCount = learnerships.filter(l => l.acceptingApplications).length;

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-end justify-between gap-3 border-b border-slate-300 pb-3">
                <div>
                    <h2 className="text-lg font-bold text-slate-900">Learnership Adverts</h2>
                    <p className="text-xs text-slate-500">
                        What the public website lists. {openCount} taking applications · {learnerships.length} learnerships in total.
                    </p>
                </div>
                <button type="button" onClick={() => setForm({ ...BLANK })} className="bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs py-2.5 px-5 rounded-xl">
                    New advert
                </button>
            </div>

            {loading ? (
                <p className="text-sm text-slate-500">Loading…</p>
            ) : learnerships.length === 0 ? (
                <div className="bg-white border border-slate-200 rounded-2xl p-8 text-center">
                    <p className="text-sm font-semibold text-slate-800">No learnerships yet</p>
                    <p className="text-xs text-slate-500 mt-1">Create one and set it to Open to list it on the website.</p>
                </div>
            ) : (
                <div className="bg-white border border-slate-200 rounded-2xl divide-y divide-slate-100">
                    {learnerships.map(l => {
                        const days = daysUntil(l.closingDate);
                        const counts = l.applicationCounts || {};
                        const waiting = (counts.SUBMITTED || 0) + (counts.SCREENING || 0);
                        return (
                            <div key={l.id} className="p-4 flex flex-wrap items-center gap-4">
                                <div className="flex-1 min-w-[220px]">
                                    <div className="flex items-center gap-2">
                                        <span className="text-sm font-bold text-slate-900">{l.name}</span>
                                        <span className={`text-[10px] font-bold uppercase rounded-full px-2 py-0.5 ${STATUS_STYLES[l.status] || STATUS_STYLES.DRAFT}`}>
                                            {l.status === 'OPEN' && !l.acceptingApplications ? 'Open · past closing date' : l.status}
                                        </span>
                                    </div>
                                    <p className="text-xs text-slate-500 mt-0.5">
                                        {[l.qualificationCode, l.nqfLevel && `NQF ${l.nqfLevel}`, l.intake,
                                            l.closingDate ? (days < 0 ? `closed ${formatDate(l.closingDate)}` : `closes ${formatDate(l.closingDate)}`) : l.status === 'OPEN' && 'open until filled']
                                            .filter(Boolean).join(' · ')}
                                    </p>
                                </div>
                                <button type="button" onClick={() => onViewApplications(l.id)} className="text-left">
                                    <span className="block text-sm font-bold text-slate-900">{l.applicationTotal || 0} applications</span>
                                    <span className="block text-xs text-slate-500">{waiting} waiting for review</span>
                                </button>
                                <div className="flex gap-2">
                                    <button type="button" onClick={() => edit(l)} className="text-xs font-semibold text-blue-600 hover:text-blue-700 px-2 py-1">Edit</button>
                                    {!l.applicationTotal && (
                                        <button type="button" onClick={() => remove(l)} className="text-xs font-semibold text-rose-600 hover:text-rose-700 px-2 py-1">Delete</button>
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

export default LearnershipAdverts;
