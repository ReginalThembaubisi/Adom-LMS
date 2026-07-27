import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLearner } from '../context/LearnerContext';

const StudentPortal = () => {
    const { learner, logoutStudent } = useLearner();
    const navigate = useNavigate();

    // Redirect to landing if no learner context exists
    useEffect(() => {
        if (!learner) {
            navigate('/?session_expired=true');
        }
    }, [learner, navigate]);

    if (!learner) return null;

    const studentNumber = learner.learnerCode || '';

    // UI Tab State: 'modules' | 'submit' | 'history'
    const [activeTab, setActiveTab] = useState('modules');
    const [alert, setAlert] = useState({ type: '', message: '' });

    // Modules state
    const [modules, setModules] = useState([]);
    const [selectedModule, setSelectedModule] = useState(null);
    const [timeline, setTimeline] = useState([]);
    const [loadingModules, setLoadingModules] = useState(false);

    // Active submit slot session state
    const [selectedSession, setSelectedSession] = useState(null);
    const [isSubmittedState, setIsSubmittedState] = useState(false);
    const [submissionConfirmData, setSubmissionConfirmData] = useState(null);

    // Submissions history state
    const [history, setHistory] = useState([]);

    // Inline submit slot session state
    const [activeUploadSessionId, setActiveUploadSessionId] = useState(null);
    const [inlineFile, setInlineFile] = useState(null);
    const [inlineAlerts, setInlineAlerts] = useState({});
    const [lastSubmission, setLastSubmission] = useState(null);

    // File upload state
    const [attachedFile, setAttachedFile] = useState(null);
    const [isDragging, setIsDragging] = useState(false);
    const [uploading, setUploading] = useState(false);
    const fileInputRef = useRef(null);

    // Fetch initial data
    useEffect(() => {
        if (studentNumber) {
            fetchModules();
            fetchTimeline();
            fetchHistory();
        }
    }, [studentNumber]);

    const checkStudentResponse = (res) => {
        if (res.status === 401 || res.status === 404) {
            logoutStudent();
            navigate('/login');
            return false;
        }
        return true;
    };

    const fetchModules = async () => {
        setLoadingModules(true);
        try {
            const res = await fetch(`/api/learners/${studentNumber}/modules`);
            if (!checkStudentResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setModules(data);
            }
        } catch (e) {
            console.error('Failed to load modules', e);
        } finally {
            setLoadingModules(false);
        }
    };

    const fetchTimeline = async () => {
        try {
            const res = await fetch(`/api/learners/${studentNumber}/timeline`);
            if (!checkStudentResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setTimeline(data);
            }
        } catch (e) {
            console.error('Failed to load timeline', e);
        }
    };

    const fetchHistory = async () => {
        try {
            const res = await fetch(`/api/learners/${studentNumber}/submissions`);
            if (!checkStudentResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setHistory(data);
            }
        } catch (e) {
            console.error('Failed to load history', e);
        }
    };

    const openModuleDetails = async (moduleId) => {
        setAlert({ type: '', message: '' });
        try {
            const res = await fetch(`/api/modules/${moduleId}?studentNumber=${studentNumber}`);
            if (!checkStudentResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setSelectedModule(data);
            }
        } catch (e) {
            setAlert({ type: 'error', message: 'Failed to load module details.' });
        }
    };

    const handleSignout = () => {
        logoutStudent();
        navigate('/');
    };

    const triggerAssignmentSelect = (session) => {
        setAlert({ type: '', message: '' });
        setSelectedSession(session);
        setAttachedFile(null);
        setSubmissionConfirmData(null);
        setActiveTab('submit');
    };

    // Tab switcher with warning redirect guard
    const handleTabChange = (tabId) => {
        setAlert({ type: '', message: '' });
        if (tabId === 'submit' && !selectedSession) {
            setAlert({ type: 'error', message: 'Please select an assignment from your Modules or Upcoming Deadlines to submit.' });
            setActiveTab('modules');
            setSelectedModule(null); // Reset detail view
            return;
        }
        setActiveTab(tabId);
        if (tabId === 'modules') {
            setSelectedModule(null); // Return to module list
            fetchModules();
            fetchTimeline();
        }
    };

    // Dropzone logic removed since submission form is inline.

    // Removed old unused submission handlers.
    const handleInlineFileChange = (e, sessionId) => {
        const file = e.target.files[0];
        if (file) {
            if (file.size > 20 * 1024 * 1024) {
                setInlineAlerts(prev => ({
                    ...prev,
                    [sessionId]: { type: 'error', message: 'File size exceeds the 20MB limit.' }
                }));
                setInlineFile(null);
                return;
            }
            setInlineAlerts(prev => ({ ...prev, [sessionId]: null }));
            setInlineFile(file);
        }
    };

    const handleInlineSubmit = async (e, sessionId) => {
        e.preventDefault();
        setInlineAlerts(prev => ({ ...prev, [sessionId]: null }));

        if (!inlineFile) {
            setInlineAlerts(prev => ({
                ...prev,
                [sessionId]: { type: 'error', message: 'Please attach your assignment file.' }
            }));
            return;
        }

        setUploading(true);
        const formData = new FormData();
        formData.append('learner_code', studentNumber);
        formData.append('session_id', sessionId);
        formData.append('file', inlineFile);

        try {
            const res = await fetch('/api/submissions', {
                method: 'POST',
                body: formData
            });

            const data = await res.json();
            if (!res.ok) throw new Error(data.message || 'Upload failed.');

            setInlineAlerts(prev => ({
                ...prev,
                [sessionId]: { type: 'success', message: 'Submission uploaded successfully!' }
            }));
            
            const slot = selectedModule && selectedModule.slots ? selectedModule.slots.find(s => s.id === sessionId) : null;
            const slotTitle = slot ? slot.title : 'Assignment';
            const sessionName = slot ? slot.sessionName : 'Session';

            setLastSubmission({
                fileName: data.originalFilename || inlineFile.name,
                slotName: `${selectedModule ? selectedModule.moduleName : 'Module'} — ${slotTitle} (${sessionName})`,
                submittedAt: new Date(data.submittedAt || new Date()).toLocaleString(),
                sessionId: sessionId
            });
            
            fetchHistory();
            if (selectedModule) {
                openModuleDetails(selectedModule.id);
            }
            fetchModules();

            setTimeout(() => {
                setActiveUploadSessionId(null);
                setInlineFile(null);
            }, 1500);

        } catch (err) {
            setInlineAlerts(prev => ({
                ...prev,
                [sessionId]: { type: 'error', message: err.message || 'Connection failed.' }
            }));
        } finally {
            setUploading(false);
        }
    };    // File upload state
    const [attachedFile, setAttachedFile] = useState(null);
    const [isDragging, setIsDragging] = useState(false);
    const [uploading, setUploading] = useState(false);
    const fileInputRef = useRef(null);

    // Fetch initial data
    useEffect(() => {
        if (studentNumber) {
            fetchModules();
            fetchTimeline();
            fetchHistory();
        }
    }, [studentNumber]);

    const checkStudentResponse = (res) => {
        if (res.status === 401 || res.status === 404) {
            logoutStudent();
            navigate('/login');
            return false;
        }
        return true;
    };

    const fetchModules = async () => {
        setLoadingModules(true);
        try {
            const res = await fetch(`/api/learners/${studentNumber}/modules`);
            if (!checkStudentResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setModules(data);
            }
        } catch (e) {
            console.error('Failed to load modules', e);
        } finally {
            setLoadingModules(false);
        }
    };

    const fetchTimeline = async () => {
        try {
            const res = await fetch(`/api/learners/${studentNumber}/timeline`);
            if (!checkStudentResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setTimeline(data);
            }
        } catch (e) {
            console.error('Failed to load timeline', e);
        }
    };

    const fetchHistory = async () => {
        try {
            const res = await fetch(`/api/learners/${studentNumber}/submissions`);
            if (!checkStudentResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setHistory(data);
            }
        } catch (e) {
            console.error('Failed to load history', e);
        }
    };

    const openModuleDetails = async (moduleId) => {
        try {
            const res = await fetch(`/api/learners/${studentNumber}/modules/${moduleId}`);
            if (!checkStudentResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setSelectedModule(data);
            }
        } catch (e) {
            console.error('Failed to load module details', e);
        }
    };

    const handleInlineFileChange = (e, sessionId) => {
        if (e.target.files && e.target.files[0]) {
            setInlineFile(e.target.files[0]);
            setInlineAlerts(prev => ({ ...prev, [sessionId]: null }));
        }
    };

    const handleInlineSubmit = async (e, sessionId) => {
        e.preventDefault();
        if (!inlineFile) {
            setInlineAlerts(prev => ({
                ...prev,
                [sessionId]: { type: 'error', message: 'Please select a file to submit.' }
            }));
            return;
        }

        setUploading(true);
        setInlineAlerts(prev => ({
            ...prev,
            [sessionId]: { type: 'info', message: 'Uploading submission...' }
        }));

        try {
            const formData = new FormData();
            formData.append('file', inlineFile);
            formData.append('learnerCode', studentNumber);

            const res = await fetch(`/api/submissions/upload/${sessionId}`, {
                method: 'POST',
                body: formData
            });

            if (!checkStudentResponse(res)) return;

            if (!res.ok) {
                const errData = await res.json();
                throw new Error(errData.message || 'Server rejected file upload.');
            }

            const data = await res.json();
            
            setInlineAlerts(prev => ({
                ...prev,
                [sessionId]: { type: 'success', message: 'Submission uploaded successfully!' }
            }));
            
            const slot = selectedModule && selectedModule.slots ? selectedModule.slots.find(s => s.id === sessionId) : null;
            const slotTitle = slot ? slot.title : 'Assignment';
            const sessionName = slot ? slot.sessionName : 'Session';

            setLastSubmission({
                fileName: data.originalFilename || inlineFile.name,
                slotName: `${selectedModule ? selectedModule.moduleName : 'Module'} — ${slotTitle} (${sessionName})`,
                submittedAt: new Date(data.submittedAt || new Date()).toLocaleString(),
                sessionId: sessionId
            });
            
            fetchHistory();
            if (selectedModule) {
                openModuleDetails(selectedModule.id);
            }
            fetchModules();

            setTimeout(() => {
                setActiveUploadSessionId(null);
                setInlineFile(null);
            }, 1500);

        } catch (err) {
            setInlineAlerts(prev => ({
                ...prev,
                [sessionId]: { type: 'error', message: err.message || 'Connection failed.' }
            }));
        } finally {
            setUploading(false);
        }
    };

    return (
        <div className="portal-wrapper">
            {/* Header */}
            <header className="portal-header">
                <div>
                    <h1 style={{ margin: 0, fontSize: '1.85rem', fontWeight: 800, color: 'var(--text-main)' }}>Welcome back, {learner.fullName}!</h1>
                </div>
                <button onClick={logoutStudent} className="signout-btn" style={{ fontWeight: 'bold' }}>Sign Out</button>
            </header>

            {/* Student Info Card Block */}
            <div className="portal-card" style={{ padding: '1.15rem 1.5rem', marginBottom: '1.5rem', display: 'flex', flexWrap: 'wrap', gap: '1.5rem', fontSize: '0.9rem' }}>
                <div style={{ flex: '1', minWidth: '140px' }}>
                    <span style={{ color: 'var(--text-muted)', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 700, marginBottom: '2px' }}>Student Number</span>
                    <strong style={{ color: 'var(--text-main)', fontSize: '0.95rem' }}>{studentNumber}</strong>
                </div>
                <div style={{ flex: '1', minWidth: '140px' }}>
                    <span style={{ color: 'var(--text-muted)', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 700, marginBottom: '2px' }}>Cohort</span>
                    <strong style={{ color: 'var(--text-main)', fontSize: '0.95rem' }}>{learner.cohort || '2026 Intake'}</strong>
                </div>
                <div style={{ flex: '2', minWidth: '220px' }}>
                    <span style={{ color: 'var(--text-muted)', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 700, marginBottom: '2px' }}>Learnership Program</span>
                    <strong style={{ color: 'var(--text-main)', fontSize: '0.95rem' }}>{learner.learnershipName || 'Unassigned'}</strong>
                </div>
            </div>

            {/* Tab Bar */}
            <nav className="tab-bar">
                <button 
                    className={`tab-btn ${activeTab === 'modules' ? 'active' : ''}`}
                    onClick={() => setActiveTab('modules')}
                    style={{ fontWeight: 'bold' }}
                >
                    My Modules
                </button>
                <button 
                    className={`tab-btn ${activeTab === 'history' ? 'active' : ''}`}
                    onClick={() => setActiveTab('history')}
                    style={{ fontWeight: 'bold' }}
                >
                    My Submissions
                </button>
            </nav>

            {lastSubmission && (
                <div className="portal-card" style={{ border: '1px solid var(--success)', background: 'rgba(22, 163, 74, 0.05)', margin: '1rem 0', padding: '1.25rem', borderRadius: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <div>
                            <h3 style={{ margin: '0 0 0.5rem 0', color: 'var(--success)', display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '1.1rem', fontWeight: 'bold' }}>
                                Submission Successful!
                            </h3>
                            <p style={{ margin: '0 0 0.25rem 0', fontSize: '0.9rem', color: 'var(--text-body)' }}>
                                <strong>File Submitted:</strong> {lastSubmission.fileName}
                            </p>
                            <p style={{ margin: '0 0 0.25rem 0', fontSize: '0.9rem', color: 'var(--text-body)' }}>
                                <strong>Assignment Slot:</strong> {lastSubmission.slotName}
                            </p>
                            <p style={{ margin: '0 0 0.75rem 0', fontSize: '0.9rem', color: 'var(--text-body)' }}>
                                <strong>Submitted At:</strong> {lastSubmission.submittedAt}
                            </p>
                            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                                You can check this anytime under <strong>My Submissions</strong>.
                            </span>
                        </div>
                        <button 
                            onClick={() => setLastSubmission(null)} 
                            className="signout-btn" 
                            style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem', marginTop: 0, fontWeight: 'bold' }}
                        >
                            Dismiss
                        </button>
                    </div>
                </div>
            )}

            {/* Alert Banner */}
            {alert.message && (
                <div className={`alert-banner ${alert.type}`} style={{ fontWeight: 'bold' }}>
                    {alert.message}
                </div>
            )}

            {/* TAB 1: My Modules */}
            {activeTab === 'modules' && (
                <div className="tab-content active">
                    <div className="portal-card">
                        {!selectedModule ? (
                            <div id="modules-list-view">
                                <div className="card-header-row">
                                    <h2>My Active Modules</h2>
                                    <p>Select your modules to see active courses, learning materials, and grades.</p>
                                </div>

                                {loadingModules ? (
                                    <p style={{ color: 'var(--text-muted)' }}>Loading active modules...</p>
                                ) : modules.length === 0 ? (
                                    <div style={{ textAlign: 'center', padding: '3rem 1.5rem', border: '2px dashed rgba(255,255,255,0.05)', borderRadius: '12px' }}>
                                        <p style={{ color: 'var(--text-muted)', margin: 0 }}>You are not enrolled in any modules yet.</p>
                                    </div>
                                ) : (
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
                                        {Array.from(new Set(modules.map(m => m.moduleType || 'General'))).map(type => {
                                            const typeModules = modules.filter(m => (m.moduleType || 'General') === type);
                                            if (typeModules.length === 0) return null;
                                            return (
                                                <div key={type}>
                                                    <h3 style={{ fontSize: '1.1rem', marginBottom: '0.75rem', borderBottom: '1px solid rgba(255,255,255,0.05)', paddingBottom: '0.25rem', color: 'var(--text-muted)' }}>
                                                        {type} Modules
                                                    </h3>
                                                    <div className="modules-grid" style={{ marginBottom: '1rem' }}>
                                                        {typeModules.map((m) => (
                                                            <div 
                                                                key={m.id} 
                                                                className="module-item-card"
                                                                onClick={() => openModuleDetails(m.id)}
                                                            >
                                                                <div className="module-meta-info">
                                                                    <span className="module-title-label">{m.moduleName}</span>
                                                                    <span className="module-lecturer-label">Lecturer: {m.lecturerName} • Code: {m.moduleCode || 'N/A'}</span>
                                                                </div>
                                                            </div>
                                                        ))}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                    )}

                                {/* Timeline Upcoming Deadlines list */}
                                <div className="timeline-section">
                                    <h2>Upcoming Deadlines</h2>
                                    {timeline.length === 0 ? (
                                        <div style={{ textAlign: 'center', padding: '2rem 0', border: '2px dashed rgba(255, 255, 255, 0.05)', borderRadius: '12px' }}>
                                            <p style={{ color: 'var(--text-muted)', margin: 0, fontSize: '0.95rem' }}>No upcoming activities require action</p>
                                        </div>
                                    ) : (
                                        <div className="timeline-list">
                                            {timeline.map((item, idx) => (
                                                <div 
                                                    key={idx} 
                                                    className="timeline-item"
                                                    onClick={() => {
                                                        openModuleDetails(item.moduleId);
                                                        setActiveUploadSessionId(item.sessionId);
                                                    }}
                                                >
                                                    <div className="timeline-info">
                                                        <strong>{item.moduleName} — {item.slotTitle}</strong>
                                                    </div>
                                                    <div className="timeline-deadline" style={{ fontWeight: 'bold' }}>
                                                        Closes {new Date(item.endTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            </div>
                        ) : (
                            <div id="module-detail-view">
                                <button className="back-btn" onClick={() => setSelectedModule(null)} style={{ fontWeight: 'bold' }}>
                                    Back to Modules List
                                </button>
                                <div className="module-detail-card" style={{ gap: '1.25rem' }}>
                                    {/* Module Detail Header Card Box */}
                                    <div className="portal-card" style={{ padding: '1.5rem', marginBottom: 0 }}>
                                        <h2 style={{ margin: '0 0 0.5rem 0', fontSize: '1.6rem', color: 'var(--text-main)', fontWeight: 'bold' }}>{selectedModule.moduleName}</h2>
                                        <p style={{ color: 'var(--text-muted)', margin: 0, fontSize: '0.95rem' }}>Faculty Lecturer: <strong>{selectedModule.lecturerName}</strong> • Code: <strong>{selectedModule.moduleCode || 'N/A'}</strong></p>
                                    </div>

                                    {/* Learning Material Card Box */}
                                    <div className="portal-card" style={{ marginBottom: 0 }}>
                                        <h3 style={{ margin: '0 0 1.25rem 0', fontSize: '1.25rem', borderBottom: '1px solid var(--card-border)', paddingBottom: '0.5rem', color: 'var(--text-main)', fontWeight: 'bold' }}>Module Course Materials</h3>
                                        {(!selectedModule.files || selectedModule.files.length === 0) ? (
                                            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>No learning materials uploaded for this module yet.</p>
                                        ) : (
                                            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                                                {Object.entries(
                                                    selectedModule.files.reduce((acc, f) => {
                                                        const cat = f.fileType || 'Other';
                                                        if (!acc[cat]) acc[cat] = [];
                                                        acc[cat].push(f);
                                                        return acc;
                                                    }, {})
                                                ).sort((a, b) => {
                                                    const getPriority = (cat) => {
                                                        const c = cat.toLowerCase();
                                                        if (c === 'learner guide') return 0;
                                                        if (c.includes('assessment') || c.includes('poe') || c.includes('instrument')) return 1;
                                                        if (c === 'facilitator guide') return 2;
                                                        if (c === 'assessor guide') return 3;
                                                        if (c === 'moderator guide') return 4;
                                                        if (c === 'programme strategy') return 5;
                                                        return 6;
                                                    };
                                                    return getPriority(a[0]) - getPriority(b[0]);
                                                }).map(([category, categoryFiles]) => (
                                                    <div key={category}>
                                                        <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.8rem', textTransform: 'uppercase', color: 'var(--text-muted)', fontWeight: 'bold' }}>{category}</h4>
                                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                                                            {categoryFiles.map(file => (
                                                                <div key={file.id} className="syllabus-section" style={{ margin: 0, padding: '1rem' }}>
                                                                    <div className="syllabus-info">
                                                                        <div>
                                                                            <p style={{ margin: 0, fontWeight: 700 }}>{file.title || 'Untitled Material'}</p>
                                                                            <span style={{ fontSize: '0.775rem', color: 'var(--text-muted)' }}>{file.fileType}</span>
                                                                        </div>
                                                                    </div>
                                                                    <a href={file.filePath} download className="syllabus-download-link" style={{ fontWeight: 'bold' }}>Download</a>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </div>

                                    {/* Assignment Slots Card Box */}
                                    <div className="portal-card" style={{ marginBottom: 0 }}>
                                        <h3 style={{ margin: '0 0 1.25rem 0', fontSize: '1.25rem', borderBottom: '1px solid var(--card-border)', paddingBottom: '0.5rem', color: 'var(--text-main)', fontWeight: 'bold' }}>Assignment Slots</h3>
                                        {(!selectedModule.slots || selectedModule.slots.length === 0) ? (
                                            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: 0 }}>No active submission slots open for this module.</p>
                                        ) : (
                                            <div className="slots-list">
                                                {selectedModule.slots.map((s) => {
                                                    const deadline = new Date(s.endTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
                                                    const isSubmitted = s.submitted || s.isSubmitted;
                                                    const statusBadge = isSubmitted
                                                        ? <span className="status-badge submitted">✓ Submitted</span>
                                                        : s.status === 'CLOSED'
                                                            ? <span className="status-badge late">Closed</span>
                                                            : <span className="status-badge graded" style={{ background: 'rgba(251, 191, 36, 0.15)', border: '1px solid #fbbf24', color: '#fbbf24' }}>Pending</span>;

                                                    return (
                                                        <div key={s.id} className="slot-card">
                                                            <div className="slot-header">
                                                                <h4 className="slot-title">{selectedModule.moduleName} — {s.title}</h4>
                                                                {statusBadge}
                                                            </div>
                                                            <p className="slot-timeline">Session Name: {s.sessionName} • Deadline: <strong>{deadline}</strong></p>
                                                            <p className="slot-desc">{s.description || 'No instructions provided.'}</p>
                                                            
                                                            {s.taskFilePath && (
                                                                <div style={{ marginBottom: '1rem' }}>
                                                                    <a 
                                                                        href={s.taskFilePath} 
                                                                        download 
                                                                        className="syllabus-download-link"
                                                                        style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', textDecoration: 'none', background: '#f1f5f9', border: '1px solid var(--card-border)', color: 'var(--text-main)', padding: '0.4rem 0.8rem', borderRadius: '6px', fontSize: '0.85rem', fontWeight: 'bold' }}
                                                                    >
                                                                        Download Task File ({s.taskFileName || 'Attachment'})
                                                                    </a>
                                                                </div>
                                                            )}
 
                                                             {!isSubmitted && s.status === 'OPEN' && (
                                                                 <div>
                                                                     {activeUploadSessionId === s.id ? (
                                                                         <form onSubmit={(e) => handleInlineSubmit(e, s.id)} style={{ padding: '1rem', background: '#f8fafc', borderRadius: '8px', border: '1px solid var(--card-border)', marginTop: '0.75rem' }}>
                                                                             <div style={{ marginBottom: '1rem', padding: '0.5rem', background: '#f1f5f9', borderRadius: '6px', fontSize: '0.85rem', color: 'var(--text-main)' }}>
                                                                                 <strong>Submitting to:</strong> {selectedModule.moduleName} — {s.title} ({s.sessionName})
                                                                             </div>
                                                                             <div className="form-group" style={{ margin: 0 }}>
                                                                                 <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Choose Assignment File (.pdf, .doc, .docx - max 20MB) *</label>
                                                                                 <div style={{ marginTop: '0.5rem' }}>
                                                                                     <input 
                                                                                         type="file" 
                                                                                         id={`file-input-${s.id}`}
                                                                                         onChange={e => handleInlineFileChange(e, s.id)} 
                                                                                         style={{ display: 'none' }} 
                                                                                         required 
                                                                                     />
                                                                                     <button 
                                                                                         type="button" 
                                                                                         onClick={() => document.getElementById(`file-input-${s.id}`).click()} 
                                                                                         className="inline-submit-btn" 
                                                                                         style={{ width: 'auto', display: 'inline-flex', alignItems: 'center', gap: '0.4rem', background: '#f1f5f9', border: '1px solid var(--card-border)', color: 'var(--text-main)', padding: '0.5rem 1rem', borderRadius: '8px', fontSize: '0.85rem', fontWeight: 'bold' }}
                                                                                     >
                                                                                         Browse File...
                                                                                     </button>
                                                                                     <span style={{ marginLeft: '1rem', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                                                                                         {inlineFile ? inlineFile.name : 'No file selected'}
                                                                                     </span>
                                                                                 </div>
                                                                             </div>
                                                                             {inlineAlerts[s.id] && (
                                                                                 <div style={{ color: inlineAlerts[s.id].type === 'success' ? 'var(--success)' : 'var(--error)', fontSize: '0.8rem', marginTop: '0.5rem', fontWeight: 'bold' }}>
                                                                                     {inlineAlerts[s.id].message}
                                                                                 </div>
                                                                             )}
                                                                             <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', marginTop: '1rem' }}>
                                                                                 <button type="submit" className="submit-btn" style={{ width: 'auto', padding: '0.35rem 1rem', fontSize: '0.85rem', marginTop: 0, fontWeight: 'bold' }} disabled={uploading}>
                                                                                     {uploading ? 'Submitting...' : 'Submit Assignment'}
                                                                                 </button>
                                                                                 <button type="button" onClick={() => { setActiveUploadSessionId(null); setInlineFile(null); }} className="signout-btn" style={{ padding: '0.35rem 1rem', fontSize: '0.85rem', fontWeight: 'bold' }}>
                                                                                     Cancel
                                                                                 </button>
                                                                             </div>
                                                                         </form>
                                                                     ) : (
                                                                         <button 
                                                                             className="inline-submit-btn"
                                                                             onClick={() => { setActiveUploadSessionId(s.id); setInlineAlerts({}); }}
                                                                             style={{ display: 'inline-flex', alignItems: 'center', width: 'auto', gap: '0.25rem', marginTop: '0.5rem', fontWeight: 'bold' }}
                                                                         >
                                                                             Submit Assignment
                                                                         </button>
                                                                     )}
                                                                 </div>
                                                             )}
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* TAB 3: My Submissions */}
            {activeTab === 'history' && (
                <div className="tab-content active">
                    <div className="portal-card">
                        <div className="card-header-row">
                            <h2>My Submission History</h2>
                            <p>View all files you have previously uploaded and check grading status.</p>
                        </div>
                        <div className="submissions-list">
                            {history.length === 0 ? (
                                <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '2rem 0' }}>You haven't uploaded any assignments yet.</p>
                            ) : (
                                history.map((sub) => (
                                    <div key={sub.submissionId} className="submission-item" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', alignItems: 'stretch' }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                            <div className="submission-info">
                                                <span className="submission-title">{sub.moduleName || 'General'} — {sub.assignmentTitle || 'Assignment'} ({sub.sessionName})</span>
                                                <span className="submission-file" style={{ color: 'var(--text-body)' }}>{sub.originalFilename}</span>
                                                <span className="submission-date">Submitted: {new Date(sub.submittedAt).toLocaleString()}</span>
                                            </div>
                                            <span className={`status-badge ${sub.status ? sub.status.toLowerCase() : 'submitted'}`}>{sub.status || 'SUBMITTED'}</span>
                                        </div>

                                        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginTop: '0.25rem' }}>
                                            <a 
                                                href={`/api/submissions/${sub.submissionId}/download?learnerCode=${studentNumber}`} 
                                                className="inline-submit-btn" 
                                                style={{ width: 'auto', padding: '0.35rem 0.75rem', fontSize: '0.8rem', background: '#f1f5f9', border: '1px solid var(--card-border)', textDecoration: 'none', color: 'var(--text-main)', display: 'inline-flex', alignItems: 'center', gap: '0.25rem', fontWeight: 'bold' }}
                                                download
                                            >
                                                Download My Submission
                                            </a>
                                            {sub.status === 'GRADED' && sub.gradedFilePath && (
                                                <a 
                                                    href={`/api/submissions/${sub.submissionId}/graded-download?learnerCode=${studentNumber}`} 
                                                    className="inline-submit-btn" 
                                                    style={{ width: 'auto', padding: '0.35rem 0.75rem', fontSize: '0.8rem', background: '#ecfdf5', border: '1px solid rgba(16, 185, 129, 0.25)', textDecoration: 'none', color: 'var(--success)', display: 'inline-flex', alignItems: 'center', gap: '0.25rem', fontWeight: 'bold' }}
                                                    download
                                                >
                                                    Download Marked Work
                                                </a>
                                            )}
                                        </div>

                                        {sub.status === 'GRADED' ? (
                                            <div style={{ background: '#ecfdf5', border: '1px solid rgba(16, 185, 129, 0.15)', borderRadius: '8px', padding: '0.85rem', marginTop: '0.25rem' }}>
                                                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.25rem' }}>
                                                    <strong>Grade: <span style={{ color: 'var(--success)' }}>{sub.grade}%</span></strong>
                                                </div>
                                                <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--text-body)', fontStyle: sub.feedback ? 'normal' : 'italic' }}>
                                                    {sub.feedback ? `Feedback: "${sub.feedback}"` : 'No feedback comment provided.'}
                                                </p>
                                            </div>
                                        ) : (
                                            <div style={{ background: '#f8fafc', border: '1px solid var(--card-border)', borderRadius: '8px', padding: '0.65rem', marginTop: '0.25rem', fontSize: '0.825rem', color: 'var(--text-muted)', fontWeight: 'bold' }}>
                                                Not yet graded.
                                            </div>
                                        )}
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
        </div>
    );
};

export default StudentPortal;
