import React, { useState, useEffect } from 'react';
import './SubmissionForm.css';

const SubmissionForm = () => {
    const [learners, setLearners] = useState([]);
    const [activeSessions, setActiveSessions] = useState([]);
    const [isLoadingSessions, setIsLoadingSessions] = useState(true);

    const [inputMode, setInputMode] = useState('select'); // 'select' or 'manual'
    
    // Form fields
    const [selectedLearnerCode, setSelectedLearnerCode] = useState('');
    const [manualLearnerCode, setManualLearnerCode] = useState('');
    const [selectedSessionId, setSelectedSessionId] = useState('');
    const [file, setFile] = useState(null);

    // UI States
    const [isDragging, setIsDragging] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [statusMessage, setStatusMessage] = useState(null);

    useEffect(() => {
        // Fetch Learners for dropdown
        fetch('/api/learners')
            .then((res) => res.ok ? res.json() : [])
            .then((data) => setLearners(data))
            .catch((err) => console.error('Error fetching learners:', err));

        // Fetch Active Sessions for dropdown
        loadActiveSessions();
    }, []);

    const loadActiveSessions = async () => {
        setIsLoadingSessions(true);
        try {
            const res = await fetch('/api/sessions/active');
            if (res.ok) {
                const data = await res.json();
                setActiveSessions(data);
            }
        } catch (err) {
            console.error('Error fetching active sessions:', err);
        } finally {
            setIsLoadingSessions(false);
        }
    };

    const effectiveLearnerCode = inputMode === 'select' ? selectedLearnerCode : manualLearnerCode.trim();

    const handleFileChange = (e) => {
        if (e.target.files && e.target.files[0]) {
            validateAndSetFile(e.target.files[0]);
        }
    };

    const validateAndSetFile = (selectedFile) => {
        const allowedExtensions = ['pdf', 'doc', 'docx'];
        const fileExt = selectedFile.name.split('.').pop().toLowerCase();
        
        if (!allowedExtensions.includes(fileExt)) {
            setStatusMessage({
                type: 'error',
                text: 'Invalid file format. Only PDF, DOC, and DOCX files are allowed.'
            });
            setFile(null);
            return;
        }

        if (selectedFile.size > 20 * 1024 * 1024) { // 20MB limit
            setStatusMessage({
                type: 'error',
                text: 'File size exceeds 20MB limit. Please choose a smaller file.'
            });
            setFile(null);
            return;
        }

        setStatusMessage(null);
        setFile(selectedFile);
    };

    const handleDragOver = (e) => {
        e.preventDefault();
        setIsDragging(true);
    };

    const handleDragLeave = (e) => {
        e.preventDefault();
        setIsDragging(false);
    };

    const handleDrop = (e) => {
        e.preventDefault();
        setIsDragging(false);
        if (e.dataTransfer.files && e.dataTransfer.files[0]) {
            validateAndSetFile(e.dataTransfer.files[0]);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setStatusMessage(null);

        if (!effectiveLearnerCode) {
            setStatusMessage({ type: 'error', text: 'Please select or enter your learner code.' });
            return;
        }

        if (!selectedSessionId) {
            setStatusMessage({ type: 'error', text: 'Please select an open submission session.' });
            return;
        }

        if (!file) {
            setStatusMessage({ type: 'error', text: 'Please attach a submission file.' });
            return;
        }

        setIsSubmitting(true);

        const formData = new FormData();
        formData.append('learner_code', effectiveLearnerCode);
        formData.append('session_id', selectedSessionId);
        formData.append('file', file);

        try {
            const response = await fetch('/api/submissions', {
                method: 'POST',
                body: formData,
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.message || 'Failed to submit assignment.');
            }

            const learnerObj = learners.find(l => l.learnerCode === effectiveLearnerCode);
            const learnerDisplayName = learnerObj ? `${learnerObj.learnerCode} — ${learnerObj.fullName}` : effectiveLearnerCode;

            setStatusMessage({
                type: 'success',
                text: `Submitted successfully as ${learnerDisplayName}! (${data.status})`
            });

            setFile(null);
        } catch (err) {
            setStatusMessage({
                type: 'error',
                text: err.message || 'An unexpected error occurred during submission.'
            });
        } finally {
            setIsSubmitting(false);
        }
    };

    const formatEndTime = (isoString) => {
        const date = new Date(isoString);
        return date.toLocaleString(undefined, {
            day: 'numeric',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    return (
        <div className="submission-container">
            <div className="submission-card">
                <div className="submission-header">
                    <h2>Submit Assignment</h2>
                    <p>Select your identity, choose an open session, and upload your file.</p>
                </div>

                {statusMessage && (
                    <div className={`alert-banner ${statusMessage.type}`}>
                        {statusMessage.type === 'success' ? '✓ ' : '⚠️ '}
                        {statusMessage.text}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="submission-form">
                    {/* Learner Identity */}
                    <div className="form-group">
                        <div className="label-row">
                            <label>Learner Identity</label>
                            <button
                                type="button"
                                className="toggle-mode-btn"
                                onClick={() => {
                                    setInputMode(inputMode === 'select' ? 'manual' : 'select');
                                    setStatusMessage(null);
                                }}
                            >
                                {inputMode === 'select' ? 'Enter Code Manually' : 'Select Name from List'}
                            </button>
                        </div>

                        {inputMode === 'select' ? (
                            <select
                                className="form-select"
                                value={selectedLearnerCode}
                                onChange={(e) => setSelectedLearnerCode(e.target.value)}
                                required
                            >
                                <option value="">-- Select Your Name --</option>
                                {learners.map((learner) => (
                                    <option key={learner.id} value={learner.learnerCode}>
                                        {learner.fullName} ({learner.learnerCode} - {learner.cohort})
                                    </option>
                                ))}
                            </select>
                        ) : (
                            <input
                                type="text"
                                className="form-input"
                                placeholder="e.g. LN2026-001"
                                value={manualLearnerCode}
                                onChange={(e) => setManualLearnerCode(e.target.value)}
                                required
                            />
                        )}
                    </div>

                    {/* Active Sessions Dropdown */}
                    <div className="form-group">
                        <label htmlFor="session-select">Submission Session</label>
                        {isLoadingSessions ? (
                            <select className="form-select" disabled>
                                <option>Loading active submission windows...</option>
                            </select>
                        ) : activeSessions.length === 0 ? (
                            <div className="no-sessions-alert">
                                💬 There's no submission window open right now — check with your facilitator.
                            </div>
                        ) : (
                            <select
                                id="session-select"
                                className="form-select"
                                value={selectedSessionId}
                                onChange={(e) => setSelectedSessionId(e.target.value)}
                                required
                            >
                                <option value="">-- Select Open Session --</option>
                                {activeSessions.map((session) => (
                                    <option key={session.id} value={session.id}>
                                        {session.sessionName} [{session.assignmentTitle}] — closes {formatEndTime(session.endTime)}
                                    </option>
                                ))}
                            </select>
                        )}
                    </div>

                    {/* File Upload Zone */}
                    <div className="form-group">
                        <label>Submission File (.pdf, .doc, .docx - max 20MB)</label>
                        <div
                            className={`dropzone ${isDragging ? 'dragging' : ''} ${file ? 'file-attached' : ''}`}
                            onDragOver={handleDragOver}
                            onDragLeave={handleDragLeave}
                            onDrop={handleDrop}
                        >
                            <input
                                type="file"
                                id="file-upload"
                                className="file-input-hidden"
                                accept=".pdf,.doc,.docx"
                                onChange={handleFileChange}
                            />
                            <label htmlFor="file-upload" className="dropzone-content">
                                {file ? (
                                    <div className="file-info">
                                        <span className="file-icon">📄</span>
                                        <div>
                                            <p className="file-name">{file.name}</p>
                                            <p className="file-size">{(file.size / (1024 * 1024)).toFixed(2)} MB</p>
                                        </div>
                                    </div>
                                ) : (
                                    <div className="drag-prompt">
                                        <span className="upload-icon">☁️</span>
                                        <p><strong>Drag & Drop your file here</strong> or <span>Browse</span></p>
                                        <small>Supports PDF, DOC, DOCX</small>
                                    </div>
                                )}
                            </label>
                        </div>
                    </div>

                    {/* Submit Button */}
                    <button
                        type="submit"
                        className="submit-btn"
                        disabled={isSubmitting || activeSessions.length === 0}
                    >
                        {isSubmitting ? 'Uploading...' : 'Submit Assignment'}
                    </button>
                </form>
            </div>
        </div>
    );
};

export default SubmissionForm;
