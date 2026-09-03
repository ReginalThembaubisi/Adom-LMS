import React, { useState, useRef, useEffect, useCallback } from 'react';
import { drawStroke } from '../utils/pdfAnnotations';

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

const RENDER_SCALE = 1.5;
const WINDOW_BUFFER = 2;

// Read-only counterpart to PdfAnnotator: renders the original PDF via pdf.js and
// replays saved annotation strokes on top. No drawing capability — view-only.
const PdfReplay = ({ documentUrl, strokes }) => {
    const [pdfDoc, setPdfDoc] = useState(null);
    const [numPages, setNumPages] = useState(0);
    const [loadError, setLoadError] = useState('');
    const [pageViewports, setPageViewports] = useState([]);
    const [windowedPages, setWindowedPages] = useState(new Set());

    const pageBitmapCacheRef = useRef(new Map());
    const pageWrapperRefs = useRef([]);
    const pdfCanvasRefs = useRef([]);
    const annotCanvasRefs = useRef([]);

    const scrollContainerRef = useRef(null);
    const numPagesRef = useRef(0);
    numPagesRef.current = numPages;
    const visiblePagesRef = useRef(new Set());
    const renderedPagesRef = useRef(new Set());

    useEffect(() => {
        let cancelled = false;
        setLoadError('');
        setPdfDoc(null);
        setPageViewports([]);
        setWindowedPages(new Set());
        visiblePagesRef.current.clear();
        pageBitmapCacheRef.current.clear();
        renderedPagesRef.current.clear();

        getPdfjs()
            .then(lib => lib.getDocument({ url: documentUrl }).promise)
            .then(async doc => {
                if (cancelled) return;
                const n = doc.numPages;
                const vps = [null];
                for (let i = 1; i <= n; i++) {
                    const page = await doc.getPage(i);
                    const vp = page.getViewport({ scale: RENDER_SCALE });
                    vps.push({ width: vp.width, height: vp.height });
                }
                if (cancelled) return;
                setPdfDoc(doc);
                setNumPages(n);
                setPageViewports(vps);
                const init = new Set();
                for (let i = 1; i <= Math.min(n, 1 + WINDOW_BUFFER); i++) init.add(i);
                setWindowedPages(init);
            })
            .catch(() => {
                if (!cancelled) setLoadError('Could not load document.');
            });
        return () => { cancelled = true; };
    }, [documentUrl]);

    const renderPage = useCallback(async (doc, pageNum) => {
        const pdfCanvas = pdfCanvasRefs.current[pageNum];
        const annotCanvas = annotCanvasRefs.current[pageNum];
        if (!pdfCanvas || !annotCanvas) return;
        const page = await doc.getPage(pageNum);
        const vp = page.getViewport({ scale: RENDER_SCALE });
        [pdfCanvas, annotCanvas].forEach(c => {
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
        const annotCtx = annotCanvas.getContext('2d');
        annotCtx.clearRect(0, 0, annotCanvas.width, annotCanvas.height);
        (strokes[pageNum] || []).forEach(s => drawStroke(annotCtx, s));
    }, [strokes]);

    useEffect(() => {
        if (!pdfDoc) return;
        renderedPagesRef.current.forEach(p => {
            if (!windowedPages.has(p)) renderedPagesRef.current.delete(p);
        });
        windowedPages.forEach(pageNum => {
            const c = pdfCanvasRefs.current[pageNum];
            if (c && !renderedPagesRef.current.has(pageNum)) {
                renderedPagesRef.current.add(pageNum);
                renderPage(pdfDoc, pageNum);
            }
        });
    }, [pdfDoc, windowedPages, renderPage]);

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
        const obs = new IntersectionObserver(entries => {
            entries.forEach(entry => {
                const p = Number(entry.target.dataset.page);
                if (entry.isIntersecting) visiblePagesRef.current.add(p);
                else visiblePagesRef.current.delete(p);
            });
            setWindowedPages(computeWindow());
        }, { root: scrollContainerRef.current, rootMargin: '0px', threshold: 0 });
        for (let i = 1; i <= numPages; i++) {
            const el = pageWrapperRefs.current[i];
            if (el) obs.observe(el);
        }
        return () => obs.disconnect();
    }, [numPages, pageViewports]);

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
                <p className="text-xs text-slate-500">Loading document...</p>
            </div>
        );
    }

    return (
        <div ref={scrollContainerRef} className="flex-1 overflow-auto rounded-2xl border border-[#1e293b] bg-[#0f172a]/60">
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
                                    <canvas
                                        ref={el => { pdfCanvasRefs.current[pageNum] = el; }}
                                        className="absolute inset-0 block"
                                        style={{ borderRadius: '5px' }}
                                    />
                                    <canvas
                                        ref={el => { annotCanvasRefs.current[pageNum] = el; }}
                                        className="absolute inset-0"
                                    />
                                </>
                            ) : (
                                <div className="w-full h-full bg-slate-900/40" style={{ borderRadius: '5px' }} />
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default PdfReplay;
