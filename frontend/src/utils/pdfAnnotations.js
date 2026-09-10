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
function scaleFactor(stroke, renderScale) {
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
