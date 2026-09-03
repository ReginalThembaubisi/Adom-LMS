import React, { useState, useRef, useEffect, useCallback } from 'react';

let _pdfjsLib = null;
async function getPdfjs() {
    if (_pdfjsLib) return _pdfjsLib;
    const [lib, workerUrl] = await Promise.all([
        import('pdfjs-dist'),
        import('pdfjs-dist/build/pdf.worker.min.mjs?url'),
    ]);
    lib.GlobalWorkerOptions.workerSrc = workerUrl.default;
    _pdfjsLib = lib;
    return lib;
}

const COLORS = ['#e34948', '#0ca30c', '#2a78d6', '#111111'];
const RENDER_SCALE = 1.5;

function drawStroke(ctx, stroke) {
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
    if (stroke.points.length < 2) return;
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

// Custom PDF viewer + annotation layer, replacing the browser's native PDF plugin for the
// grading workspace specifically — the native viewer has no way to hand back what a grader
// draws, so nothing could ever be saved. This renders pages via pdf.js onto a canvas and
// overlays a second canvas for freehand pen strokes and a click-to-place tick stamp (precise
// placement was the actual ask — drawing a tick shape with a mouse is fiddly). "Save Marked
// Copy" flattens every page's drawing onto its raster and bundles them into a new PDF.
const PdfAnnotator = ({ documentUrl, onSave, saving, saveError }) => {
    const [pdfDoc, setPdfDoc] = useState(null);
    const [numPages, setNumPages] = useState(0);
    const [currentPage, setCurrentPage] = useState(1);
    const [loadError, setLoadError] = useState('');
    const [tool, setTool] = useState('pen');
    const [color, setColor] = useState(COLORS[0]);
    const [thickness, setThickness] = useState(3);
    const [, bumpVersion] = useState(0);
    const [saveProgress, setSaveProgress] = useState(null);

    const pageCanvasRef = useRef(null);
    const annotationCanvasRef = useRef(null);
    const strokesByPageRef = useRef({});
    const isDrawingRef = useRef(false);
    const currentStrokeRef = useRef(null);
    const pageBitmapCacheRef = useRef(new Map());

    useEffect(() => {
        let cancelled = false;
        setLoadError('');
        setPdfDoc(null);
        strokesByPageRef.current = {};
        pageBitmapCacheRef.current.clear();
        getPdfjs().then(pdfjsLib => pdfjsLib.getDocument({ url: documentUrl }).promise).then(doc => {
            if (cancelled) return;
            setPdfDoc(doc);
            setNumPages(doc.numPages);
            setCurrentPage(1);
        }).catch(() => {
            if (!cancelled) setLoadError('Could not load this document for marking.');
        });
        return () => { cancelled = true; };
    }, [documentUrl]);

    const redrawAnnotations = useCallback(() => {
        const canvas = annotationCanvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        const strokes = strokesByPageRef.current[currentPage] || [];
        strokes.forEach(s => drawStroke(ctx, s));
    }, [currentPage]);

    useEffect(() => {
        if (!pdfDoc) return;
        let cancelled = false;
        const pageNum = currentPage;
        const cached = pageBitmapCacheRef.current.get(pageNum);
        if (cached) {
            const pageCanvas = pageCanvasRef.current;
            const annCanvas = annotationCanvasRef.current;
            if (!pageCanvas || !annCanvas) return;
            pageCanvas.width = cached.width;
            pageCanvas.height = cached.height;
            pageCanvas.style.width = `${cached.width}px`;
            pageCanvas.style.height = `${cached.height}px`;
            annCanvas.width = cached.width;
            annCanvas.height = cached.height;
            annCanvas.style.width = `${cached.width}px`;
            annCanvas.style.height = `${cached.height}px`;
            pageCanvas.getContext('2d').drawImage(cached, 0, 0);
            redrawAnnotations();
            return;
        }
        pdfDoc.getPage(pageNum).then(page => {
            if (cancelled) return;
            const viewport = page.getViewport({ scale: RENDER_SCALE });
            const pageCanvas = pageCanvasRef.current;
            const annCanvas = annotationCanvasRef.current;
            if (!pageCanvas || !annCanvas) return;
            // Set the CSS display size explicitly on both canvases, in addition to the pixel
            // buffer size — a wrapping container relying on a ref's value to size itself is
            // stale on the render that first mounts these canvases (the ref isn't attached
            // yet), which silently breaks pointer-to-canvas coordinate mapping.
            pageCanvas.width = viewport.width;
            pageCanvas.height = viewport.height;
            pageCanvas.style.width = `${viewport.width}px`;
            pageCanvas.style.height = `${viewport.height}px`;
            annCanvas.width = viewport.width;
            annCanvas.height = viewport.height;
            annCanvas.style.width = `${viewport.width}px`;
            annCanvas.style.height = `${viewport.height}px`;
            const ctx = pageCanvas.getContext('2d');
            page.render({ canvasContext: ctx, viewport }).promise.then(() => {
                if (cancelled) return;
                createImageBitmap(pageCanvas).then(bm => {
                    pageBitmapCacheRef.current.set(pageNum, bm);
                }).catch(() => {});
                redrawAnnotations();
            });
        });
        return () => { cancelled = true; };
    }, [pdfDoc, currentPage, redrawAnnotations]);

    const getCanvasPoint = (e) => {
        const canvas = annotationCanvasRef.current;
        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const scaleY = canvas.height / rect.height;
        return { x: (e.clientX - rect.left) * scaleX, y: (e.clientY - rect.top) * scaleY };
    };

    const handlePointerDown = (e) => {
        e.preventDefault();
        const point = getCanvasPoint(e);
        if (tool === 'tick' || tool === 'cross') {
            const stroke = { tool, color, x: point.x, y: point.y, size: 28 };
            if (!strokesByPageRef.current[currentPage]) strokesByPageRef.current[currentPage] = [];
            strokesByPageRef.current[currentPage].push(stroke);
            redrawAnnotations();
            bumpVersion(n => n + 1);
        } else {
            isDrawingRef.current = true;
            currentStrokeRef.current = { tool: 'pen', color, thickness, points: [point] };
        }
    };

    const handlePointerMove = (e) => {
        if (!isDrawingRef.current || tool !== 'pen') return;
        e.preventDefault();
        currentStrokeRef.current.points.push(getCanvasPoint(e));
        redrawAnnotations();
        const ctx = annotationCanvasRef.current.getContext('2d');
        drawStroke(ctx, currentStrokeRef.current);
    };

    const finishStroke = () => {
        if (!isDrawingRef.current) return;
        isDrawingRef.current = false;
        const stroke = currentStrokeRef.current;
        currentStrokeRef.current = null;
        if (stroke && stroke.points.length > 1) {
            if (!strokesByPageRef.current[currentPage]) strokesByPageRef.current[currentPage] = [];
            strokesByPageRef.current[currentPage].push(stroke);
        }
        redrawAnnotations();
        bumpVersion(n => n + 1);
    };

    const handleUndo = () => {
        const strokes = strokesByPageRef.current[currentPage] || [];
        strokes.pop();
        redrawAnnotations();
        bumpVersion(n => n + 1);
    };

    const handleClearPage = () => {
        strokesByPageRef.current[currentPage] = [];
        redrawAnnotations();
        bumpVersion(n => n + 1);
    };

    const currentPageStrokeCount = (strokesByPageRef.current[currentPage] || []).length;
    const hasAnyAnnotations = Object.values(strokesByPageRef.current).some(s => s && s.length > 0);

    const handleSave = async () => {
        if (!pdfDoc) return;
        setSaveProgress(0);
        try {
            const { jsPDF } = await import('jspdf');
            const firstPage = await pdfDoc.getPage(1);
            const firstView = firstPage.getViewport({ scale: 1 });
            const doc = new jsPDF({ unit: 'pt', format: [firstView.width, firstView.height] });

            for (let i = 1; i <= numPages; i++) {
                setSaveProgress(Math.round((i - 1) / numPages * 100));
                await new Promise(r => setTimeout(r, 0));
                const page = await pdfDoc.getPage(i);
                const renderViewport = page.getViewport({ scale: RENDER_SCALE });
                const canvas = document.createElement('canvas');
                canvas.width = renderViewport.width;
                canvas.height = renderViewport.height;
                const ctx = canvas.getContext('2d');
                const cached = pageBitmapCacheRef.current.get(i);
                if (cached) {
                    ctx.drawImage(cached, 0, 0);
                } else {
                    await page.render({ canvasContext: ctx, viewport: renderViewport }).promise;
                }
                (strokesByPageRef.current[i] || []).forEach(s => drawStroke(ctx, s));

                const dataUrl = canvas.toDataURL('image/jpeg', 0.85);
                const pageView1 = page.getViewport({ scale: 1 });
                if (i > 1) doc.addPage([pageView1.width, pageView1.height]);
                doc.addImage(dataUrl, 'JPEG', 0, 0, pageView1.width, pageView1.height);
            }

            setSaveProgress(100);
            const blob = doc.output('blob');
            onSave(blob);
        } finally {
            setSaveProgress(null);
        }
    };

    if (loadError) {
        return (
            <div className="flex-1 flex items-center justify-center text-center p-6">
                <p className="text-sm text-rose-400">{loadError}</p>
            </div>
        );
    }

    if (!pdfDoc) {
        return (
            <div className="flex-1 flex items-center justify-center">
                <p className="text-xs text-slate-500">Loading document for marking...</p>
            </div>
        );
    }

    return (
        <div className="flex-1 flex flex-col min-h-0">
            <div className="flex flex-wrap items-center gap-2 pb-3 flex-shrink-0">
                <button type="button" onClick={() => setTool('pen')}
                    className={`text-xs font-semibold py-1.5 px-3 rounded-lg transition-colors cursor-pointer ${tool === 'pen' ? 'bg-blue-600 text-[#f8fafc]' : 'bg-[#1e293b] text-[#e2e8f0] hover:bg-[#334155]'}`}>
                    ✎ Pen
                </button>
                <button type="button" onClick={() => setTool('tick')}
                    className={`text-xs font-semibold py-1.5 px-3 rounded-lg transition-colors cursor-pointer ${tool === 'tick' ? 'bg-blue-600 text-[#f8fafc]' : 'bg-[#1e293b] text-[#e2e8f0] hover:bg-[#334155]'}`}>
                    ✓ Tick Stamp
                </button>
                <button type="button" onClick={() => setTool('cross')}
                    className={`text-xs font-semibold py-1.5 px-3 rounded-lg transition-colors cursor-pointer ${tool === 'cross' ? 'bg-blue-600 text-[#f8fafc]' : 'bg-[#1e293b] text-[#e2e8f0] hover:bg-[#334155]'}`}>
                    ✗ Wrong Stamp
                </button>

                <div className="flex items-center gap-1.5 bg-[#1e293b] rounded-lg px-2 py-1.5">
                    {COLORS.map(c => (
                        <button key={c} type="button" onClick={() => setColor(c)}
                            className={`w-5 h-5 rounded-full cursor-pointer transition-transform ${color === c ? 'ring-2 ring-[#f8fafc] scale-110' : ''}`}
                            style={{ backgroundColor: c }} />
                    ))}
                </div>

                {tool === 'pen' && (
                    <div className="flex items-center gap-1.5 bg-[#1e293b] rounded-lg px-2.5 py-1.5">
                        <span className="text-[10px] text-[#94a3b8] font-semibold">Thickness</span>
                        <input type="range" min="1" max="8" value={thickness}
                            onChange={e => setThickness(Number(e.target.value))} className="w-16" />
                    </div>
                )}

                <button type="button" onClick={handleUndo} disabled={currentPageStrokeCount === 0}
                    className="text-xs font-semibold py-1.5 px-3 rounded-lg bg-[#1e293b] text-[#e2e8f0] hover:bg-[#334155] disabled:opacity-40 cursor-pointer">
                    Undo
                </button>
                <button type="button" onClick={handleClearPage} disabled={currentPageStrokeCount === 0}
                    className="text-xs font-semibold py-1.5 px-3 rounded-lg bg-[#1e293b] text-[#e2e8f0] hover:bg-[#334155] disabled:opacity-40 cursor-pointer">
                    Clear Page
                </button>

                {numPages > 1 && (
                    <div className="flex items-center gap-2 ml-auto">
                        <button type="button" onClick={() => setCurrentPage(p => Math.max(1, p - 1))} disabled={currentPage === 1}
                            className="text-xs font-semibold py-1.5 px-3 rounded-lg bg-[#1e293b] text-[#e2e8f0] hover:bg-[#334155] disabled:opacity-40 cursor-pointer">
                            ← Prev
                        </button>
                        <span className="text-xs text-[#94a3b8] font-semibold min-w-[90px] text-center">Page {currentPage} of {numPages}</span>
                        <button type="button" onClick={() => setCurrentPage(p => Math.min(numPages, p + 1))} disabled={currentPage === numPages}
                            className="text-xs font-semibold py-1.5 px-3 rounded-lg bg-[#1e293b] text-[#e2e8f0] hover:bg-[#334155] disabled:opacity-40 cursor-pointer">
                            Next →
                        </button>
                    </div>
                )}

                <button type="button" onClick={handleSave} disabled={saving || saveProgress !== null || !hasAnyAnnotations}
                    className={`text-xs font-bold py-1.5 px-4 rounded-lg cursor-pointer transition-colors disabled:opacity-40 ${numPages > 1 ? '' : 'ml-auto'} bg-emerald-600 hover:bg-emerald-700 text-[#f8fafc]`}>
                    {saving ? 'Saving...' : saveProgress !== null ? `Saving ${saveProgress}%` : 'Save Marked Copy'}
                </button>
            </div>

            {saveError && <p className="text-[11px] text-rose-400 pb-2">{saveError}</p>}

            <div className="flex-1 overflow-auto rounded-2xl border border-[#1e293b] bg-[#0f172a]/60 flex items-start justify-center p-4">
                <div className="relative inline-block">
                    <canvas ref={pageCanvasRef} className="block" />
                    <canvas
                        ref={annotationCanvasRef}
                        className="absolute inset-0 touch-none"
                        style={{ cursor: tool === 'tick' ? 'crosshair' : 'crosshair' }}
                        onPointerDown={handlePointerDown}
                        onPointerMove={handlePointerMove}
                        onPointerUp={finishStroke}
                        onPointerLeave={finishStroke}
                    />
                </div>
            </div>
        </div>
    );
};

export default PdfAnnotator;
