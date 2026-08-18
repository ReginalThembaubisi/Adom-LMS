// Validated color tokens shared across dashboards, so status/role colors stay
// consistent and never collide (e.g. a status pill and a role badge both being
// blue). Hex values are CVD-checked and pass ≥3:1 contrast on both surfaces —
// see the design system's palette reference for how these were chosen.

export const STATUS_COLORS = {
    // "good" — assessment outcome: Competent
    COMPETENT: {
        dark: 'bg-[#0ca30c]/10 text-[#0ca30c] border-[#0ca30c]/20',
        light: 'bg-[#0ca30c]/10 text-[#0ca30c] border-[#0ca30c]/30',
        dot: 'bg-[#0ca30c]',
        container: 'bg-[#0ca30c]/5 border-[#0ca30c]/20 text-[#0ca30c]',
    },
    // "critical" — assessment outcome: Not Yet Competent (picked over "serious"
    // for its much stronger light-surface contrast: 4.68:1 vs 2.57:1)
    NOT_YET_COMPETENT: {
        dark: 'bg-[#d03b3b]/10 text-[#d03b3b] border-[#d03b3b]/20',
        light: 'bg-[#d03b3b]/10 text-[#d03b3b] border-[#d03b3b]/30',
        dot: 'bg-[#d03b3b]',
        container: 'bg-[#d03b3b]/5 border-[#d03b3b]/20 text-[#d03b3b]',
    },
    // Neutral — awaiting a grade. Deliberately gray, not blue: blue is the
    // Facilitator role color and the two badges appear together in the same row.
    SUBMITTED: {
        dark: 'bg-slate-500/10 text-slate-400 border-slate-500/20',
        light: 'bg-slate-100 text-slate-600 border-slate-300/60',
        dot: 'bg-slate-400',
        container: 'bg-slate-50 border-slate-200 text-slate-700',
    },
    LATE: {
        dark: 'bg-[#fab219]/10 text-[#fab219] border-[#fab219]/20',
        light: 'bg-[#fab219]/10 text-amber-700 border-[#fab219]/30',
        dot: 'bg-[#fab219]',
        container: 'bg-[#fab219]/5 border-[#fab219]/20 text-amber-700',
    },
};

export function getStatusBadgeClasses(status, theme = 'dark') {
    const entry = STATUS_COLORS[status] || STATUS_COLORS.SUBMITTED;
    return entry[theme] || entry.dark;
}

export function getStatusDotClasses(status) {
    return (STATUS_COLORS[status] || STATUS_COLORS.SUBMITTED).dot;
}

export function getStatusContainerClasses(status) {
    return (STATUS_COLORS[status] || STATUS_COLORS.SUBMITTED).container;
}

export function getStatusLabel(status) {
    switch (status) {
        case 'COMPETENT': return 'Competent';
        case 'NOT_YET_COMPETENT': return 'Not Yet Competent';
        case 'LATE': return 'Late';
        case 'SUBMITTED': return 'Submitted';
        default: return status;
    }
}
