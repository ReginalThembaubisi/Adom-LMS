export function drawStroke(ctx, stroke) {
    if (stroke.tool === 'tick') {
        const { x, y, color, size } = stroke;
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
        const { x, y, color, size } = stroke;
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
    ctx.lineWidth = stroke.thickness;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.beginPath();
    stroke.points.forEach((p, i) => {
        if (i === 0) ctx.moveTo(p.x, p.y);
        else ctx.lineTo(p.x, p.y);
    });
    ctx.stroke();
}
