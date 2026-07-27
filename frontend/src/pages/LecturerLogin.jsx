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
        <div className="landing-container">
            <div className="landing-card">
                <div className="landing-header">
                    <h2>Lecturer Portal Login</h2>
                    <p>Enter your lecturer credentials to access course and session management</p>
                </div>

                {error && (
                    <div className="alert-banner error" style={{ display: 'block', marginBottom: '1.25rem', fontWeight: 'bold' }}>
                        {error}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="student-login-form">
                    <div className="form-group">
                        <label>Username</label>
                        <input
                            type="text"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            className="form-input"
                            required
                            placeholder="e.g. janedoe"
                            disabled={loading}
                        />
                    </div>
                    <div className="form-group" style={{ marginTop: '0.5rem' }}>
                        <label>Password</label>
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
                    <button 
                        type="submit" 
                        className="submit-btn" 
                        disabled={loading}
                        style={{ marginTop: '1.25rem', fontWeight: 'bold' }}
                    >
                        {loading ? 'Authenticating...' : 'Sign In as Lecturer'}
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

export default LecturerLogin;
