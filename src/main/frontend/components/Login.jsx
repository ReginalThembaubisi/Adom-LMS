import React, { useState } from 'react';
import './Login.css';

const Login = ({ onLoginSuccess }) => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMessage('');
        setIsSubmitting(true);

        const credentials = btoa(`${username.trim()}:${password}`);

        try {
            const res = await fetch('/api/auth/me', {
                headers: {
                    'Authorization': `Basic ${credentials}`
                }
            });

            if (res.ok) {
                const data = await res.json();
                if (data.authenticated) {
                    // Store credentials in memory callback
                    if (onLoginSuccess) {
                        onLoginSuccess(credentials, data.username);
                    } else {
                        // Redirect to admin dashboard
                        window.location.href = '/admin.html';
                    }
                } else {
                    throw new Error('Authentication failed.');
                }
            } else {
                throw new Error('Invalid username or password.');
            }
        } catch (err) {
            setErrorMessage(err.message || 'Login failed. Please check your credentials.');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="login-container">
            <div className="login-card">
                <div className="login-header">
                    <h2>Facilitator Login</h2>
                    <p>Enter your administrator credentials to access the dashboard</p>
                </div>

                {errorMessage && (
                    <div className="alert-error">
                        ⚠️ {errorMessage}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="login-form">
                    <div className="form-group">
                        <label>Username</label>
                        <input
                            type="text"
                            className="form-input"
                            placeholder="e.g. admin"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label>Password</label>
                        <input
                            type="password"
                            className="form-input"
                            placeholder="••••••••"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                        />
                    </div>

                    <button
                        type="submit"
                        className="submit-btn"
                        disabled={isSubmitting}
                    >
                        {isSubmitting ? 'Logging in...' : 'Sign In'}
                    </button>
                </form>
            </div>
        </div>
    );
};

export default Login;
