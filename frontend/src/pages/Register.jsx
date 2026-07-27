import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLearner } from '../context/LearnerContext';

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

        // Basic email pattern check
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!email.trim()) {
            errors.email = 'Email address is required.';
        } else if (!emailRegex.test(email.trim())) {
            errors.email = 'Please enter a valid email address.';
        }

        if (!password) {
            errors.password = 'Password is required.';
        } else if (password.length < 4) {
            errors.password = 'Password must be at least 4 characters long.';
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
        <div className="landing-container">
            <div className="landing-card" style={{ maxWidth: '550px' }}>
                <div className="landing-header">
                    <h2>New Student Registration</h2>
                    {!registrationOpen ? (
                        <p style={{ color: 'var(--error)', fontWeight: 'bold' }}>Registration is currently closed by the administrator.</p>
                    ) : (
                        <p>Provide your personal details to register and receive a Student Number</p>
                    )}
                </div>

                {!registrationOpen ? (
                    <div style={{ textAlign: 'center', padding: '2rem 0' }}>
                        <p style={{ color: 'var(--text-muted)', marginBottom: '1.5rem', fontWeight: 'bold' }}>
                            We are not accepting new student registrations at this time. Please contact your system administrator or facilitator for assistance.
                        </p>
                        <button 
                            onClick={() => navigate('/')} 
                            className="submit-btn"
                            style={{ fontWeight: 'bold' }}
                        >
                            Back to Entry
                        </button>
                    </div>
                ) : (
                    <>
                        {error && (
                            <div className="alert-banner error" style={{ display: 'block', marginBottom: '1.25rem', fontWeight: 'bold' }}>
                                {error}
                            </div>
                        )}

                        <form onSubmit={handleSubmit} className="student-login-form" style={{ gap: '1rem' }}>
                    <div className="form-group">
                        <label>Full Name *</label>
                        <input
                            type="text"
                            value={fullName}
                            onChange={(e) => {
                                setFullName(e.target.value);
                                if (fieldErrors.fullName) setFieldErrors(prev => ({ ...prev, fullName: null }));
                            }}
                            className="form-input"
                            required
                            placeholder="e.g. Sipho Ndlovu"
                            disabled={loading}
                        />
                        {fieldErrors.fullName && <span style={{ color: 'var(--error)', fontSize: '0.8rem', marginTop: '0.25rem', display: 'block', fontWeight: 'bold' }}>{fieldErrors.fullName}</span>}
                    </div>

                    <div className="form-group">
                        <label>Email Address</label>
                        <input
                            type="email"
                            value={email}
                            onChange={(e) => {
                                setEmail(e.target.value);
                                if (fieldErrors.email) setFieldErrors(prev => ({ ...prev, email: null }));
                            }}
                            className="form-input"
                            placeholder="e.g. sipho@example.com"
                            disabled={loading}
                        />
                        {fieldErrors.email && <span style={{ color: 'var(--error)', fontSize: '0.8rem', marginTop: '0.25rem', display: 'block', fontWeight: 'bold' }}>{fieldErrors.email}</span>}
                    </div>

                    <div className="form-group">
                        <label>ID / Passport Number</label>
                        <input
                            type="text"
                            value={idNumber}
                            onChange={(e) => setIdNumber(e.target.value)}
                            className="form-input"
                            placeholder="e.g. 9801155029087"
                            disabled={loading}
                        />
                    </div>

                    <div className="form-group">
                        <label>Phone Number</label>
                        <input
                            type="text"
                            value={phoneNumber}
                            onChange={(e) => setPhoneNumber(e.target.value)}
                            className="form-input"
                            placeholder="e.g. +27821234567"
                            disabled={loading}
                        />
                    </div>

                    <div className="form-group">
                        <label>Learnership Qualification *</label>
                        <select
                            value={learnershipId}
                            onChange={(e) => setLearnershipId(e.target.value)}
                            className="form-input"
                            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--text-main)' }}
                            disabled={loading}
                            required
                        >
                            <option value="">-- Select Learnership --</option>
                            {learnerships.map(l => (
                                <option key={l.id} value={l.id}>{l.name} ({l.qualificationCode || 'No Code'})</option>
                            ))}
                        </select>
                    </div>

                    <div className="form-group">
                        <label>Intake Cohort</label>
                        <select
                            value={cohort}
                            onChange={(e) => setCohort(e.target.value)}
                            className="form-input"
                            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--text-main)' }}
                            disabled={loading}
                        >
                            <option value="2026 Intake A">2026 Intake A</option>
                            <option value="2026 Intake B">2026 Intake B</option>
                        </select>
                    </div>

                    <div className="form-group">
                        <label>Password *</label>
                        <input
                            type="password"
                            value={password}
                            onChange={(e) => {
                                setPassword(e.target.value);
                                if (fieldErrors.password) setFieldErrors(prev => ({ ...prev, password: null }));
                            }}
                            className="form-input"
                            required
                            placeholder="••••••••"
                            disabled={loading}
                        />
                        {fieldErrors.password && <span style={{ color: 'var(--error)', fontSize: '0.8rem', marginTop: '0.25rem', display: 'block', fontWeight: 'bold' }}>{fieldErrors.password}</span>}
                    </div>

                    <div className="form-group">
                        <label>Confirm Password *</label>
                        <input
                            type="password"
                            value={confirmPassword}
                            onChange={(e) => {
                                setConfirmPassword(e.target.value);
                                if (fieldErrors.confirmPassword) setFieldErrors(prev => ({ ...prev, confirmPassword: null }));
                            }}
                            className="form-input"
                            required
                            placeholder="••••••••"
                            disabled={loading}
                        />
                        {fieldErrors.confirmPassword && <span style={{ color: 'var(--error)', fontSize: '0.8rem', marginTop: '0.25rem', display: 'block', fontWeight: 'bold' }}>{fieldErrors.confirmPassword}</span>}
                    </div>

                    <button 
                        type="submit" 
                        className="submit-btn" 
                        disabled={loading}
                        style={{ marginTop: '0.75rem', fontWeight: 'bold' }}
                    >
                        {loading ? 'Registering...' : 'Register & Log In'}
                    </button>
                </form>
                    </>
                )}

                <div style={{ marginTop: '1.5rem', textAlign: 'center' }}>
                    <button 
                        onClick={() => navigate('/')} 
                        style={{ background: 'transparent', border: 'none', color: 'var(--primary-color)', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.9rem' }}
                    >
                        Back to Entry
                    </button>
                </div>
            </div>
        </div>
    );
};

export default Register;
