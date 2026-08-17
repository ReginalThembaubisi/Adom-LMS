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
    const [activeTab, setActiveTab] = useState('overview');
    const [activeSubmission, setActiveSubmission] = useState(null);
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

                    
                {/* Main Facilitator Workspace with Sidebar */}
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 mt-6">
                    {/* Left Navigation Sidebar */}
                    <div className="lg:col-span-3">
                        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl space-y-2 sticky top-6">
                            <div className="pb-3 border-b border-slate-800 mb-2">
                                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider pl-3">Navigation Menu</span>
                            </div>
                            {[
                                { id: 'overview', label: 'Dashboard Overview', icon: '📊' },
                                { id: 'materials', label: 'Module Materials', icon: '📚' },
                                { id: 'setup', label: 'Assignments & Setup', icon: '⚙️' },
                                { id: 'grading', label: 'Grading Console', icon: '📝' }
                            ].map(tab => (
                                <button
                                    key={tab.id}
                                    type="button"
                                    onClick={() => setActiveTab(tab.id)}
                                    className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl text-xs font-bold transition-all ${
                                        activeTab === tab.id
                                            ? 'bg-indigo-600 text-white shadow-md shadow-indigo-500/20'
                                            : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
                                    }`}
                                >
                                    <span>{tab.icon}</span>
                                    <span>{tab.label}</span>
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* Right Content Workspace */}
                    <div className="lg:col-span-9 space-y-6">
                        {activeTab === 'overview' && (
                            <div className="space-y-6">
                                <div className="border-b border-slate-300 pb-3">
                                    <h2 className="text-lg font-bold text-slate-900">Dashboard Overview</h2>
                                    <p className="text-xs text-slate-500">Overview metrics, assigned category details, and profile quick access.</p>
                                </div>
                                <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-sm space-y-4 text-slate-800">
                                    <h3 className="text-sm font-bold text-slate-800">Facilitator Profile Details</h3>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                                        <div className="p-3 bg-slate-50 rounded-xl">
                                            <span className="text-[10px] font-bold text-slate-400 uppercase">Assigned Categories</span>
                                            <p className="font-semibold mt-1 text-slate-800">
                                                {profile?.assignedCategories?.join(', ') || 'None assigned yet'}
                                            </p>
                                        </div>
                                        <div className="p-3 bg-slate-50 rounded-xl">
                                            <span className="text-[10px] font-bold text-slate-400 uppercase">Account Email</span>
                                            <p className="font-semibold mt-1 text-slate-800">{profile?.email || 'N/A'}</p>
                                        </div>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={() => setEditingProfile({ fullName: profile?.fullName || '', email: profile?.email || '', password: '' })}
                                        className="bg-indigo-50 text-indigo-600 border border-indigo-200 hover:bg-indigo-100 font-semibold text-xs px-4 py-2 rounded-xl transition-all cursor-pointer"
                                    >
                                        Edit Profile Credentials
                                    </button>
                                </div>
                            </div>
                        )}

                        {activeTab === 'materials' && (
                            <div className="space-y-6">
                                <div className="border-b border-slate-300 pb-3">
                                    <h2 className="text-lg font-bold text-slate-900">Module Materials</h2>
                                    <p className="text-xs text-slate-500">Upload syllabus materials, resource worksheets, and manage documents.</p>
                                </div>
                                ${step2}
                            </div>
                        )}

                        {activeTab === 'setup' && (
                            <div className="space-y-6">
                                <div className="border-b border-slate-300 pb-3">
                                    <h2 className="text-lg font-bold text-slate-900">Assignments & Intake Windows</h2>
                                    <p className="text-xs text-slate-500">Define course evaluation slots and schedule student submission windows.</p>
                                </div>
                                <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                                    <div className="lg:col-span-6 space-y-6">
                                        ${step1}
                                        ${step3}
                                    </div>
                                    <div className="lg:col-span-6">
                                        ${step4}
                                    </div>
                                </div>
                            </div>
                        )}

                        {activeTab === 'grading' && (
                            <div className="space-y-6">
                                <div className="border-b border-slate-300 pb-3">
                                    <h2 className="text-lg font-bold text-slate-900">Grading Console</h2>
                                    <p className="text-xs text-slate-500">Review, grade, and return student work within the side-by-side workspace.</p>
                                </div>
                                ${auditBlock}
                                {!activeSessionId && (
                                    <div className="text-center py-12 bg-white border border-slate-200/80 rounded-2xl">
                                        <p className="text-xs text-slate-500 font-medium">No session selected for grading.</p>
                                        <p className="text-[10px] text-slate-400 mt-1">Please select "Inspect Submissions" under the Assignments & Setup tab.</p>
                                        <button
                                            type="button"
                                            onClick={() => setActiveTab('setup')}
                                            className="mt-4 bg-indigo-600 text-white font-semibold text-xs py-2 px-4 rounded-xl hover:bg-indigo-700 transition-all cursor-pointer"
                                        >
                                            Go to Setup Tab
                                        </button>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* SubmissionMarker Modal overlay */}
            {activeSubmission && (
                <SubmissionMarker
                    submission={activeSubmission}
                    onClose={() => {
                        setActiveSubmission(null);
                        inspectSubmissions(activeSessionId);
                    }}
                    onSaveGrade={async (submissionId, formData) => {
                        const res = await fetch(`/api/lecturer/submissions/${submissionId}/grade`, {
                            method: 'PUT',
                            headers: {
                                'Authorization': `Basic ${token}`
                            },
                            body: formData
                        });
                        if (!res.ok) {
                            throw new Error('Failed to save grade.');
                        }
                        inspectSubmissions(activeSessionId);
                    }}
                    role="Facilitator"
                />
            )}


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
