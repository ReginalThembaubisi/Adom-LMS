import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLearner } from '../context/LearnerContext';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const Register = () => {
    const [fullName, setFullName] = useState('');
    const [email, setEmail] = useState('');
    const [idNumber, setIdNumber] = useState('');
    const [phoneNumber, setPhoneNumber] = useState('');
    const [cohort, setCohort] = useState('2026 Intake A');
    const [learnerships, setLearnerships] = useState([]);
    const [learnershipId, setLearnershipId] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [fieldErrors, setFieldErrors] = useState({});
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [registrationOpen, setRegistrationOpen] = useState(true);
    
    const { registerStudent } = useLearner();
    const navigate = useNavigate();

    React.useEffect(() => {
        const loadRegistrationStatus = async () => {
            try {
                const res = await fetch('/api/registration-status');
                if (res.ok) {
                    const data = await res.json();
                    setRegistrationOpen(data.open);
                }
            } catch (e) {
                console.error(e);
            }
        };

        const loadLearnerships = async () => {
            try {
                const res = await fetch('/api/learnerships');
                if (res.ok) {
                    const data = await res.json();
                    setLearnerships(data);
                    if (data.length > 0) {
                        setLearnershipId(data[0].id.toString());
                    }
                }
            } catch (e) {
                console.error(e);
            }
        };
        
        loadRegistrationStatus();
        loadLearnerships();
    }, []);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setFieldErrors({});

        const errors = {};

        if (!fullName.trim()) {
            errors.fullName = 'Full name is required.';
        }

        if (!email.trim()) {
            errors.email = 'Email address is required.';
        } else if (!EMAIL_REGEX.test(email.trim())) {
            errors.email = 'Please enter a valid email address.';
        }

        if (!password) {
            errors.password = 'Password is required.';
        } else if (password.length < 8) {
            errors.password = 'Password must be at least 8 characters long.';
        }

        if (!confirmPassword) {
            errors.confirmPassword = 'Confirm password is required.';
        } else if (password !== confirmPassword) {
            errors.confirmPassword = 'Passwords do not match.';
        }

        if (Object.keys(errors).length > 0) {
            setFieldErrors(errors);
            return;
        }

        setLoading(true);
        try {
            await registerStudent({
                fullName,
                email,
                idNumber,
                phoneNumber,
                cohort,
                learnershipId: learnershipId ? parseInt(learnershipId) : null,
                password
            });
            navigate('/portal');
        } catch (err) {
            setError(err.message || 'Registration failed. Please check your details.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-page-container bg-slate-50/80 min-h-screen text-slate-800 antialiased flex flex-col justify-center items-center p-4 relative overflow-hidden">
            {/* Ambient background blobs */}
            <div className="absolute top-0 -left-4 w-72 h-72 bg-blue-400/10 rounded-full filter blur-3xl opacity-70 animate-blob pointer-events-none"></div>
            <div className="absolute top-0 -right-4 w-72 h-72 bg-blue-600/10 rounded-full filter blur-3xl opacity-70 animate-blob animation-delay-2000 pointer-events-none"></div>
            <div className="absolute -bottom-8 left-20 w-72 h-72 bg-blue-300/10 rounded-full filter blur-3xl opacity-70 animate-blob animation-delay-4000 pointer-events-none"></div>

            <div className="max-w-xl w-full bg-white border border-slate-200/80 rounded-2xl p-6 shadow-sm hover:shadow-md/50 transition-all duration-200 space-y-6 relative z-10">
                <div className="text-center space-y-1">
                    <h2 className="text-xl font-extrabold text-slate-900">New Student Registration</h2>
                    {!registrationOpen ? (
                        <p className="text-xs font-bold text-red-600">Registration is currently closed by the administrator.</p>
                    ) : (
                        <p className="text-xs text-slate-500">Provide your personal details to register and receive a Student Number</p>
                    )}
                </div>

                {!registrationOpen ? (
                    <div className="text-center py-6 space-y-4">
                        <p className="text-sm font-semibold text-slate-600 leading-relaxed">
                            We are not accepting new student registrations at this time. Please contact your system administrator or facilitator for assistance.
                        </p>
                        <button 
                            onClick={() => navigate('/')} 
                            className="w-full bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-sm py-2.5 px-5 rounded-xl shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150"
                        >
                            Back to Entry
                        </button>
                    </div>
                ) : (
                    <>
                        {error && (
                            <div className="p-4 rounded-xl text-xs font-semibold shadow-xs border bg-red-50 border-red-200 text-red-800 text-center">
                                {error}
                            </div>
                        )}

                        <form onSubmit={handleSubmit} className="space-y-4">
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <div className="space-y-1.5">
                                    <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Full Name *</label>
                                    <input
                                        type="text"
                                        value={fullName}
                                        onChange={(e) => {
                                            setFullName(e.target.value);
                                            if (fieldErrors.fullName) setFieldErrors(prev => ({ ...prev, fullName: null }));
                                        }}
                                        className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                        required
                                        autoComplete="name"
                                        placeholder="e.g. Sipho Ndlovu"
                                        disabled={loading}
                                    />
                                    {fieldErrors.fullName && <span className="text-xs font-bold text-red-600 block mt-1">{fieldErrors.fullName}</span>}
                                </div>

                                <div className="space-y-1.5">
                                    <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Email Address</label>
                                    <input
                                        type="email"
                                        value={email}
                                        onChange={(e) => {
                                            setEmail(e.target.value);
                                            if (fieldErrors.email) setFieldErrors(prev => ({ ...prev, email: null }));
                                        }}
                                        onBlur={() => {
                                            const trimmed = email.trim();
                                            if (trimmed && !EMAIL_REGEX.test(trimmed)) {
                                                setFieldErrors(prev => ({ ...prev, email: 'Please enter a valid email address.' }));
                                            }
                                        }}
                                        className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                        autoComplete="email"
                                        placeholder="e.g. sipho@example.com"
                                        disabled={loading}
                                    />
                                    {fieldErrors.email && <span className="text-xs font-bold text-red-600 block mt-1">{fieldErrors.email}</span>}
                                </div>
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <div className="space-y-1.5">
                                    <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">ID / Passport Number</label>
                                    <input
                                        type="text"
                                        value={idNumber}
                                        onChange={(e) => setIdNumber(e.target.value)}
                                        className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                        placeholder="e.g. 9801155029087"
                                        disabled={loading}
                                    />
                                </div>

                                <div className="space-y-1.5">
                                    <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Phone Number</label>
                                    <input
                                        type="text"
                                        value={phoneNumber}
                                        onChange={(e) => setPhoneNumber(e.target.value)}
                                        className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                        autoComplete="tel"
                                        placeholder="e.g. +27821234567"
                                        disabled={loading}
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <div className="space-y-1.5">
                                    <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Learnership Qualification *</label>
                                    <select
                                        value={learnershipId}
                                        onChange={(e) => setLearnershipId(e.target.value)}
                                        className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                        disabled={loading}
                                        required
                                    >
                                        <option value="">-- Select Learnership --</option>
                                        {learnerships.map(l => (
                                            <option key={l.id} value={l.id}>{l.name} ({l.qualificationCode || 'No Code'})</option>
                                        ))}
                                    </select>
                                </div>

                                <div className="space-y-1.5">
                                    <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Intake Cohort</label>
                                    <select
                                        value={cohort}
                                        onChange={(e) => setCohort(e.target.value)}
                                        className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                        disabled={loading}
                                    >
                                        <option value="2026 Intake A">2026 Intake A</option>
                                        <option value="2026 Intake B">2026 Intake B</option>
                                    </select>
                                </div>
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <div className="space-y-1.5">
                                    <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Password *</label>
                                    <div className="relative">
                                        <input
                                            type={showPassword ? "text" : "password"}
                                            value={password}
                                            onChange={(e) => {
                                                setPassword(e.target.value);
                                                if (fieldErrors.password) setFieldErrors(prev => ({ ...prev, password: null }));
                                            }}
                                            className="w-full bg-white border border-slate-200 rounded-xl pl-3.5 pr-10 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                            required
                                            minLength={8}
                                            autoComplete="new-password"
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
                                    {fieldErrors.password ? (
                                        <span className="text-xs font-bold text-red-600 block mt-1">{fieldErrors.password}</span>
                                    ) : (
                                        <span className="text-xs text-slate-400 block mt-1">Minimum 8 characters</span>
                                    )}
                                </div>

                                <div className="space-y-1.5">
                                    <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Confirm Password *</label>
                                    <div className="relative">
                                        <input
                                            type={showConfirmPassword ? "text" : "password"}
                                            value={confirmPassword}
                                            onChange={(e) => {
                                                setConfirmPassword(e.target.value);
                                                if (fieldErrors.confirmPassword) setFieldErrors(prev => ({ ...prev, confirmPassword: null }));
                                            }}
                                            onBlur={() => {
                                                if (confirmPassword && password !== confirmPassword) {
                                                    setFieldErrors(prev => ({ ...prev, confirmPassword: 'Passwords do not match.' }));
                                                }
                                            }}
                                            className="w-full bg-white border border-slate-200 rounded-xl pl-3.5 pr-10 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                            required
                                            autoComplete="new-password"
                                            placeholder="••••••••"
                                            disabled={loading}
                                        />
                                        <button
                                            type="button"
                                            onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                                            className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 focus:outline-none"
                                        >
                                            {showConfirmPassword ? (
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
                                    {fieldErrors.confirmPassword && <span className="text-xs font-bold text-red-600 block mt-1">{fieldErrors.confirmPassword}</span>}
                                </div>
                            </div>

                            <button 
                                type="submit" 
                                className="w-full bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-sm py-2.5 px-5 rounded-xl shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150"
                                disabled={loading}
                            >
                                {loading ? 'Registering...' : 'Register & Log In'}
                            </button>
                        </form>
                    </>
                )}

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

export default Register;
