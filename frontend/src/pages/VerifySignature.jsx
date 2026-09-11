import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { CheckCircle, XCircle, ShieldCheck } from '@phosphor-icons/react';

/**
 * Public signature verification — no login, no learner code in the URL.
 *
 * Only ever renders what {@code GET /api/verify/{code}} returns: document type, signer role,
 * signature date, and whether the hash still matches. The backend is the enforcement point for
 * that rule; this page just has nothing else to show even if it wanted to.
 */
export default function VerifySignature() {
    const { code } = useParams();
    const [state, setState] = useState({ loading: true, result: null, error: null });

    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                const res = await fetch(`/api/verify/${encodeURIComponent(code)}`);
                if (!res.ok) {
                    if (!cancelled) setState({ loading: false, result: null, error: 'No signature was found for this code.' });
                    return;
                }
                const result = await res.json();
                if (!cancelled) setState({ loading: false, result, error: null });
            } catch {
                if (!cancelled) setState({ loading: false, result: null, error: 'Could not check this signature right now.' });
            }
        })();
        return () => { cancelled = true; };
    }, [code]);

    return (
        <div className="min-h-screen flex items-center justify-center px-4" style={{ background: '#F6F7FB' }}>
            <div className="w-full max-w-sm bg-white rounded-3xl p-6 space-y-4 shadow-sm">
                <div className="flex items-center gap-2">
                    <ShieldCheck size={22} weight="fill" style={{ color: '#4A3AFF' }} />
                    <h1 className="text-[15px] font-bold text-[#101425]">Signature verification</h1>
                </div>

                {state.loading && <p className="text-[12px] text-[#8A90A8]">Checking...</p>}

                {state.error && (
                    <p className="text-[12px] font-medium px-3 py-2.5 rounded-xl" style={{ background: '#FEF3F2', color: '#B42318' }}>
                        {state.error}
                    </p>
                )}

                {state.result && (
                    <div className="space-y-3">
                        <div className={`flex items-center gap-2 px-3 py-2.5 rounded-xl`}
                            style={{ background: state.result.valid ? '#ECFDF3' : '#FEF3F2' }}>
                            {state.result.valid
                                ? <CheckCircle size={18} weight="fill" style={{ color: '#027A48' }} />
                                : <XCircle size={18} weight="fill" style={{ color: '#B42318' }} />}
                            <span className="text-[12px] font-bold" style={{ color: state.result.valid ? '#027A48' : '#B42318' }}>
                                {state.result.valid ? 'Valid signature' : state.result.revoked ? 'Signature withdrawn' : 'Hash mismatch'}
                            </span>
                        </div>

                        <dl className="text-[12px] space-y-2">
                            <Row label="Document type" value={state.result.documentType} />
                            <Row label="Signed by" value={state.result.signerRole} />
                            <Row label="Signed on" value={new Date(state.result.signedAt).toLocaleString()} />
                            <Row label="Hash still matches" value={state.result.hashMatches ? 'Yes' : 'No'} />
                            {state.result.revoked && <Row label="Withdrawn" value="Yes" />}
                        </dl>

                        <p className="text-[10px] text-[#8A90A8] leading-relaxed pt-2 border-t" style={{ borderColor: '#EEF0FF' }}>
                            This page shows only the document type, who signed it, when, and whether the
                            recorded hash still matches — no names, no ID numbers, and no file content.
                        </p>
                    </div>
                )}
            </div>
        </div>
    );
}

function Row({ label, value }) {
    return (
        <div className="flex items-center justify-between">
            <dt className="text-[#8A90A8]">{label}</dt>
            <dd className="font-semibold text-[#101425]">{value}</dd>
        </div>
    );
}
