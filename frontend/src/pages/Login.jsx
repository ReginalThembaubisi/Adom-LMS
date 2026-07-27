import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLearner } from '../context/LearnerContext';

const Login = () => {
    const [studentNumber, setStudentNumber] = useState('');
    const [password, setPassword] = useState('');
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
        <div className="landing-container">
            <div className="landing-card">
                <div className="landing-header">
                    <h2>Log In</h2>
                    <p>Enter your credentials to access the student portal</p>
                </div>

                {error && (
                    <div className="alert-banner error" style={{ display: 'block', marginBottom: '1.25rem', fontWeight: 'bold' }}>
                        {error}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="student-login-form">
                    <div className="form-group" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                        <div>
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
                        <div>
                            <label style={{ fontSize: '0.8rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Password</label>
                            <input
                                type="password"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                className="form-input"
                                required
                                placeholder="••••••••"
                                disabled={loading}
                            />
                        </div>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
                        <button
                            type="button"
                            onClick={() => navigate('/forgot-password')}
                            style={{ background: 'transparent', border: 'none', color: 'var(--primary-color)', cursor: 'pointer', fontSize: '0.8rem', fontWeight: 'bold', padding: 0 }}
                        >
                            Forgot password?
                        </button>
                    </div>

                    <button 
                        type="submit" 
                        className="submit-btn" 
                        disabled={loading}
                        style={{ marginTop: '1rem', fontWeight: 'bold' }}
                    >
                        {loading ? 'Logging in...' : 'Continue to Portal'}
                    </button>
                </form>

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

export default Login;
