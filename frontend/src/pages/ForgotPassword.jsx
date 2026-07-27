import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

const ForgotPassword = () => {
    const [step, setStep] = useState(1); // 1 = Request Code, 2 = Verify Code & Reset, 3 = Success
    const [studentNumber, setStudentNumber] = useState('');
    const [email, setEmail] = useState('');
    const [resetCode, setResetCode] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleRequestCode = async (e) => {
        e.preventDefault();
        setError('');
        
        const num = studentNumber.trim();
        const mail = email.trim();
        if (!num || !mail) return;

        setLoading(true);
        try {
            const res = await fetch('/api/learners/forgot-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ studentNumber: num, email: mail })
            });

            if (!res.ok) {
                const data = await res.json();
                throw new Error(data.message || 'Student number or email is incorrect');
            }

            setStep(2);
        } catch (err) {
            setError(err.message || 'Verification failed. Please check details.');
        } finally {
            setLoading(false);
        }
    };

    const handleResetPassword = async (e) => {
        e.preventDefault();
        setError('');

        const code = resetCode.trim();
        const pwd = newPassword;
        if (!code || !pwd) return;

        if (pwd.length < 4) {
            setError('Password must be at least 4 characters long.');
            return;
        }

        if (pwd !== confirmPassword) {
            setError('Passwords do not match.');
            return;
        }

        setLoading(true);
        try {
            const res = await fetch('/api/learners/reset-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    studentNumber: studentNumber.trim(),
                    resetCode: code,
                    newPassword: pwd
                })
            });

            if (!res.ok) {
                const data = await res.json();
                throw new Error(data.message || 'Invalid or expired reset code');
            }

            setStep(3);
        } catch (err) {
            setError(err.message || 'Failed to reset password.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="landing-container">
            <div className="landing-card" style={{ maxWidth: '450px' }}>
                {step === 1 && (
                    <>
                        <div className="landing-header">
                            <h2>Forgot Password?</h2>
                            <p>Enter your student number and registered email to receive a 6-digit reset code.</p>
                        </div>

                        {error && (
                            <div className="alert-banner error" style={{ display: 'block', marginBottom: '1.25rem', fontWeight: 'bold' }}>
                                {error}
                            </div>
                        )}

                        <form onSubmit={handleRequestCode} className="student-login-form" style={{ gap: '1rem' }}>
                            <div className="form-group">
                                <label style={{ fontSize: '0.8rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Student Number</label>
                                <input
                                    type="text"
                                    value={studentNumber}
                                    onChange={(e) => setStudentNumber(e.target.value)}
                                    className="form-input"
                                    required
                                    placeholder="e.g. 202600001"
                                    disabled={loading}
                                />
                            </div>
                            <div className="form-group">
                                <label style={{ fontSize: '0.8rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Email Address</label>
                                <input
                                    type="email"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    className="form-input"
                                    required
                                    placeholder="e.g. sipho@example.com"
                                    disabled={loading}
                                />
                            </div>
                            <button
                                type="submit"
                                className="submit-btn"
                                disabled={loading}
                                style={{ marginTop: '0.5rem', fontWeight: 'bold' }}
                            >
                                {loading ? 'Sending...' : 'Send Reset Code'}
                            </button>
                        </form>
                    </>
                )}

                {step === 2 && (
                    <>
                        <div className="landing-header">
                            <h2>Enter Reset Code</h2>
                            <p>We've sent a 6-digit reset code to {email}. Enter it below with your new password.</p>
                        </div>

                        {error && (
                            <div className="alert-banner error" style={{ display: 'block', marginBottom: '1.25rem', fontWeight: 'bold' }}>
                                {error}
                            </div>
                        )}

                        <form onSubmit={handleResetPassword} className="student-login-form" style={{ gap: '1rem' }}>
                            <div className="form-group">
                                <label style={{ fontSize: '0.8rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Reset Code (6 digits)</label>
                                <input
                                    type="text"
                                    value={resetCode}
                                    onChange={(e) => setResetCode(e.target.value)}
                                    className="form-input"
                                    required
                                    placeholder="e.g. 123456"
                                    disabled={loading}
                                    maxLength={6}
                                />
                            </div>
                            <div className="form-group">
                                <label style={{ fontSize: '0.8rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>New Password</label>
                                <input
                                    type="password"
                                    value={newPassword}
                                    onChange={(e) => setNewPassword(e.target.value)}
                                    className="form-input"
                                    required
                                    placeholder="••••••••"
                                    disabled={loading}
                                />
                            </div>
                            <div className="form-group">
                                <label style={{ fontSize: '0.8rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Confirm New Password</label>
                                <input
                                    type="password"
                                    value={confirmPassword}
                                    onChange={(e) => setConfirmPassword(e.target.value)}
                                    className="form-input"
                                    required
                                    placeholder="••••••••"
                                    disabled={loading}
                                />
                            </div>
                            <button
                                type="submit"
                                className="submit-btn"
                                disabled={loading}
                                style={{ marginTop: '0.5rem', fontWeight: 'bold' }}
                            >
                                {loading ? 'Resetting...' : 'Reset Password'}
                            </button>
                        </form>
                    </>
                )}

                {step === 3 && (
                    <div style={{ textAlign: 'center', padding: '1rem 0' }}>
                        <h2 style={{ marginBottom: '0.5rem', color: 'var(--text-main)', fontWeight: 'bold' }}>Password Reset Successful</h2>
                        <p style={{ color: 'var(--text-muted)', marginBottom: '1.5rem', fontSize: '0.9rem' }}>
                            Your password has been updated. You can now log in using your new credentials.
                        </p>
                        <button
                            onClick={() => navigate('/login')}
                            className="submit-btn"
                            style={{ fontWeight: 'bold' }}
                        >
                            Go to Log In
                        </button>
                    </div>
                )}

                {step !== 3 && (
                    <div style={{ marginTop: '1.5rem', textAlign: 'center' }}>
                        <button
                            onClick={() => navigate('/login')}
                            style={{ background: 'transparent', border: 'none', color: 'var(--primary-color)', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.9rem' }}
                        >
                            Back to Login
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
};

export default ForgotPassword;
