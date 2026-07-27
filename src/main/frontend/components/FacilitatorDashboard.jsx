import React, { useState, useEffect } from 'react';
import './FacilitatorDashboard.css';

const FacilitatorDashboard = () => {
    const [assignments, setAssignments] = useState([]);
    const [sessions, setSessions] = useState([]);
    const [selectedSessionId, setSelectedSessionId] = useState(null);
    const [sessionOverview, setSessionOverview] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [isLoadingOverview, setIsLoadingOverview] = useState(false);

    // Modal state for creating new assignment
    const [showAssignmentModal, setShowAssignmentModal] = useState(false);
    const [newTitle, setNewTitle] = useState('');
    const [newDescription, setNewDescription] = useState('');
    const [newDueDate, setNewDueDate] = useState('');
    const [assignmentError, setAssignmentError] = useState('');

    // Modal state for opening new submission session
    const [showSessionModal, setShowSessionModal] = useState(false);
    const [sessionName, setSessionName] = useState('');
    const [selectedAssignmentId, setSelectedAssignmentId] = useState('');
    const [startTime, setStartTime] = useState('');
    const [endTime, setEndTime] = useState('');
    const [sessionError, setSessionError] = useState('');

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        await Promise.all([loadAssignments(), loadSessions()]);
    };

    const loadAssignments = async () => {
        try {
            const res = await fetch('/api/assignments');
            if (res.ok) {
                const data = await res.json();
                setAssignments(data);
            }
        } catch (err) {
            console.error('Failed to load assignments:', err);
        }
    };

    const loadSessions = async () => {
        try {
            const res = await fetch('/api/sessions');
            if (res.ok) {
                const data = await res.json();
                setSessions(data);
                if (data.length > 0 && !selectedSessionId) {
                    selectSession(data[0].id);
                }
            }
        } catch (err) {
            console.error('Failed to load sessions:', err);
        }
    };

    const selectSession = async (sessionId) => {
        setSelectedSessionId(sessionId);
        setIsLoadingOverview(true);
        try {
            const res = await fetch(`/api/sessions/${sessionId}/submissions`);
            if (res.ok) {
                const data = await res.json();
                setSessionOverview(data);
            }
        } catch (err) {
            console.error('Failed to load session submissions overview:', err);
        } finally {
            setIsLoadingOverview(false);
        }
    };

    const handleCreateAssignment = async (e) => {
        e.preventDefault();
        setAssignmentError('');

        try {
            const res = await fetch('/api/assignments', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    title: newTitle,
                    description: newDescription,
                    dueDate: newDueDate
                })
            });

            if (!res.ok) {
                const errData = await res.json();
                throw new Error(errData.message || 'Failed to create assignment.');
            }

            setNewTitle('');
            setNewDescription('');
            setNewDueDate('');
            setShowAssignmentModal(false);
            await loadAssignments();
        } catch (err) {
            setAssignmentError(err.message);
        }
    };

    const handleCreateSession = async (e) => {
        e.preventDefault();
        setSessionError('');

        try {
            const res = await fetch('/api/sessions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sessionName,
                    assignmentId: Number(selectedAssignmentId),
                    startTime,
                    endTime
                })
            });

            if (!res.ok) {
                const errData = await res.json();
                throw new Error(errData.message || 'Failed to create submission session.');
            }

            const created = await res.json();
            setSessionName('');
            setSelectedAssignmentId('');
            setStartTime('');
            setEndTime('');
            setShowSessionModal(false);

            await loadSessions();
            selectSession(created.id);
        } catch (err) {
            setSessionError(err.message);
        }
    };

    const handleToggleSessionStatus = async (sessionId, currentStatus) => {
        const endpoint = currentStatus === 'OPEN' ? `/api/sessions/${sessionId}/close` : `/api/sessions/${sessionId}/open`;
        try {
            const res = await fetch(endpoint, { method: 'PUT' });
            if (res.ok) {
                await loadSessions();
                if (selectedSessionId === sessionId) {
                    selectSession(sessionId);
                }
            }
        } catch (err) {
            console.error('Failed to toggle session status:', err);
        }
    };

    // Filter functions for search
    const filterLearner = (learner) => {
        if (!searchQuery.trim()) return true;
        const q = searchQuery.toLowerCase();
        const nameMatch = learner.fullName && learner.fullName.toLowerCase().includes(q);
        const codeMatch = learner.learnerCode && learner.learnerCode.toLowerCase().includes(q);
        const cohortMatch = learner.cohort && learner.cohort.toLowerCase().includes(q);
        return nameMatch || codeMatch || cohortMatch;
    };

    const filteredSubmitted = sessionOverview?.submitted?.filter(filterLearner) || [];
    const filteredUnsubmitted = sessionOverview?.unsubmitted?.filter(filterLearner) || [];

    return (
        <div className="dashboard-container">
            {/* Header */}
            <header className="dashboard-header">
                <div>
                    <h1>Facilitator Dashboard</h1>
                    <p className="subtitle">Manage submission sessions, track learner progress, and post assignments</p>
                </div>
                <div className="header-actions">
                    <button
                        className="btn-secondary"
                        onClick={() => setShowAssignmentModal(true)}
                    >
                        + New Assignment
                    </button>
                    <button
                        className="btn-primary"
                        onClick={() => setShowSessionModal(true)}
                    >
                        + Open New Session
                    </button>
                </div>
            </header>

            {/* Dashboard Layout */}
            <div className="dashboard-grid">
                {/* Sidebar: Submission Sessions */}
                <aside className="assignments-sidebar">
                    <div className="sidebar-title-row">
                        <h3>Submission Sessions</h3>
                    </div>

                    <div className="assignments-list">
                        {sessions.length === 0 ? (
                            <p className="empty-text">No submission sessions created yet.</p>
                        ) : (
                            sessions.map((sess) => {
                                const isSelected = sess.id === selectedSessionId;
                                const statusClass = sess.status.toLowerCase();
                                return (
                                    <div
                                        key={sess.id}
                                        className={`assignment-card ${isSelected ? 'active' : ''}`}
                                        onClick={() => selectSession(sess.id)}
                                    >
                                        <div className="card-title-row">
                                            <span className="card-title">{sess.sessionName}</span>
                                            <span className={`status-pill ${statusClass}`}>{sess.status}</span>
                                        </div>
                                        <div className="card-sub">{sess.assignmentTitle}</div>
                                        <div className="card-due">
                                            {new Date(sess.startTime).toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                                            {' ➔ '}
                                            {new Date(sess.endTime).toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                                        </div>

                                        <div className="card-action-bar" onClick={(e) => e.stopPropagation()}>
                                            {sess.status === 'OPEN' ? (
                                                <button
                                                    className="btn-status-toggle close-now"
                                                    onClick={() => handleToggleSessionStatus(sess.id, 'OPEN')}
                                                >
                                                    ⛔ Close Now
                                                </button>
                                            ) : (
                                                <button
                                                    className="btn-status-toggle open-now"
                                                    onClick={() => handleToggleSessionStatus(sess.id, sess.status)}
                                                >
                                                    ⚡ Open Now
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                );
                            })
                        )}
                    </div>
                </aside>

                {/* Main Area: Session Breakdown */}
                <main className="submissions-main">
                    {sessionOverview ? (
                        <>
                            <div className="overview-header">
                                <div>
                                    <h2>{sessionOverview.sessionName}</h2>
                                    <p className="assignment-ref">Linked Assignment: <strong>{sessionOverview.assignmentTitle}</strong></p>
                                    <div className="session-time-info">
                                        <span className={`status-tag ${sessionOverview.sessionStatus.toLowerCase()}`}>
                                            STATUS: {sessionOverview.sessionStatus}
                                        </span>
                                        <span className="time-range">
                                            Window: {new Date(sessionOverview.startTime).toLocaleString()} — {new Date(sessionOverview.endTime).toLocaleString()}
                                        </span>
                                    </div>
                                </div>

                                {/* Search Input */}
                                <div className="search-box">
                                    <input
                                        type="text"
                                        placeholder="Filter learner name or cohort..."
                                        value={searchQuery}
                                        onChange={(e) => setSearchQuery(e.target.value)}
                                        className="search-input"
                                    />
                                </div>
                            </div>

                            {/* Summary Stats */}
                            <div className="stats-row">
                                <div className="stat-pill total">Total Learners: {sessionOverview.totalLearners}</div>
                                <div className="stat-pill submitted">Submitted: {sessionOverview.submittedCount}</div>
                                <div className="stat-pill unsubmitted">Pending: {sessionOverview.unsubmittedCount}</div>
                            </div>

                            {isLoadingOverview ? (
                                <div className="loading-spinner">Loading session submission details...</div>
                            ) : (
                                /* Two Columns Layout */
                                <div className="columns-grid">
                                    {/* Submitted Column */}
                                    <div className="status-column submitted-col">
                                        <div className="column-header">
                                            <h3>Submitted ({filteredSubmitted.length})</h3>
                                        </div>
                                        <div className="learner-cards-list">
                                            {filteredSubmitted.length === 0 ? (
                                                <p className="empty-column-text">No matching submitted learners.</p>
                                            ) : (
                                                filteredSubmitted.map((item) => (
                                                    <div key={item.submissionId} className="learner-card item-submitted">
                                                        <div className="learner-info">
                                                            <span className="learner-name">{item.fullName}</span>
                                                            <span className="learner-code">{item.learnerCode} • {item.cohort}</span>
                                                        </div>
                                                        <div className="submission-meta">
                                                            <span className={`status-tag ${item.status.toLowerCase()}`}>
                                                                {item.status}
                                                            </span>
                                                            <span className="time-stamp">
                                                                {new Date(item.submittedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                                            </span>
                                                            {item.filePath && (
                                                                <a
                                                                    href={`/api/submissions/${item.submissionId}/download`}
                                                                    target="_blank"
                                                                    rel="noopener noreferrer"
                                                                    className="download-link"
                                                                    title={item.originalFilename}
                                                                >
                                                                    📥 {item.originalFilename}
                                                                </a>
                                                            )}
                                                        </div>
                                                    </div>
                                                ))
                                            )}
                                        </div>
                                    </div>

                                    {/* Not Submitted Column */}
                                    <div className="status-column unsubmitted-col">
                                        <div className="column-header">
                                            <h3>Not Submitted ({filteredUnsubmitted.length})</h3>
                                        </div>
                                        <div className="learner-cards-list">
                                            {filteredUnsubmitted.length === 0 ? (
                                                <p className="empty-column-text">All learners submitted! 🎉</p>
                                            ) : (
                                                filteredUnsubmitted.map((item) => (
                                                    <div key={item.learnerId} className="learner-card item-unsubmitted">
                                                        <div className="learner-info">
                                                            <span className="learner-name">{item.fullName}</span>
                                                            <span className="learner-code">{item.learnerCode} • {item.cohort}</span>
                                                        </div>
                                                        <span className="status-tag pending">PENDING</span>
                                                    </div>
                                                ))
                                            )}
                                        </div>
                                    </div>
                                </div>
                            )}
                        </>
                    ) : (
                        <div className="empty-state">
                            <p>Select a submission session from the sidebar to view detailed learner progress.</p>
                        </div>
                    )}
                </main>
            </div>

            {/* Modal: Open New Submission Session */}
            {showSessionModal && (
                <div className="modal-backdrop">
                    <div className="modal-card">
                        <div className="modal-header">
                            <h3>Open Submission Session</h3>
                            <button className="close-btn" onClick={() => setShowSessionModal(false)}>×</button>
                        </div>
                        {sessionError && <div className="alert-error">{sessionError}</div>}
                        <form onSubmit={handleCreateSession} className="modal-form">
                            <div className="form-field">
                                <label>Session Name</label>
                                <input
                                    type="text"
                                    required
                                    placeholder="e.g. March Intake - Assignment 1 Window"
                                    value={sessionName}
                                    onChange={(e) => setSessionName(e.target.value)}
                                />
                            </div>
                            <div className="form-field">
                                <label>Target Assignment</label>
                                <select
                                    required
                                    className="form-select"
                                    value={selectedAssignmentId}
                                    onChange={(e) => setSelectedAssignmentId(e.target.value)}
                                >
                                    <option value="">-- Choose Assignment --</option>
                                    {assignments.map((a) => (
                                        <option key={a.id} value={a.id}>
                                            {a.title}
                                        </option>
                                    ))}
                                </select>
                            </div>
                            <div className="form-field">
                                <label>Start Time</label>
                                <input
                                    type="datetime-local"
                                    required
                                    value={startTime}
                                    onChange={(e) => setStartTime(e.target.value)}
                                />
                            </div>
                            <div className="form-field">
                                <label>End Time</label>
                                <input
                                    type="datetime-local"
                                    required
                                    value={endTime}
                                    onChange={(e) => setEndTime(e.target.value)}
                                />
                            </div>
                            <div className="modal-actions">
                                <button
                                    type="button"
                                    className="btn-secondary"
                                    onClick={() => setShowSessionModal(false)}
                                >
                                    Cancel
                                </button>
                                <button type="submit" className="btn-primary">
                                    Create Session
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modal: Create New Assignment */}
            {showAssignmentModal && (
                <div className="modal-backdrop">
                    <div className="modal-card">
                        <div className="modal-header">
                            <h3>Create New Assignment</h3>
                            <button className="close-btn" onClick={() => setShowAssignmentModal(false)}>×</button>
                        </div>
                        {assignmentError && <div className="alert-error">{assignmentError}</div>}
                        <form onSubmit={handleCreateAssignment} className="modal-form">
                            <div className="form-field">
                                <label>Title</label>
                                <input
                                    type="text"
                                    required
                                    placeholder="e.g. Java Core & OOP Assignment"
                                    value={newTitle}
                                    onChange={(e) => setNewTitle(e.target.value)}
                                />
                            </div>
                            <div className="form-field">
                                <label>Description</label>
                                <textarea
                                    rows="3"
                                    placeholder="Assignment instructions..."
                                    value={newDescription}
                                    onChange={(e) => setNewDescription(e.target.value)}
                                />
                            </div>
                            <div className="form-field">
                                <label>Due Date</label>
                                <input
                                    type="datetime-local"
                                    required
                                    value={newDueDate}
                                    onChange={(e) => setNewDueDate(e.target.value)}
                                />
                            </div>
                            <div className="modal-actions">
                                <button
                                    type="button"
                                    className="btn-secondary"
                                    onClick={() => setShowAssignmentModal(false)}
                                >
                                    Cancel
                                </button>
                                <button type="submit" className="btn-primary">
                                    Post Assignment
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default FacilitatorDashboard;
