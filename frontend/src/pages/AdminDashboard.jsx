import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const AdminDashboard = () => {
    const navigate = useNavigate();
    const token = sessionStorage.getItem('admin_auth');

    // Auth guard
    useEffect(() => {
        if (!token) {
            navigate('/admin-login');
        }
    }, [token, navigate]);

    if (!token) return null;

    // State variables
    const [lecturers, setLecturers] = useState([]);
    const [modules, setModules] = useState([]);
    const [categories, setCategories] = useState([]);
    const [learnerships, setLearnerships] = useState([]);
    const [selectedLearnershipId, setSelectedLearnershipId] = useState('');
    const [overview, setOverview] = useState({ lecturersCount: 0, modulesCount: 0, submissionsCount: 0 });
    const [alert, setAlert] = useState({ type: '', message: '' });
    const [registrationOpen, setRegistrationOpen] = useState(true);
    const [loadingRegStatus, setLoadingRegStatus] = useState(false);
    const [learners, setLearners] = useState([]);
    const [editingLecturer, setEditingLecturer] = useState(null);
    const [activeTab, setActiveTab] = useState('overview');

    // Learnership form
    const [learnershipName, setLearnershipName] = useState('');
    const [qualificationCode, setQualificationCode] = useState('');

    // Category form
    const [newCategoryName, setNewCategoryName] = useState('');

    // Lecturer form
    const [fullName, setFullName] = useState('');
    const [email, setEmail] = useState('');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);

    // Module form
    const [moduleName, setModuleName] = useState('');
    const [moduleCode, setModuleCode] = useState('');
    const [categoryId, setCategoryId] = useState('');

    useEffect(() => {
        if (token) {
            fetchOverview();
            fetchLecturers();
            fetchModules();
            fetchCategories();
            fetchLearnerships();
            fetchRegistrationStatus();
            fetchLearners();
        }
    }, [token]);

    const showMsg = (type, message) => {
        setAlert({ type, message });
        setTimeout(() => setAlert({ type: '', message: '' }), 5000);
    };

    const checkAuthResponse = (res) => {
        if (res.status === 401) {
            sessionStorage.removeItem('admin_auth');
            navigate('/admin-login');
            return false;
        }
        return true;
    };

    const fetchRegistrationStatus = async () => {
        try {
            const res = await fetch('/api/admin/settings/registration-status', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setRegistrationOpen(data.open);
            }
        } catch (e) {
            console.error(e);
        }
    };

    const handleToggleRegistration = async () => {
        setLoadingRegStatus(true);
        try {
            const res = await fetch('/api/admin/settings/registration-status/toggle', {
                method: 'POST',
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setRegistrationOpen(data.open);
                showMsg('success', `Student registration is now ${data.open ? 'OPEN' : 'CLOSED'}.`);
            } else {
                showMsg('error', 'Failed to toggle registration status.');
            }
        } catch (err) {
            showMsg('error', 'Connection failed.');
        } finally {
            setLoadingRegStatus(false);
        }
    };

    const fetchOverview = async () => {
        try {
            const res = await fetch('/api/admin/overview', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setOverview(data);
            }
        } catch (e) {
            console.error(e);
        }
    };

    const fetchLecturers = async () => {
        try {
            const res = await fetch('/api/admin/lecturers', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setLecturers(data);
            }
        } catch (e) {
            console.error(e);
        }
    };

    const fetchModules = async () => {
        try {
            const res = await fetch('/api/admin/modules', {
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

    const fetchCategories = async () => {
        try {
            const res = await fetch('/api/admin/categories', {
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

    const fetchLearners = async () => {
        try {
            const res = await fetch('/api/admin/learners', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setLearners(data);
            }
        } catch (e) {
            console.error(e);
        }
    };

    const handleResetPassword = async (learnerId, learnerName) => {
        const newPassword = prompt(`Enter new password for student ${learnerName}:`);
        if (newPassword === null) return;
        if (!newPassword.trim()) {
            showMsg('error', 'Password cannot be blank.');
            return;
        }
        try {
            const res = await fetch(`/api/admin/learners/${learnerId}/reset-password`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Basic ${token}`
                },
                body: JSON.stringify({ newPassword: newPassword.trim() })
            });
            if (res.ok) {
                showMsg('success', `Password for ${learnerName} reset successfully!`);
            } else {
                showMsg('error', 'Failed to reset password.');
            }
        } catch (e) {
            showMsg('error', 'Network error.');
        }
    };

    const handleDeleteLearner = async (learnerId, learnerName) => {
        if (!confirm(`Are you sure you want to delete student ${learnerName}? This action cannot be undone.`)) return;
        try {
            const res = await fetch(`/api/admin/learners/${learnerId}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (res.ok) {
                showMsg('success', `Student ${learnerName} deleted successfully.`);
                fetchLearners();
            } else {
                showMsg('error', 'Failed to delete student.');
            }
        } catch (e) {
            showMsg('error', 'Network error.');
        }
    };

    const handleDeleteLecturer = async (lecturerId, lecturerName) => {
        if (!confirm(`Are you sure you want to delete facilitator ${lecturerName}? This will unassign them from any active categories. This action cannot be undone.`)) return;
        try {
            const res = await fetch(`/api/admin/lecturers/${lecturerId}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (res.ok) {
                showMsg('success', `Facilitator ${lecturerName} deleted successfully.`);
                fetchLecturers();
                fetchOverview();
            } else {
                showMsg('error', 'Failed to delete facilitator.');
            }
        } catch (e) {
            showMsg('error', 'Network error.');
        }
    };

    const handleAssignLecturer = async (categoryId, lecturerIdVal) => {
        try {
            const res = await fetch(`/api/admin/categories/${categoryId}/assign-lecturer`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Basic ${token}`
                },
                body: JSON.stringify({
                    lecturerId: lecturerIdVal ? parseInt(lecturerIdVal) : null
                })
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                showMsg('success', 'Lecturer assigned to category successfully!');
                fetchCategories();
                fetchModules();
            } else {
                showMsg('error', 'Failed to assign lecturer to category.');
            }
        } catch (err) {
            showMsg('error', 'Connection failed.');
        }
    };

    const fetchLearnerships = async () => {
        try {
            const res = await fetch('/api/admin/learnerships', {
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (!checkAuthResponse(res)) return;
            if (res.ok) {
                const data = await res.json();
                setLearnerships(data);
                if (data.length > 0 && !selectedLearnershipId) {
                    setSelectedLearnershipId(data[0].id.toString());
                }
            }
        } catch (e) {
            console.error(e);
        }
    };

    const handleCreateLearnership = async (e) => {
        e.preventDefault();
        if (!learnershipName.trim()) return;
        try {
            const res = await fetch('/api/admin/learnerships', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Basic ${token}`
                },
                body: JSON.stringify({ name: learnershipName, qualificationCode })
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                const newL = await res.json();
                showMsg('success', 'Learnership created successfully!');
                setLearnershipName('');
                setQualificationCode('');
                fetchLearnerships();
                setSelectedLearnershipId(newL.id.toString());
            } else {
                showMsg('error', 'Failed to create learnership.');
            }
        } catch (err) {
            showMsg('error', 'Connection failed.');
        }
    };

    const handleCreateCategory = async (e) => {
        e.preventDefault();
        if (!newCategoryName.trim() || !selectedLearnershipId) return;
        try {
            const res = await fetch(`/api/admin/learnerships/${selectedLearnershipId}/categories`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Basic ${token}`
                },
                body: JSON.stringify({ categoryType: newCategoryName })
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                showMsg('success', 'Category added under learnership successfully!');
                setNewCategoryName('');
                fetchCategories();
            } else {
                showMsg('error', 'Failed to add category under learnership.');
            }
        } catch (err) {
            showMsg('error', 'Connection failed.');
        }
    };

        const handleCreateStaff = async (e) => {
        e.preventDefault();
        let endpoint = '/api/admin/lecturers';
        let successMsg = 'Facilitator registered successfully!';
        if (staffRole === 'MODERATOR') {
            endpoint = '/api/admin/moderators';
            successMsg = 'Moderator registered successfully!';
        } else if (staffRole === 'ASSESSOR') {
            endpoint = '/api/admin/assessors';
            successMsg = 'Assessor registered successfully!';
        }

        try {
            const res = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Basic ${token}`
                },
                body: JSON.stringify({ fullName, email, username, password })
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                showMsg('success', successMsg);
                setFullName('');
                setEmail('');
                setUsername('');
                setPassword('');
                fetchLecturers();
                fetchModerators();
                fetchAssessors();
                fetchOverview();
            } else {
                showMsg('error', 'Failed to register account (username may already be taken).');
            }
        } catch (err) {
            showMsg('error', 'Connection failed.');
        }
    };

    const handleDeleteModerator = async (id, name) => {
        if (!confirm(`Are you sure you want to delete moderator ${name}? This action cannot be undone.`)) return;
        try {
            const res = await fetch(`/api/admin/moderators/${id}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (res.ok) {
                showMsg('success', `Moderator ${name} deleted successfully.`);
                fetchModerators();
            } else {
                showMsg('error', 'Failed to delete moderator.');
            }
        } catch (e) {
            showMsg('error', 'Network error.');
        }
    };

    const handleDeleteAssessor = async (id, name) => {
        if (!confirm(`Are you sure you want to delete assessor ${name}? This action cannot be undone.`)) return;
        try {
            const res = await fetch(`/api/admin/assessors/${id}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Basic ${token}` }
            });
            if (res.ok) {
                showMsg('success', `Assessor ${name} deleted successfully.`);
                fetchAssessors();
            } else {
                showMsg('error', 'Failed to delete assessor.');
            }
        } catch (e) {
            showMsg('error', 'Network error.');
        }
    };

    const handleUpdateModerator = async (e) => {
        e.preventDefault();
        try {
            const res = await fetch(`/api/admin/moderators/${editingModerator.id}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Basic ${token}`
                },
                body: JSON.stringify({
                    fullName: editingModerator.fullName,
                    email: editingModerator.email || '',
                    username: editingModerator.username,
                    password: editingModerator.password || ''
                })
            });
            if (res.ok) {
                showMsg('success', 'Moderator updated successfully!');
                setEditingModerator(null);
                fetchModerators();
            } else {
                showMsg('error', 'Failed to update moderator.');
            }
        } catch (err) {
            showMsg('error', 'Network error.');
        }
    };

    const handleUpdateAssessor = async (e) => {
        e.preventDefault();
        try {
            const res = await fetch(`/api/admin/assessors/${editingAssessor.id}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Basic ${token}`
                },
                body: JSON.stringify({
                    fullName: editingAssessor.fullName,
                    email: editingAssessor.email || '',
                    username: editingAssessor.username,
                    password: editingAssessor.password || ''
                })
            });
            if (res.ok) {
                showMsg('success', 'Assessor updated successfully!');
                setEditingAssessor(null);
                fetchAssessors();
            } else {
                showMsg('error', 'Failed to update assessor.');
            }
        } catch (err) {
            showMsg('error', 'Network error.');
        }
    };;

    const handleCreateModule = async (e) => {
        e.preventDefault();
        try {
            const res = await fetch('/api/admin/modules', {
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
                fetchOverview();
            } else {
                showMsg('error', 'Failed to create module.');
            }
        } catch (err) {
            showMsg('error', 'Connection failed.');
        }
    };

    const handleSignout = () => {
        sessionStorage.removeItem('admin_auth');
        navigate('/');
    };

    return (
        <div className="bg-slate-50/80 min-h-screen text-slate-800 antialiased py-8 space-y-8">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8">
                {/* Hero Header */}
                <header className="flex justify-between items-center pb-6 border-b border-slate-300 bg-linear-to-r from-slate-900 to-indigo-950 text-white p-6 rounded-2xl shadow-md mb-6">
                    <div>
                        <h1 className="text-2xl font-bold tracking-tight">Admin Console ⚙️</h1>
                        <p className="text-sm text-slate-300">System Administration & Facilitator Registrations</p>
                    </div>
                    <button onClick={handleSignout} className="border border-white/20 bg-white/10 px-4 py-2 rounded-xl text-xs font-semibold hover:bg-white/20 hover:scale-[0.99] text-white transition-all shadow-xs">
                        Sign Out
                    </button>
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

                
                {/* Main Admin Workspace with Sidebar */}
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 mt-6">
                    {/* Left Navigation Sidebar */}
                    <div className="lg:col-span-3">
                        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl space-y-2 sticky top-6">
                            <div className="pb-3 border-b border-slate-800 mb-2">
                                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider pl-3">Navigation Menu</span>
                            </div>
                            {[
                                { id: 'overview', label: 'Overview & Status', icon: '📊' },
                                { id: 'programs', label: 'Programs & Categories', icon: '🎓' },
                                { id: 'modules', label: 'Modules Directory', icon: '📚' },
                                { id: 'staff', label: 'Staff Registry', icon: '👥' },
                                { id: 'students', label: 'Student Directory', icon: '👤' }
                            ].map(tab => (
                                <button
                                    key={tab.id}
                                    type="button"
                                    onClick={() => setActiveTab(tab.id)}
                                    className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl text-xs font-bold transition-all ${
                                        activeTab === tab.id
                                            ? 'bg-blue-600 text-white shadow-md shadow-blue-500/20'
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
                                    <h2 className="text-lg font-bold text-slate-900">Overview & Status</h2>
                                    <p className="text-xs text-slate-500">System overview metrics and registration status.</p>
                                </div>
                                {/* Overview Stats */}
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
                    <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-5 h-full flex flex-col justify-between">
                        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Facilitators</h3>
                        <span className="text-3xl font-extrabold text-blue-600 mt-2 block">{overview.lecturersCount}</span>
                    </div>
                    <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-5 h-full flex flex-col justify-between">
                        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Active Modules</h3>
                        <span className="text-3xl font-extrabold text-blue-600 mt-2 block">{overview.modulesCount}</span>
                    </div>
                    <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-5 h-full flex flex-col justify-between">
                        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Submissions</h3>
                        <span className="text-3xl font-extrabold text-emerald-600 mt-2 block">{overview.submissionsCount}</span>
                    </div>
                    <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-5 h-full flex flex-col justify-between space-y-3">
                        <div>
                            <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Registration Status</h3>
                            <div className="flex items-center gap-2 mt-2">
                                <span className={`w-2.5 h-2.5 rounded-full ${
                                    registrationOpen ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.6)]' : 'bg-rose-500 shadow-[0_0_8px_rgba(244,63,94,0.6)]'
                                }`} />
                                <span className={`inline-flex items-center gap-1.5 border text-xs font-semibold px-3 py-1 rounded-full ${
                                    registrationOpen ? 'bg-emerald-50 text-emerald-700 border-emerald-200/60' : 'bg-rose-50 text-rose-700 border-rose-200/60'
                                }`}>
                                    {registrationOpen ? 'OPEN' : 'CLOSED'}
                                </span>
                            </div>
                        </div>
                        <button 
                            onClick={handleToggleRegistration} 
                            disabled={loadingRegStatus}
                            className={`w-full text-center text-xs font-semibold py-2 rounded-xl border transition-colors ${
                                registrationOpen 
                                    ? 'bg-rose-50 text-rose-700 border-rose-200 hover:bg-rose-100' 
                                    : 'bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100'
                            }`}
                        >
                            {loadingRegStatus ? 'Updating...' : registrationOpen ? 'Close Registration' : 'Open Registration'}
                        </button>
                    </div>
                </div>
                            </div>
                        )}

                        {activeTab === 'programs' && (
                            <div className="space-y-6">
                                <div className="border-b border-slate-300 pb-3">
                                    <h2 className="text-lg font-bold text-slate-900">Programs & Categories</h2>
                                    <p className="text-xs text-slate-500">Manage learnership programs, categories, and assign facilitators.</p>
                                </div>
                                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                                    <div className="space-y-6">
                                        {/* Card B1: Create Learnership Program */}
                        <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 space-y-4">
                            <div>
                                <h2 className="text-lg font-bold text-slate-900">Create Learnership Program</h2>
                                <p className="text-xs text-slate-500">Define a new qualification container for modules.</p>
                            </div>
                            <form onSubmit={handleCreateLearnership} className="space-y-4">
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Learnership Name *</label>
                                        <input 
                                            type="text" 
                                            value={learnershipName} 
                                            onChange={e => setLearnershipName(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                            required 
                                            placeholder="e.g. IT Systems Support NQF4" 
                                        />
                                    </div>
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Qualification Code</label>
                                        <input 
                                            type="text" 
                                            value={qualificationCode} 
                                            onChange={e => setQualificationCode(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                            placeholder="e.g. 48220" 
                                        />
                                    </div>
                                </div>
                                <button type="submit" className="bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-xs py-2.5 px-5 rounded-xl shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150">
                                    Create Learnership
                                </button>
                            </form>
                        </div>
                                        {/* Card B2: Add Category under Learnership */}
                        <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 space-y-4">
                            <div>
                                <h2 className="text-lg font-bold text-slate-900">Add Category under Learnership</h2>
                                <p className="text-xs text-slate-500">Define new grouping tags (e.g. Fundamental, Practical) for a learnership.</p>
                            </div>
                            <form onSubmit={handleCreateCategory} className="space-y-4">
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Select Learnership *</label>
                                        <select 
                                            value={selectedLearnershipId} 
                                            onChange={e => setSelectedLearnershipId(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                            required
                                        >
                                            <option value="">-- Choose Learnership --</option>
                                            {learnerships.map(l => (
                                                <option key={l.id} value={l.id}>{l.name} ({l.qualificationCode || 'No Code'})</option>
                                            ))}
                                        </select>
                                    </div>
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Category Name *</label>
                                        <input 
                                            type="text" 
                                            value={newCategoryName} 
                                            onChange={e => setNewCategoryName(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                            required 
                                            placeholder="e.g. Fundamental, Elective, Practical" 
                                        />
                                    </div>
                                </div>
                                <button type="submit" className="bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-xs py-2.5 px-5 rounded-xl shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150" disabled={!selectedLearnershipId}>
                                    Add Category
                                </button>
                            </form>
                        </div>
                                    </div>
                                    <div>
                                        {/* Card C: Category Facilitator Assignment */}
                        <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 space-y-4">
                            <div>
                                <h2 className="text-lg font-bold text-slate-900">Category Facilitator Assignment</h2>
                                <p className="text-xs text-slate-500">SETA learnership module ownership is assigned by Category per Learnership.</p>
                            </div>
                            <div className="space-y-4">
                                {categories.map(c => {
                                    const learnership = learnerships.find(l => l.id === c.learnershipId);
                                    const learnershipNameText = learnership ? `${learnership.name}` : 'Unknown Learnership';
                                    return (
                                        <div key={c.id} className="bg-slate-50/50 border border-slate-200 rounded-xl p-4 space-y-2">
                                            <span className="text-[10px] font-bold text-blue-600 uppercase tracking-wider">{learnershipNameText}</span>
                                            <h3 className="text-sm font-bold text-slate-800">{c.categoryType} Category</h3>
                                            <p className="text-xs text-slate-600">
                                                Currently: <strong className="text-blue-600 font-semibold">{c.lecturerName || 'Unassigned'}</strong>
                                            </p>
                                            <div className="pt-2">
                                                <select 
                                                    value={c.lecturerId || ''} 
                                                    onChange={e => handleAssignLecturer(c.id, e.target.value)} 
                                                    className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                                >
                                                    <option value="">-- Assign Facilitator --</option>
                                                    {lecturers.map(l => (
                                                        <option key={l.id} value={l.id}>{l.fullName} ({l.username})</option>
                                                    ))}
                                                </select>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                                    </div>
                                </div>
                            </div>
                        )}

                        {activeTab === 'modules' && (
                            <div className="space-y-6">
                                <div className="border-b border-slate-300 pb-3">
                                    <h2 className="text-lg font-bold text-slate-900">Modules Directory</h2>
                                    <p className="text-xs text-slate-500">Manage curriculum modules and syllabus content.</p>
                                </div>
                                {/* Card D: All Modules Overview Table */}
                        <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-5 overflow-hidden space-y-4">
                            <div>
                                <h2 className="text-lg font-bold text-slate-900">All Modules Overview</h2>
                                <p className="text-xs text-slate-500">Review created modules, code mapping, assigned facilitators, and syllabus files.</p>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="w-full border-collapse text-xs">
                                    <thead>
                                        <tr className="bg-slate-100/70 text-slate-500 text-[10px] font-bold uppercase tracking-wider border-b border-slate-300">
                                            <th className="p-3 text-left rounded-l-lg">Code</th>
                                            <th className="p-3 text-left">Module</th>
                                            <th className="p-3 text-left">Facilitator</th>
                                            <th className="p-3 text-right rounded-r-lg">Syllabus</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-slate-100">
                                        {modules.length === 0 ? (
                                            <tr>
                                                <td colSpan="4" className="text-center py-4 text-slate-400">No modules created yet.</td>
                                            </tr>
                                        ) : (
                                            modules.map(m => (
                                                <tr key={m.id} className="hover:bg-blue-50/50 transition-colors cursor-pointer">
                                                    <td className="p-3 font-bold text-slate-700">
                                                        <span className="bg-slate-200/60 text-slate-700 font-bold px-2 py-0.5 rounded text-[10px] whitespace-nowrap">
                                                            Code: {m.moduleCode || 'N/A'}
                                                        </span>
                                                    </td>
                                                    <td className="p-3 font-semibold text-slate-900">{m.moduleName}</td>
                                                    <td className="p-3 text-slate-500">{m.lecturerName || 'Unassigned'}</td>
                                                    <td className="p-3 text-right text-blue-600 hover:text-blue-800">
                                                        {m.filePath ? (
                                                            <a href={m.filePath} className="font-bold" download>
                                                                Download
                                                            </a>
                                                        ) : (
                                                            <span className="text-slate-400 italic">None</span>
                                                        )}
                                                    </td>
                                                </tr>
                                            ))
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                            </div>
                        )}

                        {activeTab === 'staff' && (
                            <div className="space-y-6">
                                <div className="border-b border-slate-300 pb-3">
                                    <h2 className="text-lg font-bold text-slate-900">Staff Registry</h2>
                                    <p className="text-xs text-slate-500">Register and manage facilitators, moderators, and assessors.</p>
                                </div>
                                {/* Card A: Register New Staff Member */}
                        <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 space-y-4">
                            <div>
                                <h2 className="text-lg font-bold text-slate-900">Register New Staff Member</h2>
                                <p className="text-xs text-slate-500">Create staff profiles with dedicated portal authentication credentials.</p>
                            </div>
                            <form onSubmit={handleCreateStaff} className="space-y-4">
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Full Name *</label>
                                        <input 
                                            type="text" 
                                            value={fullName} 
                                            onChange={e => setFullName(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                            required 
                                            placeholder="e.g. Dr. Jane Smith" 
                                        />
                                    </div>
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Staff Role *</label>
                                        <select 
                                            value={staffRole} 
                                            onChange={e => setStaffRole(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs"
                                        >
                                            <option value="LECTURER">Facilitator (Lecturer)</option>
                                            <option value="MODERATOR">Moderator</option>
                                            <option value="ASSESSOR">Assessor (Accessor)</option>
                                        </select>
                                    </div>
                                </div>
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Email Address</label>
                                        <input 
                                            type="email" 
                                            value={email} 
                                            onChange={e => setEmail(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                            placeholder="e.g. janesmith@example.com" 
                                        />
                                    </div>
                                    <div className="space-y-1.5">
                                        <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Username *</label>
                                        <input 
                                            type="text" 
                                            value={username} 
                                            onChange={e => setUsername(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                            required 
                                            placeholder="e.g. janesmith" 
                                        />
                                    </div>
                                </div>
                                <div className="space-y-1.5">
                                    <label className="block text-[11px] font-bold tracking-wider text-slate-400 uppercase mb-1.5">Initial Password *</label>
                                    <div className="relative">
                                        <input 
                                            type={showPassword ? "text" : "password"} 
                                            value={password} 
                                            onChange={e => setPassword(e.target.value)} 
                                            className="w-full bg-white border border-slate-200 rounded-xl pl-3.5 pr-10 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-500/10 transition-all shadow-2xs" 
                                            required 
                                            placeholder="••••••••" 
                                        />
                                        <button
                                            type="button"
                                            onClick={() => setShowPassword(!showPassword)}
                                            className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 focus:outline-none"
                                        >
                                            {showPassword ? (
                                                <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88" />
                                                </svg>
                                            ) : (
                                                <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                                                    <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                                </svg>
                                            )}
                                        </button>
                                    </div>
                                </div>
                                <button type="submit" className="bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-xs py-2.5 px-5 rounded-xl shadow-xs shadow-blue-500/20 hover:shadow-md hover:shadow-blue-500/25 transition-all duration-150">
                                    Register {staffRole.charAt(0) + staffRole.slice(1).toLowerCase()}
                                </button>
                            </form>
                        </div>
                                {/* Card D-2: Registered Facilitators (Full Width) */}
                <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 space-y-4">
                    <div>
                        <h2 className="text-lg font-bold text-slate-900">Registered Facilitators (Lecturers)</h2>
                        <p className="text-xs text-slate-500">View registered facilitators, their contact details, usernames, and delete accounts.</p>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full border-collapse text-xs">
                            <thead>
                                <tr className="bg-slate-100/70 text-slate-500 text-[10px] font-bold uppercase tracking-wider border-b border-slate-300">
                                    <th className="p-3 text-left rounded-l-lg">Username</th>
                                    <th className="p-3 text-left">Full Name</th>
                                    <th className="p-3 text-left">Email Address</th>
                                    <th className="p-3 text-right rounded-r-lg">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {lecturers.length === 0 ? (
                                    <tr>
                                        <td colSpan="4" className="p-4 text-center text-slate-400 font-medium italic">No facilitators registered in the system.</td>
                                    </tr>
                                ) : (
                                    lecturers.map(l => (
                                        <tr key={l.id} className="hover:bg-slate-50/60 transition-colors">
                                            <td className="p-3 font-bold text-blue-600">{l.username}</td>
                                            <td className="p-3 font-semibold text-slate-900">{l.fullName}</td>
                                            <td className="p-3 text-slate-500">{l.email || 'N/A'}</td>
                                            <td className="p-3 text-right whitespace-nowrap space-x-2">
                                                <button 
                                                    onClick={() => setEditingLecturer({ id: l.id, fullName: l.fullName, email: l.email || '', username: l.username, password: '' })}
                                                    className="bg-blue-50 text-blue-600 border border-blue-200/50 hover:bg-blue-100 hover:text-blue-700 font-semibold px-2.5 py-1 rounded-lg transition-all"
                                                >
                                                    Edit
                                                </button>
                                                <button 
                                                    onClick={() => handleDeleteLecturer(l.id, l.fullName)}
                                                    className="bg-rose-50 text-rose-600 border border-rose-200/50 hover:bg-rose-100 hover:text-rose-700 font-semibold px-2.5 py-1 rounded-lg transition-all"
                                                >
                                                    Delete
                                                </button>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
                                {/* Card D-3: Registered Moderators (Full Width) */}
                <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 space-y-4">
                    <div>
                        <h2 className="text-lg font-bold text-slate-900 font-display">Registered Moderators</h2>
                        <p className="text-xs text-slate-500">View registered moderators, usernames, contact emails, and manage accounts.</p>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full border-collapse text-xs">
                            <thead>
                                <tr className="bg-slate-100/70 text-slate-500 text-[10px] font-bold uppercase tracking-wider border-b border-slate-300">
                                    <th className="p-3 text-left rounded-l-lg">Username</th>
                                    <th className="p-3 text-left">Full Name</th>
                                    <th className="p-3 text-left">Email Address</th>
                                    <th className="p-3 text-right rounded-r-lg">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100 font-sans">
                                {moderators.length === 0 ? (
                                    <tr>
                                        <td colSpan="4" className="p-4 text-center text-slate-400 font-medium italic">No moderators registered in the system.</td>
                                    </tr>
                                ) : (
                                    moderators.map(m => (
                                        <tr key={m.id} className="hover:bg-slate-50/60 transition-colors">
                                            <td className="p-3 font-bold text-blue-600">{m.username}</td>
                                            <td className="p-3 font-semibold text-slate-900">{m.fullName}</td>
                                            <td className="p-3 text-slate-500">{m.email || 'N/A'}</td>
                                            <td className="p-3 text-right whitespace-nowrap space-x-2">
                                                <button 
                                                    onClick={() => setEditingModerator({ id: m.id, fullName: m.fullName, email: m.email || '', username: m.username, password: '' })}
                                                    className="bg-blue-50 text-blue-600 border border-blue-200/50 hover:bg-blue-100 hover:text-blue-700 font-semibold px-2.5 py-1 rounded-lg transition-all"
                                                >
                                                    Edit
                                                </button>
                                                <button 
                                                    onClick={() => handleDeleteModerator(m.id, m.fullName)}
                                                    className="bg-rose-50 text-rose-600 border border-rose-200/50 hover:bg-rose-100 hover:text-rose-700 font-semibold px-2.5 py-1 rounded-lg transition-all"
                                                >
                                                    Delete
                                                </button>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>

                {/* Card D-4: Registered Assessors (Full Width) */}
                <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 space-y-4">
                    <div>
                        <h2 className="text-lg font-bold text-slate-900 font-display">Registered Assessors</h2>
                        <p className="text-xs text-slate-500">View registered assessors, usernames, contact emails, and manage accounts.</p>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full border-collapse text-xs">
                            <thead>
                                <tr className="bg-slate-100/70 text-slate-500 text-[10px] font-bold uppercase tracking-wider border-b border-slate-300">
                                    <th className="p-3 text-left rounded-l-lg">Username</th>
                                    <th className="p-3 text-left">Full Name</th>
                                    <th className="p-3 text-left">Email Address</th>
                                    <th className="p-3 text-right rounded-r-lg">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100 font-sans">
                                {assessors.length === 0 ? (
                                    <tr>
                                        <td colSpan="4" className="p-4 text-center text-slate-400 font-medium italic">No assessors registered in the system.</td>
                                    </tr>
                                ) : (
                                    assessors.map(a => (
                                        <tr key={a.id} className="hover:bg-slate-50/60 transition-colors">
                                            <td className="p-3 font-bold text-blue-600">{a.username}</td>
                                            <td className="p-3 font-semibold text-slate-900">{a.fullName}</td>
                                            <td className="p-3 text-slate-500">{a.email || 'N/A'}</td>
                                            <td className="p-3 text-right whitespace-nowrap space-x-2">
                                                <button 
                                                    onClick={() => setEditingAssessor({ id: a.id, fullName: a.fullName, email: a.email || '', username: a.username, password: '' })}
                                                    className="bg-blue-50 text-blue-600 border border-blue-200/50 hover:bg-blue-100 hover:text-blue-700 font-semibold px-2.5 py-1 rounded-lg transition-all"
                                                >
                                                    Edit
                                                </button>
                                                <button 
                                                    onClick={() => handleDeleteAssessor(a.id, a.fullName)}
                                                    className="bg-rose-50 text-rose-600 border border-rose-200/50 hover:bg-rose-100 hover:text-rose-700 font-semibold px-2.5 py-1 rounded-lg transition-all"
                                                >
                                                    Delete
                                                </button>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
                            </div>
                        )}

                        {activeTab === 'students' && (
                            <div className="space-y-6">
                                <div className="border-b border-slate-300 pb-3">
                                    <h2 className="text-lg font-bold text-slate-900">Student Directory</h2>
                                    <p className="text-xs text-slate-500">View and manage registered student profiles.</p>
                                </div>
                                {/* Card E: Registered Students (Full Width) */}
                <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-6 space-y-4">
                    <div>
                        <h2 className="text-lg font-bold text-slate-900">Registered Students (Learners)</h2>
                        <p className="text-xs text-slate-500">View registered student credentials, active cohorts, and perform manual password resets or account deletion.</p>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full border-collapse text-xs">
                            <thead>
                                <tr className="bg-slate-100/70 text-slate-500 text-[10px] font-bold uppercase tracking-wider border-b border-slate-300">
                                    <th className="p-3 text-left rounded-l-lg">Student Code</th>
                                    <th className="p-3 text-left">Full Name</th>
                                    <th className="p-3 text-left">Email Address</th>
                                    <th className="p-3 text-left">Phone</th>
                                    <th className="p-3 text-left">Cohort</th>
                                    <th className="p-3 text-left">Learnership</th>
                                    <th className="p-3 text-right rounded-r-lg">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {learners.length === 0 ? (
                                    <tr>
                                        <td colSpan="7" className="text-center py-4 text-slate-400">No students registered yet.</td>
                                    </tr>
                                ) : (
                                    learners.map(l => (
                                        <tr key={l.id} className="hover:bg-slate-50/60 transition-colors">
                                            <td className="p-3 font-bold text-blue-600">{l.learnerCode}</td>
                                            <td className="p-3 font-semibold text-slate-900">{l.fullName}</td>
                                            <td className="p-3 text-slate-500">{l.email || 'N/A'}</td>
                                            <td className="p-3 text-slate-500">{l.phoneNumber || 'N/A'}</td>
                                            <td className="p-3 text-slate-500">{l.cohort || 'N/A'}</td>
                                            <td className="p-3 text-slate-600 font-medium">{l.learnershipName || 'Unassigned'}</td>
                                            <td className="p-3 text-right space-x-2 whitespace-nowrap">
                                                <button 
                                                    onClick={() => handleResetPassword(l.id, l.fullName)}
                                                    className="bg-blue-50 text-blue-600 border border-blue-200/50 hover:bg-blue-100 hover:text-blue-700 font-semibold px-2.5 py-1 rounded-lg transition-all"
                                                >
                                                    Reset Password
                                                </button>
                                                <button 
                                                    onClick={() => handleDeleteLearner(l.id, l.fullName)}
                                                    className="bg-rose-50 text-rose-600 border border-rose-200/50 hover:bg-rose-100 hover:text-rose-700 font-semibold px-2.5 py-1 rounded-lg transition-all"
                                                >
                                                    Delete
                                                </button>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>


            {editingLecturer && (
                <div className="fixed inset-0 bg-slate-950/60 backdrop-blur-sm flex items-center justify-center p-4 z-50">
                    <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-xl w-full max-w-md space-y-6 relative text-slate-800">
                        <div className="flex justify-between items-center">
                            <h3 className="text-base font-extrabold text-slate-900">Edit Facilitator</h3>
                            <button 
                                onClick={() => setEditingLecturer(null)}
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
                                const res = await fetch(`/api/admin/lecturers/${editingLecturer.id}`, {
                                    method: 'PUT',
                                    headers: {
                                        'Content-Type': 'application/json',
                                        'Authorization': `Basic ${token}`
                                    },
                                    body: JSON.stringify({
                                        fullName: editingLecturer.fullName,
                                        email: editingLecturer.email,
                                        username: editingLecturer.username,
                                        password: editingLecturer.password || ''
                                    })
                                });
                                if (res.ok) {
                                    showMsg('success', 'Facilitator updated successfully!');
                                    setEditingLecturer(null);
                                    fetchLecturers();
                                } else {
                                    const errMsg = await res.text();
                                    showMsg('error', errMsg || 'Failed to update facilitator.');
                                }
                            } catch (err) {
                                showMsg('error', 'Network error.');
                            }
                        }} className="space-y-4 text-left">
                            <div className="space-y-1.5">
                                <label className="block text-[10px] font-bold tracking-wider text-slate-400 uppercase">Full Name</label>
                                <input 
                                    type="text" 
                                    value={editingLecturer.fullName}
                                    onChange={e => setEditingLecturer({...editingLecturer, fullName: e.target.value})}
                                    className="w-full px-3.5 py-2 text-xs border rounded-xl"
                                    required
                                />
                            </div>
                            <div className="space-y-1.5">
                                <label className="block text-[10px] font-bold tracking-wider text-slate-400 uppercase">Email</label>
                                <input 
                                    type="email" 
                                    value={editingLecturer.email || ''}
                                    onChange={e => setEditingLecturer({...editingLecturer, email: e.target.value})}
                                    className="w-full px-3.5 py-2 text-xs border rounded-xl"
                                />
                            </div>
                            <div className="space-y-1.5">
                                <label className="block text-[10px] font-bold tracking-wider text-slate-400 uppercase">Username</label>
                                <input 
                                    type="text" 
                                    value={editingLecturer.username}
                                    onChange={e => setEditingLecturer({...editingLecturer, username: e.target.value})}
                                    className="w-full px-3.5 py-2 text-xs border rounded-xl"
                                    required
                                />
                            </div>
                            <div className="space-y-1.5">
                                <label className="block text-[10px] font-bold tracking-wider text-slate-400 uppercase">Password (Leave blank to keep current)</label>
                                <input 
                                    type="password" 
                                    placeholder="••••••••"
                                    value={editingLecturer.password || ''}
                                    onChange={e => setEditingLecturer({...editingLecturer, password: e.target.value})}
                                    className="w-full px-3.5 py-2 text-xs border rounded-xl"
                                />
                            </div>
                            <div className="flex justify-end gap-2 pt-2">
                                <button 
                                    type="button" 
                                    onClick={() => setEditingLecturer(null)}
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

export default AdminDashboard;
