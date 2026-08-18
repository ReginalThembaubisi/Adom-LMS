import React from 'react';

// Distinct color per role so it's visible at a glance who recorded an outcome,
// since Facilitators, Assessors, and Moderators can each grade independently.
const ROLE_STYLES = {
    FACILITATOR: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    ASSESSOR: 'bg-violet-500/10 text-violet-400 border-violet-500/20',
    MODERATOR: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
};

const ROLE_STYLES_LIGHT = {
    FACILITATOR: 'bg-blue-50 text-blue-700 border-blue-200/60',
    ASSESSOR: 'bg-violet-50 text-violet-700 border-violet-200/60',
    MODERATOR: 'bg-amber-50 text-amber-700 border-amber-200/60',
};

const ROLE_LABELS = {
    FACILITATOR: 'Facilitator',
    ASSESSOR: 'Assessor',
    MODERATOR: 'Moderator',
};

export const GraderBadge = ({ role, name, theme = 'dark' }) => {
    if (!role) return null;
    const styles = (theme === 'light' ? ROLE_STYLES_LIGHT : ROLE_STYLES)[role] || (theme === 'light' ? ROLE_STYLES_LIGHT.FACILITATOR : ROLE_STYLES.FACILITATOR);
    const label = ROLE_LABELS[role] || role;
    return (
        <span className={`inline-flex items-center gap-1 border text-[10px] font-bold px-2 py-0.5 rounded-full ${styles}`}>
            {label}{name ? ` · ${name}` : ''}
        </span>
    );
};
