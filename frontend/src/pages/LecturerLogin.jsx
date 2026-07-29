import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

const LecturerLogin = () => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        const user = username.trim();
        const pass = password.trim();

        if (!user || !pass) return;

        setLoading(true);

        // Construct Basic Auth token
        const token = btoa(`${user}:${pass}`);

        try {
            const res = await fetch('/api/lecturer/modules', {
                method: 'GET',
                headers: {
                    'Authorization': `Basic ${token}`
                }
            });

            if (res.status === 401 || res.status === 403) {
                throw new Error('Invalid lecturer credentials. Please try again.');
            }

            if (res.ok) {
                // Save basic auth token in session storage for calls
                sessionStorage.setItem('lecturer_auth', token);
                navigate('/lecturer-dashboard');
            } else {
                throw new Error('Authentication failed.');
            }
        } catch (err) {
            setError(err.message || 'Connection failed.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="bg-slate-50/80 min-h-screen text-slate-800 antialiased flex flex-col justify-center items-center p-4 relative overflow-hidden">
            {/* Ambient background blobs */}
            <div className="absolute top-0 -left-4 w-72 h-72 bg-blue-400/10 rounded-full filter blur-3xl opacity-70 animate-blob pointer-events-none"></div>
            <div className="absolute top-0 -right-4 w-72 h-72 bg-purple-400/10 rounded-full filter blur-3xl opacity-70 animate-blob animation-delay-2000 pointer-events-none"></div>
            <div className="absolute -bottom-8 left-20 w-72 h-72 bg-indigo-400/10 rounded-full filter blur-3xl opacity-70 animate-blob animation-delay-4000 pointer-events-none"></div>

            <div className="max-w-md w-full bg-white border border-slate-200/80 rounded-2xl p-6 shadow-sm hover:shadow-md/50 transition-all duration-200 space-y-6 relative z-10">
                <div className="text-center space-y-1">
                    <h2 className="text-xl font-extrabold text-slate-900">Lecturer Portal Login</h2>
                    <p className="text-xs text-slate-500">Enter your lecturer credentials to access course and session management</p>
                </div>

                {error && (
                    <div className="p-4 rounded-xl text-xs font-semibold shadow-xs border bg-rose-50 border-rose-200 text-rose-800 text-center">
                        {error}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="space-y-4">
                    <div className="space-y-4">
                        <div className="space-y-1.5">
                            <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Username</label>
                            <input
                                type="text"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                required
                                placeholder="e.g. janedoe"
                                disabled={loading}
                            />
                        </div>
                        <div className="space-y-1.5">
                            <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Password</label>
                            <input
                                type="password"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                required
                                placeholder="••••••••"
                                disabled={loading}
                            />
                        </div>
                    </div>
                    <button 
                        type="submit" 
                        className="w-full bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-sm py-2.5 px-5 rounded-xl shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150"
                        disabled={loading}
                    >
                        {loading ? 'Authenticating...' : 'Sign In as Lecturer'}
                    </button>
                </form>

                <div className="text-center pt-2 border-t border-slate-100">
                    <button 
                        onClick={() => navigate('/')} 
                        className="bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-medium text-xs py-2 px-4 rounded-xl transition-all flex items-center justify-center gap-1 mt-4 w-full"
                    >
                        ← Back to Main Entry
                    </button>
                </div>
            </div>
        </div>
    );
};

export default LecturerLogin;
