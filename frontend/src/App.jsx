import React, { Suspense, lazy } from 'react';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { LearnerProvider } from './context/LearnerContext';

const Landing = lazy(() => import('./pages/Landing'));
const Login = lazy(() => import('./pages/Login'));
const Register = lazy(() => import('./pages/Register'));
const ForgotPassword = lazy(() => import('./pages/ForgotPassword'));
const StudentPortal = lazy(() => import('./pages/StudentPortal'));
const AdminLogin = lazy(() => import('./pages/AdminLogin'));
const AdminDashboard = lazy(() => import('./pages/AdminDashboard'));
const LecturerLogin = lazy(() => import('./pages/LecturerLogin'));
const LecturerDashboard = lazy(() => import('./pages/LecturerDashboard'));
const ModeratorLogin = lazy(() => import('./pages/ModeratorLogin'));
const ModeratorDashboard = lazy(() => import('./pages/ModeratorDashboard'));
const AssessorLogin = lazy(() => import('./pages/AssessorLogin'));
const AssessorDashboard = lazy(() => import('./pages/AssessorDashboard'));
const AnnotatorTest = lazy(() => import('./pages/AnnotatorTest'));

function App() {
  return (
    <LearnerProvider>
      <Router>
        <Suspense fallback={null}>
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
            <Route path="/annotator-test" element={<AnnotatorTest />} />
          </Routes>
        </Suspense>
      </Router>
    </LearnerProvider>
  );
}

export default App;
