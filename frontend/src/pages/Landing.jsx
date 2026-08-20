import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { GraduationCap, ShieldCheck, ChalkboardTeacher, ClipboardText, UserCheck } from '@phosphor-icons/react';

const Landing = () => {
    const navigate = useNavigate();
    const location = useLocation();

    // Check if redirect was due to session expired
    const query = new URLSearchParams(location.search);
    const sessionExpired = query.get('session_expired');

    return (
        <div className="auth-page-container bg-slate-50/80 min-h-screen text-slate-800 antialiased flex flex-col justify-center items-center p-4 relative overflow-hidden">
            {/* Ambient background blobs */}
            <div className="absolute top-0 -left-4 w-72 h-72 bg-blue-400/10 rounded-full filter blur-3xl opacity-70 animate-blob pointer-events-none"></div>
            <div className="absolute top-0 -right-4 w-72 h-72 bg-blue-600/10 rounded-full filter blur-3xl opacity-70 animate-blob animation-delay-2000 pointer-events-none"></div>
            <div className="absolute -bottom-8 left-20 w-72 h-72 bg-blue-300/10 rounded-full filter blur-3xl opacity-70 animate-blob animation-delay-4000 pointer-events-none"></div>

            <div className="max-w-md w-full bg-white border border-slate-200/80 rounded-2xl p-6 shadow-sm hover:shadow-md/50 transition-all duration-200 space-y-6 relative z-10 animate-fadeInUp">
                <div className="text-center space-y-2">
                    <div className="bg-linear-to-b from-blue-500 to-blue-600 text-white p-3.5 rounded-2xl mx-auto w-fit mb-3 shadow-md">
                        <GraduationCap size={24} weight="fill" />
                    </div>
                    <h2 className="text-xl font-extrabold text-slate-900">Student Portal Entry</h2>
                    <p className="text-xs text-slate-500 max-w-xs mx-auto">Submit your assignments and view your submission history</p>
                </div>

                {sessionExpired && (
                    <div className="p-4 rounded-xl text-xs font-semibold shadow-xs border bg-red-50 border-red-200 text-red-800 text-center animate-fadeIn">
                        Your session is invalid or has expired. Please log in again.
                    </div>
                )}

                <div className="space-y-6">
                    <div className="space-y-2">
                        <h3 className="text-sm font-bold text-slate-800">Already Registered?</h3>
                        <p className="text-xs text-slate-500">Enter your 9-digit Student Number to continue</p>
                        <button 
                            className="w-full bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-sm py-2.5 px-5 rounded-xl shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150 text-center block" 
                            onClick={() => navigate('/login')}
                        >
                            Log In with Student Number
                        </button>
                    </div>

                    <div className="border-t border-slate-200 my-6" />

                    <div className="space-y-2">
                        <h3 className="text-sm font-bold text-slate-800">New Student?</h3>
                        <p className="text-xs text-slate-500">Register yourself to get your 9-digit Student Number</p>
                        <button 
                            className="w-full bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-medium text-sm py-2.5 px-3.5 rounded-xl transition-colors text-center block" 
                            onClick={() => navigate('/register')}
                        >
                            Register New Student
                        </button>
                    </div>
                </div>

                <div className="grid grid-cols-2 gap-3 pt-4 border-t border-slate-200">
                    <button
                        onClick={() => navigate('/admin-login')}
                        className="bg-slate-100 hover:bg-slate-200/80 hover:-translate-y-0.5 text-slate-700 font-medium text-xs py-2.5 px-3 rounded-xl transition-all text-center flex items-center justify-center gap-1.5"
                    >
                        <ShieldCheck size={14} weight="bold" /> Admin Portal
                    </button>
                    <button
                        onClick={() => navigate('/lecturer-login')}
                        className="bg-slate-100 hover:bg-slate-200/80 hover:-translate-y-0.5 text-slate-700 font-medium text-xs py-2.5 px-3 rounded-xl transition-all text-center flex items-center justify-center gap-1.5"
                    >
                        <ChalkboardTeacher size={14} weight="bold" /> Facilitator Portal
                    </button>
                    <button
                        onClick={() => navigate('/moderator-login')}
                        className="bg-slate-100 hover:bg-slate-200/80 hover:-translate-y-0.5 text-slate-700 font-medium text-xs py-2.5 px-3 rounded-xl transition-all text-center flex items-center justify-center gap-1.5"
                    >
                        <ClipboardText size={14} weight="bold" /> Moderator Portal
                    </button>
                    <button
                        onClick={() => navigate('/assessor-login')}
                        className="bg-slate-100 hover:bg-slate-200/80 hover:-translate-y-0.5 text-slate-700 font-medium text-xs py-2.5 px-3 rounded-xl transition-all text-center flex items-center justify-center gap-1.5"
                    >
                        <UserCheck size={14} weight="bold" /> Assessor Portal
                    </button>
                </div>
            </div>
        </div>
    );
};

export default Landing;
