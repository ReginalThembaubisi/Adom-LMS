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
        <div className="portal-wrapper">
            <header className="portal-header">
                <div>
                    <h1 style={{ margin: 0, fontSize: '1.85rem', fontWeight: 800, color: 'var(--text-main)' }}>Admin Dashboard</h1>
                    <p className="subtitle">System Administration & Lecturer Registrations</p>
                </div>
                <button onClick={handleSignout} className="signout-btn" style={{ fontWeight: 'bold' }}>Sign Out</button>
            </header>

            {alert.message && (
                <div className={`alert-banner ${alert.type}`} style={{ fontWeight: 'bold' }}>
                    {alert.message}
                </div>
            )}

            {/* Overview Stats */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1.25rem', marginBottom: '2rem' }}>
                <div className="portal-card" style={{ padding: '1.5rem', textAlign: 'center' }}>
                    <h3 style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.9rem', textTransform: 'uppercase' }}>Lecturers</h3>
                    <span style={{ fontSize: '2.5rem', fontWeight: 800, color: 'var(--primary-color)' }}>{overview.lecturersCount}</span>
                </div>
                <div className="portal-card" style={{ padding: '1.5rem', textAlign: 'center' }}>
                    <h3 style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.9rem', textTransform: 'uppercase' }}>Active Modules</h3>
                    <span style={{ fontSize: '2.5rem', fontWeight: 800, color: 'var(--primary-color)' }}>{overview.modulesCount}</span>
                </div>
                <div className="portal-card" style={{ padding: '1.5rem', textAlign: 'center' }}>
                    <h3 style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.9rem', textTransform: 'uppercase' }}>Submissions</h3>
                    <span style={{ fontSize: '2.5rem', fontWeight: 800, color: 'var(--success)' }}>{overview.submissionsCount}</span>
                </div>
                <div className="portal-card" style={{ padding: '1.25rem 1.5rem', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center' }}>
                    <h3 style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.9rem', textTransform: 'uppercase', marginBottom: '0.5rem' }}>Registration Status</h3>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.75rem' }}>
                        <span style={{ 
                            display: 'inline-block', 
                            width: '10px', 
                            height: '10px', 
                            borderRadius: '50%', 
                            backgroundColor: registrationOpen ? 'var(--success)' : 'var(--error)',
                            boxShadow: registrationOpen ? '0 0 8px var(--success)' : '0 0 8px var(--error)'
                        }}></span>
                        <strong style={{ fontSize: '1.1rem', color: registrationOpen ? 'var(--success)' : 'var(--error)' }}>
                            {registrationOpen ? 'OPEN' : 'CLOSED'}
                        </strong>
                    </div>
                    <button 
                        onClick={handleToggleRegistration} 
                        disabled={loadingRegStatus}
                        className="submit-btn"
                        style={{ 
                            fontSize: '0.8rem', 
                            padding: '0.35rem 0.75rem', 
                            margin: 0, 
                            background: registrationOpen ? '#fef2f2' : '#ecfdf5',
                            color: registrationOpen ? '#dc2626' : '#16a34a',
                            border: `1px solid ${registrationOpen ? '#dc2626' : '#16a34a'}`,
                            width: 'auto',
                            fontWeight: 'bold'
                        }}
                    >
                        {loadingRegStatus ? 'Updating...' : registrationOpen ? 'Close Registration' : 'Open Registration'}
                    </button>
                </div>
            </div>

            {/* Step 1: Lecturer & Learnership Setup */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(450px, 1fr))', gap: '2rem', marginBottom: '2rem' }}>
                {/* Register New Lecturer */}
                <div className="portal-card">
                    <h2 style={{ fontSize: '1.3rem', marginBottom: '0.5rem', fontWeight: 'bold' }}>1. Register New Lecturer</h2>
                    <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1.5rem' }}>Create lecturer profiles with dedicated dashboard authentication credentials.</p>
                    <form onSubmit={handleCreateLecturer} className="student-login-form" style={{ gap: '1rem' }}>
                        <div className="form-group">
                            <label>Full Name *</label>
                            <input type="text" value={fullName} onChange={e => setFullName(e.target.value)} className="form-input" required placeholder="e.g. Dr. Jane Smith" />
                        </div>
                        <div className="form-group">
                            <label>Email Address</label>
                            <input type="email" value={email} onChange={e => setEmail(e.target.value)} className="form-input" placeholder="e.g. janesmith@example.com" />
                        </div>
                        <div className="form-group">
                            <label>Username *</label>
                            <input type="text" value={username} onChange={e => setUsername(e.target.value)} className="form-input" required placeholder="e.g. janesmith" />
                        </div>
                        <div className="form-group">
                            <label>Initial Password *</label>
                            <input type="password" value={password} onChange={e => setPassword(e.target.value)} className="form-input" required placeholder="••••••••" />
                        </div>
                        <button type="submit" className="submit-btn" style={{ marginTop: '0.5rem', fontWeight: 'bold' }}>Register Lecturer</button>
                    </form>
                </div>

                {/* Create Learnership Form */}
                <div className="portal-card">
                    <h2 style={{ fontSize: '1.3rem', marginBottom: '0.5rem', fontWeight: 'bold' }}>Create Learnership</h2>
                    <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1.5rem' }}>Define a new qualification program container.</p>
                    <form onSubmit={handleCreateLearnership} className="student-login-form" style={{ gap: '1.05rem' }}>
                        <div className="form-group">
                            <label>Learnership Name *</label>
                            <input type="text" value={learnershipName} onChange={e => setLearnershipName(e.target.value)} className="form-input" required placeholder="e.g. IT Systems Support NQF4" />
                        </div>
                        <div className="form-group">
                            <label>Qualification Code</label>
                            <input type="text" value={qualificationCode} onChange={e => setQualificationCode(e.target.value)} className="form-input" placeholder="e.g. 48220" />
                        </div>
                        <button type="submit" className="submit-btn" style={{ marginTop: '2.5rem', fontWeight: 'bold' }}>Create Learnership</button>
                    </form>
                </div>
            </div>

            {/* Step 2: Categories Definition */}
            <div className="portal-card" style={{ marginBottom: '2rem' }}>
                <h2 style={{ fontSize: '1.3rem', marginBottom: '0.5rem', fontWeight: 'bold' }}>Add Category under Learnership</h2>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1.5rem' }}>Define new grouping tags (e.g. Fundamental, Practical) for the selected learnership.</p>
                <form onSubmit={handleCreateCategory} className="student-login-form">
                    <div className="grid-form">
                        <div className="form-group">
                            <label>Select Learnership *</label>
                            <select 
                                value={selectedLearnershipId} 
                                onChange={e => setSelectedLearnershipId(e.target.value)} 
                                className="form-input" 
                                required
                                style={{ background: 'var(--input-bg)', color: 'var(--text-main)' }}
                            >
                                <option value="">-- Choose Learnership --</option>
                                {learnerships.map(l => (
                                    <option key={l.id} value={l.id}>{l.name} ({l.qualificationCode || 'No Code'})</option>
                                ))}
                            </select>
                        </div>
                        <div className="form-group">
                            <label>Category Name *</label>
                            <input type="text" value={newCategoryName} onChange={e => setNewCategoryName(e.target.value)} className="form-input" required placeholder="e.g. Fundamental, Elective, Practical" />
                        </div>
                    </div>
                    <button type="submit" className="submit-btn" style={{ width: 'auto', padding: '0.5rem 1.5rem', marginTop: '1rem', fontWeight: 'bold' }} disabled={!selectedLearnershipId}>Add Category</button>
                </form>
            </div>

            {/* Step 3: Category Lecturer Assignment */}
            <div className="portal-card" style={{ marginBottom: '2rem' }}>
                <h2 style={{ fontSize: '1.3rem', marginBottom: '0.5rem', fontWeight: 'bold' }}>3. Category Lecturer Assignment</h2>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1.5rem' }}>
                    SETA learnership module ownership is assigned by Category per Learnership.
                </p>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.5rem' }}>
                    {categories.map(c => {
                        const learnership = learnerships.find(l => l.id === c.learnershipId);
                        const learnershipNameText = learnership ? `${learnership.name}` : 'Unknown Learnership';
                        return (
                            <div key={c.id} style={{ background: '#f8fafc', padding: '1rem', borderRadius: '8px', border: '1px solid var(--card-border)' }}>
                                <span style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: 'var(--primary-color)', fontWeight: 600 }}>{learnershipNameText}</span>
                                <h3 style={{ margin: '0.25rem 0 0.5rem 0', fontSize: '1rem', color: 'var(--text-main)', fontWeight: 'bold' }}>{c.categoryType} Category</h3>
                                <p style={{ fontSize: '0.825rem', color: 'var(--text-muted)', margin: '0 0 1rem 0' }}>
                                    Currently: <strong style={{ color: 'var(--primary-color)' }}>{c.lecturerName || 'Unassigned'}</strong>
                                </p>
                                <div>
                                    <select 
                                        value={c.lecturerId || ''} 
                                        onChange={e => handleAssignLecturer(c.id, e.target.value)} 
                                        className="form-input" 
                                        style={{ background: 'var(--input-bg)', color: 'var(--text-main)', fontSize: '0.85rem', padding: '0.35rem 0.5rem' }}
                                    >
                                        <option value="">-- Assign Lecturer --</option>
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

            {/* Lists Section */}
            <div className="portal-card" style={{ marginBottom: '2rem' }}>
                <h2 style={{ fontSize: '1.3rem', fontWeight: 'bold' }}>All Modules & Lecturers</h2>
                <div style={{ overflowX: 'auto', marginTop: '1rem' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
                        <thead>
                            <tr style={{ borderBottom: '1px solid var(--card-border)', textTransform: 'uppercase', color: 'var(--text-muted)' }}>
                                <th style={{ padding: '0.75rem', textAlign: 'left' }}>Module Code</th>
                                <th style={{ padding: '0.75rem', textAlign: 'left' }}>Module Name</th>
                                <th style={{ padding: '0.75rem', textAlign: 'left' }}>Lecturer</th>
                                <th style={{ padding: '0.75rem', textAlign: 'left' }}>Syllabus File</th>
                            </tr>
                        </thead>
                        <tbody>
                            {modules.length === 0 ? (
                                <tr>
                                    <td colSpan="4" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>No modules created yet.</td>
                                </tr>
                            ) : (
                                modules.map(m => (
                                    <tr key={m.id} style={{ borderBottom: '1px solid var(--card-border)' }}>
                                        <td style={{ padding: '0.75rem' }}><strong>{m.moduleCode || 'N/A'}</strong></td>
                                        <td style={{ padding: '0.75rem' }}>{m.moduleName}</td>
                                        <td style={{ padding: '0.75rem', color: 'var(--text-body)' }}>{m.lecturerName}</td>
                                        <td style={{ padding: '0.75rem' }}>{m.filePath ? <a href={m.filePath} style={{ color: 'var(--success)', textDecoration: 'none', fontWeight: 'bold' }} download>Download</a> : <span style={{ color: 'var(--text-muted)' }}>None</span>}</td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
};

export default AdminDashboard;
