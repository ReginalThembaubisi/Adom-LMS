import React, { Suspense, lazy, useState } from 'react';
const PdfAnnotator = lazy(() => import('../components/PdfAnnotator'));

const AnnotatorTest = () => {
    const [saved, setSaved] = useState(false);
    const handleSave = (blob) => {
        setSaved(true);
        setTimeout(() => setSaved(false), 3000);
    };
    return (
        <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col p-6" style={{ fontFamily: 'system-ui, sans-serif' }}>
            <div className="mb-4">
                <h1 className="text-lg font-bold text-slate-100">PdfAnnotator — Continuous Scroll Test</h1>
                <p className="text-xs text-slate-400 mt-1">3-page test document. Scroll through pages, place ticks/crosses, draw with pen, then Save Marked Copy.</p>
                {saved && <p className="text-xs text-emerald-400 mt-1">✓ Save callback fired — blob received.</p>}
            </div>
            <div className="flex-1 flex flex-col" style={{ height: 'calc(100vh - 120px)' }}>
                <Suspense fallback={<div className="text-xs text-slate-500 p-4">Loading PDF viewer...</div>}>
                    <PdfAnnotator
                        documentUrl="/test.pdf"
                        onSave={handleSave}
                        saving={false}
                        saveError=""
                    />
                </Suspense>
            </div>
        </div>
    );
};

export default AnnotatorTest;
