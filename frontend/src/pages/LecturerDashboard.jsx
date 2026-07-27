import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const LecturerDashboard = () => {
    const navigate = useNavigate();
    const token = sessionStorage.getItem('lecturer_auth');

    // Auth guard
    useEffect(() => {
        if (!token) {
            navigate('/lecturer-login');
        }
    }, [token, navigate]);

    if (!token) return null;

    // UI state
    const [modules, setModules] = useState([]);
    const [sessions, setSessions] = useState([]);
    const [assignments, setAssignments] = useState([]);
    const [categories, setCategories] = useState([]);
    const [inspectedSubmissions, setInspectedSubmissions] = useState([]);
    const [activeSessionId, setActiveSessionId] = useState('');
    const [alert, setAlert] = useState({ type: '', message: '' });

    // Module Creation form
    const [moduleName, setModuleName] = useState('');
    const [moduleCode, setModuleCode] = useState('');
    const [categoryId, setCategoryId] = useState('');

    // Syllabus file upload
    const [uploadingModuleId, setUploadingModuleId] = useState(null);
    const [uploadFile, setUploadFile] = useState(null);
    const [uploadFileType, setUploadFileType] = useState('');
    const [uploadFileTitle, setUploadFileTitle] = useState('');

    // Session form
    const [sessionName, setSessionName] = useState('');
    const [assignmentId, setAssignmentId] = useState('');
    const [selectedModuleId, setSelectedModuleId] = useState('');
    const [sessionDescription, setSessionDescription] = useState('');
    const [sessionTaskFile, setSessionTaskFile] = useState(null);
    const [startTime, setStartTime] = useState('');
    const [endTime, setEndTime] = useState('');

    // Grading state
    const [gradingSubId, setGradingSubId] = useState(null);
    const [editGrade, setEditGrade] = useState('');
    const [editFeedback, setEditFeedback] = useState('');
    const [markedFile, setMarkedFile] = useState(null);
    const [profile, setProfile] = useState(null);

    useEffect(() => {
        if (token) {
            fetchModules();
            fetchSessions();
            fetchAssignments();
            fetchCategories();
            fetchProfile();
        }
    }, [token]);

    const showMsg = (type, message) => {
        setAlert({ type, message });
        setTimeout(() => setAlert({ type: '', message: '' }), 5000);
    };

    const checkAuthResponse = (res) => {
        if (res.status === 401) {
            sessionStorage.removeItem('lecturer_auth');
            navigate('/lecturer-login');
            return false;
        }
        return true;
    };

    const downloadSubmissionFile = async (e, submissionId, filename, isGraded = false) => {
        e.preventDefault();
        const endpoint = isGraded ? 'graded-download' : 'download';
        try {
            const res = await fetch(`/api/submissions/${submissionId}/${endpoint}`, {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const blob = await res.blob();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = filename;
                document.body.appendChild(a);
                a.click();
                a.remove();
                window.URL.revokeObjectURL(url);
            } else {
                showMsg('error', 'Failed to download the submission file.');
            }
        } catch (err) {
            showMsg('error', 'Connection issue during download.');
        }
    };

    const fetchProfile = async () => {
        try {
            const res = await fetch('/api/lecturer/profile', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setProfile(data);
            }
        } catch (e) {
            console.error('Failed to fetch lecturer profile', e);
        }
    };

    const fetchCategories = async () => {
        try {
            const res = await fetch('/api/lecturer/categories', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setCategories(data);
            }
        } catch (e) {
            console.error(e);
        }
    };

    const handleCreateModule = async (e) => {
        e.preventDefault();
        try {
            const res = await fetch('/api/lecturer/modules', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Basic ${token}`
                },
                body: JSON.stringify({
                    moduleName,
                    moduleCode,
                    categoryId: categoryId ? parseInt(categoryId) : null
                })
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                showMsg('success', 'Module created successfully under category!');
                setModuleName('');
                setModuleCode('');
                setCategoryId('');
                fetchModules();
            } else {
                const txt = await res.text();
                showMsg('error', txt || 'Failed to create module.');
            }
        } catch (err) {
            showMsg('error', 'Connection failed.');
        }
    };

    const fetchModules = async () => {
        try {
            const res = await fetch('/api/lecturer/modules', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setModules(data);
            }
        } catch (e) {
            console.error(e);
        }
    };

    const fetchSessions = async () => {
        try {
            const res = await fetch('/api/lecturer/sessions', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setSessions(data);
            }
        } catch (e) {
            console.error(e);
        }
    };

    const fetchAssignments = async () => {
        try {
            const res = await fetch('/api/assignments', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setAssignments(data);
            }
        } catch (e) {
            console.error(e);
        }
    };

    const handleUploadModuleFile = async (e, moduleId) => {
        e.preventDefault();
        if (!uploadFile) return;

        const formData = new FormData();
        formData.append('file', uploadFile);
        formData.append('fileType', uploadFileType);
        formData.append('title', uploadFileTitle);

        try {
            const res = await fetch(`/api/lecturer/modules/${moduleId}/upload`, {
                method: 'POST',
                headers: { 'Authorization': `Basic ${token}` },
                body: formData
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                showMsg('success', `"${uploadFileTitle}" uploaded successfully!`);
                setUploadFile(null);
                setUploadFileType('');
                setUploadFileTitle('');
                setUploadingModuleId(null);
                fetchModules();
            } else {
                showMsg('error', 'File upload rejected.');
            }
        } catch (err) {
            showMsg('error', 'Connection issue during file upload.');
        }
    };

    const handleCreateSession = async (e) => {
        e.preventDefault();
        try {
            const formData = new FormData();
            formData.append('sessionName', sessionName);
            formData.append('moduleId', selectedModuleId);
            // Format datetime local format to ISO-like or parseable format locally
            // Datetime local values are parseable via LocalDateTime.parse if we make sure it has 'T' (which it does, e.g. 2026-07-23T13:52)
            // But LocalDateTime expects seconds as well, or we can just append ':00' if it doesn't have seconds:
            let start = startTime;
            if (start && start.length === 16) start += ':00';
            let end = endTime;
            if (end && end.length === 16) end += ':00';

            formData.append('startTime', start);
            formData.append('endTime', end);
            if (sessionDescription) {
                formData.append('description', sessionDescription);
            }
            if (sessionTaskFile) {
                formData.append('file', sessionTaskFile);
            }

            const res = await fetch('/api/lecturer/sessions', {
                method: 'POST',
                headers: {
                    'Authorization': `Basic ${token}`
                },
                body: formData
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                showMsg('success', 'Intake session window opened successfully!');
                setSessionName('');
                setSelectedModuleId('');
                setSessionDescription('');
                setSessionTaskFile(null);
                setStartTime('');
                setEndTime('');
                const fileInput = document.getElementById('task-file-input');
                if (fileInput) fileInput.value = '';
                fetchSessions();
            } else {
                const txt = await res.text();
                showMsg('error', txt || 'Failed to open submission slot.');
            }
        } catch (err) {
            showMsg('error', 'Connection failed.');
        }
    };

    const toggleSessionClose = async (id, isClosed) => {
        const endpoint = isClosed ? 'open' : 'close';
        try {
            const res = await fetch(`/api/lecturer/sessions/${id}/${endpoint}`, {
                method: 'PUT',
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                showMsg('success', `Session status changed successfully!`);
                fetchSessions();
            } else {
                showMsg('error', 'Failed to toggle session status.');
            }
        } catch (e) {
            showMsg('error', 'Connection issue.');
        }
    };

    const inspectSubmissions = async (sessionId) => {
        setActiveSessionId(sessionId);
        if (!sessionId) {
            setInspectedSubmissions([]);
            return;
        }

        try {
            const res = await fetch(`/api/lecturer/sessions/${sessionId}/submissions`, {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setInspectedSubmissions(data.submitted || []);
            } else {
                showMsg('error', 'Failed to load submissions list.');
            }
        } catch (e) {
            showMsg('error', 'Connection issue.');
        }
    };

    const handleSaveGrade = async (e, submissionId) => {
        e.preventDefault();
        const gradeVal = parseInt(editGrade);
        if (isNaN(gradeVal) || gradeVal < 0 || gradeVal > 100) {
            showMsg('error', 'Grade must be a percentage between 0 and 100.');
            return;
        }

        const formData = new FormData();
        formData.append('grade', gradeVal);
        formData.append('feedback', editFeedback);
        if (markedFile) {
            formData.append('file', markedFile);
        }

        try {
            const res = await fetch(`/api/lecturer/submissions/${submissionId}/grade`, {
                method: 'PUT',
                headers: {
                    'Authorization': `Basic ${token}`
                },
                body: formData
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                showMsg('success', 'Submission marked successfully!');
                setGradingSubId(null);
                setEditGrade('');
                setEditFeedback('');
                setMarkedFile(null);
                inspectSubmissions(activeSessionId);
            } else {
                showMsg('error', 'Failed to save grade.');
            }
        } catch (err) {
            showMsg('error', 'Connection failed.');
        }
    };

    const handleSignout = () => {
        sessionStorage.removeItem('lecturer_auth');
        navigate('/');
    };

    const mySessions = sessions;

    return (
        <div className="portal-wrapper">
            <header className="portal-header">
                <div>
                    <h1 style={{ margin: 0, fontSize: '1.85rem', fontWeight: 800, color: 'var(--text-main)' }}>{profile ? `Welcome, ${profile.fullName}!` : 'Lecturer Portal'}</h1>
                    <p className="subtitle">
                        {profile ? (
                            profile.assignedCategories && profile.assignedCategories.length > 0 ? (
                                `You are assigned to: ${profile.assignedCategories.join(', ')}`
                            ) : (
                                "No category assigned yet — contact your administrator"
                            )
                        ) : (
                            'Manage Course Syllabus Documents & Assignment Submissions'
                        )}
                    </p>
                </div>
                <button onClick={handleSignout} className="signout-btn" style={{ fontWeight: 'bold' }}>Sign Out</button>
            </header>

            {alert.message && (
                <div className={`alert-banner ${alert.type}`} style={{ fontWeight: 'bold' }}>
                    {alert.message}
                </div>
            )}

            {/* Step 1: Create Module Section */}
            <div className="portal-card" style={{ marginBottom: '2rem' }}>
                <h2 style={{ fontSize: '1.3rem', marginBottom: '0.5rem', fontWeight: 'bold' }}>1. Create Module</h2>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1.5rem' }}>Add a new module under one of your assigned categories.</p>
                <form onSubmit={handleCreateModule} className="student-login-form">
                    <div className="grid-form">
                        <div className="form-group">
                            <label>Module Name *</label>
                            <input 
                                type="text" 
                                value={moduleName} 
                                onChange={e => setModuleName(e.target.value)} 
                                className="form-input" 
                                required 
                                placeholder="e.g. System Analysis & Design" 
                            />
                        </div>
                        <div className="form-group">
                            <label>Module Code</label>
                            <input 
                                type="text" 
                                value={moduleCode} 
                                onChange={e => setModuleCode(e.target.value)} 
                                className="form-input" 
                                placeholder="e.g. SAD2026" 
                            />
                        </div>
                        <div className="form-group">
                            <label>Category *</label>
                            <select 
                                value={categoryId} 
                                onChange={e => setCategoryId(e.target.value)} 
                                className="form-input" 
                                required 
                                style={{ background: 'var(--input-bg)', color: 'var(--text-main)' }}
                            >
                                <option value="">-- Choose Category --</option>
                                {categories.map(c => (
                                    <option key={c.id} value={c.id}>{c.categoryType}</option>
                                ))}
                            </select>
                        </div>
                    </div>
                    <button type="submit" className="submit-btn" style={{ width: 'auto', padding: '0.5rem 1.5rem', marginTop: '1rem', fontWeight: 'bold' }}>
                        Create Module
                    </button>
                </form>
            </div>

            {modules.length === 0 ? (
                /* Prominent "Create your first module to get started" welcome message */
                <div className="portal-card" style={{ textAlign: 'center', padding: '3.5rem 1.5rem', marginBottom: '2rem' }}>
                    <h2 style={{ margin: '1rem 0 0.5rem 0', fontSize: '1.4rem', color: 'var(--text-main)', fontWeight: 'bold' }}>Create your first module to get started</h2>
                    <p style={{ color: 'var(--text-muted)', maxWidth: '480px', margin: '0 auto', fontSize: '0.9rem', lineHeight: 1.5 }}>
                        Once you register your first module above under an assigned category, you'll be able to upload learning materials (Syllabus, Slides, Guides) and schedule submission sessions for your students.
                    </p>
                </div>
            ) : (
                <>
                    {/* Step 2: My Assigned Modules */}
                    <div className="portal-card" style={{ marginBottom: '2rem' }}>
                        <h2 style={{ fontSize: '1.3rem', marginBottom: '1rem' }}>2. My Assigned Modules</h2>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
                            {Array.from(new Set(modules.map(m => m.moduleType || 'CORE'))).map(type => {
                                const typeModules = modules.filter(m => (m.moduleType || 'CORE') === type);
                                if (typeModules.length === 0) return null;
                                return (
                                    <div key={type}>
                                        <h3 style={{ fontSize: '1.1rem', marginBottom: '1rem', borderBottom: '1px solid var(--card-border)', paddingBottom: '0.25rem', color: 'var(--primary-color)', fontWeight: 'bold' }}>
                                            {type} Modules
                                        </h3>
                                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.25rem' }}>
                                            {typeModules.map(m => (
                                                <div key={m.id} style={{ background: '#ffffff', border: '1px solid var(--card-border)', borderRadius: '12px', padding: '1.25rem' }}>
                                                    <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '1.1rem', color: 'var(--text-main)', fontWeight: 'bold' }}>{m.moduleName}</h4>
                                                    <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', margin: '0 0 1rem 0' }}>Code: <strong>{m.moduleCode || 'N/A'}</strong></p>
                                                    
                                                     <div style={{ marginBottom: '1.25rem' }}>
                                                        <h5 style={{ fontSize: '0.85rem', margin: '0 0 0.5rem 0', color: 'var(--text-muted)', fontWeight: 'bold' }}>Uploaded Files:</h5>
                                                        {(!m.files || m.files.length === 0) ? (
                                                            <p style={{ fontSize: '0.8rem', color: 'var(--error)', margin: 0, fontStyle: 'italic' }}>No files uploaded yet.</p>
                                                        ) : (
                                                            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                                                                {m.files.map(file => (
                                                                    <div key={file.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#f8fafc', padding: '0.4rem 0.6rem', borderRadius: '6px', fontSize: '0.8rem', border: '1px solid var(--card-border)' }}>
                                                                        <span style={{ color: 'var(--text-body)' }}><strong>{file.title || 'Untitled'}</strong> ({file.fileType}): <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>{file.originalFilename}</span></span>
                                                                        <a href={file.filePath} style={{ color: 'var(--primary-color)', textDecoration: 'none', fontWeight: 'bold' }} download>Download</a>
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        )}
                                                    </div>
 
                                                     {uploadingModuleId === m.id ? (
                                                        <form onSubmit={(e) => handleUploadModuleFile(e, m.id)} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', padding: '0.75rem', background: '#f8fafc', borderRadius: '8px', border: '1px solid var(--card-border)' }}>
                                                            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                                                                <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', width: '60px' }}>Title *:</label>
                                                                <input 
                                                                    type="text" 
                                                                    value={uploadFileTitle} 
                                                                    onChange={e => setUploadFileTitle(e.target.value)} 
                                                                    placeholder="e.g. Week 3 Lecture Slides" 
                                                                    className="form-input" 
                                                                    style={{ padding: '0.25rem', fontSize: '0.8rem', flex: 1, margin: 0, background: 'var(--input-bg)', color: 'var(--text-main)' }} 
                                                                    required 
                                                                />
                                                            </div>
                                                            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                                                                <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', width: '60px' }}>Category *:</label>
                                                                <input 
                                                                    type="text" 
                                                                    value={uploadFileType} 
                                                                    onChange={e => setUploadFileType(e.target.value)} 
                                                                    placeholder="e.g. Lecture Slides, Notes" 
                                                                    className="form-input" 
                                                                    style={{ padding: '0.25rem', fontSize: '0.8rem', flex: 1, margin: 0, background: 'var(--input-bg)', color: 'var(--text-main)' }} 
                                                                    required 
                                                                />
                                                            </div>
                                                            <input type="file" onChange={e => setUploadFile(e.target.files[0])} style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }} required />
                                                            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                                                                <button type="submit" className="submit-btn" style={{ width: 'auto', padding: '0.25rem 0.75rem', fontSize: '0.8rem', marginTop: 0, fontWeight: 'bold' }}>Upload File</button>
                                                                <button type="button" onClick={() => { setUploadingModuleId(null); setUploadFile(null); setUploadFileTitle(''); }} className="signout-btn" style={{ padding: '0.25rem 0.75rem', fontSize: '0.8rem', fontWeight: 'bold' }}>Cancel</button>
                                                            </div>
                                                        </form>
                                                    ) : (
                                                        <button 
                                                            onClick={() => { setUploadingModuleId(m.id); setUploadFileType(''); setUploadFileTitle(''); }} 
                                                            className="submit-btn" 
                                                            style={{ width: '100%', padding: '0.35rem 0.75rem', fontSize: '0.8rem', marginTop: 0, fontWeight: 'bold' }}
                                                        >
                                                            Upload Module File
                                                        </button>
                                                    )}
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
 
                     {/* Step 3: Schedule Intake Session */}
                    <div className="portal-card" style={{ marginBottom: '2rem' }}>
                        <h2 style={{ fontSize: '1.3rem', marginBottom: '0.5rem', fontWeight: 'bold' }}>3. Schedule Intake Session</h2>
                        <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1.5rem' }}>Open a timing slot where students can upload files for their module assignments.</p>
                        <form onSubmit={handleCreateSession} className="student-login-form">
                            <div className="grid-form">
                                <div className="form-group">
                                    <label>Session Name *</label>
                                    <input type="text" value={sessionName} onChange={e => setSessionName(e.target.value)} className="form-input" required placeholder="e.g. Research Methodology - Java OOP Assignment" />
                                </div>
                                <div className="form-group">
                                    <label>Which of your modules is this for? *</label>
                                    <select value={selectedModuleId} onChange={e => setSelectedModuleId(e.target.value)} className="form-input" required style={{ background: 'var(--input-bg)', color: 'var(--text-main)' }}>
                                        <option value="">-- Choose Module --</option>
                                        {modules.map(m => (
                                            <option key={m.id} value={m.id}>{m.moduleName} ({m.moduleCode || 'N/A'})</option>
                                        ))}
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label>Description / Instructions</label>
                                    <textarea 
                                        value={sessionDescription} 
                                        onChange={e => setSessionDescription(e.target.value)} 
                                        className="form-input" 
                                        placeholder="Instructions for students..."
                                        style={{ background: 'var(--input-bg)', color: 'var(--text-main)', minHeight: '80px', resize: 'vertical' }}
                                    />
                                </div>
                                <div className="form-group">
                                    <label>Task File Attachment (Optional)</label>
                                    <input 
                                        id="task-file-input"
                                        type="file" 
                                        onChange={e => setSessionTaskFile(e.target.files[0])} 
                                        style={{ fontSize: '0.9rem', color: 'var(--text-muted)' }} 
                                    />
                                </div>
                                <div className="form-group">
                                    <label>Opens *</label>
                                    <input type="datetime-local" value={startTime} onChange={e => setStartTime(e.target.value)} className="form-input" required />
                                </div>
                                <div className="form-group">
                                    <label>Closes *</label>
                                    <input type="datetime-local" value={endTime} onChange={e => setEndTime(e.target.value)} className="form-input" required />
                                </div>
                            </div>
                            <button type="submit" className="submit-btn" style={{ width: 'auto', padding: '0.75rem 2rem', fontWeight: 'bold' }}>Open Session Window</button>
                        </form>
                    </div>

                    {/* Step 4: Active Intake Windows */}
                    <div className="portal-card" style={{ marginBottom: '2rem' }}>
                        <h2 style={{ fontSize: '1.3rem', marginBottom: '1rem', fontWeight: 'bold' }}>4. Active Intake Windows</h2>
                        <div style={{ overflowX: 'auto' }}>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
                                <thead>
                                    <tr style={{ borderBottom: '1px solid var(--card-border)', textTransform: 'uppercase', color: 'var(--text-muted)' }}>
                                        <th style={{ padding: '0.75rem', textAlign: 'left' }}>Session Name</th>
                                        <th style={{ padding: '0.75rem', textAlign: 'left' }}>Assignment</th>
                                        <th style={{ padding: '0.75rem', textAlign: 'left' }}>Status</th>
                                        <th style={{ padding: '0.75rem', textAlign: 'left' }}>Deadline</th>
                                        <th style={{ padding: '0.75rem', textAlign: 'left' }}>Actions</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {mySessions.length === 0 ? (
                                        <tr>
                                            <td colSpan="5" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>No submission sessions mapped to your modules.</td>
                                        </tr>
                                    ) : (
                                        mySessions.map(s => (
                                            <tr key={s.id} style={{ borderBottom: '1px solid var(--card-border)' }}>
                                                <td style={{ padding: '0.75rem' }}><strong>{s.sessionName}</strong></td>
                                                <td style={{ padding: '0.75rem' }}>{s.assignmentTitle}</td>
                                                <td style={{ padding: '0.75rem' }}>
                                                    <span className={`status-badge ${s.status.toLowerCase()}`}>{s.status}</span>
                                                </td>
                                                <td style={{ padding: '0.75rem' }}>{new Date(s.endTime).toLocaleString()}</td>
                                                <td style={{ padding: '0.75rem', display: 'flex', gap: '0.75rem' }}>
                                                    <button 
                                                        onClick={() => toggleSessionClose(s.id, s.status === 'CLOSED')} 
                                                        className="btn" 
                                                        style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem', background: s.status === 'CLOSED' ? 'var(--success)' : 'var(--error)', fontWeight: 'bold' }}
                                                    >
                                                        {s.status === 'CLOSED' ? 'Open' : 'Close'}
                                                    </button>
                                                    <button 
                                                        onClick={() => inspectSubmissions(s.id)} 
                                                        className="btn" 
                                                        style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem', background: '#f1f5f9', border: '1px solid var(--card-border)', color: 'var(--text-main)', fontWeight: 'bold' }}
                                                    >
                                                        Inspect Files
                                                    </button>
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </>
            )}

            {/* Submission file list overlay/section */}
            {activeSessionId && (
                <div className="portal-card" style={{ marginBottom: '2rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--card-border)', paddingBottom: '0.5rem', marginBottom: '1rem' }}>
                        <h2 style={{ fontSize: '1.3rem', fontWeight: 'bold' }}>Student Submissions Audit</h2>
                        <button onClick={() => inspectSubmissions('')} className="signout-btn" style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem', fontWeight: 'bold' }}>Close Audit</button>
                    </div>

                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
                            <thead>
                                <tr style={{ borderBottom: '1px solid var(--card-border)', textTransform: 'uppercase', color: 'var(--text-muted)' }}>
                                    <th style={{ padding: '0.75rem', textAlign: 'left' }}>Student Name</th>
                                    <th style={{ padding: '0.75rem', textAlign: 'left' }}>Student Number</th>
                                    <th style={{ padding: '0.75rem', textAlign: 'left' }}>File Name</th>
                                    <th style={{ padding: '0.75rem', textAlign: 'left' }}>Uploaded At</th>
                                    <th style={{ padding: '0.75rem', textAlign: 'left' }}>Status</th>
                                    <th style={{ padding: '0.75rem', textAlign: 'left' }}>Grade & Feedback</th>
                                    <th style={{ padding: '0.75rem', textAlign: 'left' }}>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {inspectedSubmissions.length === 0 ? (
                                    <tr>
                                        <td colSpan="7" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>No student files submitted for this session yet.</td>
                                    </tr>
                                ) : (
                                    inspectedSubmissions.map(sub => (
                                        <React.Fragment key={sub.submissionId}>
                                            <tr style={{ borderBottom: '1px solid var(--card-border)' }}>
                                                <td style={{ padding: '0.75rem' }}><strong>{sub.fullName || sub.learnerName || 'Student'}</strong></td>
                                                <td style={{ padding: '0.75rem' }}>{sub.learnerCode || 'N/A'}</td>
                                                <td style={{ padding: '0.75rem', color: 'var(--primary-color)', fontWeight: 'bold' }}>{sub.originalFilename}</td>
                                                <td style={{ padding: '0.75rem' }}>{new Date(sub.submittedAt).toLocaleString()}</td>
                                                <td style={{ padding: '0.75rem' }}>
                                                    <span className={`status-badge ${sub.status ? sub.status.toLowerCase() : 'submitted'}`}>
                                                        {sub.status || 'SUBMITTED'}
                                                    </span>
                                                </td>
                                                <td style={{ padding: '0.75rem' }}>
                                                    {sub.status === 'GRADED' ? (
                                                        <div>
                                                            <div style={{ fontWeight: 'bold', color: 'var(--success)' }}>Grade: {sub.grade}%</div>
                                                            <div style={{ fontSize: '0.8rem', color: 'var(--text-body)' }}>{sub.feedback || 'No feedback provided.'}</div>
                                                            {sub.gradedFilePath && (
                                                                <div style={{ marginTop: '0.25rem', fontSize: '0.8rem' }}>
                                                                    <a 
                                                                        href="#" 
                                                                        onClick={(e) => downloadSubmissionFile(e, sub.submissionId, sub.gradedOriginalFilename, true)} 
                                                                        style={{ color: 'var(--primary-color)', textDecoration: 'none', fontWeight: 'bold' }}
                                                                    >
                                                                        Graded File: {sub.gradedOriginalFilename}
                                                                    </a>
                                                                </div>
                                                            )}
                                                        </div>
                                                    ) : (
                                                        <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>Pending Grade</span>
                                                    )}
                                                </td>
                                                <td style={{ padding: '0.75rem', display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                                                     <a 
                                                         href="#" 
                                                         onClick={(e) => downloadSubmissionFile(e, sub.submissionId, sub.originalFilename, false)} 
                                                         style={{ color: 'var(--success)', textDecoration: 'none', fontWeight: 'bold', fontSize: '0.85rem' }}
                                                     >
                                                         Download
                                                     </a>
                                                    {gradingSubId !== sub.submissionId && (
                                                        <button 
                                                            onClick={() => {
                                                                setGradingSubId(sub.submissionId);
                                                                setEditGrade(sub.grade !== null && sub.grade !== undefined ? sub.grade.toString() : '');
                                                                setEditFeedback(sub.feedback || '');
                                                            }}
                                                            className="submit-btn"
                                                            style={{ width: 'auto', padding: '0.25rem 0.5rem', fontSize: '0.75rem', marginTop: 0, fontWeight: 'bold' }}
                                                        >
                                                            {sub.status === 'GRADED' ? 'Edit' : 'Grade'}
                                                        </button>
                                                    )}
                                                </td>
                                            </tr>
                                            {gradingSubId === sub.submissionId && (
                                                <tr style={{ background: '#f8fafc' }}>
                                                    <td colSpan="7" style={{ padding: '1rem' }}>
                                                        <form onSubmit={(e) => handleSaveGrade(e, sub.submissionId)} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                                                            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                                                                <div className="form-group" style={{ margin: 0, width: '120px' }}>
                                                                    <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Grade (0-100) *</label>
                                                                    <input 
                                                                        type="number" 
                                                                        value={editGrade} 
                                                                        onChange={e => setEditGrade(e.target.value)} 
                                                                        className="form-input" 
                                                                        style={{ padding: '0.35rem' }}
                                                                        min="0" 
                                                                        max="100" 
                                                                        required 
                                                                    />
                                                                </div>
                                                                <div className="form-group" style={{ margin: 0, flex: 1 }}>
                                                                    <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Feedback Comments</label>
                                                                    <input 
                                                                        type="text" 
                                                                        value={editFeedback} 
                                                                        onChange={e => setEditFeedback(e.target.value)} 
                                                                        className="form-input" 
                                                                        style={{ padding: '0.35rem' }} 
                                                                        placeholder="Add comments for the learner..." 
                                                                    />
                                                                </div>
                                                                <div className="form-group" style={{ margin: 0, width: '220px' }}>
                                                                    <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Returned File (optional)</label>
                                                                    <input 
                                                                        type="file" 
                                                                        onChange={e => setMarkedFile(e.target.files[0])} 
                                                                        style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }} 
                                                                    />
                                                                </div>
                                                            </div>
                                                            <div style={{ display: 'flex', gap: '0.5rem', alignSelf: 'flex-end' }}>
                                                                <button type="submit" className="submit-btn" style={{ width: 'auto', padding: '0.35rem 1rem', fontSize: '0.8rem', marginTop: 0 }}>Save Grade</button>
                                                                <button type="button" onClick={() => setGradingSubId(null)} className="signout-btn" style={{ padding: '0.35rem 1rem', fontSize: '0.8rem' }}>Cancel</button>
                                                            </div>
                                                        </form>
                                                    </td>
                                                </tr>
                                            )}
                                        </React.Fragment>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
};

export default LecturerDashboard;
