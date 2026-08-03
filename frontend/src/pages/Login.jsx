import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLearner } from '../context/LearnerContext';

const Login = () => {
    const [studentNumber, setStudentNumber] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const { loginStudent } = useLearner();
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        
        const num = studentNumber.trim();
        if (!num || !password.trim()) return;

        setLoading(true);
        try {
            await loginStudent(num, password);
            navigate('/portal');
        } catch (err) {
            setError(err.message || 'Student number or password is incorrect');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-page-container bg-slate-50/80 min-h-screen text-slate-800 antialiased flex flex-col justify-center items-center p-4 relative overflow-hidden">
            {/* Ambient background blobs */}
            <div className="absolute top-0 -left-4 w-72 h-72 bg-blue-400/10 rounded-full filter blur-3xl opacity-70 animate-blob pointer-events-none"></div>
            <div className="absolute top-0 -right-4 w-72 h-72 bg-purple-400/10 rounded-full filter blur-3xl opacity-70 animate-blob animation-delay-2000 pointer-events-none"></div>
            <div className="absolute -bottom-8 left-20 w-72 h-72 bg-indigo-400/10 rounded-full filter blur-3xl opacity-70 animate-blob animation-delay-4000 pointer-events-none"></div>

            <div className="max-w-md w-full bg-white border border-slate-200/80 rounded-2xl p-6 shadow-sm hover:shadow-md/50 transition-all duration-200 space-y-6 relative z-10">
                <div className="text-center space-y-1">
                    <h2 className="text-xl font-extrabold text-slate-900">Log In</h2>
                    <p className="text-xs text-slate-500">Enter your credentials to access the student portal</p>
                </div>

                {error && (
                    <div className="p-4 rounded-xl text-xs font-semibold shadow-xs border bg-rose-50 border-rose-200 text-rose-800 text-center">
                        {error}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="space-y-4">
                    <div className="space-y-4">
                        <div className="space-y-1.5">
                            <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Student Number</label>
                            <input
                                type="text"
                                value={studentNumber}
                                onChange={(e) => setStudentNumber(e.target.value)}
                                className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                required
                                placeholder="e.g. 202600001"
                                disabled={loading}
                            />
                        </div>
                        <div className="space-y-1.5">
                            <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Password</label>
                            <div className="relative">
                                <input
                                    type={showPassword ? "text" : "password"}
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    className="w-full bg-white border border-slate-200 rounded-xl pl-3.5 pr-10 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                    required
                                    placeholder="••••••••"
                                    disabled={loading}
                                />
                                <button
                                    type="button"
                                    onClick={() => setShowPassword(!showPassword)}
                                    className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 focus:outline-none"
                                >
                                    {showPassword ? (
                                        <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88" />
                                        </svg>
                                    ) : (
                                        <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                                            <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                        </svg>
                                    )}
                                </button>
                            </div>
                        </div>
                    </div>

                    <div className="flex justify-end pt-1">
                        <button
                            type="button"
                            onClick={() => navigate('/forgot-password')}
                            className="text-xs font-bold text-blue-600 hover:text-blue-800 transition-colors"
                        >
                            Forgot password?
                        </button>
                    </div>

                    <button 
                        type="submit" 
                        className="w-full bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-sm py-2.5 px-5 rounded-xl shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150"
                        disabled={loading}
                    >
                        {loading ? 'Logging in...' : 'Continue to Portal'}
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

export default Login;
