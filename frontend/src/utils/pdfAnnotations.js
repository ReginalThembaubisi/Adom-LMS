/**
 * Replaying a mark where the marker actually put it.
 *
 * Strokes are captured in canvas pixels, and the canvas is rendered at a scale that is not the
 * same everywhere: the marking workspace fits the page to its container (so it depends on the
 * window width and whether the sidebar is open) while the learner's viewer renders at a fixed
 * 1.5. Replaying raw pixels therefore displaced every mark by the ratio between those two
 * scales, growing with distance from the top-left corner — a tick drawn beside one row appeared
 * beside another. On a portfolio that gets audited, that is wrong evidence, not a cosmetic
 * glitch. It also moved a marker's own marks when they resized their window mid-session,
 * because the annotator re-renders at the new scale and replayed the old pixels unchanged.
 *
 * Each stroke now records the scale it was drawn at, and every draw converts into the scale
 * being drawn at now. Strokes saved before this carry no scale; they are drawn unconverted,
 * exactly as they were before, because there is no way to recover a scale that was never
 * recorded. Those stay approximate — re-mark them if the placement matters.
 */
export function scaleFactor(stroke, renderScale) {
    const captured = stroke && stroke.s;
    if (!captured || !renderScale || captured <= 0) return 1;
    return renderScale / captured;
}

export function drawStroke(ctx, stroke, renderScale) {
    const k = scaleFactor(stroke, renderScale);
    if (stroke.tool === 'tick') {
        const { color } = stroke;
        const x = stroke.x * k, y = stroke.y * k, size = stroke.size * k;
        ctx.strokeStyle = color;
        ctx.lineWidth = Math.max(2.5, size * 0.16);
        ctx.lineJoin = 'round';
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.moveTo(x - size * 0.5, y);
        ctx.lineTo(x - size * 0.15, y + size * 0.4);
        ctx.lineTo(x + size * 0.55, y - size * 0.5);
        ctx.stroke();
        return;
    }
    if (stroke.tool === 'cross') {
        const { color } = stroke;
        const x = stroke.x * k, y = stroke.y * k, size = stroke.size * k;
        ctx.strokeStyle = color;
        ctx.lineWidth = Math.max(2.5, size * 0.16);
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.moveTo(x - size * 0.45, y - size * 0.45);
        ctx.lineTo(x + size * 0.45, y + size * 0.45);
        ctx.moveTo(x + size * 0.45, y - size * 0.45);
        ctx.lineTo(x - size * 0.45, y + size * 0.45);
        ctx.stroke();
        return;
    }
    if (!stroke.points || stroke.points.length < 2) return;
    ctx.strokeStyle = stroke.color;
    ctx.lineWidth = stroke.thickness * k;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.beginPath();
    stroke.points.forEach((p, i) => {
        if (i === 0) ctx.moveTo(p.x * k, p.y * k);
        else ctx.lineTo(p.x * k, p.y * k);
    });
    ctx.stroke();
}

/**
 * Whether a click at (x, y) — in the scale currently rendered — lands on this stroke.
 *
 * Used by the eraser, so a marker can remove one mark instead of undoing everything placed
 * after it. Undo is last-in-first-out; taking out an early tick used to mean discarding every
 * later one and redoing them, which is not a reasonable thing to ask of someone marking a
 * hundred scripts.
 *
 * The stroke is converted into the current scale first, for the same reason drawing is: its
 * stored coordinates are in whatever scale it was drawn at, which is not necessarily this one.
 * Tolerances are deliberately generous — a pen line one or two pixels wide is not something
 * anyone can click precisely, and the cost of a near miss is an erase that does nothing while
 * the cost of a slightly wide hit is an undo away.
 */
export function strokeHitTest(stroke, x, y, renderScale) {
    if (!stroke) return false;
    const k = scaleFactor(stroke, renderScale);

    if (stroke.tool === 'tick' || stroke.tool === 'cross') {
        const cx = stroke.x * k;
        const cy = stroke.y * k;
        const half = (stroke.size || 28) * k * 0.6;
        return x >= cx - half && x <= cx + half && y >= cy - half && y <= cy + half;
    }

    const points = stroke.points;
    if (!points || points.length === 0) return false;
    const tolerance = Math.max((stroke.thickness || 2) * k, 10) / 2 + 4;

    if (points.length === 1) {
        return Math.hypot(x - points[0].x * k, y - points[0].y * k) <= tolerance;
    }
    for (let i = 1; i < points.length; i++) {
        const ax = points[i - 1].x * k, ay = points[i - 1].y * k;
        const bx = points[i].x * k, by = points[i].y * k;
        if (distanceToSegment(x, y, ax, ay, bx, by) <= tolerance) return true;
    }
    return false;
}

function distanceToSegment(px, py, ax, ay, bx, by) {
    const dx = bx - ax;
    const dy = by - ay;
    const lengthSquared = dx * dx + dy * dy;
    if (lengthSquared === 0) return Math.hypot(px - ax, py - ay);
    // Position of the closest point along the segment, clamped so it stays on it.
    let t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
    t = Math.max(0, Math.min(1, t));
    return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}
