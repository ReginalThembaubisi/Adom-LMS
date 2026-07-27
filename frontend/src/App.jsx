import React from 'react';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { LearnerProvider } from './context/LearnerContext';
import Landing from './pages/Landing';
import Login from './pages/Login';
import Register from './pages/Register';
import StudentPortal from './pages/StudentPortal';
import AdminLogin from './pages/AdminLogin';
import AdminDashboard from './pages/AdminDashboard';
import LecturerLogin from './pages/LecturerLogin';
import LecturerDashboard from './pages/LecturerDashboard';
import ForgotPassword from './pages/ForgotPassword';

function App() {
  return (
    <LearnerProvider>
      <Router>
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
        </Routes>
      </Router>
    </LearnerProvider>
  );
}

export default App;
