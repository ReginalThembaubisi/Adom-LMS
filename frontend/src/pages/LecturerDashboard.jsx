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

    // Module Edit form
    const [editingModuleId, setEditingModuleId] = useState(null);
    const [editModuleName, setEditModuleName] = useState('');
    const [editModuleCode, setEditModuleCode] = useState('');
    const [editCategoryId, setEditCategoryId] = useState('');

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
    const [editingProfile, setEditingProfile] = useState(null);

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
        const endpoint = isGraded ? 'graded-download-url' : 'download-url';
        try {
            const res = await fetch(`/api/submissions/${submissionId}/${endpoint}`, {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                const downloadUrl = data.url;

                if (downloadUrl.startsWith('http://') || downloadUrl.startsWith('https://')) {
                    // Open Cloudinary cloud URL directly in a new tab to bypass fetch CORS/Credentials limits
                    window.open(downloadUrl, '_blank');
                } else {
                    // For local files, fetch the stream binary with Authorization header
                    const fileRes = await fetch(downloadUrl, {
                        headers: { 'Authorization': `Basic ${token}` }
                    });
                    if (fileRes.ok) {
                        const blob = await fileRes.blob();
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
                }
            } else {
                showMsg('error', 'Failed to retrieve download link.');
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

    const startEditModule = (m) => {
        setEditingModuleId(m.id);
        setEditModuleName(m.moduleName);
        setEditModuleCode(m.moduleCode || '');
        const cat = categories.find(c => c.categoryType === m.moduleType);
        setEditCategoryId(cat ? cat.id.toString() : '');
    };

    const handleUpdateModule = async (e, moduleId) => {
        e.preventDefault();
        if (!editModuleName.trim()) return;
        try {
            const res = await fetch(`/api/lecturer/modules/${moduleId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Basic ${token}`
                },
                body: JSON.stringify({
                    moduleName: editModuleName,
                    moduleCode: editModuleCode,
                    categoryId: editCategoryId ? parseInt(editCategoryId) : null
                })
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                showMsg('success', 'Module updated successfully!');
                setEditingModuleId(null);
                fetchModules();
            } else {
                const text = await res.text();
                showMsg('error', text || 'Failed to update module.');
            }
        } catch (err) {
            showMsg('error', 'Connection error.');
        }
    };

    const handleDeleteModule = async (moduleId) => {
        if (!window.confirm("Are you sure you want to delete this module? This will also delete all files uploaded to it and cannot be undone.")) return;
        try {
            const res = await fetch(`/api/lecturer/modules/${moduleId}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                showMsg('success', 'Module deleted successfully!');
                fetchModules();
            } else {
                const text = await res.text();
                showMsg('error', text || 'Failed to delete module.');
            }
        } catch (err) {
            showMsg('error', 'Connection error.');
        }
    };

    const handleDeleteFile = async (fileId) => {
        if (!window.confirm("Are you sure you want to delete this file? This action cannot be undone.")) return;
        try {
            const res = await fetch(`/api/lecturer/files/${fileId}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                showMsg('success', 'File deleted successfully!');
                fetchModules();
            } else {
                showMsg('error', 'Failed to delete file.');
            }
        } catch (err) {
            showMsg('error', 'Connection error.');
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

        if (uploadFile.size > 20 * 1024 * 1024) {
            showMsg('error', 'File size exceeds the 20MB limit. Please choose a smaller file.');
            return;
        }

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
        if (sessionTaskFile && sessionTaskFile.size > 20 * 1024 * 1024) {
            showMsg('error', 'Brief file size exceeds the 20MB limit. Please choose a smaller file.');
            return;
        }
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

    const deleteSession = async (id) => {
        if (!window.confirm("Are you sure you want to delete this submission session? This will also delete ALL student submissions linked to it. This action cannot be undone.")) {
            return;
        }
        try {
            const res = await fetch(`/api/lecturer/sessions/${id}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                showMsg('success', 'Session and submissions deleted successfully!');
                fetchSessions();
                if (activeSessionId === id) {
                    inspectSubmissions('');
                }
            } else {
                const txt = await res.text();
                showMsg('error', txt || 'Failed to delete session.');
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
        <div className="bg-slate-50/80 min-h-screen text-slate-800 antialiased py-8 space-y-8">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8">
                {/* Header */}
                <header className="flex justify-between items-center pb-6 border-b border-slate-300 bg-linear-to-r from-slate-900 to-indigo-950 text-white p-6 rounded-2xl shadow-md mb-6">
                    <div>
                        <h1 className="text-2xl font-bold tracking-tight">{profile ? `Welcome, ${profile.fullName}! 👋` : 'Facilitator Portal'}</h1>
                        <p className="text-sm text-slate-300">
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
                    <div className="flex gap-2">
                        <button 
                            onClick={() => setEditingProfile({ fullName: profile?.fullName || '', email: profile?.email || '', password: '' })}
                            className="border border-white/20 bg-white/10 px-4 py-2 rounded-xl text-xs font-semibold hover:bg-white/20 hover:scale-[0.99] text-white transition-all shadow-xs"
                        >
                            Edit Profile
                        </button>
                        <button onClick={handleSignout} className="border border-white/20 bg-white/10 px-4 py-2 rounded-xl text-xs font-semibold hover:bg-white/20 hover:scale-[0.99] text-white transition-all shadow-xs">
                            Sign Out
                        </button>
                    </div>
                </header>

                {alert.message && (
                    <div className={`p-4 rounded-xl text-xs font-semibold shadow-xs border ${
                        alert.type === 'error' 
                            ? 'bg-rose-50 border-rose-200 text-rose-800' 
                            : 'bg-blue-50 border-blue-200 text-blue-800'
                    }`}>
                        {alert.message}
                    </div>
                )}

                    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
                        {/* Left Column: Action Forms (lg:col-span-7) */}
                        <div className="lg:col-span-7 flex flex-col gap-6">
                            {/* Step 1: Create Module Section */}
                            <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 flex flex-col space-y-4">
                                <div>
                                    <h2 className="text-lg font-bold text-slate-900">1. Create Module</h2>
                                    <p className="text-xs text-slate-500">Add a new module under one of your assigned categories.</p>
                                </div>
                                <form onSubmit={handleCreateModule} className="space-y-4">
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <div className="space-y-1.5">
                                            <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Module Name *</label>
                                            <input 
                                                type="text" 
                                                value={moduleName} 
                                                onChange={e => setModuleName(e.target.value)} 
                                                className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                                required 
                                                placeholder="e.g. System Analysis & Design" 
                                            />
                                        </div>
                                        <div className="space-y-1.5">
                                            <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Module Code</label>
                                            <input 
                                                type="text" 
                                                value={moduleCode} 
                                                onChange={e => setModuleCode(e.target.value)} 
                                                className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                                placeholder="e.g. SAD2026" 
                                            />
                                        </div>
                                    </div>
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Category *</label>
                                        <select 
                                            value={categoryId} 
                                            onChange={e => setCategoryId(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                            required
                                        >
                                            <option value="">-- Choose Category --</option>
                                            {categories.map(c => (
                                                <option key={c.id} value={c.id}>{c.categoryType}</option>
                                            ))}
                                        </select>
                                    </div>
                                    <button type="submit" className="bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-xs py-2.5 px-5 rounded-xl shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150">
                                        Create Module
                                    </button>
                                </form>
                            </div>

                            {/* Step 3: Schedule Intake Session */}
                            <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 flex flex-col space-y-4">
                                <div>
                                    <h2 className="text-lg font-bold text-slate-900">3. Schedule Intake Session</h2>
                                    <p className="text-xs text-slate-500">Open a timing slot where students can upload files for their module assignments.</p>
                                </div>
                                {modules.length === 0 ? (
                                    <div className="bg-slate-50/50 p-5 rounded-xl border border-slate-200/60 text-center py-8 text-xs text-slate-500 font-medium">
                                        Create a module first using the form above to schedule intake sessions.
                                    </div>
                                ) : (
                                    <form onSubmit={handleCreateSession} className="bg-slate-50/50 p-5 rounded-xl border border-slate-200/60 space-y-4">
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Session Name *</label>
                                        <input 
                                            type="text" 
                                            value={sessionName} 
                                            onChange={e => setSessionName(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                            required 
                                            placeholder="e.g. Research Methodology - Java OOP Assignment" 
                                        />
                                    </div>
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Which of your modules is this for? *</label>
                                        <select 
                                            value={selectedModuleId} 
                                            onChange={e => setSelectedModuleId(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                            required
                                        >
                                            <option value="">-- Choose Module --</option>
                                            {modules.map(m => (
                                                <option key={m.id} value={m.id}>{m.moduleName} ({m.moduleCode || 'N/A'})</option>
                                            ))}
                                        </select>
                                    </div>
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Description / Instructions</label>
                                        <textarea 
                                            value={sessionDescription} 
                                            onChange={e => setSessionDescription(e.target.value)} 
                                            className="w-full bg-white border border-slate-300 rounded-lg p-2 text-xs text-slate-800 outline-hidden focus:border-blue-500 transition-colors min-h-[80px] resize-y" 
                                            placeholder="Instructions for students..."
                                        />
                                    </div>
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Task File Attachment (Optional)</label>
                                        <div 
                                            onClick={() => document.getElementById('task-file-input').click()}
                                            className="border-2 border-dashed border-slate-300 hover:border-blue-500 rounded-lg p-4 text-center cursor-pointer bg-white hover:bg-slate-50 transition-all flex flex-col items-center justify-center gap-1"
                                        >
                                            <span className="text-xs font-bold text-slate-500">
                                                {sessionTaskFile ? sessionTaskFile.name : 'Select or drop a Brief File (Optional)'}
                                            </span>
                                            <span className="text-[10px] text-slate-400">Click to browse files</span>
                                            <input 
                                                id="task-file-input"
                                                type="file" 
                                                onChange={e => setSessionTaskFile(e.target.files[0])} 
                                                className="hidden"
                                            />
                                        </div>
                                    </div>
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <div className="space-y-1.5">
                                            <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Opens *</label>
                                            <input 
                                                type="datetime-local" 
                                                value={startTime} 
                                                onChange={e => setStartTime(e.target.value)} 
                                                className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                                required 
                                            />
                                        </div>
                                        <div className="space-y-1.5">
                                            <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Closes *</label>
                                            <input 
                                                type="datetime-local" 
                                                value={endTime} 
                                                onChange={e => setEndTime(e.target.value)} 
                                                className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                                required 
                                            />
                                        </div>
                                    </div>
                                    
                                    {/* Action Bar */}
                                    <div className="border-t border-slate-200 pt-4 mt-4 flex justify-end">
                                        <button type="submit" className="text-xs font-bold text-white bg-blue-600 hover:bg-blue-700 px-4 py-2.5 rounded-lg shadow-xs transition-colors">
                                            Open Session Window
                                        </button>
                                    </div>
                                </form>
                                )}
                            </div>
                        </div>

                        {/* Right Column: Active Data & Management (lg:col-span-5) */}
                        <div className="lg:col-span-5 flex flex-col gap-6">
                            {/* Step 2: My Assigned Modules */}
                            <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 flex flex-col space-y-4">
                                <div>
                                    <h2 className="text-lg font-bold text-slate-900">2. My Assigned Modules</h2>
                                    <p className="text-xs text-slate-500">Manage course documents for each module.</p>
                                </div>
                                <div className="space-y-6">
                                    {modules.length === 0 ? (
                                        <p className="text-xs text-slate-500 text-center py-6 font-medium">You have no modules created yet.</p>
                                    ) : Array.from(new Set(modules.map(m => m.moduleType || 'CORE'))).map(type => {
                                        const typeModules = modules.filter(m => (m.moduleType || 'CORE') === type);
                                        if (typeModules.length === 0) return null;
                                        return (
                                            <div key={type} className="space-y-4">
                                                <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider border-b border-slate-300 pb-2">
                                                    {type} Modules
                                                </h3>
                                                <div className="space-y-4">
                                                    {typeModules.map(m => (
                                                        <div key={m.id} className="bg-slate-50/50 border border-slate-300 rounded-xl p-4 space-y-3">
                                                            {editingModuleId === m.id ? (
                                                                <form onSubmit={(e) => handleUpdateModule(e, m.id)} className="space-y-3 p-3 bg-white border border-slate-200 rounded-xl w-full">
                                                                    <div className="space-y-2">
                                                                        <div className="space-y-1">
                                                                            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Module Name *</label>
                                                                            <input
                                                                                type="text"
                                                                                value={editModuleName}
                                                                                onChange={e => setEditModuleName(e.target.value)}
                                                                                className="w-full bg-white border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-900 focus:outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-500/10 transition-all"
                                                                                required
                                                                            />
                                                                        </div>
                                                                        <div className="space-y-1">
                                                                            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Module Code</label>
                                                                            <input
                                                                                type="text"
                                                                                value={editModuleCode}
                                                                                onChange={e => setEditModuleCode(e.target.value)}
                                                                                className="w-full bg-white border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-900 focus:outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-500/10 transition-all"
                                                                            />
                                                                        </div>
                                                                        <div className="space-y-1">
                                                                            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Category *</label>
                                                                            <select
                                                                                value={editCategoryId}
                                                                                onChange={e => setEditCategoryId(e.target.value)}
                                                                                className="w-full bg-white border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-900 focus:outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-500/10 transition-all"
                                                                                required
                                                                            >
                                                                                <option value="">-- Choose Category --</option>
                                                                                {categories.map(c => (
                                                                                    <option key={c.id} value={c.id}>{c.categoryType}</option>
                                                                                ))}
                                                                            </select>
                                                                        </div>
                                                                    </div>
                                                                    <div className="flex gap-1.5 justify-end">
                                                                        <button type="submit" className="text-[10px] font-bold text-white bg-blue-600 hover:bg-blue-700 px-3 py-1.5 rounded-lg shadow-xs transition-colors">Save</button>
                                                                        <button type="button" onClick={() => setEditingModuleId(null)} className="text-[10px] font-bold text-slate-500 bg-white border border-slate-300 hover:bg-slate-50 px-3 py-1.5 rounded-lg transition-colors">Cancel</button>
                                                                    </div>
                                                                </form>
                                                            ) : (
                                                                <>
                                                                    <div className="flex justify-between items-start gap-2">
                                                                        <div className="space-y-1 pr-2">
                                                                            <h4 className="font-bold text-slate-800 text-sm leading-snug line-clamp-2" title={m.moduleName}>{m.moduleName}</h4>
                                                                            <span className="bg-blue-50 text-blue-700 font-bold px-2 py-0.5 rounded text-[10px] inline-block">
                                                                                Code: {m.moduleCode || 'N/A'}
                                                                            </span>
                                                                        </div>
                                                                        <div className="flex gap-1 flex-shrink-0">
                                                                            <button 
                                                                                onClick={() => startEditModule(m)} 
                                                                                className="text-[10px] font-bold text-blue-600 hover:text-blue-800 bg-white border border-slate-300 px-2 py-1 rounded shadow-2xs hover:bg-slate-50 transition-all"
                                                                            >
                                                                                Edit
                                                                            </button>
                                                                            <button 
                                                                                onClick={() => handleDeleteModule(m.id)} 
                                                                                className="text-[10px] font-bold text-rose-600 hover:text-rose-800 bg-white border border-slate-300 px-2 py-1 rounded shadow-2xs hover:bg-slate-50 transition-all"
                                                                            >
                                                                                Delete
                                                                            </button>
                                                                        </div>
                                                                    </div>
                                                                    
                                                                    <div className="space-y-1.5">
                                                                        <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Uploaded Files:</h5>
                                                                        {(!m.files || m.files.length === 0) ? (
                                                                            <p className="text-xs text-rose-500 font-medium italic">No files uploaded yet.</p>
                                                                        ) : (
                                                                            <div className="space-y-1.5">
                                                                                {m.files.map(file => (
                                                                                    <div key={file.id} className="flex justify-between items-center bg-white border border-slate-300 p-2.5 rounded-lg text-xs hover:shadow-2xs transition-shadow">
                                                                                        <div className="space-y-0.5 pr-2 truncate">
                                                                                            <span className="font-semibold text-slate-700 block truncate">{file.title || 'Untitled'}</span>
                                                                                            <span className="text-[10px] text-slate-400 block truncate">{file.fileType} • {file.originalFilename}</span>
                                                                                        </div>
                                                                                        <div className="flex gap-1.5 items-center flex-shrink-0">
                                                                                            <a 
                                                                                                href={file.filePath} 
                                                                                                className="text-[10px] font-bold text-blue-600 hover:text-blue-800 bg-slate-50 border border-slate-300 px-2 py-1 rounded shadow-2xs hover:bg-slate-100 transition-colors" 
                                                                                                download
                                                                                            >
                                                                                                Download
                                                                                            </a>
                                                                                            <button 
                                                                                                onClick={() => handleDeleteFile(file.id)} 
                                                                                                className="text-[10px] font-bold text-rose-600 hover:text-rose-800 bg-slate-50 border border-slate-300 px-2 py-1 rounded shadow-2xs hover:bg-rose-100 transition-colors"
                                                                                            >
                                                                                                Delete
                                                                                            </button>
                                                                                        </div>
                                                                                    </div>
                                                                                ))}
                                                                            </div>
                                                                        )}
                                                                    </div>
                                                                </>
                                                            )}

                                                            {uploadingModuleId === m.id ? (
                                                                <form onSubmit={(e) => handleUploadModuleFile(e, m.id)} className="space-y-3 p-3 bg-white border border-slate-300 rounded-xl">
                                                                    <div className="space-y-1">
                                                                        <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">File Title *</label>
                                                                        <input 
                                                                            type="text" 
                                                                            value={uploadFileTitle} 
                                                                            onChange={e => setUploadFileTitle(e.target.value)} 
                                                                            placeholder="e.g. Week 3 Lecture Slides" 
                                                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                                                            required 
                                                                        />
                                                                    </div>
                                                                    <div className="space-y-1">
                                                                        <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">File Category *</label>
                                                                        <input 
                                                                            type="text" 
                                                                            value={uploadFileType} 
                                                                            onChange={e => setUploadFileType(e.target.value)} 
                                                                            placeholder="e.g. Lecture Slides, Notes" 
                                                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                                                            required 
                                                                        />
                                                                    </div>
                                                                    <div className="space-y-1">
                                                                        <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Syllabus File *</label>
                                                                        <input 
                                                                            type="file" 
                                                                            onChange={e => setUploadFile(e.target.files[0])} 
                                                                            className="block w-full text-xs text-slate-500 file:mr-4 file:py-1.5 file:px-3 file:rounded-lg file:border-0 file:text-xs file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100" 
                                                                            required 
                                                                        />
                                                                    </div>
                                                                    <div className="flex gap-1.5 justify-end">
                                                                        <button type="submit" className="text-[10px] font-bold text-white bg-blue-600 hover:bg-blue-700 px-3 py-1.5 rounded-lg shadow-xs transition-colors">Upload File</button>
                                                                        <button type="button" onClick={() => { setUploadingModuleId(null); setUploadFile(null); setUploadFileTitle(''); }} className="text-[10px] font-bold text-slate-500 bg-white border border-slate-300 hover:bg-slate-50 px-3 py-1.5 rounded-lg transition-colors">Cancel</button>
                                                                    </div>
                                                                </form>
                                                            ) : (
                                                                <button 
                                                                    onClick={() => { setUploadingModuleId(m.id); setUploadFileType(''); setUploadFileTitle(''); }} 
                                                                    className="text-xs font-bold text-blue-600 bg-blue-50 hover:bg-blue-100 border border-blue-100 py-2 rounded-lg text-center w-full block transition-colors shadow-2xs"
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

                            {/* Step 4: Active Intake Windows */}
                            <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 flex flex-col space-y-4">
                                <div>
                                    <h2 className="text-lg font-bold text-slate-900">4. Active Intake Windows</h2>
                                    <p className="text-xs text-slate-500">Manage ongoing submission slots.</p>
                                </div>
                                <div className="space-y-4">
                                    {mySessions.length === 0 ? (
                                        <p className="text-xs text-slate-500 text-center py-6">No submission sessions mapped to your modules.</p>
                                    ) : (
                                        mySessions.map(s => {
                                            const closesAt = new Date(s.endTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
                                            return (
                                                <div key={s.id} className="bg-slate-50/50 border border-slate-300 rounded-xl p-4 space-y-3 relative overflow-hidden">
                                                    <div className="flex justify-between items-start gap-2">
                                                        <div className="space-y-1">
                                                            <h4 className="text-xs font-bold text-slate-800 leading-tight">{s.sessionName}</h4>
                                                            <p className="text-[10px] text-slate-500">Assignment: <strong className="font-medium text-slate-700">{s.assignmentTitle || 'General'}</strong></p>
                                                        </div>
                                                        <span className={`inline-flex items-center gap-1.5 border text-xs font-semibold px-3 py-1 rounded-full ${
                                                            s.status === 'CLOSED'
                                                                ? 'bg-rose-50 text-rose-700 border-rose-200/60'
                                                                : 'bg-emerald-50 text-emerald-700 border-emerald-200/60'
                                                        }`}>
                                                            {s.status}
                                                        </span>
                                                    </div>
                                                    <div className="text-[11px] text-slate-600 bg-white border border-slate-300 rounded-lg p-2 font-medium flex justify-between items-center">
                                                        <span>⏰ Closes: <strong>{closesAt}</strong></span>
                                                    </div>
                                                    <div className="flex gap-2 pt-1">
                                                        <button 
                                                            onClick={() => toggleSessionClose(s.id, s.status === 'CLOSED')} 
                                                            className={`font-medium text-[10px] py-1.5 px-3 rounded-lg border transition-colors flex-1 text-center ${
                                                                s.status === 'CLOSED'
                                                                    ? 'bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100'
                                                                    : 'bg-rose-50 text-rose-700 border-rose-200 hover:bg-rose-100'
                                                            }`}
                                                        >
                                                            {s.status === 'CLOSED' ? 'Open' : 'Close'}
                                                        </button>
                                                        <button 
                                                            onClick={() => inspectSubmissions(s.id)} 
                                                            className={`font-medium text-[10px] py-1.5 px-3 rounded-lg border transition-colors flex-1 text-center ${
                                                                activeSessionId === s.id
                                                                    ? 'bg-blue-600 border-blue-600 text-white hover:bg-blue-700'
                                                                    : 'bg-slate-100 border-slate-200 text-slate-700 hover:bg-slate-200/80'
                                                            }`}
                                                        >
                                                            Inspect
                                                        </button>
                                                        <button 
                                                            onClick={() => deleteSession(s.id)} 
                                                            className="font-medium text-[10px] py-1.5 px-3 rounded-lg border border-slate-200 bg-slate-50 text-rose-600 hover:bg-rose-50 hover:border-rose-200 transition-colors flex-1 text-center"
                                                        >
                                                            Delete
                                                        </button>
                                                    </div>
                                                </div>
                                            );
                                        })
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>

                {/* Submission file list overlay/section */}
                {activeSessionId && (
                    <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 mt-8 space-y-6">
                        <div className="flex justify-between items-center border-b border-slate-300 pb-3">
                            <div className="space-y-1">
                                <h2 className="text-lg font-bold text-slate-900">Student Submissions Audit</h2>
                                <p className="text-xs text-slate-500">Grade student work, download submitted files, and add feedback comments.</p>
                            </div>
                            <button 
                                onClick={() => inspectSubmissions('')} 
                                className="bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-medium text-xs py-2 px-4 rounded-lg transition-colors"
                            >
                                Close Audit
                            </button>
                        </div>

                        <div className="space-y-4">
                            {inspectedSubmissions.length === 0 ? (
                                <p className="text-xs text-slate-500 text-center py-6">No student files submitted for this session yet.</p>
                            ) : (
                                inspectedSubmissions.map(sub => (
                                    <div key={sub.submissionId} className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-4 space-y-4 bg-slate-50/10 hover:border-slate-300">
                                        <div className="flex justify-between items-start gap-4 flex-wrap sm:flex-nowrap">
                                            <div className="space-y-1 min-w-0 flex-1">
                                                <h4 className="text-sm font-bold text-slate-800 truncate">{sub.fullName || sub.learnerName || 'Student'}</h4>
                                                <p className="text-xs font-semibold text-slate-500">Student Number: <span className="text-slate-700">{sub.learnerCode || 'N/A'}</span></p>
                                                <p className="text-xs text-blue-600 font-bold break-all">File: {sub.originalFilename}</p>
                                                <p className="text-[10px] text-slate-400">Uploaded: {new Date(sub.submittedAt).toLocaleString()}</p>
                                            </div>
                                            <div className="flex flex-col items-end gap-2">
                                                <span className={`inline-flex items-center gap-1.5 border text-xs font-semibold px-3 py-1 rounded-full ${
                                                    sub.status === 'GRADED'
                                                        ? 'bg-emerald-50 text-emerald-700 border-emerald-200/60'
                                                        : 'bg-blue-50 text-blue-700 border-blue-200/60'
                                                }`}>
                                                    {sub.status || 'SUBMITTED'}
                                                </span>
                                                {sub.status === 'GRADED' && (
                                                    <span className="text-sm font-bold text-emerald-600">Grade: {sub.grade}%</span>
                                                )}
                                            </div>
                                        </div>

                                        {sub.status === 'GRADED' && (
                                            <div className="bg-slate-50/50 border border-slate-200 rounded-xl p-3.5 text-xs text-slate-600 space-y-2">
                                                <p className="font-semibold italic">Feedback: "{sub.feedback || 'No comments provided.'}"</p>
                                                {sub.gradedFilePath && (
                                                    <div className="pt-1.5 border-t border-slate-300 flex items-center justify-between">
                                                        <span className="text-[10px] text-slate-400">Returned marked file: {sub.gradedOriginalFilename}</span>
                                                        <a 
                                                            href="#" 
                                                            onClick={(e) => downloadSubmissionFile(e, sub.submissionId, sub.gradedOriginalFilename, true)} 
                                                            className="text-[10px] font-bold text-emerald-700 hover:text-emerald-900"
                                                        >
                                                            Download Marked File
                                                        </a>
                                                    </div>
                                                )}
                                            </div>
                                        )}

                                        <div className="flex gap-2">
                                            <a 
                                                href="#" 
                                                onClick={(e) => downloadSubmissionFile(e, sub.submissionId, sub.originalFilename, false)} 
                                                className="bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-medium text-xs py-2 px-3.5 rounded-lg transition-colors flex items-center gap-1.5"
                                            >
                                                Download Student File
                                            </a>
                                            {gradingSubId !== sub.submissionId && (
                                                <button 
                                                    onClick={() => {
                                                        setGradingSubId(sub.submissionId);
                                                        setEditGrade(sub.grade !== null && sub.grade !== undefined ? sub.grade.toString() : '');
                                                        setEditFeedback(sub.feedback || '');
                                                    }}
                                                    className="bg-blue-50 border border-blue-200 hover:bg-blue-100/80 text-blue-700 font-medium text-xs py-2 px-3.5 rounded-lg transition-colors"
                                                >
                                                    {sub.status === 'GRADED' ? 'Edit Grade' : 'Grade Submission'}
                                                </button>
                                            )}
                                        </div>

                                        {gradingSubId === sub.submissionId && (
                                            <form onSubmit={(e) => handleSaveGrade(e, sub.submissionId)} className="bg-slate-50/50 border border-slate-200 rounded-xl p-4 space-y-4">
                                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                                    <div className="space-y-1.5">
                                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Grade (0-100) *</label>
                                                        <input 
                                                            type="number" 
                                                            value={editGrade} 
                                                            onChange={e => setEditGrade(e.target.value)} 
                                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                                            min="0" 
                                                            max="100" 
                                                            required 
                                                        />
                                                    </div>
                                                    <div className="space-y-1.5 md:col-span-2">
                                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Feedback Comments</label>
                                                        <input 
                                                            type="text" 
                                                            value={editFeedback} 
                                                            onChange={e => setEditFeedback(e.target.value)} 
                                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                                            placeholder="e.g. Excellent presentation of analytical models." 
                                                        />
                                                    </div>
                                                </div>

                                                <div className="space-y-1.5">
                                                    <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Return Graded File (Optional)</label>
                                                    <input 
                                                        type="file" 
                                                        onChange={e => setMarkedFile(e.target.files[0])} 
                                                        className="block w-full text-xs text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-xs file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100" 
                                                    />
                                                </div>

                                                <div className="flex justify-end gap-2">
                                                    <button 
                                                        type="submit" 
                                                        className="bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-xs py-2.5 px-5 rounded-xl shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150"
                                                    >
                                                        Save Grade
                                                    </button>
                                                    <button 
                                                        type="button" 
                                                        onClick={() => setGradingSubId(null)} 
                                                        className="bg-slate-100 hover:bg-slate-200/80 text-slate-500 font-medium text-xs py-2 px-4 rounded-lg transition-colors"
                                                    >
                                                        Cancel
                                                    </button>
                                                </div>
                                            </form>
                                        )}
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                )}
            </div>

            {editingProfile && (
                <div className="fixed inset-0 bg-slate-950/60 backdrop-blur-sm flex items-center justify-center p-4 z-50">
                    <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-xl w-full max-w-md space-y-6 relative text-slate-800">
                        <div className="flex justify-between items-center">
                            <h3 className="text-base font-extrabold text-slate-900">Edit Profile</h3>
                            <button 
                                onClick={() => setEditingProfile(null)}
                                className="text-slate-400 hover:text-slate-600 transition-colors"
                            >
                                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                </svg>
                            </button>
                        </div>
                        
                        <form onSubmit={async (e) => {
                            e.preventDefault();
                            try {
                                const res = await fetch(`/api/lecturer/profile`, {
                                    method: 'PUT',
                                    headers: {
                                        'Content-Type': 'application/json',
                                        'Authorization': `Basic ${token}`
                                    },
                                    body: JSON.stringify({
                                        fullName: editingProfile.fullName,
                                        email: editingProfile.email,
                                        password: editingProfile.password || ''
                                    })
                                });
                                if (res.ok) {
                                    showMsg('success', 'Profile updated successfully!');
                                    setEditingProfile(null);
                                    fetchProfile();
                                } else {
                                    showMsg('error', 'Failed to update profile.');
                                }
                            } catch (err) {
                                showMsg('error', 'Network error.');
                            }
                        }} className="space-y-4 text-left">
                            <div className="space-y-1.5">
                                <label className="block text-[10px] font-bold tracking-wider text-slate-400 uppercase">Full Name</label>
                                <input 
                                    type="text" 
                                    value={editingProfile.fullName}
                                    onChange={e => setEditingProfile({...editingProfile, fullName: e.target.value})}
                                    className="w-full px-3.5 py-2 text-xs border rounded-xl"
                                    required
                                />
                            </div>
                            <div className="space-y-1.5">
                                <label className="block text-[10px] font-bold tracking-wider text-slate-400 uppercase">Email Address</label>
                                <input 
                                    type="email" 
                                    value={editingProfile.email}
                                    onChange={e => setEditingProfile({...editingProfile, email: e.target.value})}
                                    className="w-full px-3.5 py-2 text-xs border rounded-xl"
                                />
                            </div>
                            <div className="space-y-1.5">
                                <label className="block text-[10px] font-bold tracking-wider text-slate-400 uppercase">Password (Leave blank to keep current)</label>
                                <input 
                                    type="password" 
                                    placeholder="••••••••"
                                    value={editingProfile.password || ''}
                                    onChange={e => setEditingProfile({...editingProfile, password: e.target.value})}
                                    className="w-full px-3.5 py-2 text-xs border rounded-xl"
                                />
                            </div>
                            <div className="flex justify-end gap-2 pt-2">
                                <button 
                                    type="button" 
                                    onClick={() => setEditingProfile(null)}
                                    className="bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-semibold px-4 py-2 rounded-xl text-xs transition-colors"
                                >
                                    Cancel
                                </button>
                                <button 
                                    type="submit"
                                    className="bg-blue-600 hover:bg-blue-700 text-white font-semibold px-4 py-2 rounded-xl text-xs transition-colors shadow-sm"
                                >
                                    Save Changes
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default LecturerDashboard;
