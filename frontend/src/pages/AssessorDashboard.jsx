import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import SubmissionMarker from '../components/SubmissionMarker';
import { GraderBadge } from '../utils/graderBadge';
import { getStatusBadgeClasses, getStatusLabel } from '../utils/colors';

const AssessorDashboard = () => {
    const navigate = useNavigate();
    const token = sessionStorage.getItem('assessor_auth');

    // Auth guard
    useEffect(() => {
        if (!token) {
            navigate('/assessor-login');
        }
    }, [token, navigate]);


    // UI state
    const [modules, setModules] = useState([]);
    const [sessions, setSessions] = useState([]);
    const [selectedModuleId, setSelectedModuleId] = useState('');
    const [selectedSessionId, setSelectedSessionId] = useState('');
    const [submissions, setSubmissions] = useState([]);
    const [activeSubmission, setActiveSubmission] = useState(null);
    const [alert, setAlert] = useState({ type: '', message: '' });
    const [loading, setLoading] = useState(false);

    const showMsg = (type, message) => {
        setAlert({ type, message });
        setTimeout(() => setAlert({ type: '', message: '' }), 5000);
    };

    const fetchModules = async () => {
        try {
            const res = await fetch('/api/assessor/modules', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (res.status === 401 || res.status === 403) {
                sessionStorage.removeItem('assessor_auth');
                navigate('/assessor-login');
                return;
            }
            if (res.ok) {
                const data = await res.json();
                setModules(data);
            }
        } catch (e) {
            showMsg('error', 'Failed to fetch modules.');
        }
    };

    const fetchSessions = async () => {
        try {
            const res = await fetch('/api/assessor/sessions', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (res.ok) {
                const data = await res.json();
                setSessions(data);
            }
        } catch (e) {
            showMsg('error', 'Failed to fetch sessions.');
        }
    };

    useEffect(() => {
        fetchModules();
        fetchSessions();
    }, []);

    const fetchSubmissions = async (sessionId) => {
        if (!sessionId) return;
        setLoading(true);
        try {
            const res = await fetch(`/api/assessor/sessions/${sessionId}/submissions`, {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (res.ok) {
                const data = await res.json();
                setSubmissions(data.submitted || []);
            } else {
                showMsg('error', 'Failed to load submissions.');
            }
        } catch (e) {
            showMsg('error', 'Network error.');
        } finally {
            setLoading(false);
        }
    };

    const handleSelectSession = (sessionId) => {
        setSelectedSessionId(sessionId);
        if (sessionId) {
            fetchSubmissions(sessionId);
        } else {
            setSubmissions([]);
        }
    };

    const handleSaveGrade = async (submissionId, payload) => {
        const res = await fetch(`/api/assessor/submissions/${submissionId}/grade`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Basic ${token}`
            },
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            throw new Error('Failed to save outcome.');
        }
        // Refresh submissions
        fetchSubmissions(selectedSessionId);
    };

    const handleSignout = () => {
        sessionStorage.removeItem('assessor_auth');
        navigate('/');
    };

    // Filter sessions to show only those linked to the selected module
    const filteredSessions = sessions.filter(s => {
        if (!selectedModuleId) return true;
        const selectedMod = modules.find(m => m.id === parseInt(selectedModuleId));
        return selectedMod ? s.assignmentTitle.includes(`[${selectedMod.moduleName}]`) : true;
    });

    if (!token) return null;

    return (
        <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
            {/* Header */}
            <header className="px-6 py-4 bg-slate-900 border-b border-slate-800 flex items-center justify-between sticky top-0 z-40">
                <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-indigo-500/10 text-indigo-400 rounded-2xl flex items-center justify-center font-extrabold text-lg shadow-sm border border-indigo-500/15">
                        A
                    </div>
                    <div>
                        <h1 className="text-base font-extrabold text-white">Assessor Marking Portal</h1>
                        <p className="text-[10px] text-slate-500 font-sans tracking-wide uppercase">Review and Grading console</p>
                    </div>
                </div>
                <button 
                    onClick={handleSignout}
                    className="bg-slate-800 hover:bg-slate-700/80 text-white font-semibold text-xs py-2 px-4 rounded-xl transition-all cursor-pointer"
                >
                    Sign Out
                </button>
            </header>

            {/* Alert toast */}
            {alert.message && (
                <div className={`fixed top-18 right-6 z-50 p-4 rounded-2xl text-xs font-semibold shadow-xl border ${
                    alert.type === 'success' ? 'bg-emerald-950/80 border-emerald-800 text-emerald-400' : 'bg-rose-950/80 border-rose-800 text-rose-400'
                }`}>
                    {alert.message}
                </div>
            )}

            {/* Main Area */}
            <main className="flex-1 max-w-7xl w-full mx-auto p-6 grid grid-cols-1 md:grid-cols-4 gap-6">
                {/* Left Side Filter Panels */}
                <div className="md:col-span-1 space-y-6">
                    <div className="bg-slate-900 border border-slate-800/80 rounded-2xl p-5 space-y-4">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">Filters</h3>
                        
                        {/* Modules Filter */}
                        <div className="space-y-1.5">
                            <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-wide">Select Module</label>
                            <select
                                value={selectedModuleId}
                                onChange={(e) => setSelectedModuleId(e.target.value)}
                                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-indigo-500 transition-colors"
                            >
                                <option value="">-- All Modules --</option>
                                {modules.map(m => (
                                    <option key={m.id} value={m.id}>{m.moduleName} ({m.moduleCode})</option>
                                ))}
                            </select>
                        </div>

                        {/* Sessions Selection */}
                        <div className="space-y-1.5">
                            <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-wide">Select Intake Window</label>
                            <select
                                value={selectedSessionId}
                                onChange={(e) => handleSelectSession(e.target.value)}
                                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-indigo-500 transition-colors"
                            >
                                <option value="">-- Select Active Window --</option>
                                {filteredSessions.map(s => (
                                    <option key={s.id} value={s.id}>{s.sessionName}</option>
                                ))}
                            </select>
                        </div>
                    </div>
                </div>

                {/* Right Side Submissions List */}
                <div className="md:col-span-3">
                    <div className="bg-slate-900 border border-slate-800/80 rounded-2xl p-6 h-full flex flex-col justify-between min-h-[60vh]">
                        <div className="space-y-6">
                            <div className="border-b border-slate-800/80 pb-4">
                                <h2 className="text-base font-extrabold text-white">Student Submissions</h2>
                                <p className="text-xs text-slate-400 mt-1">Review and grade submitted documents for the selected session.</p>
                            </div>

                            {loading ? (
                                <div className="text-center py-12 text-slate-400 text-xs animate-pulse">Loading submissions...</div>
                            ) : !selectedSessionId ? (
                                <div className="text-center py-16 text-slate-500 text-xs">
                                    Please select an Intake Window from the sidebar to inspect student submissions.
                                </div>
                            ) : submissions.length === 0 ? (
                                <div className="text-center py-16 text-slate-500 text-xs">
                                    No student submissions have been made for this intake session yet.
                                </div>
                            ) : (
                                <div className="overflow-x-auto">
                                    <table className="w-full text-left text-xs border-collapse">
                                        <thead>
                                            <tr className="border-b border-slate-800 text-slate-400 font-bold uppercase tracking-wider text-[10px]">
                                                <th className="py-3 px-4">Learner Code</th>
                                                <th className="py-3 px-4">Full Name</th>
                                                <th className="py-3 px-4">File Submitted</th>
                                                <th className="py-3 px-4">Submitted At</th>
                                                <th className="py-3 px-4">Outcome</th>
                                                <th className="py-3 px-4">Graded By</th>
                                                <th className="py-3 px-4 text-right">Action</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {submissions.map(sub => (
                                                <tr key={sub.submissionId} className="border-b border-slate-800/50 hover:bg-slate-850/30 transition-colors">
                                                    <td className="py-3 px-4 font-semibold text-slate-300">{sub.learnerCode}</td>
                                                    <td className="py-3 px-4 font-semibold text-slate-200">{sub.fullName}</td>
                                                    <td className="py-3 px-4 text-slate-400 truncate max-w-[150px]" title={sub.originalFilename}>
                                                        {sub.originalFilename}
                                                    </td>
                                                    <td className="py-3 px-4 text-slate-500">
                                                        {sub.submittedAt ? new Date(sub.submittedAt).toLocaleDateString() : 'N/A'}
                                                    </td>
                                                    <td className="py-3 px-4">
                                                        <span className={`px-2 py-0.5 rounded-full text-[9px] font-bold tracking-wider ${getStatusBadgeClasses(sub.status, 'dark')}`}>
                                                            {getStatusLabel(sub.status)}
                                                        </span>
                                                    </td>
                                                    <td className="py-3 px-4">
                                                        <GraderBadge role={sub.gradedByRole} name={sub.gradedByName} theme="dark" />
                                                    </td>
                                                    <td className="py-3 px-4 text-right">
                                                        <button
                                                            onClick={() => setActiveSubmission(sub)}
                                                            className="bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-[10px] py-1.5 px-3 rounded-lg transition-colors cursor-pointer"
                                                        >
                                                            Inspect & Assess
                                                        </button>
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </main>

            {/* Split-pane Assessment Modal */}
            {activeSubmission && (
                <SubmissionMarker
                    submission={activeSubmission}
                    onClose={() => {
                        setActiveSubmission(null);
                        fetchSubmissions(selectedSessionId);
                    }}
                    onSaveGrade={handleSaveGrade}
                    role="Assessor"
                />
            )}
        </div>
    );
};

export default AssessorDashboard;
