import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLearner } from '../context/LearnerContext';
import SubmissionViewer from '../components/SubmissionViewer';
import MessagesPanel from '../components/MessagesPanel';
import { GraderBadge } from '../utils/graderBadge';
import { getStatusBadgeClasses, getStatusLabel, getStatusDotClasses, getStatusContainerClasses } from '../utils/colors';
import {
    ChalkboardTeacher,
    Clock,
    CheckCircle,
    ChatCircleDots,
    ArrowLeft,
    CaretLeft,
    CaretRight,
    X,
    PaperPlaneTilt,
    House,
    Stack,
    ClipboardText,
    UserCircle,
    MagnifyingGlass,
    UploadSimple
} from '@phosphor-icons/react';

// Module/slot titles come from the backend in ALL CAPS (SETA unit-standard convention);
// normalize for display so long titles don't read as a wall of caps.
const toSentenceCase = (str) => {
    if (!str) return str;
    const lower = str.toLowerCase();
    return lower.charAt(0).toUpperCase() + lower.slice(1);
};

// Per-module color coding: each module gets a consistent accent so the dashboard
// is easier to scan at a glance and different subjects feel visually distinct.
// Amber stays reserved separately for urgency (due-soon/closes-at), never reused here.
const MODULE_COLOR_PALETTE = [
    { bar: 'bg-blue-500', badge: 'bg-blue-50 text-blue-700 border-blue-200', hoverBorder: 'hover:border-blue-500', dot: 'bg-blue-500', icon: 'text-blue-500', hoverText: 'group-hover:text-blue-700' },
    { bar: 'bg-violet-500', badge: 'bg-violet-50 text-violet-700 border-violet-200', hoverBorder: 'hover:border-violet-500', dot: 'bg-violet-500', icon: 'text-violet-500', hoverText: 'group-hover:text-violet-700' },
    { bar: 'bg-emerald-500', badge: 'bg-emerald-50 text-emerald-700 border-emerald-200', hoverBorder: 'hover:border-emerald-500', dot: 'bg-emerald-500', icon: 'text-emerald-500', hoverText: 'group-hover:text-emerald-700' },
    { bar: 'bg-rose-500', badge: 'bg-rose-50 text-rose-700 border-rose-200', hoverBorder: 'hover:border-rose-500', dot: 'bg-rose-500', icon: 'text-rose-500', hoverText: 'group-hover:text-rose-700' },
    { bar: 'bg-cyan-500', badge: 'bg-cyan-50 text-cyan-700 border-cyan-200', hoverBorder: 'hover:border-cyan-500', dot: 'bg-cyan-500', icon: 'text-cyan-500', hoverText: 'group-hover:text-cyan-700' },
    { bar: 'bg-orange-500', badge: 'bg-orange-50 text-orange-700 border-orange-200', hoverBorder: 'hover:border-orange-500', dot: 'bg-orange-500', icon: 'text-orange-500', hoverText: 'group-hover:text-orange-700' },
    { bar: 'bg-fuchsia-500', badge: 'bg-fuchsia-50 text-fuchsia-700 border-fuchsia-200', hoverBorder: 'hover:border-fuchsia-500', dot: 'bg-fuchsia-500', icon: 'text-fuchsia-500', hoverText: 'group-hover:text-fuchsia-700' },
    { bar: 'bg-teal-500', badge: 'bg-teal-50 text-teal-700 border-teal-200', hoverBorder: 'hover:border-teal-500', dot: 'bg-teal-500', icon: 'text-teal-500', hoverText: 'group-hover:text-teal-700' },
];

const getModuleColor = (id) => {
    const str = String(id ?? '');
    let hash = 0;
    for (let i = 0; i < str.length; i++) hash = (hash * 31 + str.charCodeAt(i)) >>> 0;
    return MODULE_COLOR_PALETTE[hash % MODULE_COLOR_PALETTE.length];
};

const DESIGN_BAR_COLORS  = ['#4A3AFF','#8B5CF6','#10B981','#F43F5E','#06B6D4','#F97316','#A855F7','#14B8A6'];
const DESIGN_BADGE_BG    = ['#EEF0FF','#F3EEFF','#ECFDF5','#FFF1F2','#ECFEFF','#FFF7ED','#FDF4FF','#F0FDFA'];
const DESIGN_BADGE_FG    = ['#4A3AFF','#7C3AED','#059669','#E11D48','#0891B2','#EA580C','#9333EA','#0D9488'];
const DESIGN_BADGE_BORD  = ['rgba(74,58,255,.2)','rgba(124,58,237,.2)','rgba(5,150,105,.2)','rgba(225,29,72,.2)','rgba(8,145,178,.2)','rgba(234,88,12,.2)','rgba(147,51,234,.2)','rgba(13,148,136,.2)'];

const getDesignModuleColor = (id) => {
    const str = String(id ?? '');
    let hash = 0;
    for (let i = 0; i < str.length; i++) hash = (hash * 31 + str.charCodeAt(i)) >>> 0;
    const idx = hash % 8;
    return { bar: DESIGN_BAR_COLORS[idx], badgeBg: DESIGN_BADGE_BG[idx], badgeFg: DESIGN_BADGE_FG[idx], badgeBorder: DESIGN_BADGE_BORD[idx] };
};

const groupTimelineByDay = (items) => {
    const groups = new Map();
    const now = new Date();
    const todayStr = now.toDateString();
    const tomorrow = new Date(now);
    tomorrow.setDate(now.getDate() + 1);
    const tomorrowStr = tomorrow.toDateString();

    items.forEach(item => {
        const d = new Date(item.endTime);
        const dStr = d.toDateString();
        let label;
        if (dStr === todayStr) label = 'Today';
        else if (dStr === tomorrowStr) label = 'Tomorrow';
        else label = d.toLocaleDateString([], { weekday: 'long', month: 'short', day: 'numeric' });

        if (!groups.has(label)) groups.set(label, []);
        groups.get(label).push(item);
    });

    return Array.from(groups.entries());
};

const StudentPortal = () => {
    const { learner, logoutStudent } = useLearner();
    const navigate = useNavigate();

    // Redirect to landing if no learner context exists
    useEffect(() => {
        if (!learner) {
            navigate('/?session_expired=true');
        }
    }, [learner, navigate]);


    const studentNumber = learner?.learnerCode || '';

    // UI Tab State: 'home' | 'modules' | 'history' | 'messages' | 'profile'
    const [activeTab, setActiveTab] = useState('home');
    const fetchedTabsRef = useRef(new Set());
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

    // Profile notification toggles (UI only)
    const [notifDeadlines, setNotifDeadlines] = useState(true);
    const [notifGrades, setNotifGrades] = useState(true);
    const [notifWifiOnly, setNotifWifiOnly] = useState(false);

    // Module detail segment control: 'assignments' | 'materials'
    const [moduleSegment, setModuleSegment] = useState('assignments');
    // Modules search filter
    const [moduleSearch, setModuleSearch] = useState('');

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

    // Unread badge poll — always active regardless of active tab
    useEffect(() => {
        if (!studentNumber) return;
        fetchUnreadMessages();
        const interval = setInterval(fetchUnreadMessages, 30000);
        return () => clearInterval(interval);
    }, [studentNumber]);

    // Per-tab data fetch — fires only on first visit to each tab
    useEffect(() => {
        if (!studentNumber) return;
        if (fetchedTabsRef.current.has(activeTab)) return;
        fetchedTabsRef.current.add(activeTab);
        if (activeTab === 'home') {
            // Pre-mark so modules/history tabs skip redundant fetches
            fetchedTabsRef.current.add('modules');
            fetchedTabsRef.current.add('history');
            fetchModules();
            fetchTimeline();
            fetchHistory();
        } else if (activeTab === 'modules') {
            fetchModules();
            fetchTimeline();
        } else if (activeTab === 'history') {
            fetchHistory();
        }
    }, [activeTab, studentNumber]);

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

    const handleTabChange = (tabId) => {
        setAlert({ type: '', message: '' });
        setActiveTab(tabId);
        if (tabId === 'modules') {
            setSelectedModule(null);
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

    // Sourced from the same real timeline data as Upcoming Deadlines, so the
    // calendar always reflects actual open submission windows.
    const deadlines = timeline
        .filter(item => item.endTime)
        .map(item => ({
            id: item.sessionId,
            title: item.slotTitle,
            moduleName: item.moduleName,
            moduleId: item.moduleId,
            dueDate: new Date(item.endTime)
        }));

    if (!learner) return null;

    const NAV_TABS = [
        { id: 'home',     label: 'Home',        Icon: House        },
        { id: 'modules',  label: 'Modules',     Icon: Stack        },
        { id: 'history',  label: 'Submissions', Icon: ClipboardText },
        { id: 'messages', label: 'Messages',    Icon: ChatCircleDots },
        { id: 'profile',  label: 'Profile',     Icon: UserCircle   },
    ];

    const filteredModules = moduleSearch.trim()
        ? modules.filter(m =>
            toSentenceCase(m.moduleName).toLowerCase().includes(moduleSearch.toLowerCase()) ||
            (m.moduleCode || '').toLowerCase().includes(moduleSearch.toLowerCase()) ||
            (m.lecturerName || '').toLowerCase().includes(moduleSearch.toLowerCase()))
        : modules;

    return (
        <div className="min-h-screen bg-[#F6F7FB] text-[#101425] antialiased">
          <div className="pb-[84px]">
                
                {/* ── Submission Success Toast ── */}
                {lastSubmission && (
                    <div className="fixed top-4 left-4 right-4 z-50 bg-white rounded-2xl px-4 py-3 flex items-center gap-3 animate-fadeIn"
                        style={{boxShadow:'0 20px 40px -12px rgba(22,169,122,0.35)', border:'1px solid rgba(22,169,122,0.25)'}}>
                        <CheckCircle size={20} weight="fill" color="#16A97A" className="flex-shrink-0" />
                        <div className="flex-1 min-w-0">
                            <p className="text-[12.5px] font-semibold text-[#101425] truncate">{lastSubmission.fileName}</p>
                            <p className="text-[10px] text-[#8A90A8]">Submitted successfully</p>
                        </div>
                        <button onClick={() => setLastSubmission(null)} className="text-[#8A90A8] hover:text-[#101425] flex-shrink-0">
                            <X size={14} weight="bold" />
                        </button>
                    </div>
                )}

                {/* ── Module Detail View ── */}
                {activeTab === 'modules' && selectedModule && (
                    <div className="animate-fadeIn">
                        {/* Indigo header */}
                        <div className="px-5 pt-14 pb-6 text-white" style={{background:'#4A3AFF'}}>
                            <button
                                onClick={() => setSelectedModule(null)}
                                className="flex items-center gap-1.5 text-sm font-semibold mb-4 px-3 py-1.5 rounded-xl"
                                style={{background:'rgba(255,255,255,0.16)'}}
                            >
                                <ArrowLeft size={14} weight="bold" /> Back
                            </button>
                            <h1 className="font-bold text-xl leading-snug line-clamp-2">{toSentenceCase(selectedModule.moduleName)}</h1>
                            <p className="text-sm mt-1 opacity-70">Facilitator: {selectedModule.lecturerName || 'Unassigned'}</p>
                        </div>

                        {/* Segment control */}
                        <div className="px-4 pt-4">
                            <div className="flex rounded-xl p-1" style={{background:'#EFEFF4'}}>
                                {['assignments','materials'].map(seg => (
                                    <button key={seg}
                                        onClick={() => setModuleSegment(seg)}
                                        className="flex-1 py-2 rounded-lg text-[12.5px] font-semibold capitalize transition-all"
                                        style={moduleSegment === seg
                                            ? {background:'#fff', color:'#101425', boxShadow:'0 2px 8px rgba(16,20,37,0.12)'}
                                            : {color:'#8A90A8'}}>
                                        {seg === 'assignments' ? 'Assignments' : 'Materials'}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {alert.message && (
                            <div className={`mx-4 mt-3 p-3 rounded-xl text-xs font-semibold ${alert.type === 'error' ? 'bg-red-50 text-[#E0524A]' : 'bg-blue-50 text-[#4A3AFF]'}`}>
                                {alert.message}
                            </div>
                        )}

                        {/* Materials segment */}
                        {moduleSegment === 'materials' && (
                            <div className="px-4 py-4 space-y-3">
                                {(!selectedModule.files || selectedModule.files.length === 0) ? (
                                    <p className="text-center py-10 text-sm text-[#8A90A8]">No learning materials uploaded yet.</p>
                                ) : (
                                    Object.entries(
                                        selectedModule.files.reduce((acc, f) => {
                                            const cat = f.fileType || 'Other';
                                            if (!acc[cat]) acc[cat] = [];
                                            acc[cat].push(f);
                                            return acc;
                                        }, {})
                                    ).map(([category, categoryFiles]) => (
                                        <div key={category}>
                                            <p className="text-[9.5px] uppercase text-[#8A90A8] mb-2 px-1" style={{fontFamily:"'JetBrains Mono', monospace", letterSpacing:'0.08em', fontWeight:500}}>{category}</p>
                                            <div className="space-y-2">
                                                {categoryFiles.map(file => (
                                                    <div key={file.id} className="bg-white rounded-2xl p-4 flex justify-between items-center gap-3" style={{boxShadow:'var(--card-shadow)'}}>
                                                        <div className="min-w-0">
                                                            <p className="font-semibold text-[13px] text-[#101425] truncate">{file.title || 'Untitled Material'}</p>
                                                            <p className="text-[10px] text-[#8A90A8] mt-0.5">{file.fileType}</p>
                                                        </div>
                                                        <a href={file.filePath} download
                                                            className="text-[11px] font-semibold text-[#4A3AFF] flex-shrink-0 px-3 py-1.5 rounded-xl"
                                                            style={{background:'#EEF0FF'}}>
                                                            Download
                                                        </a>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        )}

                        {/* Assignments segment */}
                        {moduleSegment === 'assignments' && (
                            <div className="px-4 py-4 space-y-3">
                                {(!selectedModule.slots || selectedModule.slots.length === 0) ? (
                                    <p className="text-center py-10 text-sm text-[#8A90A8]">No active submission slots open for this module.</p>
                                ) : (
                                    selectedModule.slots.map((s) => {
                                        const deadline = new Date(s.endTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
                                        const isSubmitted = s.submitted || s.isSubmitted;
                                        const statusPill = isSubmitted
                                            ? <span className="inline-flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1 rounded-full" style={{background:'#ECFDF5',color:'#059669'}}><span className="w-1.5 h-1.5 rounded-full bg-[#059669]" />Submitted</span>
                                            : s.status === 'CLOSED'
                                                ? <span className="inline-flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1 rounded-full" style={{background:'#FFF1F2',color:'#E11D48'}}><span className="w-1.5 h-1.5 rounded-full bg-[#E11D48]" />Closed</span>
                                                : s.status === 'SCHEDULED'
                                                    ? <span className="inline-flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1 rounded-full" style={{background:'#F6F7FB',color:'#8A90A8'}}><span className="w-1.5 h-1.5 rounded-full bg-[#8A90A8]" />Not Open Yet</span>
                                                    : <span className="inline-flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1 rounded-full" style={{background:'#FDF2E2',color:'#9A6412'}}><span className="w-1.5 h-1.5 rounded-full bg-amber-500" />Pending</span>;

                                        return (
                                            <div key={s.id} className="bg-white rounded-2xl p-4 space-y-3" style={{boxShadow:'var(--card-shadow)'}}>
                                                <div className="flex justify-between items-start gap-2">
                                                    <div className="min-w-0">
                                                        <h4 className="font-bold text-[13px] text-[#101425] leading-snug">{toSentenceCase(s.title)}</h4>
                                                        <p className="text-[10px] text-[#8A90A8] mt-0.5">Session: {s.sessionName}</p>
                                                    </div>
                                                    {statusPill}
                                                </div>

                                                <div className="space-y-1">
                                                    {s.status === 'SCHEDULED' && s.startTime && (
                                                        <p className="text-[11px] text-[#8A90A8]">Opens: {new Date(s.startTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</p>
                                                    )}
                                                    <p className="text-[11px] font-semibold" style={{color:'#9A6412'}}>Deadline: {deadline}</p>
                                                    {s.description && <p className="text-[11px] text-[#8A90A8]">{s.description}</p>}
                                                </div>

                                                {s.taskFilePath && (
                                                    <div className="flex justify-between items-center px-3 py-2 rounded-xl gap-2" style={{background:'#F6F7FB'}}>
                                                        <span className="text-[10px] font-semibold text-[#101425] truncate flex-shrink min-w-0">{s.taskFileName || 'Brief Attachment'}</span>
                                                        <a href={s.taskFilePath} download className="text-[10px] font-semibold text-[#4A3AFF] flex-shrink-0">Download Brief</a>
                                                    </div>
                                                )}

                                                {isSubmitted && activeUploadSessionId !== s.id && (
                                                    <div className="flex justify-between items-center text-[10px]">
                                                        <span className="text-[#8A90A8]">File uploaded on system.</span>
                                                        {s.status === 'OPEN' && (
                                                            <button onClick={() => { setActiveUploadSessionId(s.id); setInlineFile(null); setInlineAlerts({}); }}
                                                                className="font-semibold" style={{color:'#4A3AFF'}}>Replace File</button>
                                                        )}
                                                    </div>
                                                )}

                                                {s.status === 'OPEN' && activeUploadSessionId === s.id && (
                                                    <form onSubmit={(e) => handleInlineSubmit(e, s.id)} className="space-y-3 pt-1">
                                                        <div className="rounded-2xl border-2 border-dashed flex flex-col items-center gap-2 py-5 cursor-pointer"
                                                            style={{borderColor:'rgba(74,58,255,.35)', background:'#EDEBFF'}}
                                                            onClick={() => document.getElementById(`file-input-${s.id}`).click()}>
                                                            <UploadSimple size={22} color="#4A3AFF" />
                                                            <p className="text-[11px] font-semibold text-[#4A3AFF]">{inlineFile ? inlineFile.name : 'Tap to choose file (max 20MB)'}</p>
                                                        </div>
                                                        <input type="file" id={`file-input-${s.id}`} onChange={e => handleInlineFileChange(e, s.id)} className="hidden" required />

                                                        {inlineFile && (
                                                            <div className="flex items-center gap-2 px-3 py-2 rounded-xl" style={{background:'#FDECE7'}}>
                                                                <span className="font-mono text-[10px] font-semibold" style={{color:'#C2472C'}}>PDF</span>
                                                                <span className="text-[11px] text-[#101425] truncate flex-1">{inlineFile.name}</span>
                                                            </div>
                                                        )}

                                                        {inlineAlerts[s.id] && (
                                                            <p className={`text-[11px] font-semibold ${inlineAlerts[s.id].type === 'success' ? 'text-[#16A97A]' : 'text-[#E0524A]'}`}>
                                                                {inlineAlerts[s.id].message}
                                                            </p>
                                                        )}

                                                        <div className="flex gap-2">
                                                            <button type="submit" disabled={uploading}
                                                                className="flex-1 py-3 rounded-xl text-white font-semibold text-[13px] disabled:opacity-50"
                                                                style={{background:'#4A3AFF', boxShadow:'0 8px 18px -8px rgba(74,58,255,.7)'}}>
                                                                {uploading ? 'Uploading…' : 'Submit'}
                                                            </button>
                                                            <button type="button" onClick={() => { setActiveUploadSessionId(null); setInlineFile(null); }}
                                                                className="px-4 py-3 rounded-xl font-semibold text-[13px] text-[#8A90A8]" style={{background:'#F6F7FB'}}>
                                                                Cancel
                                                            </button>
                                                        </div>
                                                    </form>
                                                )}

                                                {s.status === 'OPEN' && !isSubmitted && activeUploadSessionId !== s.id && (
                                                    <button onClick={() => { setActiveUploadSessionId(s.id); setInlineAlerts({}); }}
                                                        className="w-full py-3 rounded-xl text-white font-semibold text-[13px]"
                                                        style={{background:'#4A3AFF', boxShadow:'0 8px 18px -8px rgba(74,58,255,.7)'}}>
                                                        Submit Assignment
                                                    </button>
                                                )}

                                                {s.status === 'SCHEDULED' && (
                                                    <div className="w-full py-3 rounded-xl text-center text-[12px] font-semibold text-[#8A90A8]" style={{background:'#F6F7FB'}}>
                                                        Submission not open yet
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })
                                )}
                            </div>
                        )}
                    </div>
                )}

                {/* ── Home Tab ── */}
                {activeTab === 'home' && !selectedModule && (
                    <div className="animate-fadeIn">
                        {/* Greeting banner — inset card on page bg */}
                        <div className="relative overflow-hidden mx-4 mt-4 px-5 pt-8 pb-7" style={{background:'#101425', borderRadius:'24px'}}>
                            <div className="absolute top-0 right-0 w-52 h-52 rounded-full pointer-events-none"
                                style={{background:'radial-gradient(circle, rgba(107,78,255,0.45) 0%, transparent 65%)', transform:'translate(28%, -28%)'}} />
                            <div className="flex justify-between items-start relative z-10">
                                <div>
                                    <p className="text-white/50 text-xs" style={{fontFamily:"'JetBrains Mono', monospace", letterSpacing:'0.08em', textTransform:'uppercase', fontWeight:500}}>Good to see you,</p>
                                    <h1 className="text-white text-2xl leading-tight mt-1" style={{fontWeight:800, letterSpacing:'-0.02em'}}>{learner.fullName?.split(' ')[0] || 'Student'}</h1>
                                </div>
                                <div className="w-10 h-10 flex items-center justify-center text-sm font-bold text-[#101425] flex-shrink-0"
                                    style={{background:'#C8F25A', borderRadius:'14px'}}>
                                    {learner.fullName?.split(' ').filter(Boolean).map(n => n[0]).join('').toUpperCase().slice(0, 2) || 'S'}
                                </div>
                            </div>
                        </div>

                        <div className="px-4 pt-4 pb-2 space-y-4">
                            {/* Stats row */}
                            <div className="grid grid-cols-3 gap-3">
                                {[
                                    { val: history.filter(s => s.status === 'COMPETENT').length, label: 'Competent' },
                                    { val: timeline.length, label: 'Slots Open' },
                                    { val: history.length, label: 'Uploads' },
                                ].map(({ val, label }) => (
                                    <div key={label} className="bg-white rounded-2xl p-4 text-center" style={{boxShadow:'var(--card-shadow)'}}>
                                        <p className="text-2xl font-bold text-[#101425]" style={{fontVariantNumeric:'tabular-nums', letterSpacing:'-0.02em'}}>{val}</p>
                                        <p className="text-[9.5px] text-[#8A90A8] mt-1 uppercase" style={{fontFamily:"'JetBrains Mono', monospace", letterSpacing:'0.08em', fontWeight:500}}>{label}</p>
                                    </div>
                                ))}
                            </div>

                            {/* Due next card */}
                            {timeline.length > 0 && (
                                <div className="bg-white rounded-2xl p-4 space-y-3" style={{boxShadow:'var(--card-shadow)'}}>
                                    <p className="text-[9.5px] uppercase text-[#8A90A8]" style={{fontFamily:"'JetBrains Mono', monospace", letterSpacing:'0.08em', fontWeight:500}}>Due next</p>
                                    <div className="flex justify-between items-center gap-3">
                                        <div className="min-w-0">
                                            <p className="font-bold text-[13px] text-[#101425] truncate">{toSentenceCase(timeline[0].moduleName)}</p>
                                            <p className="text-[11px] text-[#8A90A8] truncate mt-0.5">{toSentenceCase(timeline[0].slotTitle)}</p>
                                        </div>
                                        <span className="px-2.5 py-1 rounded-full text-[10px] font-semibold flex-shrink-0"
                                            style={{background:'#FDF2E2', color:'#9A6412'}}>
                                            {new Date(timeline[0].endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                        </span>
                                    </div>
                                    <div className="flex gap-2 pt-1">
                                        <button
                                            onClick={() => { handleTabChange('modules'); openModuleDetails(timeline[0].moduleId); setActiveUploadSessionId(timeline[0].sessionId); }}
                                            className="flex-1 py-2.5 rounded-xl text-white font-semibold text-[13px]"
                                            style={{background:'#4A3AFF', boxShadow:'0 8px 18px -8px rgba(74,58,255,.7)'}}>
                                            Submit
                                        </button>
                                        {timeline[0].taskFilePath && (
                                            <a href={timeline[0].taskFilePath} download
                                                className="flex-1 py-2.5 rounded-xl text-center font-semibold text-[13px] text-[#8A90A8]"
                                                style={{background:'#F6F7FB'}}>
                                                Brief
                                            </a>
                                        )}
                                    </div>
                                </div>
                            )}

                            {/* Upcoming deadlines */}
                            <div>
                                <p className="text-[9.5px] uppercase text-[#8A90A8] mb-3 px-1" style={{fontFamily:"'JetBrains Mono', monospace", letterSpacing:'0.08em', fontWeight:500}}>Upcoming Deadlines</p>
                                {timeline.length === 0 ? (
                                    <p className="text-center py-8 text-sm text-[#8A90A8]">No upcoming deadlines — you're all caught up!</p>
                                ) : (
                                    <div className="space-y-2">
                                        {timeline.map(item => {
                                            const { bar } = getDesignModuleColor(item.moduleId);
                                            return (
                                                <div key={item.sessionId}
                                                    className="bg-white rounded-2xl flex items-center overflow-hidden cursor-pointer active:opacity-80"
                                                    style={{boxShadow:'var(--card-shadow)'}}
                                                    onClick={() => { handleTabChange('modules'); openModuleDetails(item.moduleId); setActiveUploadSessionId(item.sessionId); }}>
                                                    <div className="w-[6px] self-stretch flex-shrink-0" style={{background: bar, minHeight:'52px'}} />
                                                    <div className="flex-1 py-3 px-3 min-w-0">
                                                        <p className="font-semibold text-[12.5px] text-[#101425] truncate">{toSentenceCase(item.moduleName)}</p>
                                                        <p className="text-[11px] text-[#8A90A8] truncate">{toSentenceCase(item.slotTitle)}</p>
                                                    </div>
                                                    <div className="pr-3 flex-shrink-0">
                                                        <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold"
                                                            style={{background:'#FDF2E2', color:'#9A6412'}}>
                                                            {new Date(item.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                                        </span>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>
                )}

                {/* ── Modules List Tab ── */}
                {activeTab === 'modules' && !selectedModule && (
                    <div className="animate-fadeIn">
                        <div className="px-5 pt-12 pb-4 bg-white" style={{borderBottom:'1px solid rgba(16,20,37,0.07)'}}>
                            <h1 className="font-extrabold text-2xl text-[#101425]">My Modules</h1>
                            <p className="text-[12.5px] text-[#8A90A8] mt-0.5">Your enrolled learning modules</p>
                        </div>
                        <div className="px-4 py-4 space-y-4">
                            {/* Search */}
                            <div className="bg-white rounded-2xl flex items-center gap-3 px-4 py-3" style={{border:'1.5px solid #B9BDCC'}}>
                                <MagnifyingGlass size={16} color="#B9BDCC" weight="regular" />
                                <input type="text" value={moduleSearch} onChange={e => setModuleSearch(e.target.value)}
                                    placeholder="Search modules…"
                                    className="flex-1 bg-transparent text-[13px] text-[#101425] placeholder-[#B9BDCC] outline-none" />
                            </div>

                            {loadingModules ? (
                                <div className="space-y-3">{[0,1,2].map(i => <div key={i} className="bg-white rounded-2xl h-20 animate-pulse" />)}</div>
                            ) : filteredModules.length === 0 ? (
                                <p className="text-center py-12 text-sm text-[#8A90A8]">{moduleSearch ? 'No modules match your search.' : 'You are not enrolled in any modules yet.'}</p>
                            ) : (
                                Array.from(new Set(filteredModules.map(m => m.moduleType || 'General'))).map(type => {
                                    const typeModules = filteredModules.filter(m => (m.moduleType || 'General') === type);
                                    return (
                                        <div key={type}>
                                            <p className="text-[9.5px] uppercase text-[#8A90A8] mb-2 px-1" style={{fontFamily:"'JetBrains Mono', monospace", letterSpacing:'0.08em', fontWeight:500}}>{type} Modules</p>
                                            <div className="space-y-3">
                                                {typeModules.map((m, i) => {
                                                    const { bar, badgeBg, badgeFg, badgeBorder } = getDesignModuleColor(m.id);
                                                    return (
                                                        <div key={m.id}
                                                            className="bg-white rounded-2xl overflow-hidden cursor-pointer active:opacity-80"
                                                            style={{boxShadow:'var(--card-shadow)', animationDelay:`${Math.min(i,8)*40}ms`}}
                                                            onClick={() => openModuleDetails(m.id)}>
                                                            <div className="h-1 w-full" style={{background:bar}} />
                                                            <div className="p-4">
                                                                <div className="flex justify-between items-start gap-2">
                                                                    <span className="font-bold text-[14.5px] text-[#101425] leading-snug line-clamp-2" style={{maxWidth:'210px'}}>{toSentenceCase(m.moduleName)}</span>
                                                                    <span className="text-[10px] font-bold px-2 py-0.5 rounded flex-shrink-0"
                                                                        style={{background:badgeBg, color:badgeFg, border:`1px solid ${badgeBorder}`}}>
                                                                        {m.moduleCode || 'N/A'}
                                                                    </span>
                                                                </div>
                                                                <p className="text-[11.5px] text-[#8A90A8] mt-1.5">{m.lecturerName || 'Unassigned'}</p>
                                                            </div>
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                        </div>
                                    );
                                })
                            )}
                        </div>
                    </div>
                )}

                {/* ── Submissions Tab ── */}
                {activeTab === 'history' && !selectedModule && (
                    <div className="animate-fadeIn">
                        <div className="px-5 pt-12 pb-4 bg-white" style={{borderBottom:'1px solid rgba(16,20,37,0.07)'}}>
                            <h1 className="font-extrabold text-2xl text-[#101425]">My Submissions</h1>
                            <p className="text-[12.5px] text-[#8A90A8] mt-0.5">{history.length} submission{history.length !== 1 ? 's' : ''}</p>
                        </div>
                        <div className="px-4 py-4 space-y-3">
                            {history.length === 0 ? (
                                <p className="text-center py-12 text-sm text-[#8A90A8]">You haven't uploaded any assignments yet.</p>
                            ) : (
                                history.map((sub, i) => (
                                    <div key={sub.submissionId} className="bg-white rounded-2xl p-4 space-y-3" style={{boxShadow:'var(--card-shadow)'}}>
                                        <div className="flex justify-between items-start gap-2">
                                            <div className="min-w-0">
                                                <h4 className="font-bold text-[14px] text-[#101425] truncate">{sub.moduleName || 'General'}</h4>
                                                <p className="text-[11.5px] text-[#8A90A8] mt-0.5 truncate">{sub.assignmentTitle || 'Assignment'} ({sub.sessionName})</p>
                                                <p className="text-[10px] mt-1" style={{color:'#B9BDCC'}}>{new Date(sub.submittedAt).toLocaleDateString()}</p>
                                            </div>
                                            <span className={`inline-flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1 rounded-full flex-shrink-0 ${getStatusBadgeClasses(sub.status, 'light')}`}>
                                                <span className={`w-1.5 h-1.5 rounded-full ${getStatusDotClasses(sub.status)}`}></span>
                                                {getStatusLabel(sub.status || 'SUBMITTED')}
                                            </span>
                                        </div>
                                        <div className="flex items-center justify-between pt-1" style={{borderTop:'1px solid rgba(16,20,37,0.06)'}}>
                                            {sub.gradedByRole ? <GraderBadge role={sub.gradedByRole} name={sub.gradedByName} theme="light" /> : <span />}
                                            <button onClick={() => setViewingSubmission(sub)}
                                                className="text-[12px] font-semibold ml-auto" style={{color:'#4A3AFF'}}>
                                                View →
                                            </button>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                )}

                {/* ── Messages Tab ── */}
                {activeTab === 'messages' && !selectedModule && (
                    <div className="animate-fadeIn">
                        <div className="px-5 pt-12 pb-4 bg-white" style={{borderBottom:'1px solid rgba(16,20,37,0.07)'}}>
                            <h1 className="font-extrabold text-2xl text-[#101425]">Messages</h1>
                            <p className="text-[12.5px] text-[#8A90A8] mt-0.5">Your facilitator conversations</p>
                        </div>
                        <div className="px-4 py-4">
                            <MessagesPanel
                                theme="light"
                                currentSenderType="LEARNER"
                                fetchThreads={fetchStudentThreads}
                                fetchThread={fetchStudentThread}
                                sendMessage={sendStudentMessage}
                            />
                        </div>
                    </div>
                )}

                {/* ── Profile Tab ── */}
                {activeTab === 'profile' && !selectedModule && (
                    <div className="px-4 pt-12 pb-6 space-y-4 animate-fadeIn">
                        {/* Avatar + name + student number */}
                        <div className="flex flex-col items-center gap-2 pt-6 pb-2">
                            <div className="w-16 h-16 flex items-center justify-center text-white font-bold text-2xl"
                                style={{background:'#4A3AFF', borderRadius:'22px'}}>
                                {learner.fullName?.split(' ').filter(Boolean).map(n => n[0]).join('').toUpperCase().slice(0, 2) || 'S'}
                            </div>
                            <h2 className="text-lg text-[#101425] mt-1" style={{fontWeight:800, letterSpacing:'-0.02em'}}>{learner.fullName}</h2>
                            <p className="text-[11px] text-[#8A90A8]" style={{fontFamily:"'JetBrains Mono', monospace", letterSpacing:'0.06em', fontWeight:500}}>{studentNumber}</p>
                        </div>

                        {/* Stats — no border, #F6F7FB bg */}
                        <div className="grid grid-cols-3 gap-3">
                            {[
                                { label: 'Modules',   value: modules.length },
                                { label: 'Competent', value: history.filter(s => s.status === 'COMPETENT').length },
                                { label: 'Uploads',   value: history.length },
                            ].map(({ label, value }) => (
                                <div key={label} className="text-center py-3 rounded-[14px]" style={{background:'#F6F7FB'}}>
                                    <p className="text-xl font-bold text-[#101425]" style={{fontVariantNumeric:'tabular-nums', letterSpacing:'-0.02em'}}>{value}</p>
                                    <p className="text-[9.5px] text-[#8A90A8] uppercase mt-0.5" style={{fontFamily:"'JetBrains Mono', monospace", letterSpacing:'0.08em', fontWeight:500}}>{label}</p>
                                </div>
                            ))}
                        </div>

                        {/* Info rows */}
                        <div className="bg-white rounded-2xl overflow-hidden" style={{boxShadow:'var(--card-shadow)'}}>
                            {[
                                { label: 'Cohort',       value: learner.cohort || 'Unassigned' },
                                { label: 'Learnership',  value: learner.learnershipName || 'Unassigned' },
                                { label: 'Email',        value: learner.email || 'Not provided' },
                            ].map(({ label, value }, i, arr) => (
                                <div key={label} className="flex justify-between items-center px-5 py-4"
                                    style={i < arr.length - 1 ? {borderBottom:'1px solid rgba(16,20,37,0.06)'} : {}}>
                                    <span className="text-[10px] text-[#8A90A8] uppercase" style={{fontFamily:"'JetBrains Mono', monospace", letterSpacing:'0.08em', fontWeight:500}}>{label}</span>
                                    <span className="text-[12.5px] font-semibold text-[#101425] text-right max-w-[55%] truncate">{value}</span>
                                </div>
                            ))}
                        </div>

                        {/* Notification toggles */}
                        <div className="bg-white rounded-2xl overflow-hidden" style={{boxShadow:'var(--card-shadow)'}}>
                            <div className="px-5 py-3" style={{borderBottom:'1px solid rgba(16,20,37,0.06)'}}>
                                <p className="text-[9.5px] uppercase text-[#8A90A8]" style={{fontFamily:"'JetBrains Mono', monospace", letterSpacing:'0.08em', fontWeight:500}}>Notifications</p>
                            </div>
                            {[
                                { label: 'Deadline Reminders',   checked: notifDeadlines,  setter: setNotifDeadlines  },
                                { label: 'Grade Alerts',          checked: notifGrades,     setter: setNotifGrades     },
                                { label: 'Wi-Fi Only Downloads',  checked: notifWifiOnly,   setter: setNotifWifiOnly   },
                            ].map(({ label, checked, setter }, i, arr) => (
                                <div key={label} className="flex justify-between items-center px-5 py-4"
                                    style={i < arr.length - 1 ? {borderBottom:'1px solid rgba(16,20,37,0.06)'} : {}}>
                                    <span className="text-[12.5px] font-semibold text-[#101425]">{label}</span>
                                    <button onClick={() => setter(v => !v)}
                                        className="relative flex-shrink-0 rounded-full"
                                        style={{width:'44px', height:'26px', background: checked ? '#4A3AFF' : '#E9E9EF', boxShadow: checked ? 'inset 0 1px 3px rgba(0,0,0,0.2)' : 'none', transition:'all 0.2s ease'}}
                                        aria-label={`Toggle ${label}`}>
                                        <span className="absolute top-[3px] w-[20px] h-[20px] bg-white rounded-full"
                                            style={{transform: checked ? 'translateX(21px)' : 'translateX(3px)', boxShadow:'0 1px 4px rgba(0,0,0,0.18)', transition:'all 0.2s ease'}} />
                                    </button>
                                </div>
                            ))}
                        </div>

                        {/* Sign out */}
                        <button onClick={handleSignout}
                            className="w-full font-semibold text-[13px] bg-white"
                            style={{padding:'14px', borderRadius:'16px', color:'#B03A32', border:'1px solid rgba(224,82,74,0.3)'}}>
                            Sign Out
                        </button>
                    </div>
                )}

            </div>{/* end pb-[84px] scroll area */}

            {/* ── Fixed Bottom Navigation ── */}
            {!selectedModule && (
                <nav className="fixed bottom-0 left-0 right-0 z-40 bg-white flex items-end justify-around"
                    style={{borderTop:'1px solid rgba(16,20,37,0.07)', padding:'10px 14px 22px'}}>
                    {NAV_TABS.map(({ id, label, Icon }) => {
                        const active = activeTab === id;
                        return (
                            <button key={id} onClick={() => handleTabChange(id)}
                                className="flex flex-col items-center gap-[3px] relative min-w-[44px]">
                                <Icon size={22} weight={active ? 'fill' : 'regular'}
                                    color={active ? '#4A3AFF' : '#D5D7E0'} />
                                <span className="text-[10px] leading-tight"
                                    style={{fontWeight: active ? 600 : 500, color: active ? '#4A3AFF' : '#8A90A8'}}>
                                    {label}
                                </span>
                                {id === 'messages' && unreadMessages > 0 && (
                                    <span className="absolute -top-1 right-1 w-4 h-4 rounded-full text-[8px] font-bold text-white flex items-center justify-center"
                                        style={{background:'#E0524A'}}>
                                        {unreadMessages}
                                    </span>
                                )}
                            </button>
                        );
                    })}
                </nav>
            )}

            {/* ── Floating Chatbot ── */}
            <div className="fixed bottom-[84px] right-4 z-50 flex flex-col items-end">
                {chatOpen && (
                    <div className="w-[340px] h-[440px] rounded-2xl shadow-2xl flex flex-col overflow-hidden mb-3 text-white animate-fadeIn"
                        style={{background:'#101425', border:'1px solid rgba(255,255,255,0.08)'}}>
                        <div className="p-4 flex justify-between items-center" style={{background:'rgba(255,255,255,0.04)', borderBottom:'1px solid rgba(255,255,255,0.08)'}}>
                            <div className="flex items-center gap-2">
                                <div className="w-2 h-2 bg-[#16A97A] rounded-full animate-pulse" />
                                <span className="text-[11px] font-bold uppercase tracking-wider">LMS Assistant</span>
                            </div>
                            <button onClick={() => setChatOpen(false)} className="text-white/40 hover:text-white/80">
                                <X size={15} weight="bold" />
                            </button>
                        </div>
                        <div className="flex-1 overflow-y-auto p-4 space-y-3">
                            {chatMessages.map((msg, idx) => (
                                <div key={idx} className={`flex ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
                                    <div className={`max-w-[85%] rounded-2xl px-3.5 py-2 text-[11px] leading-relaxed whitespace-pre-line ${
                                        msg.sender === 'user'
                                            ? 'text-white rounded-br-none'
                                            : 'text-white/80 rounded-bl-none'
                                    }`} style={msg.sender === 'user' ? {background:'#4A3AFF'} : {background:'rgba(255,255,255,0.08)'}}>
                                        {msg.text}
                                    </div>
                                </div>
                            ))}
                            {chatLoading && (
                                <div className="flex justify-start">
                                    <div className="rounded-2xl rounded-bl-none px-3.5 py-2 text-[11px] flex items-center gap-1" style={{background:'rgba(255,255,255,0.08)'}}>
                                        {[0,160,320].map(d => <span key={d} className="w-1 h-1 bg-white/40 rounded-full animate-typing-pulse" style={{animationDelay:`${d}ms`}} />)}
                                    </div>
                                </div>
                            )}
                            <div ref={chatEndRef} />
                        </div>
                        <form onSubmit={handleSendChatMessage} className="p-3 flex gap-2" style={{borderTop:'1px solid rgba(255,255,255,0.08)'}}>
                            <input type="text" value={chatQuery} onChange={e => setChatQuery(e.target.value)}
                                placeholder="Ask me something…"
                                className="flex-1 rounded-xl px-3 py-1.5 text-xs text-white outline-none focus:ring-1"
                                style={{background:'rgba(255,255,255,0.08)', border:'1px solid rgba(255,255,255,0.1)'}} />
                            <button type="submit" className="p-2 rounded-xl flex items-center justify-center" style={{background:'#4A3AFF'}}>
                                <PaperPlaneTilt size={15} weight="fill" />
                            </button>
                        </form>
                    </div>
                )}
                <button onClick={() => setChatOpen(!chatOpen)}
                    className="w-12 h-12 text-white rounded-full flex items-center justify-center shadow-xl active:scale-95 transition-all cursor-pointer"
                    style={{background:'#4A3AFF', boxShadow:'0 8px 20px -6px rgba(74,58,255,.6)'}}
                    title="Open LMS Chat Assistant">
                    {chatOpen ? <X size={18} weight="bold" /> : <ChatCircleDots size={20} weight="fill" />}
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
