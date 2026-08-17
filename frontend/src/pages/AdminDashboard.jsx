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

    const handleCreateLecturer = async (e) => {
        e.preventDefault();
        try {
            const res = await fetch('/api/admin/lecturers', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Basic ${token}`
                },
                body: JSON.stringify({ fullName, email, username, password })
            });
            if (!checkAuthResponse(res)) return;

            if (res.ok) {
                showMsg('success', 'Lecturer registered successfully!');
                setFullName('');
                setEmail('');
                setUsername('');
                setPassword('');
                fetchLecturers();
                fetchOverview();
            } else {
                showMsg('error', 'Failed to register lecturer account (username may already be taken).');
            }
        } catch (err) {
            showMsg('error', 'Connection failed.');
        }
    };

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
                                        ${cardB1}
                                        ${cardB2}
                                    </div>
                                    <div>
                                        ${cardC}
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
                                ${cardD}
                            </div>
                        )}

                        {activeTab === 'staff' && (
                            <div className="space-y-6">
                                <div className="border-b border-slate-300 pb-3">
                                    <h2 className="text-lg font-bold text-slate-900">Staff Registry</h2>
                                    <p className="text-xs text-slate-500">Register and manage facilitators, moderators, and assessors.</p>
                                </div>
                                ${cardA}
                                ${cardD2}
                                ${cardD3}
                                ${cardD4}
                            </div>
                        )}

                        {activeTab === 'students' && (
                            <div className="space-y-6">
                                <div className="border-b border-slate-300 pb-3">
                                    <h2 className="text-lg font-bold text-slate-900">Student Directory</h2>
                                    <p className="text-xs text-slate-500">View and manage registered student profiles.</p>
                                </div>
                                ${cardE}
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
