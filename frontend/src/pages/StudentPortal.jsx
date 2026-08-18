import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLearner } from '../context/LearnerContext';
import SubmissionViewer from '../components/SubmissionViewer';
import MessagesPanel from '../components/MessagesPanel';
import { GraderBadge } from '../utils/graderBadge';
import { getStatusBadgeClasses, getStatusLabel, getStatusDotClasses, getStatusContainerClasses } from '../utils/colors';

const StudentPortal = () => {
    const { learner, logoutStudent } = useLearner();
    const navigate = useNavigate();

    // Redirect to landing if no learner context exists
    useEffect(() => {
        if (!learner) {
            navigate('/?session_expired=true');
        }
    }, [learner, navigate]);


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
    const [viewingSubmission, setViewingSubmission] = useState(null);
    const [unreadMessages, setUnreadMessages] = useState(0);

    // Calendar State
    const [currentMonth, setCurrentMonth] = useState(new Date());

    // Chatbot State
    const [chatOpen, setChatOpen] = useState(false);
    const [chatQuery, setChatQuery] = useState('');
    const [chatMessages, setChatMessages] = useState([
        { sender: 'bot', text: "Hello! 🤖 I'm your LMS Assistant. Ask me about upcoming deadlines, your grades, or facilitator contacts. Type 'help' to see what I can do!" }
    ]);
    const [chatLoading, setChatLoading] = useState(false);
    const chatEndRef = useRef(null);

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
            fetchUnreadMessages();
            const interval = setInterval(fetchUnreadMessages, 30000);
            return () => clearInterval(interval);
        }
    }, [studentNumber]);

    const fetchUnreadMessages = async () => {
        try {
            const res = await fetch(`/api/learners/${studentNumber}/messages/unread-count`);
            if (res.ok) {
                const data = await res.json();
                setUnreadMessages(data.unreadCount || 0);
            }
        } catch (e) {
            // Silent — background poll.
        }
    };

    const fetchStudentThreads = async () => {
        const res = await fetch(`/api/learners/${studentNumber}/messages`);
        if (!res.ok) throw new Error('Failed to load conversations');
        return res.json();
    };

    const fetchStudentThread = async (lecturerId) => {
        const res = await fetch(`/api/learners/${studentNumber}/messages/${lecturerId}`);
        if (!res.ok) throw new Error('Failed to load conversation');
        fetchUnreadMessages();
        return res.json();
    };

    const sendStudentMessage = async (lecturerId, body) => {
        const res = await fetch(`/api/learners/${studentNumber}/messages/${lecturerId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ body })
        });
        if (!res.ok) throw new Error('Failed to send message');
        return res.json();
    };

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

    useEffect(() => {
        if (chatEndRef.current) {
            chatEndRef.current.scrollIntoView({ behavior: 'smooth' });
        }
    }, [chatMessages, chatOpen]);

    const handleSendChatMessage = async (e) => {
        e.preventDefault();
        const msg = chatQuery.trim();
        if (!msg) return;

        const updatedMessages = [...chatMessages, { sender: 'user', text: msg }];
        setChatMessages(updatedMessages);
        setChatQuery('');
        setChatLoading(true);

        try {
            const res = await fetch('/api/chatbot/ask', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    query: msg,
                    studentNumber: studentNumber
                })
            });
            if (res.ok) {
                const data = await res.json();
                setChatMessages([...updatedMessages, { sender: 'bot', text: data.response }]);
            } else {
                setChatMessages([...updatedMessages, { sender: 'bot', text: "Sorry, I encountered an error. Please try again." }]);
            }
        } catch (err) {
            setChatMessages([...updatedMessages, { sender: 'bot', text: "Network error. Please try again." }]);
        } finally {
            setChatLoading(false);
        }
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
    };

    // Calendar helper variables
    const getDaysInMonth = (y, m) => new Date(y, m + 1, 0).getDate();
    const getFirstDayOfMonth = (y, m) => new Date(y, m, 1).getDay();

    const year = currentMonth.getFullYear();
    const month = currentMonth.getMonth();
    const daysInMonth = getDaysInMonth(year, month);
    const firstDay = getFirstDayOfMonth(year, month);

    const days = [];
    for (let i = 0; i < firstDay; i++) {
        days.push(null);
    }
    for (let d = 1; d <= daysInMonth; d++) {
        days.push(new Date(year, month, d));
    }

    const deadlines = [];
    modules.forEach(m => {
        if (m.activeSessions) {
            m.activeSessions.forEach(s => {
                if (s.endTime) {
                    deadlines.push({
                        id: s.id,
                        title: s.assignmentTitle || s.sessionName,
                        moduleName: m.moduleName,
                        moduleCode: m.moduleCode,
                        moduleId: m.id,
                        dueDate: new Date(s.endTime)
                    });
                }
            });
        }
    });

    if (!learner) return null;

    return (
        <div className="bg-slate-50/80 min-h-screen text-slate-800 antialiased py-8 space-y-8">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-6">
                
                {/* Header Strip or Dark Banner */}
                {selectedModule ? (
                    /* Cohesive Header Strip */
                    <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-4 flex flex-col sm:flex-row justify-between sm:items-center gap-4 mb-6">
                        <div className="flex items-center gap-3">
                            <button 
                                className="bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-medium text-xs py-2 px-4 rounded-lg transition-colors flex items-center gap-1.5"
                                onClick={() => setSelectedModule(null)}
                            >
                                ← Back to Modules
                            </button>
                            <span className="text-slate-300 hidden sm:inline">|</span>
                            <span className="text-sm font-extrabold text-slate-900">
                                Code {selectedModule.moduleCode || 'N/A'}: {selectedModule.moduleName}
                            </span>
                        </div>
                        <div className="text-xs text-slate-500 font-semibold sm:text-right">
                            Facilitator: <span className="text-slate-800 font-bold">{selectedModule.lecturerName || 'Unassigned'}</span>
                        </div>
                    </div>
                ) : (
                    /* Top Dark Hero Welcome Banner */
                    <header className="flex justify-between items-center pb-6 border-b border-slate-300 bg-linear-to-r from-slate-900 to-indigo-950 text-white p-6 rounded-2xl shadow-md mb-6">
                        <div className="flex items-center gap-4">
                            <div className="w-12 h-12 bg-white/10 hover:bg-white/20 transition-all rounded-full flex items-center justify-center font-bold text-lg text-white border border-white/20 shadow-xs flex-shrink-0">
                                {learner.fullName ? learner.fullName.split(' ').map(n => n[0]).join('').toUpperCase() : 'S'}
                            </div>
                            <div>
                                <h1 className="text-2xl font-bold tracking-tight">Welcome back, {learner.fullName}! 👋</h1>
                                <p className="text-sm text-slate-300">Here is your academic overview for this term.</p>
                            </div>
                        </div>
                        <button onClick={logoutStudent} className="border border-white/20 bg-white/10 px-4 py-2 rounded-xl text-xs font-semibold hover:bg-white/20 text-white transition-all shadow-xs">
                            Sign Out
                        </button>
                    </header>
                )}

                {/* Alert Banner */}
                {alert.message && (
                    <div className={`p-4 rounded-xl text-xs font-semibold shadow-xs border ${
                        alert.type === 'error' 
                            ? 'bg-rose-50 border-rose-200 text-rose-800' 
                            : 'bg-blue-50 border-blue-200 text-blue-800'
                    }`}>
                        {alert.message}
                    </div>
                )}

                {/* Student Info Card Block */}
                {!selectedModule && (
                    <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-5 grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                        <div>
                            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Student Number</span>
                            <strong className="text-sm font-semibold text-slate-800 mt-1 block">{studentNumber}</strong>
                        </div>
                        <div>
                            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Cohort</span>
                            <strong className="text-sm font-semibold text-slate-800 mt-1 block">{learner.cohort || '2026 Intake'}</strong>
                        </div>
                        <div>
                            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Learnership Program</span>
                            <strong className="text-sm font-semibold text-slate-800 mt-1 block">{learner.learnershipName || 'Unassigned'}</strong>
                        </div>
                    </div>
                )}

                {/* Tab Bar */}
                {!selectedModule && (
                    <nav className="bg-slate-100 p-1 rounded-xl inline-flex gap-1 mb-6">
                        <button 
                            className={activeTab === 'modules' 
                                ? "bg-white shadow-xs text-slate-900 px-4 py-2 rounded-lg text-xs font-bold transition-all" 
                                : "text-slate-500 hover:text-slate-800 px-4 py-2 rounded-lg text-xs font-semibold transition-all"
                            }
                            onClick={() => handleTabChange('modules')}
                        >
                            My Modules
                        </button>
                        <button 
                            className={activeTab === 'calendar' 
                                ? "bg-white shadow-xs text-slate-900 px-4 py-2 rounded-lg text-xs font-bold transition-all" 
                                : "text-slate-500 hover:text-slate-800 px-4 py-2 rounded-lg text-xs font-semibold transition-all"
                            }
                            onClick={() => handleTabChange('calendar')}
                        >
                            My Calendar
                        </button>
                        <button
                            className={activeTab === 'history'
                                ? "bg-white shadow-xs text-slate-900 px-4 py-2 rounded-lg text-xs font-bold transition-all"
                                : "text-slate-500 hover:text-slate-800 px-4 py-2 rounded-lg text-xs font-semibold transition-all"
                            }
                            onClick={() => handleTabChange('history')}
                        >
                            My Submissions
                        </button>
                        <button
                            className={activeTab === 'messages'
                                ? "bg-white shadow-xs text-slate-900 px-4 py-2 rounded-lg text-xs font-bold transition-all flex items-center gap-1.5"
                                : "text-slate-500 hover:text-slate-800 px-4 py-2 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5"
                            }
                            onClick={() => handleTabChange('messages')}
                        >
                            Messages
                            {unreadMessages > 0 && (
                                <span className="bg-rose-500 text-white text-[9px] font-bold rounded-full w-4 h-4 flex items-center justify-center">
                                    {unreadMessages}
                                </span>
                            )}
                        </button>
                    </nav>
                )}

                {lastSubmission && (
                    <div className="bg-emerald-50/50 border border-emerald-200 rounded-xl p-5 shadow-sm space-y-3 mb-6">
                        <div className="flex justify-between items-start">
                            <div>
                                <h3 className="text-sm font-bold text-emerald-800 flex items-center gap-1.5">
                                    <span>✓</span> Submission Successful!
                                </h3>
                                <div className="text-xs text-emerald-700 mt-2 space-y-1">
                                    <p><strong>File Submitted:</strong> {lastSubmission.fileName}</p>
                                    <p><strong>Assignment Slot:</strong> {lastSubmission.slotName}</p>
                                    <p><strong>Submitted At:</strong> {lastSubmission.submittedAt}</p>
                                </div>
                                <p className="text-[11px] text-slate-500 mt-3">
                                    You can check this anytime under <strong>My Submissions</strong>.
                                </p>
                            </div>
                            <button 
                                onClick={() => setLastSubmission(null)} 
                                className="bg-white border border-slate-300 hover:bg-slate-50 text-slate-700 text-[10px] font-bold py-1 px-2.5 rounded transition-colors"
                            >
                                Dismiss
                            </button>
                        </div>
                    </div>
                )}

                {/* TAB 1: My Modules */}
                {activeTab === 'modules' && (
                    <div className="space-y-6">
                        {!selectedModule ? (
                            <div id="modules-list-view" className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                                {/* Left Area (Active Modules - 8 Columns) */}
                                <div className="lg:col-span-8 space-y-6">
                                    <div className="border-b border-slate-300 pb-3">
                                        <h2 className="text-lg font-bold text-slate-900">My Active Modules</h2>
                                        <p className="text-xs text-slate-500">Select a module to see active courses, learning materials, and grades.</p>
                                    </div>

                                    {loadingModules ? (
                                        <p className="text-sm text-slate-500">Loading active modules...</p>
                                    ) : modules.length === 0 ? (
                                        <div className="text-center py-12 border-2 border-dashed border-slate-300 rounded-xl">
                                            <p className="text-sm text-slate-400">You are not enrolled in any modules yet.</p>
                                        </div>
                                    ) : (
                                        <div className="space-y-8">
                                            {Array.from(new Set(modules.map(m => m.moduleType || 'General'))).map(type => {
                                                const typeModules = modules.filter(m => (m.moduleType || 'General') === type);
                                                if (typeModules.length === 0) return null;
                                                return (
                                                    <div key={type} className="space-y-4">
                                                        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider border-b border-slate-300 pb-2">
                                                            {type} Modules
                                                        </h3>
                                                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                                            {typeModules.map((m) => (
                                                                <div 
                                                                    key={m.id} 
                                                                    className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 hover:border-blue-500 transition-all duration-200 cursor-pointer p-5 flex flex-col justify-between gap-3 w-full"
                                                                    onClick={() => openModuleDetails(m.id)}
                                                                >
                                                                    <div className="space-y-3">
                                                                        <div className="flex justify-between items-start gap-2">
                                                                            <span className="font-bold text-slate-800 text-base block leading-snug line-clamp-2" title={m.moduleName}>{m.moduleName}</span>
                                                                            <span className="bg-blue-50 text-blue-700 border border-blue-200 px-2 py-1 rounded text-xs font-bold whitespace-nowrap flex-shrink-0">
                                                                                Code: {m.moduleCode || 'N/A'}
                                                                            </span>
                                                                        </div>
                                                                        <div className="flex items-center gap-1.5 text-xs text-slate-400 mt-2">
                                                                            <span className="text-[14px]">👨‍🏫</span>
                                                                            <span className="text-slate-500">Facilitator: <strong className="font-semibold text-slate-600">{m.lecturerName}</strong></span>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    )}
                                </div>

                                {/* Right Area (Upcoming Deadlines - 4 Columns) */}
                                <div className="lg:col-span-4 space-y-6">
                                    <div className="border-b border-slate-300 pb-3">
                                        <h2 className="text-lg font-bold text-slate-900">Upcoming Deadlines</h2>
                                        <p className="text-xs text-slate-500">Keep track of your upcoming submissions.</p>
                                    </div>

                                    {timeline.length === 0 ? (
                                        <div className="text-center py-8 border border-dashed border-slate-300 rounded-xl">
                                            <p className="text-xs text-slate-400">No upcoming activities require action</p>
                                        </div>
                                    ) : (
                                        <div className="space-y-4">
                                            {timeline.map((item, idx) => {
                                                const closesAt = new Date(item.endTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
                                                return (
                                                    <div 
                                                        key={idx} 
                                                        className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 hover:border-amber-400 transition-all duration-200 cursor-pointer p-4 space-y-3 relative overflow-hidden group"
                                                        onClick={() => {
                                                            openModuleDetails(item.moduleId);
                                                            setActiveUploadSessionId(item.sessionId);
                                                        }}
                                                    >
                                                        {/* Left warning line indicator */}
                                                        <div className="absolute left-0 top-0 bottom-0 w-1 bg-amber-500"></div>

                                                        <div className="flex justify-between items-start gap-2 pl-2">
                                                            <div className="space-y-1">
                                                                <h4 className="text-xs font-bold text-slate-800 leading-tight group-hover:text-amber-700 transition-colors">
                                                                    {item.moduleName}
                                                                </h4>
                                                                <p className="text-[11px] font-medium text-slate-500">
                                                                    Slot: {item.slotTitle}
                                                                </p>
                                                            </div>
                                                            <span className="bg-amber-50 text-amber-700 border border-amber-200 px-2 py-0.5 rounded-full text-[10px] font-extrabold uppercase tracking-wider flex-shrink-0 flex items-center gap-1">
                                                                <span className="w-1.5 h-1.5 bg-amber-500 rounded-full animate-pulse"></span>
                                                                Due Soon
                                                            </span>
                                                        </div>
                                                        <div className="text-xs text-amber-800 bg-amber-50/50 border border-amber-100 rounded-lg p-2 font-semibold flex items-center gap-1.5 pl-3">
                                                            <span>⏰</span> Closes {closesAt}
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    )}
                                </div>
                            </div>
                        ) : (
                            <div id="module-detail-view" className="space-y-6">
                                <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
                                    {/* Left Area (Course Materials - 7 Columns) */}
                                    <div className="lg:col-span-7 space-y-6">
                                        {/* Learning Material Card Box */}
                                        <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-5 space-y-4">
                                            <h3 className="text-base font-bold text-slate-900 border-b border-slate-300 pb-3">Module Course Materials</h3>
                                            {(!selectedModule.files || selectedModule.files.length === 0) ? (
                                                <p className="text-xs text-slate-500">No learning materials uploaded for this module yet.</p>
                                            ) : (
                                                <div className="space-y-4">
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
                                                        <div key={category} className="bg-slate-50/30 border border-slate-200/80 rounded-2xl p-5 space-y-3">
                                                            <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">{category}</h4>
                                                            <div className="space-y-2">
                                                                {categoryFiles.map(file => (
                                                                    <div key={file.id} className="p-4 bg-slate-50/50 border border-slate-200/80 rounded-xl flex justify-between items-center gap-4 hover:bg-slate-100/50 hover:shadow-xs transition-all">
                                                                        <div className="space-y-1">
                                                                            <p className="text-xs font-bold text-slate-800">{file.title || 'Untitled Material'}</p>
                                                                            <span className="text-[10px] font-medium text-slate-400">{file.fileType}</span>
                                                                        </div>
                                                                        <a 
                                                                            href={file.filePath} 
                                                                            download 
                                                                            className="text-xs font-bold text-slate-700 bg-white border border-slate-300 px-3.5 py-2 rounded-lg shadow-2xs hover:shadow-xs hover:bg-slate-50 transition-all flex-shrink-0"
                                                                        >
                                                                            Download
                                                                        </a>
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        </div>
                                                    ))}
                                                </div>
                                            )}
                                        </div>
                                    </div>

                                    {/* Right Area (Assignment Slots - 5 Columns) */}
                                    <div className="lg:col-span-5 space-y-6">
                                        <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-5 space-y-4">
                                            <h3 className="text-base font-bold text-slate-900 border-b border-slate-300 pb-3">Assignment Slots</h3>
                                            {(!selectedModule.slots || selectedModule.slots.length === 0) ? (
                                                <p className="text-xs text-slate-500">No active submission slots open for this module.</p>
                                            ) : (
                                                <div className="space-y-4">
                                                    {selectedModule.slots.map((s) => {
                                                        const deadline = new Date(s.endTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
                                                        const isSubmitted = s.submitted || s.isSubmitted;
                                                        const statusBadge = isSubmitted
                                                            ? <span className="inline-flex items-center gap-1.5 bg-emerald-50 text-emerald-700 border border-emerald-200/60 text-xs font-semibold px-3 py-1 rounded-full"><span className="w-1.5 h-1.5 bg-emerald-500 rounded-full"></span>Submitted</span>
                                                            : s.status === 'CLOSED'
                                                                ? <span className="inline-flex items-center gap-1.5 bg-rose-50 text-rose-700 border border-rose-200/60 text-xs font-semibold px-3 py-1 rounded-full"><span className="w-1.5 h-1.5 bg-rose-500 rounded-full"></span>Closed</span>
                                                                : s.status === 'SCHEDULED'
                                                                    ? <span className="inline-flex items-center gap-1.5 bg-slate-100 text-slate-600 border border-slate-300/60 text-xs font-semibold px-3 py-1 rounded-full"><span className="w-1.5 h-1.5 bg-slate-400 rounded-full"></span>Not Open Yet</span>
                                                                    : <span className="inline-flex items-center gap-1.5 bg-amber-50 text-amber-700 border border-amber-200/60 text-xs font-semibold px-3 py-1 rounded-full"><span className="w-1.5 h-1.5 bg-amber-500 rounded-full"></span>Pending</span>;

                                                        return (
                                                            <div key={s.id} className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-5 flex flex-col gap-4">
                                                                {/* Slot Header with Badge */}
                                                                <div className="flex justify-between items-start border-b border-slate-300 pb-3 w-full gap-2">
                                                                    <div>
                                                                        <h4 className="text-xs font-bold text-slate-800 leading-tight">{s.title}</h4>
                                                                        <p className="text-[10px] text-slate-400 mt-1">
                                                                            Session: <strong className="text-slate-600 font-semibold">{s.sessionName}</strong>
                                                                        </p>
                                                                    </div>
                                                                    {statusBadge}
                                                                </div>

                                                                <div className="text-[11px] text-slate-500 space-y-1">
                                                                    {s.status === 'SCHEDULED' && s.startTime && (
                                                                        <p className="font-semibold text-slate-500">
                                                                            Opens: {new Date(s.startTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                                                                        </p>
                                                                    )}
                                                                    <p className="font-semibold text-amber-600">Deadline: {deadline}</p>
                                                                    <p>{s.description || 'No instructions provided.'}</p>
                                                                </div>

                                                                {/* Instructor Brief Download */}
                                                                {s.taskFilePath && (
                                                                    <div className="flex justify-between items-center bg-slate-50 p-2.5 rounded-lg border border-slate-300/80 w-full gap-2">
                                                                        <span className="text-[10px] font-semibold text-slate-600 truncate flex-shrink min-w-0">{s.taskFileName || 'Brief Attachment'}</span>
                                                                        <a 
                                                                            href={s.taskFilePath}
                                                                            download
                                                                            className="bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-medium text-[10px] py-1 px-2.5 rounded transition-colors flex-shrink-0"
                                                                        >
                                                                            Download Brief
                                                                        </a>
                                                                    </div>
                                                                )}

                                                                {/* Student Submission Receipt */}
                                                                {isSubmitted && activeUploadSessionId !== s.id && (
                                                                    <div className="pt-1 flex justify-between items-center text-[10px] w-full">
                                                                        <span className="text-slate-400">File uploaded on system.</span>
                                                                        {s.status === 'OPEN' && (
                                                                            <button 
                                                                                onClick={() => { setActiveUploadSessionId(s.id); setInlineFile(null); setInlineAlerts({}); }}
                                                                                className="font-bold text-blue-600 hover:text-blue-800 hover:underline"
                                                                            >
                                                                                Replace File
                                                                            </button>
                                                                        )}
                                                                    </div>
                                                                )}

                                                                {/* Inline Resubmit / Submit Form */}
                                                                {s.status === 'OPEN' && activeUploadSessionId === s.id && (
                                                                    <div className="pt-1 w-full">
                                                                        <form onSubmit={(e) => handleInlineSubmit(e, s.id)} className="p-3 bg-slate-50 border border-slate-300 rounded-lg space-y-3 w-full">
                                                                            <div className="space-y-1.5">
                                                                                <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Choose Assignment File (max 20MB) *</label>
                                                                                <div className="flex items-center space-x-2">
                                                                                    <input 
                                                                                        type="file" 
                                                                                        id={`file-input-${s.id}`}
                                                                                        onChange={e => handleInlineFileChange(e, s.id)} 
                                                                                        className="hidden" 
                                                                                        required 
                                                                                    />
                                                                                    <button 
                                                                                        type="button" 
                                                                                        onClick={() => document.getElementById(`file-input-${s.id}`).click()} 
                                                                                        className="bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-medium text-[10px] py-1.5 px-3 rounded transition-colors flex-shrink-0"
                                                                                    >
                                                                                        Browse File
                                                                                    </button>
                                                                                    <span className="text-[10px] text-slate-500 font-semibold truncate flex-shrink min-w-0">
                                                                                        {inlineFile ? inlineFile.name : 'No file selected'}
                                                                                    </span>
                                                                                </div>
                                                                            </div>

                                                                            {inlineAlerts[s.id] && (
                                                                                <div className={`text-[10px] font-bold ${inlineAlerts[s.id].type === 'success' ? 'text-emerald-600' : 'text-rose-600'}`}>
                                                                                    {inlineAlerts[s.id].message}
                                                                                </div>
                                                                            )}

                                                                            <div className="flex justify-end gap-1.5 pt-1">
                                                                                <button 
                                                                                    type="submit" 
                                                                                    disabled={uploading}
                                                                                    className="bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-[10px] py-1.5 px-3.5 rounded-lg shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150 disabled:opacity-50"
                                                                                >
                                                                                    {uploading ? 'Uploading...' : 'Submit'}
                                                                                </button>
                                                                                <button 
                                                                                    type="button" 
                                                                                    onClick={() => { setActiveUploadSessionId(null); setInlineFile(null); }} 
                                                                                    className="bg-slate-100 hover:bg-slate-200/80 text-slate-500 font-medium text-[10px] py-1.5 px-3 rounded transition-colors"
                                                                                >
                                                                                    Cancel
                                                                                </button>
                                                                            </div>
                                                                        </form>
                                                                    </div>
                                                                )}

                                                                {/* Initial submit button */}
                                                                {s.status === 'OPEN' && !isSubmitted && activeUploadSessionId !== s.id && (
                                                                    <div className="pt-1">
                                                                        <button
                                                                            onClick={() => { setActiveUploadSessionId(s.id); setInlineAlerts({}); }}
                                                                            className="bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-xs py-2 px-4 rounded-xl shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150 text-center w-full block"
                                                                        >
                                                                            Submit Assignment
                                                                        </button>
                                                                    </div>
                                                                )}

                                                                {/* Not open yet — explain why there's no submit button instead of leaving a dead end */}
                                                                {s.status === 'SCHEDULED' && (
                                                                    <div className="pt-1">
                                                                        <div className="bg-slate-50 border border-slate-200 text-slate-500 text-xs font-semibold py-2 px-4 rounded-xl text-center w-full">
                                                                            Submission not open yet
                                                                        </div>
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
                            </div>
                        )}
                    </div>
                )}

                {/* TAB 2: My Calendar */}
                {activeTab === 'calendar' && (
                    <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 space-y-6 text-slate-800">
                        <div className="flex justify-between items-center border-b border-slate-200 pb-4">
                            <div>
                                <h2 className="text-lg font-bold text-slate-900">Assignment Calendar</h2>
                                <p className="text-xs text-slate-500">Track and manage your upcoming assignment deadlines.</p>
                            </div>
                            <div className="flex items-center gap-2">
                                <button 
                                    onClick={() => setCurrentMonth(new Date(year, month - 1, 1))}
                                    className="bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold p-2 rounded-xl text-xs transition-colors"
                                >
                                    &larr; Prev
                                </button>
                                <span className="text-xs font-bold text-slate-800 px-3 uppercase tracking-wider">
                                    {currentMonth.toLocaleString('default', { month: 'long' })} {year}
                                </span>
                                <button 
                                    onClick={() => setCurrentMonth(new Date(year, month + 1, 1))}
                                    className="bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold p-2 rounded-xl text-xs transition-colors"
                                >
                                    Next &rarr;
                                </button>
                            </div>
                        </div>

                        {/* Calendar Grid */}
                        <div className="grid grid-cols-7 gap-2 text-center text-xs">
                            {/* Days of Week */}
                            {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map(d => (
                                <div key={d} className="font-bold text-[10px] uppercase tracking-wider text-slate-400 py-2">{d}</div>
                            ))}
                            {/* Days of Month */}
                            {days.map((day, idx) => {
                                if (!day) {
                                    return <div key={`empty-${idx}`} className="bg-slate-50/20 rounded-xl p-3 border border-transparent"></div>;
                                }
                                const isToday = day.toDateString() === new Date().toDateString();
                                const dayDeadlines = deadlines.filter(dl => dl.dueDate.toDateString() === day.toDateString());

                                return (
                                    <div 
                                        key={day.toISOString()} 
                                        className={`relative rounded-xl p-3 border transition-all flex flex-col justify-between items-center min-h-[85px] ${
                                            isToday 
                                                ? 'bg-blue-50/50 border-blue-300 text-blue-600 font-bold' 
                                                : 'bg-slate-50/30 border-slate-200/50 hover:bg-slate-50/80 hover:border-slate-300'
                                        }`}
                                    >
                                        <span className="font-semibold text-slate-700">{day.getDate()}</span>
                                        {dayDeadlines.length > 0 && (
                                            <div className="space-y-1 w-full mt-1.5 z-10">
                                                {dayDeadlines.map(dl => (
                                                    <button 
                                                        key={dl.id}
                                                        onClick={() => {
                                                            setSelectedModule(null);
                                                            // Auto navigate to module
                                                            fetchModuleDetails(dl.moduleId);
                                                        }}
                                                        className="w-full text-left text-[9px] bg-blue-600 hover:bg-blue-700 text-white font-bold px-1.5 py-1 rounded truncate block transition-all"
                                                        title={`${dl.title} (${dl.moduleName})`}
                                                    >
                                                        ⏰ {dl.title}
                                                    </button>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                )}

                {/* TAB 3: My Submissions */}
                {activeTab === 'history' && (
                    <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 space-y-6">
                        <div className="border-b border-slate-300 pb-3">
                            <h2 className="text-lg font-bold text-slate-900">My Submission History</h2>
                            <p className="text-xs text-slate-500">View all files you have previously uploaded and check grading status.</p>
                        </div>
                        <div className="space-y-4">
                            {history.length === 0 ? (
                                <p className="text-xs text-slate-500 text-center py-6">You haven't uploaded any assignments yet.</p>
                            ) : (
                                history.map((sub) => (
                                    <div key={sub.submissionId} className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-5 space-y-4 bg-slate-50/10">
                                        <div className="space-y-1">
                                            <h4 className="text-sm font-bold text-slate-800">{sub.moduleName || 'General'}</h4>
                                            <p className="text-xs font-semibold text-slate-600">{sub.assignmentTitle || 'Assignment'} ({sub.sessionName})</p>
                                            <p className="text-[11px] text-slate-400">File: {sub.originalFilename}</p>
                                            <p className="text-[10px] text-slate-400">Submitted: {new Date(sub.submittedAt).toLocaleString()}</p>
                                        </div>

                                        {/* Unified footer container for status, outcome, and feedback */}
                                        <div className={`p-4 rounded-xl border flex flex-col md:flex-row justify-between items-center gap-3 ${getStatusContainerClasses(sub.status)}`}>
                                            {/* Left side: Status badge & Feedback */}
                                            <div className="flex flex-col md:flex-row items-center md:items-start gap-3 w-full md:w-auto">
                                                <span className={`inline-flex items-center gap-1.5 border text-xs font-semibold px-3 py-1 rounded-full flex-shrink-0 ${getStatusBadgeClasses(sub.status, 'light')}`}>
                                                    <span className={`w-1.5 h-1.5 rounded-full ${getStatusDotClasses(sub.status)}`}></span>
                                                    {getStatusLabel(sub.status || 'SUBMITTED')}
                                                </span>
                                                {sub.gradedByRole && <GraderBadge role={sub.gradedByRole} name={sub.gradedByName} theme="light" />}
                                                <div className="text-xs text-center md:text-left space-y-0.5">
                                                    {(sub.status === 'COMPETENT' || sub.status === 'NOT_YET_COMPETENT') ? (
                                                        <p className="text-slate-600 font-medium italic">
                                                            {sub.feedback ? `"${sub.feedback}"` : 'No feedback comment provided.'}
                                                        </p>
                                                    ) : (
                                                        <p className="text-slate-500 font-medium italic">Not yet assessed.</p>
                                                    )}
                                                </div>
                                            </div>

                                            {/* Right side: Action Buttons */}
                                            <div className="flex flex-wrap gap-2 w-full md:w-auto justify-center md:justify-end">
                                                <button
                                                    onClick={() => setViewingSubmission(sub)}
                                                    className="bg-slate-800 hover:bg-slate-900 text-white font-semibold text-xs py-2 px-3.5 rounded-lg transition-colors flex items-center gap-1.5 cursor-pointer"
                                                >
                                                    View Submission
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                )}

                {activeTab === 'messages' && (
                    <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 space-y-4">
                        <div className="border-b border-slate-300 pb-3">
                            <h2 className="text-lg font-bold text-slate-900">Messages</h2>
                            <p className="text-xs text-slate-500">Message the facilitators of your enrolled modules.</p>
                        </div>
                        <MessagesPanel
                            theme="light"
                            currentSenderType="LEARNER"
                            fetchThreads={fetchStudentThreads}
                            fetchThread={fetchStudentThread}
                            sendMessage={sendStudentMessage}
                        />
                    </div>
                )}
            </div>

            {/* Floating Chatbot Assistant Widget */}
            <div className="fixed bottom-6 right-6 z-50 flex flex-col items-end">
                {/* Chat window */}
                {chatOpen && (
                    <div className="w-[360px] h-[480px] bg-slate-900 border border-slate-700/80 rounded-2xl shadow-2xl flex flex-col overflow-hidden mb-4 text-white animate-fadeIn">
                        {/* Header */}
                        <div className="bg-slate-950 p-4 border-b border-slate-700/80 flex justify-between items-center">
                            <div className="flex items-center gap-2">
                                <div className="w-2.5 h-2.5 bg-emerald-500 rounded-full animate-pulse"></div>
                                <span className="text-xs font-bold uppercase tracking-wider">LMS Chat Assistant</span>
                            </div>
                            <button 
                                onClick={() => setChatOpen(false)}
                                className="text-slate-400 hover:text-slate-200 transition-colors"
                            >
                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M19 9l-7 7-7-7" />
                                </svg>
                            </button>
                        </div>

                        {/* Messages display area */}
                        <div className="flex-1 overflow-y-auto p-4 space-y-3 scrollbar-thin scrollbar-thumb-slate-800">
                            {chatMessages.map((msg, idx) => (
                                <div 
                                    key={idx} 
                                    className={`flex ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}
                                >
                                    <div 
                                        className={`max-w-[85%] rounded-2xl px-3.5 py-2 text-[11px] leading-relaxed shadow-sm whitespace-pre-line ${
                                            msg.sender === 'user' 
                                                ? 'bg-blue-600 text-white rounded-br-none' 
                                                : 'bg-slate-800 text-slate-200 border border-slate-700/50 rounded-bl-none'
                                        }`}
                                    >
                                        {msg.text}
                                    </div>
                                </div>
                            ))}
                            {chatLoading && (
                                <div className="flex justify-start">
                                    <div className="bg-slate-800 text-slate-400 border border-slate-700/50 rounded-2xl rounded-bl-none px-3.5 py-2 text-[11px] flex items-center gap-1">
                                        <span className="w-1 h-1 bg-slate-400 rounded-full animate-bounce"></span>
                                        <span className="w-1 h-1 bg-slate-400 rounded-full animate-bounce delay-100"></span>
                                        <span className="w-1 h-1 bg-slate-400 rounded-full animate-bounce delay-200"></span>
                                    </div>
                                </div>
                            )}
                            <div ref={chatEndRef} />
                        </div>

                        {/* Form input */}
                        <form onSubmit={handleSendChatMessage} className="bg-slate-950 p-3 border-t border-slate-700/80 flex gap-2">
                            <input 
                                type="text"
                                value={chatQuery}
                                onChange={e => setChatQuery(e.target.value)}
                                placeholder="Ask me something..."
                                className="flex-1 bg-slate-800 border border-slate-700/80 rounded-xl px-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors"
                            />
                            <button 
                                type="submit"
                                className="bg-blue-600 hover:bg-blue-700 text-white p-2 rounded-xl transition-all flex items-center justify-center shadow-md shadow-blue-500/20 active:scale-[0.98]"
                            >
                                <svg className="w-4 h-4 transform rotate-90" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                                </svg>
                            </button>
                        </form>
                    </div>
                )}

                {/* Collapsed floating button */}
                <button 
                    onClick={() => setChatOpen(!chatOpen)}
                    className="w-14 h-14 bg-blue-600 hover:bg-blue-700 text-white rounded-full flex items-center justify-center shadow-xl hover:scale-105 active:scale-95 transition-all cursor-pointer relative"
                    title="Open LMS Chat Assistant"
                >
                    {chatOpen ? (
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    ) : (
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                        </svg>
                    )}
                </button>
            </div>

            {viewingSubmission && (
                <SubmissionViewer
                    submission={viewingSubmission}
                    learnerCode={studentNumber}
                    onClose={() => setViewingSubmission(null)}
                />
            )}
        </div>
    );
};

export default StudentPortal;
