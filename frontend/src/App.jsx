import React, { lazy, Suspense } from 'react';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { LearnerProvider } from './context/LearnerContext';

// Always-needed pages (tiny, load eagerly)
import Landing from './pages/Landing';
import Login from './pages/Login';
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';
import AdminLogin from './pages/AdminLogin';
import LecturerLogin from './pages/LecturerLogin';
import ModeratorLogin from './pages/ModeratorLogin';
import AssessorLogin from './pages/AssessorLogin';

// Heavy dashboard pages — code-split per route so pdfjs / jsPDF only load for graders
const StudentPortal = lazy(() => import('./pages/StudentPortal'));
const AdminDashboard = lazy(() => import('./pages/AdminDashboard'));
const LecturerDashboard = lazy(() => import('./pages/LecturerDashboard'));
const ModeratorDashboard = lazy(() => import('./pages/ModeratorDashboard'));
const AssessorDashboard = lazy(() => import('./pages/AssessorDashboard'));

function PageLoader() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>
      <div style={{ width: 40, height: 40, border: '4px solid #e5e7eb', borderTopColor: '#3b82f6', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

function App() {
  return (
    <LearnerProvider>
      <Router>
        <Suspense fallback={<PageLoader />}>
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<Login />} />
            <Route path="/forgot-password" element={<ForgotPassword />} />
            <Route path="/register" element={<Register />} />
            <Route path="/portal" element={<StudentPortal />} />
            <Route path="/admin" element={<Navigate to="/admin-login" replace />} />
            <Route path="/admin-login" element={<AdminLogin />} />
            <Route path="/admin-dashboard" element={<AdminDashboard />} />
            <Route path="/lecturer" element={<Navigate to="/lecturer-login" replace />} />
            <Route path="/lecturer-login" element={<LecturerLogin />} />
            <Route path="/lecturer-dashboard" element={<LecturerDashboard />} />
            <Route path="/moderator" element={<Navigate to="/moderator-login" replace />} />
            <Route path="/moderator-login" element={<ModeratorLogin />} />
            <Route path="/moderator-dashboard" element={<ModeratorDashboard />} />
            <Route path="/assessor" element={<Navigate to="/assessor-login" replace />} />
            <Route path="/assessor-login" element={<AssessorLogin />} />
            <Route path="/assessor-dashboard" element={<AssessorDashboard />} />
          </Routes>
        </Suspense>
      </Router>
    </LearnerProvider>
  );
}

export default App;
