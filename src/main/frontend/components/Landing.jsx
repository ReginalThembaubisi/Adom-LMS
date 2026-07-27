import React, { useState } from 'react';
import './Landing.css';

const Landing = ({ onSelectRegister, onLoginSuccess }) => {
    const [studentNumber, setStudentNumber] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');

    const handleLoginSubmit = async (e) => {
        e.preventDefault();
        setErrorMessage('');
        setIsSubmitting(true);

        const cleanNumber = studentNumber.trim();

        try {
            const res = await fetch('http://localhost:8080/api/learners/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ studentNumber: cleanNumber })
            });

            const data = await res.json();

            if (res.ok) {
                if (onLoginSuccess) {
                    onLoginSuccess(data);
                } else {
                    sessionStorage.setItem('logged_student', JSON.stringify(data));
                    window.location.href = 'portal.html';
                }
            } else {
                throw new Error(data.message || 'Student number not found. Please register first.');
            }
        } catch (err) {
            // Local fallback simulation for offline mode
            const mockList = JSON.parse(localStorage.getItem('mock_learners') || '[]');
            const foundMock = mockList.find(l => l.learnerCode === cleanNumber) ||
                (cleanNumber === '202600001' ? { id: 1, learnerCode: '202600001', fullName: 'Sipho Ndlovu', cohort: '2026 Intake A' } : null);

            if (foundMock) {
                if (onLoginSuccess) {
                    onLoginSuccess(foundMock);
                } else {
                    sessionStorage.setItem('logged_student', JSON.stringify(foundMock));
                    window.location.href = 'portal.html';
                }
            } else {
                setErrorMessage(err.message || 'Student number not found. Please register first.');
            }
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="landing-container">
            <div className="landing-card">
                <div className="landing-header">
                    <h2>Student Portal Entry</h2>
                    <p>Submit your assignments and view your submission history</p>
                </div>

                {errorMessage && (
                    <div className="alert-error">
                        ⚠️ {errorMessage}
                    </div>
                )}

                <div className="landing-sections">
                    <!-- Option 1: Existing Student Login -->
                    <div className="login-box">
                        <h3>Already Registered?</h3>
                        <p className="sub-text">Enter your 9-digit Student Number to continue</p>
                        <form onSubmit={handleLoginSubmit} className="student-login-form">
                            <input
                                type="text"
                                className="form-input"
                                placeholder="e.g. 202600001"
                                value={studentNumber}
                                onChange={(e) => setStudentNumber(e.target.value)}
                                required
                            />
                            <button
                                type="submit"
                                className="submit-btn"
                                disabled={isSubmitting}
                            >
                                {isSubmitting ? 'Checking...' : 'Continue to Portal →'}
                            </button>
                        </form>
                    </div>

                    <div className="divider"><span>OR</span></div>

                    <!-- Option 2: New Student Registration -->
                    <div className="register-box">
                        <h3>New Student?</h3>
                        <p className="sub-text">Register yourself to get your 9-digit Student Number</p>
                        <button
                            type="button"
                            className="register-btn"
                            onClick={() => {
                                if (onSelectRegister) {
                                    onSelectRegister();
                                } else {
                                    window.location.href = 'register.html';
                                }
                            }}
                        >
                            ✍️ Register New Student
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Landing;
