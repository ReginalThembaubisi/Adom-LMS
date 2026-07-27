import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

const Landing = () => {
    const navigate = useNavigate();
    const location = useLocation();

    // Check if redirect was due to session expired
    const query = new URLSearchParams(location.search);
    const sessionExpired = query.get('session_expired');

    return (
        <div className="landing-container">
            <div className="landing-card">
                <div className="landing-header">
                    <h2>Student Portal Entry</h2>
                    <p>Submit your assignments and view your submission history</p>
                </div>

                {sessionExpired && (
                    <div className="alert-banner error" style={{ display: 'block', marginBottom: '1.25rem', fontWeight: 'bold' }}>
                        Your session is invalid or has expired. Please log in again.
                    </div>
                )}

                <div className="landing-sections">
                    <div className="login-box">
                        <h3>Already Registered?</h3>
                        <p className="sub-text">Enter your 9-digit Student Number to continue</p>
                        <button 
                            className="submit-btn" 
                            onClick={() => navigate('/login')}
                            style={{ fontWeight: 'bold' }}
                        >
                            Log In with Student Number
                        </button>
                    </div>

                    <div className="divider"><span>OR</span></div>

                    <div className="register-box">
                        <h3>New Student?</h3>
                        <p className="sub-text">Register yourself to get your 9-digit Student Number</p>
                        <button 
                            className="register-btn" 
                            onClick={() => navigate('/register')}
                            style={{ fontWeight: 'bold' }}
                        >
                            Register New Student
                        </button>
                    </div>
                </div>

                <div style={{ marginTop: '1.75rem', borderTop: '1px solid var(--card-border)', paddingTop: '1.25rem', display: 'flex', gap: '1rem', justifyContent: 'space-between' }}>
                    <button 
                        onClick={() => navigate('/admin-login')} 
                        className="subtle-card-btn"
                    >
                        Admin Portal
                    </button>
                    <button 
                        onClick={() => navigate('/lecturer-login')} 
                        className="subtle-card-btn"
                    >
                        Lecturer Portal
                    </button>
                </div>
            </div>
        </div>
    );
};

export default Landing;
