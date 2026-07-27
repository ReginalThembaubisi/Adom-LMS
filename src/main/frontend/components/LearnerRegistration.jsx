import React, { useState } from 'react';
import './LearnerRegistration.css';

const LearnerRegistration = () => {
    const [fullName, setFullName] = useState('');
    const [email, setEmail] = useState('');
    const [phoneNumber, setPhoneNumber] = useState('');
    const [cohort, setCohort] = useState('');
    const [idNumber, setIdNumber] = useState('');

    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [registeredLearner, setRegisteredLearner] = useState(null);
    const [copied, setCopied] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');
        setRegisteredLearner(null);
        setCopied(false);
        setIsSubmitting(true);

        try {
            const response = await fetch('/api/learners', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    fullName,
                    email: email.trim() ? email.trim() : null,
                    phoneNumber,
                    cohort,
                    idNumber: idNumber.trim() ? idNumber.trim() : null
                })
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.message || 'Registration failed.');
            }

            setRegisteredLearner(data);

            // Clear form fields
            setFullName('');
            setEmail('');
            setPhoneNumber('');
            setCohort('');
            setIdNumber('');
        } catch (err) {
            setErrorMsg(err.message || 'An error occurred during student registration.');
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleCopyCode = () => {
        if (registeredLearner?.learnerCode) {
            navigator.clipboard.writeText(registeredLearner.learnerCode);
            setCopied(true);
            setTimeout(() => setCopied(false), 2500);
        }
    };

    const getWhatsAppUrl = () => {
        if (!registeredLearner) return '#';
        const msg = `Hi ${registeredLearner.fullName}! Your 9-digit Student Number for assignment submissions is: *${registeredLearner.learnerCode}*. Keep this number safe!`;
        const phone = registeredLearner.phoneNumber ? registeredLearner.phoneNumber.replace(/[^0-9]/g, '') : '';
        return `https://wa.me/${phone}?text=${encodeURIComponent(msg)}`;
    };

    const getEmailMailtoUrl = () => {
        if (!registeredLearner) return '#';
        const subject = `Student Registration Pass - ${registeredLearner.fullName}`;
        const body = `Hi ${registeredLearner.fullName},\n\nYour registration is complete!\nYour 9-Digit Student Number: ${registeredLearner.learnerCode}\nCohort: ${registeredLearner.cohort}\n\nPlease keep this number safe as you will need it to submit your assignments.`;
        return `mailto:${registeredLearner.email || ''}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
    };

    return (
        <div className="registration-container">
            <div className="registration-card centered">
                <div className="card-header">
                    <h2>Student Registration Portal</h2>
                    <p>Register yourself to generate your 9-digit Student Number</p>
                </div>

                {errorMsg && (
                    <div className="alert-error">
                        ⚠️ {errorMsg}
                    </div>
                )}

                {/* Success Display Card */}
                {registeredLearner && (
                    <div className="success-code-banner">
                        <div className="badge-tag">REGISTRATION SUCCESSFUL</div>
                        <h3 className="learner-name">{registeredLearner.fullName}</h3>
                        <p className="code-label">Your 9-Digit Student Number:</p>
                        <div className="code-display">
                            <span>{registeredLearner.learnerCode}</span>
                            <button className="copy-btn" onClick={handleCopyCode}>
                                {copied ? '✓ Copied!' : '📋 Copy'}
                            </button>
                        </div>
                        <p className="safety-note">🔒 Save this number! Send a copy to your phone or email below so you don't lose it.</p>
                        <div className="action-buttons">
                            <a
                                href={getWhatsAppUrl()}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="whatsapp-btn"
                            >
                                💬 WhatsApp Me
                            </a>
                            {registeredLearner.email && (
                                <a
                                    href={getEmailMailtoUrl()}
                                    className="email-btn"
                                >
                                    ✉️ Email Me
                                </a>
                            )}
                            <button
                                className="print-btn"
                                onClick={() => window.print()}
                            >
                                🖨️ Print Pass
                            </button>
                        </div>
                    </div>
                )}

                <form onSubmit={handleSubmit} className="registration-form">
                    <div className="form-group">
                        <label>Full Name *</label>
                        <input
                            type="text"
                            className="form-input"
                            placeholder="e.g. Themba Sbuyi"
                            value={fullName}
                            onChange={(e) => setFullName(e.target.value)}
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label>Email Address</label>
                        <input
                            type="email"
                            className="form-input"
                            placeholder="e.g. student@example.com"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                        />
                    </div>

                    <div className="form-group">
                        <label>Phone Number *</label>
                        <input
                            type="tel"
                            className="form-input"
                            placeholder="e.g. 0761764642"
                            value={phoneNumber}
                            onChange={(e) => setPhoneNumber(e.target.value)}
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label>Cohort / Intake *</label>
                        <input
                            type="text"
                            className="form-input"
                            placeholder="e.g. 2026 Intake A"
                            value={cohort}
                            onChange={(e) => setCohort(e.target.value)}
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label>SA ID Number (Optional)</label>
                        <input
                            type="text"
                            className="form-input"
                            placeholder="e.g. 9807295148083"
                            value={idNumber}
                            onChange={(e) => setIdNumber(e.target.value)}
                        />
                    </div>

                    <button
                        type="submit"
                        className="submit-btn"
                        disabled={isSubmitting}
                    >
                        {isSubmitting ? 'Registering...' : 'Register Me'}
                    </button>
                </form>
            </div>
        </div>
    );
};

export default LearnerRegistration;
