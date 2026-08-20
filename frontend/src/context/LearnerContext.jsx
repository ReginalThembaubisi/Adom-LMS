import React, { createContext, useState, useContext } from 'react';

const LearnerContext = createContext(null);

const SESSION_TTL_MS = 24 * 60 * 60 * 1000; // 1 day

const readStoredSession = () => {
    const stored = localStorage.getItem('learner_session');
    if (!stored) return null;

    try {
        const parsed = JSON.parse(stored);
        if (!parsed.expiresAt || Date.now() > parsed.expiresAt) {
            localStorage.removeItem('learner_session');
            return null;
        }
        return parsed.data;
    } catch {
        localStorage.removeItem('learner_session');
        return null;
    }
};

const storeSession = (data) => {
    localStorage.setItem('learner_session', JSON.stringify({
        data,
        expiresAt: Date.now() + SESSION_TTL_MS
    }));
};

export const LearnerProvider = ({ children }) => {
    const [learner, setLearner] = useState(readStoredSession);

    const loginStudent = async (studentNumber, password) => {
        const res = await fetch('/api/learners/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ studentNumber, password })
        });

        const data = await res.json();
        if (!res.ok) {
            throw new Error(data.message || 'Login failed.');
        }

        setLearner(data);
        storeSession(data);
        return data;
    };

    const registerStudent = async (formData) => {
        const res = await fetch('/api/learners', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(formData)
        });

        const data = await res.json();
        if (!res.ok) {
            throw new Error(data.message || 'Registration failed.');
        }

        setLearner(data);
        storeSession(data);
        return data;
    };

    const logoutStudent = () => {
        setLearner(null);
        localStorage.removeItem('learner_session');
    };

    return (
        <LearnerContext.Provider value={{ learner, setLearner, loginStudent, registerStudent, logoutStudent }}>
            {children}
        </LearnerContext.Provider>
    );
};

export const useLearner = () => {
    const context = useContext(LearnerContext);
    if (!context) {
        throw new Error('useLearner must be used within a LearnerProvider');
    }
    return context;
};
