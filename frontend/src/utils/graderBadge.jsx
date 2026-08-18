import React from 'react';

// Distinct color per role so it's visible at a glance who recorded an outcome,
// since Facilitators, Assessors, and Moderators can each grade independently.
// Hex values are a validated categorical triple (CVD-safe, ≥3:1 contrast on
// both surfaces) — see frontend/src/utils/colors.js.
const ROLE_STYLES = {
    FACILITATOR: 'bg-[#3987e5]/10 text-[#3987e5] border-[#3987e5]/20',
    ASSESSOR: 'bg-[#9085e9]/10 text-[#9085e9] border-[#9085e9]/20',
    MODERATOR: 'bg-[#d95926]/10 text-[#d95926] border-[#d95926]/20',
};

const ROLE_STYLES_LIGHT = {
    FACILITATOR: 'bg-[#2a78d6]/10 text-[#2a78d6] border-[#2a78d6]/20',
    ASSESSOR: 'bg-[#4a3aa7]/10 text-[#4a3aa7] border-[#4a3aa7]/20',
    MODERATOR: 'bg-[#eb6834]/10 text-[#eb6834] border-[#eb6834]/20',
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
