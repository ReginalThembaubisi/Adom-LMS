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
const WINDOW_BUFFER = 2; // pages beyond the visible area to keep rendered

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

// Continuous-scroll annotator: all pages stacked vertically in one scrollable container.
// Three canvas layers per page — pdf (rendered page), annot (committed strokes, static),
// live (current in-progress stroke only) — so pointer-move only touches one cheap canvas
// instead of clearing and replaying all history on every frame. An IntersectionObserver
// manages a render window of visible±WINDOW_BUFFER pages; pages outside it are replaced by
// correctly-sized placeholder divs so the scrollbar height never jumps.
const PdfAnnotator = ({ documentUrl, onSave, saving, saveError }) => {
    const [pdfDoc, setPdfDoc] = useState(null);
    const [numPages, setNumPages] = useState(0);
    const [loadError, setLoadError] = useState('');
    const [tool, setTool] = useState('pen');
    const [color, setColor] = useState(COLORS[0]);
    const [thickness, setThickness] = useState(3);
    const [, bumpVersion] = useState(0);
    const [saveProgress, setSaveProgress] = useState(null);
    // [null, {width, height}, ...] — 1-indexed so pageViewports[pageNum] works directly
    const [pageViewports, setPageViewports] = useState([]);
    const [windowedPages, setWindowedPages] = useState(new Set());
    const [centerPage, setCenterPage] = useState(1);

    const strokesByPageRef = useRef({});
    const pageBitmapCacheRef = useRef(new Map());
    const isDrawingRef = useRef(false);
    const currentStrokeRef = useRef(null);
    const drawingPageRef = useRef(null);

    // Per-page ref arrays, 1-indexed (index 0 unused)
    const pageWrapperRefs = useRef([]);
    const pdfCanvasRefs = useRef([]);
    const annotCanvasRefs = useRef([]);  // static committed-stroke layer
    const liveCanvasRefs = useRef([]);   // top layer — in-progress stroke only

    const scrollContainerRef = useRef(null);
    const numPagesRef = useRef(0);
    numPagesRef.current = numPages;

    // IntersectionObserver state tracked in refs to avoid stale closures in callbacks
    const visiblePagesRef = useRef(new Set());
    // Tracks which pages have had their PDF raster drawn; entries cleared when canvas is unmounted
    const renderedPagesRef = useRef(new Set());
    // Tracks where the grader last placed a mark — Undo/Clear target this, not scroll position.
    // centerPage drives the display indicator only; destructive actions follow annotation intent.
    const lastAnnotatedPageRef = useRef(1);

    // Load PDF and fetch all page viewport dimensions before rendering anything,
    // so placeholder divs have correct heights and the scrollbar never jumps.
    useEffect(() => {
        let cancelled = false;
        setLoadError('');
        setPdfDoc(null);
        setPageViewports([]);
        setWindowedPages(new Set());
        setCenterPage(1);
        visiblePagesRef.current.clear();
        lastAnnotatedPageRef.current = 1;
        strokesByPageRef.current = {};
        pageBitmapCacheRef.current.clear();
        renderedPagesRef.current.clear();

        getPdfjs()
            .then(lib => lib.getDocument({ url: documentUrl }).promise)
            .then(async doc => {
                if (cancelled) return;
                const n = doc.numPages;
                const vps = [null]; // 1-indexed
                for (let i = 1; i <= n; i++) {
                    const page = await doc.getPage(i);
                    const vp = page.getViewport({ scale: RENDER_SCALE });
                    vps.push({ width: vp.width, height: vp.height });
                }
                if (cancelled) return;
                setPdfDoc(doc);
                setNumPages(n);
                setPageViewports(vps);
                // Pre-warm window so first render shows real pages, not placeholders
                const init = new Set();
                for (let i = 1; i <= Math.min(n, 1 + WINDOW_BUFFER); i++) init.add(i);
                setWindowedPages(init);
            })
            .catch(() => {
                if (!cancelled) setLoadError('Could not load this document for marking.');
            });
        return () => { cancelled = true; };
    }, [documentUrl]);

    // Clear + replay all committed strokes onto the static annotation canvas for one page.
    // Called on undo, clear, and when a page re-enters the render window after eviction.
    const redrawStaticLayer = useCallback((pageNum) => {
        const canvas = annotCanvasRefs.current[pageNum];
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        (strokesByPageRef.current[pageNum] || []).forEach(s => drawStroke(ctx, s));
    }, []);

    // Render one page's PDF content onto its canvas triple. Uses the bitmap cache when
    // available; falls back to pdf.js render and stores a new bitmap for next time.
    const renderPage = useCallback(async (doc, pageNum) => {
        const pdfCanvas = pdfCanvasRefs.current[pageNum];
        const annotCanvas = annotCanvasRefs.current[pageNum];
        const liveCanvas = liveCanvasRefs.current[pageNum];
        if (!pdfCanvas || !annotCanvas || !liveCanvas) return;
        const page = await doc.getPage(pageNum);
        const vp = page.getViewport({ scale: RENDER_SCALE });
        [pdfCanvas, annotCanvas, liveCanvas].forEach(c => {
            c.width = vp.width;
            c.height = vp.height;
            c.style.width = `${vp.width}px`;
            c.style.height = `${vp.height}px`;
        });
        const pdfCtx = pdfCanvas.getContext('2d');
        const cached = pageBitmapCacheRef.current.get(pageNum);
        if (cached) {
            pdfCtx.drawImage(cached, 0, 0);
        } else {
            await page.render({ canvasContext: pdfCtx, viewport: vp }).promise;
            createImageBitmap(pdfCanvas).then(bm => {
                pageBitmapCacheRef.current.set(pageNum, bm);
            }).catch(() => {});
        }
        // Replay any existing annotations (page may have been annotated before eviction)
        const annotCtx = annotCanvas.getContext('2d');
        annotCtx.clearRect(0, 0, annotCanvas.width, annotCanvas.height);
        (strokesByPageRef.current[pageNum] || []).forEach(s => drawStroke(annotCtx, s));
    }, []);

    // Render pages that just entered the render window. Pages leaving the window have their
    // canvas elements unmounted, so we clear their entry from renderedPagesRef so they get
    // re-rendered on re-entry.
    useEffect(() => {
        if (!pdfDoc) return;
        // Clear tracking for pages no longer in the window (canvases are now unmounted)
        renderedPagesRef.current.forEach(p => {
            if (!windowedPages.has(p)) renderedPagesRef.current.delete(p);
        });
        // Render pages newly added to the window
        windowedPages.forEach(pageNum => {
            const c = pdfCanvasRefs.current[pageNum];
            if (c && !renderedPagesRef.current.has(pageNum)) {
                renderedPagesRef.current.add(pageNum);
                renderPage(pdfDoc, pageNum);
            }
        });
    }, [pdfDoc, windowedPages, renderPage]);

    // Set up IntersectionObserver once the page dimensions are known.
    // Manages the render window (visible ± WINDOW_BUFFER pages).
    // Center-page tracking uses a scroll listener instead — IO intersection ratios
    // are unreliable when a single page is taller than the scroll container.
    useEffect(() => {
        if (!numPages || pageViewports.length < 2) return;

        const computeWindow = () => {
            const next = new Set();
            visiblePagesRef.current.forEach(p => {
                for (let d = -WINDOW_BUFFER; d <= WINDOW_BUFFER; d++) {
                    const n = p + d;
                    if (n >= 1 && n <= numPagesRef.current) next.add(n);
                }
            });
            return next;
        };

        const mainObserver = new IntersectionObserver(entries => {
            entries.forEach(entry => {
                const p = Number(entry.target.dataset.page);
                if (entry.isIntersecting) visiblePagesRef.current.add(p);
                else visiblePagesRef.current.delete(p);
            });
            setWindowedPages(computeWindow());
        }, { root: scrollContainerRef.current, rootMargin: '0px', threshold: 0 });

        for (let i = 1; i <= numPages; i++) {
            const el = pageWrapperRefs.current[i];
            if (el) mainObserver.observe(el);
        }
        return () => mainObserver.disconnect();
    }, [numPages, pageViewports]);

    // Compute center page from scroll position. Arithmetic against accumulated page heights
    // is accurate regardless of page-to-container-height ratio. Runs once on mount (scroll=0
    // → page 1) and on every scroll event.
    useEffect(() => {
        if (pageViewports.length < 2) return;
        const el = scrollContainerRef.current;
        if (!el) return;

        const STACK_PAD_TOP = 52; // py value of the inner page-stack div
        const PAGE_GAP = 24;      // gap-6 between pages

        const computeCenter = () => {
            const centerY = el.scrollTop + el.clientHeight / 2;
            let accumulated = STACK_PAD_TOP;
            let bestPage = 1;
            let bestDist = Infinity;
            for (let i = 1; i < pageViewports.length; i++) {
                const pageCenter = accumulated + pageViewports[i].height / 2;
                const dist = Math.abs(pageCenter - centerY);
                if (dist < bestDist) { bestDist = dist; bestPage = i; }
                accumulated += pageViewports[i].height + PAGE_GAP;
            }
            setCenterPage(bestPage);
        };

        computeCenter(); // initialise on mount
        el.addEventListener('scroll', computeCenter, { passive: true });
        return () => el.removeEventListener('scroll', computeCenter);
    }, [pageViewports]);

    // --- Pointer handlers (per-page, coordinate-mapped to each page's live canvas) ---

    const getPoint = (e, pageNum) => {
        const canvas = liveCanvasRefs.current[pageNum];
        const rect = canvas.getBoundingClientRect();
        return {
            x: (e.clientX - rect.left) * (canvas.width / rect.width),
            y: (e.clientY - rect.top) * (canvas.height / rect.height),
        };
    };

    const handlePointerDown = (e, pageNum) => {
        e.preventDefault();
        const point = getPoint(e, pageNum);
        if (tool === 'tick' || tool === 'cross') {
            const stroke = { tool, color, x: point.x, y: point.y, size: 28 };
            if (!strokesByPageRef.current[pageNum]) strokesByPageRef.current[pageNum] = [];
            strokesByPageRef.current[pageNum].push(stroke);
            lastAnnotatedPageRef.current = pageNum;
            // Draw directly onto the static layer — no clear/replay needed for a new stamp
            const annotCanvas = annotCanvasRefs.current[pageNum];
            if (annotCanvas) drawStroke(annotCanvas.getContext('2d'), stroke);
            bumpVersion(n => n + 1);
        } else {
            isDrawingRef.current = true;
            drawingPageRef.current = pageNum;
            currentStrokeRef.current = { tool: 'pen', color, thickness, points: [point] };
        }
    };

    const handlePointerMove = (e, pageNum) => {
        if (!isDrawingRef.current || tool !== 'pen' || drawingPageRef.current !== pageNum) return;
        e.preventDefault();
        currentStrokeRef.current.points.push(getPoint(e, pageNum));
        // Only touch the live canvas — cheap clear+draw of the single in-progress stroke
        const liveCanvas = liveCanvasRefs.current[pageNum];
        if (!liveCanvas) return;
        const ctx = liveCanvas.getContext('2d');
        ctx.clearRect(0, 0, liveCanvas.width, liveCanvas.height);
        drawStroke(ctx, currentStrokeRef.current);
    };

    const finishStroke = (pageNum) => {
        if (!isDrawingRef.current || drawingPageRef.current !== pageNum) return;
        isDrawingRef.current = false;
        const stroke = currentStrokeRef.current;
        currentStrokeRef.current = null;
        drawingPageRef.current = null;
        const liveCanvas = liveCanvasRefs.current[pageNum];
        if (liveCanvas) liveCanvas.getContext('2d').clearRect(0, 0, liveCanvas.width, liveCanvas.height);
        if (stroke && stroke.points.length > 1) {
            if (!strokesByPageRef.current[pageNum]) strokesByPageRef.current[pageNum] = [];
            strokesByPageRef.current[pageNum].push(stroke);
            lastAnnotatedPageRef.current = pageNum;
            // Accumulate onto static layer (no clear+replay — just draw the new stroke)
            const annotCanvas = annotCanvasRefs.current[pageNum];
            if (annotCanvas) drawStroke(annotCanvas.getContext('2d'), stroke);
        }
        bumpVersion(n => n + 1);
    };

    // Undo and clear target the page the grader last annotated, not the scroll position.
    // centerPage is display-only so it doesn't matter whether the grader has scrolled away.
    const handleUndo = () => {
        const target = lastAnnotatedPageRef.current;
        const strokes = strokesByPageRef.current[target] || [];
        if (!strokes.length) return;
        strokes.pop();
        redrawStaticLayer(target); // full replay needed to remove the last stroke
        bumpVersion(n => n + 1);
    };

    const handleClearPage = () => {
        const target = lastAnnotatedPageRef.current;
        strokesByPageRef.current[target] = [];
        redrawStaticLayer(target);
        bumpVersion(n => n + 1);
    };

    const scrollToPage = (pageNum) => {
        const el = pageWrapperRefs.current[pageNum];
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    };

    // Save: unchanged from paged version — jsPDF, ImageBitmap cache, per-page yield, progress %
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
            onSave(doc.output('blob'));
        } finally {
            setSaveProgress(null);
        }
    };

    // Derived values for toolbar
    const lastAnnotatedPageStrokes = (strokesByPageRef.current[lastAnnotatedPageRef.current] || []).length;
    const hasAnyAnnotations = Object.values(strokesByPageRef.current).some(s => s && s.length > 0);
    const markedPageNums = Object.entries(strokesByPageRef.current)
        .filter(([, s]) => s && s.length > 0)
        .map(([p]) => Number(p))
        .sort((a, b) => a - b);

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
            {/* Toolbar */}
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

                <button type="button" onClick={handleUndo} disabled={lastAnnotatedPageStrokes === 0}
                    className="text-xs font-semibold py-1.5 px-3 rounded-lg bg-[#1e293b] text-[#e2e8f0] hover:bg-[#334155] disabled:opacity-40 cursor-pointer">
                    Undo
                </button>
                <button type="button" onClick={handleClearPage} disabled={lastAnnotatedPageStrokes === 0}
                    className="text-xs font-semibold py-1.5 px-3 rounded-lg bg-[#1e293b] text-[#e2e8f0] hover:bg-[#334155] disabled:opacity-40 cursor-pointer">
                    Clear Page
                </button>

                <button type="button" onClick={handleSave}
                    disabled={saving || saveProgress !== null || !hasAnyAnnotations}
                    className="ml-auto text-xs font-bold py-1.5 px-4 rounded-lg cursor-pointer transition-colors disabled:opacity-40 bg-emerald-600 hover:bg-emerald-700 text-[#f8fafc]">
                    {saving ? 'Saving...' : saveProgress !== null ? `Saving ${saveProgress}%` : 'Save Marked Copy'}
                </button>
            </div>

            {saveError && <p className="text-[11px] text-rose-400 pb-2">{saveError}</p>}

            {/* Scroll container */}
            <div ref={scrollContainerRef} className="flex-1 overflow-auto rounded-2xl border border-[#1e293b] bg-[#0f172a]/60 relative">

                {/* Floating page indicator + marked-pages jump strip, sticky inside the scroll container */}
                <div className="sticky top-3 z-10 flex justify-end pr-3 pointer-events-none">
                    <div className="pointer-events-auto flex flex-col items-end gap-1">
                        <div className="bg-slate-900/90 border border-slate-700 rounded-lg px-2.5 py-1 text-[11px] font-bold text-slate-300 tabular-nums">
                            {centerPage} / {numPages}
                        </div>
                        {markedPageNums.length > 0 && (
                            <div className="flex flex-col gap-0.5">
                                {markedPageNums.map(p => (
                                    <button
                                        key={p}
                                        type="button"
                                        onClick={() => scrollToPage(p)}
                                        title={`Jump to page ${p}`}
                                        className={`text-[10px] font-bold rounded px-1.5 py-0.5 cursor-pointer transition-colors ${p === centerPage ? 'bg-emerald-500 text-white' : 'bg-emerald-800/70 hover:bg-emerald-600 text-emerald-200'}`}>
                                        p{p}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                </div>

                {/* Vertically stacked pages — py-[52px] / gap-6 gives the "sheets on a dark desk" look */}
                <div className="flex flex-col items-center gap-6" style={{ padding: '52px 62px' }}>
                    {pageViewports.slice(1).map((vp, idx) => {
                        const pageNum = idx + 1;
                        const inWindow = windowedPages.has(pageNum);
                        return (
                            <div
                                key={pageNum}
                                ref={el => { pageWrapperRefs.current[pageNum] = el; }}
                                data-page={pageNum}
                                style={{
                                    width: vp.width,
                                    height: vp.height,
                                    borderRadius: '5px',
                                    boxShadow: '0 16px 40px -18px rgba(0,0,0,.6)',
                                }}
                                className="relative flex-shrink-0"
                            >
                                {inWindow ? (
                                    <>
                                        {/* Layer 1: rendered PDF page */}
                                        <canvas
                                            ref={el => { pdfCanvasRefs.current[pageNum] = el; }}
                                            className="absolute inset-0 block"
                                            style={{ borderRadius: '5px' }}
                                        />
                                        {/* Layer 2: committed annotation strokes (static) */}
                                        <canvas
                                            ref={el => { annotCanvasRefs.current[pageNum] = el; }}
                                            className="absolute inset-0"
                                        />
                                        {/* Layer 3: in-progress stroke only (cleared each move) */}
                                        <canvas
                                            ref={el => { liveCanvasRefs.current[pageNum] = el; }}
                                            className="absolute inset-0 touch-none"
                                            style={{ cursor: 'crosshair' }}
                                            onPointerDown={e => handlePointerDown(e, pageNum)}
                                            onPointerMove={e => handlePointerMove(e, pageNum)}
                                            onPointerUp={() => finishStroke(pageNum)}
                                            onPointerLeave={() => finishStroke(pageNum)}
                                        />
                                    </>
                                ) : (
                                    // Placeholder keeps correct height so scrollbar doesn't jump
                                    <div className="w-full h-full bg-slate-900/40" style={{ borderRadius: '5px' }} />
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};

export default PdfAnnotator;
