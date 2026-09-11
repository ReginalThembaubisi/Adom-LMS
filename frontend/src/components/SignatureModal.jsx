import React, { useEffect, useRef, useState } from 'react';
import { X } from '@phosphor-icons/react';
import { useLearner } from '../context/LearnerContext';

/**
 * Sign a submission or a personal document.
 *
 * Declaration wording is fetched from the server rather than hardcoded here — the backend
 * stores the exact text shown at signing time onto the event, and a copy baked into this
 * component could drift from that without either side noticing. See
 * SignatureService.declarationTextFor on the backend.
 *
 * The drawn specimen is a plain <canvas>, not a library: signature_pad is not a dependency of
 * this project and a scribble captured as one PNG frame does not need one.
 */
export default function SignatureModal({ signableType, signableId, documentLabel, onClose, onSigned }) {
    const { authFetch } = useLearner();
    const canvasRef = useRef(null);
    const drawingRef = useRef(false);
    const hasDrawnRef = useRef(false);

    const [declarationText, setDeclarationText] = useState('');
    const [agreed, setAgreed] = useState(false);
    const [password, setPassword] = useState('');
    const [hasDrawn, setHasDrawn] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                const res = await authFetch(`/api/me/signatures/declaration-text?signableType=${signableType}`);
                if (!res.ok) throw new Error();
                const data = await res.json();
                if (!cancelled) setDeclarationText(data.text);
            } catch {
                if (!cancelled) setError('Could not load the declaration text. Please close and try again.');
            }
        })();
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [signableType]);

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        ctx.lineWidth = 2.5;
        ctx.lineCap = 'round';
        ctx.strokeStyle = '#101425';
    }, []);

    const pointFromEvent = (canvas, e) => {
        const rect = canvas.getBoundingClientRect();
        const point = e.touches ? e.touches[0] : e;
        return {
            x: (point.clientX - rect.left) * (canvas.width / rect.width),
            y: (point.clientY - rect.top) * (canvas.height / rect.height)
        };
    };

    const startDraw = (e) => {
        e.preventDefault();
        const canvas = canvasRef.current;
        const ctx = canvas.getContext('2d');
        const { x, y } = pointFromEvent(canvas, e);
        ctx.beginPath();
        ctx.moveTo(x, y);
        drawingRef.current = true;
    };

    const draw = (e) => {
        if (!drawingRef.current) return;
        e.preventDefault();
        const canvas = canvasRef.current;
        const ctx = canvas.getContext('2d');
        const { x, y } = pointFromEvent(canvas, e);
        ctx.lineTo(x, y);
        ctx.stroke();
        if (!hasDrawnRef.current) {
            hasDrawnRef.current = true;
            setHasDrawn(true);
        }
    };

    const stopDraw = () => { drawingRef.current = false; };

    const clearSignature = () => {
        const canvas = canvasRef.current;
        canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
        hasDrawnRef.current = false;
        setHasDrawn(false);
    };

    const submit = async () => {
        setError('');
        if (!agreed) { setError('Tick the declaration to continue.'); return; }
        if (!hasDrawn) { setError('Draw your signature above.'); return; }
        if (!password) { setError('Enter your password to confirm it is you.'); return; }

        setSubmitting(true);
        try {
            const specimenImage = canvasRef.current.toDataURL('image/png');
            const res = await authFetch('/api/me/signatures', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ signableType, signableId, specimenImage, password })
            });
            if (!res.ok) {
                const body = await res.json().catch(() => ({}));
                throw new Error(body.message || 'Could not sign this document.');
            }
            const signature = await res.json();
            onSigned(signature);
        } catch (err) {
            setError(err.message || 'Could not sign this document.');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center" style={{ background: 'rgba(16,20,37,0.5)' }}>
            <div className="w-full sm:max-w-md bg-white rounded-t-3xl sm:rounded-3xl p-5 space-y-4 max-h-[90vh] overflow-y-auto">
                <div className="flex items-start justify-between">
                    <h3 className="text-[15px] font-bold text-[#101425]">Sign {documentLabel}</h3>
                    <button onClick={onClose} className="text-[#8A90A8] cursor-pointer"><X size={20} /></button>
                </div>

                <div className="text-[11px] leading-relaxed text-[#5A6072] bg-[#F6F7FB] rounded-2xl p-3">
                    {declarationText || 'Loading declaration...'}
                </div>

                <label className="flex items-start gap-2 text-[11px] text-[#101425]">
                    <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} className="mt-0.5" />
                    <span>I have read and agree to the declaration above.</span>
                </label>

                <div>
                    <p className="text-[10px] font-semibold text-[#8A90A8] mb-1.5">Draw your signature</p>
                    <canvas
                        ref={canvasRef}
                        width={400}
                        height={160}
                        className="w-full rounded-xl border touch-none"
                        style={{ borderColor: '#DDE1EC', background: '#FCFCFD', height: 140 }}
                        onMouseDown={startDraw}
                        onMouseMove={draw}
                        onMouseUp={stopDraw}
                        onMouseLeave={stopDraw}
                        onTouchStart={startDraw}
                        onTouchMove={draw}
                        onTouchEnd={stopDraw}
                    />
                    <button onClick={clearSignature} className="text-[10px] font-semibold text-[#4A3AFF] mt-1 cursor-pointer">
                        Clear
                    </button>
                </div>

                <div>
                    <p className="text-[10px] font-semibold text-[#8A90A8] mb-1.5">Confirm it's you — enter your password</p>
                    <input
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        className="w-full rounded-xl border px-3 py-2.5 text-[13px]"
                        style={{ borderColor: '#DDE1EC' }}
                        placeholder="Your password"
                    />
                </div>

                {error && (
                    <p className="text-[11px] font-medium px-3 py-2 rounded-xl" style={{ background: '#FEF3F2', color: '#B42318' }}>
                        {error}
                    </p>
                )}

                <button
                    onClick={submit}
                    disabled={submitting}
                    className="w-full py-3 rounded-xl text-[13px] font-semibold text-white cursor-pointer"
                    style={{ background: submitting ? '#B8B4FF' : '#4A3AFF' }}
                >
                    {submitting ? 'Signing...' : 'Sign'}
                </button>
            </div>
        </div>
    );
}
