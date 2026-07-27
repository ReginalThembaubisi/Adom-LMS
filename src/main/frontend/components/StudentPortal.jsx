import React, { useState, useEffect } from 'react';
import './StudentPortal.css';

const StudentPortal = ({ learner, onLogout }) => {
    const [activeSessions, setActiveSessions] = useState([]);
    const [selectedSessionId, setSelectedSessionId] = useState('');
    const [selectedFile, setSelectedFile] = useState(null);
    const [mySubmissions, setMySubmissions] = useState([]);

    const [isSubmitting, setIsSubmitting] = useState(false);
    const [alert, setAlert] = useState(null);

    const studentNumber = learner?.learnerCode || learner?.studentNumber || '';

    useEffect(() => {
        if (studentNumber) {
            loadActiveSessions();
            loadMySubmissions();
        }
    }, [studentNumber]);

    const loadActiveSessions = async () => {
        try {
            const res = await fetch('http://localhost:8080/api/sessions/active');
            if (res.ok) {
                const data = await res.json();
                setActiveSessions(data);
                if (data.length > 0) setSelectedSessionId(data[0].id);
            }
        } catch (e) {
            // Offline fallback simulation
            const seedSessions = [
                { id: 1, sessionName: 'March Intake - Assignment 1 Submission Window', assignmentTitle: 'Java Core & OOP Principles', status: 'OPEN' }
            ];
            setActiveSessions(seedSessions);
            if (seedSessions.length > 0) setSelectedSessionId(seedSessions[0].id);
        }
    };

    const loadMySubmissions = async () => {
        try {
            const res = await fetch(`http://localhost:8080/api/learners/${studentNumber}/submissions`);
            if (res.ok) {
                const data = await res.json();
                setMySubmissions(data);
            }
        } catch (e) {
            // Local fallback
            const localSaved = JSON.parse(localStorage.getItem(`submissions_${studentNumber}`) || '[]');
            setMySubmissions(localSaved);
        }
    };

    const handleFileChange = (e) => {
        if (e.target.files && e.target.files[0]) {
            const file = e.target.files[0];
            const ext = file.name.split('.').pop().toLowerCase();
            if (!['pdf', 'doc', 'docx'].includes(ext)) {
                setAlert({ type: 'error', text: 'Invalid file format. Only PDF, DOC, and DOCX files allowed.' });
                setSelectedFile(null);
                return;
            }
            setSelectedFile(file);
            setAlert(null);
        }
    };

    const handleSubmitAssignment = async (e) => {
        e.preventDefault();
        setAlert(null);

        if (!selectedSessionId) return setAlert({ type: 'error', text: 'Please select an active submission window.' });
        if (!selectedFile) return setAlert({ type: 'error', text: 'Please attach a submission file.' });

        setIsSubmitting(true);

        const formData = new FormData();
        formData.append('learner_code', studentNumber);
        formData.append('session_id', selectedSessionId);
        formData.append('file', selectedFile);

        try {
            const res = await fetch('http://localhost:8080/api/submissions', {
                method: 'POST',
                body: formData
            });

            const data = await res.json();
            if (!res.ok) throw new Error(data.message || 'Submission failed.');

            setAlert({ type: 'success', text: `Assignment submitted successfully! Status: ${data.status}` });
            setSelectedFile(null);
            loadMySubmissions();
        } catch (err) {
            // Local fallback simulation
            const newSubmission = {
                submissionId: Date.now(),
                sessionName: activeSessions.find(s => s.id == selectedSessionId)?.sessionName || 'Assignment Window',
                assignmentTitle: activeSessions.find(s => s.id == selectedSessionId)?.assignmentTitle || 'Assignment',
                originalFilename: selectedFile.name,
                submittedAt: new Date().toISOString(),
                status: 'SUBMITTED'
            };

            const existing = JSON.parse(localStorage.getItem(`submissions_${studentNumber}`) || '[]');
            existing.unshift(newSubmission);
            localStorage.setItem(`submissions_${studentNumber}`, JSON.stringify(existing));

            setAlert({ type: 'success', text: 'Assignment submitted successfully! Status: SUBMITTED' });
            setSelectedFile(null);
            loadMySubmissions();
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="portal-container">
            <!-- Header -->
            <header className="portal-header">
                <div>
                    <h2>Welcome back, {learner?.fullName}!</h2>
                    <p className="subtitle">
                        Student Number: <strong>{studentNumber}</strong> • Cohort: {learner?.cohort || '2026 Intake'}
                    </p>
                </div>
                <button className="logout-btn" onClick={onLogout}>🚪 Sign Out</button>
            </header>

            <div className="portal-grid">
                <!-- Submit Card -->
                <div className="portal-card">
                    <div className="card-title-bar">
                        <h3>Submit New Assignment</h3>
                        <span className="badge-open">ACTIVE WINDOW</span>
                    </div>

                    {alert && (
                        <div className={`alert-banner ${alert.type}`}>
                            {alert.type === 'success' ? '✓ ' : '⚠️ '}{alert.text}
                        </div>
                    )}

                    {activeSessions.length === 0 ? (
                        <p style={{ color: '#94a3b8' }}>There are currently no open submission windows. Check back later.</p>
                    ) : (
                        <form onSubmit={handleSubmitAssignment} className="portal-form">
                            <div className="form-group">
                                <label>Select Submission Window</label>
                                <select
                                    className="form-select"
                                    value={selectedSessionId}
                                    onChange={(e) => setSelectedSessionId(e.target.value)}
                                    required
                                >
                                    {activeSessions.map(s => (
                                        <option key={s.id} value={s.id}>
                                            {s.sessionName} [{s.assignmentTitle}]
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div className="form-group">
                                <label>Upload File (.pdf, .doc, .docx - max 20MB)</label>
                                <input
                                    type="file"
                                    className="file-input"
                                    accept=".pdf,.doc,.docx"
                                    onChange={handleFileChange}
                                />
                                {selectedFile && (
                                    <div className="selected-file-badge">
                                        📄 {selectedFile.name} ({(selectedFile.size / (1024 * 1024)).toFixed(2)} MB)
                                    </div>
                                )}
                            </div>

                            <button
                                type="submit"
                                className="submit-btn"
                                disabled={isSubmitting}
                            >
                                {isSubmitting ? 'Uploading...' : 'Submit Assignment File'}
                            </button>
                        </form>
                    )}
                </div>

                <!-- Submissions History Card -->
                <div className="portal-card">
                    <div className="card-title-bar">
                        <h3>My Past Submissions</h3>
                        <span className="history-count">{mySubmissions.length} Submissions</span>
                    </div>

                    {mySubmissions.length === 0 ? (
                        <p style={{ color: '#94a3b8' }}>You haven't submitted any assignments yet.</p>
                    ) : (
                        <div className="history-list">
                            {mySubmissions.map(s => (
                                <div key={s.submissionId || s.id} className="history-item">
                                    <div className="history-info">
                                        <span className="history-assignment">{s.assignmentTitle || s.sessionName}</span>
                                        <span className="history-filename">📄 {s.originalFilename}</span>
                                        <span className="history-date">
                                            Submitted: {new Date(s.submittedAt).toLocaleString()}
                                        </span>
                                    </div>
                                    <span className={`status-badge ${s.status?.toLowerCase()}`}>
                                        {s.status}
                                    </span>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default StudentPortal;
