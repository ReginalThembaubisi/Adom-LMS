import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

const Landing = () => {
    const navigate = useNavigate();
    const location = useLocation();

    // Check if redirect was due to session expired
    const query = new URLSearchParams(location.search);
    const sessionExpired = query.get('session_expired');

    return (
        <div className="bg-slate-50/80 min-h-screen text-slate-800 antialiased flex flex-col justify-center items-center p-4 relative overflow-hidden">
            {/* Ambient background blobs */}
            <div className="absolute top-0 -left-4 w-72 h-72 bg-blue-400/10 rounded-full filter blur-3xl opacity-70 animate-blob pointer-events-none"></div>
            <div className="absolute top-0 -right-4 w-72 h-72 bg-purple-400/10 rounded-full filter blur-3xl opacity-70 animate-blob animation-delay-2000 pointer-events-none"></div>
            <div className="absolute -bottom-8 left-20 w-72 h-72 bg-indigo-400/10 rounded-full filter blur-3xl opacity-70 animate-blob animation-delay-4000 pointer-events-none"></div>

            <div className="max-w-md w-full bg-white border border-slate-200/80 rounded-2xl p-6 shadow-sm hover:shadow-md/50 transition-all duration-200 space-y-6 relative z-10">
                <div className="text-center space-y-2">
                    {/* Branding Badge: graduation cap SVG */}
                    <div className="bg-linear-to-b from-blue-500 to-blue-600 text-white p-3.5 rounded-2xl mx-auto w-fit mb-3 shadow-md">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M12 14l9-5-9-5-9 5 9 5z" />
                            <path strokeLinecap="round" strokeLinejoin="round" d="M12 14l6.16-3.422a12.083 12.083 0 01.665 6.479A11.952 11.952 0 0012 20.055a11.952 11.952 0 00-6.824-2.998 12.078 12.078 0 01.665-6.479L12 14z" />
                            <path strokeLinecap="round" strokeLinejoin="round" d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2v-7" />
                        </svg>
                    </div>
                    <h2 className="text-xl font-extrabold text-slate-900">Student Portal Entry</h2>
                    <p className="text-xs text-slate-500 max-w-xs mx-auto">Submit your assignments and view your submission history</p>
                </div>

                {sessionExpired && (
                    <div className="p-4 rounded-xl text-xs font-semibold shadow-xs border bg-rose-50 border-rose-200 text-rose-800 text-center">
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

                <div className="flex gap-4 pt-4 border-t border-slate-200">
                    <button 
                        onClick={() => navigate('/admin-login')} 
                        className="bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-medium text-xs py-2.5 px-4 rounded-xl transition-all flex-1 text-center"
                    >
                        Admin Portal
                    </button>
                    <button 
                        onClick={() => navigate('/lecturer-login')} 
                        className="bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-medium text-xs py-2.5 px-4 rounded-xl transition-all flex-1 text-center"
                    >
                        Lecturer Portal
                    </button>
                </div>
            </div>
        </div>
    );
};

export default Landing;
