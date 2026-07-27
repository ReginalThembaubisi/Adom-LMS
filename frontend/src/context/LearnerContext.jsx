import React, { createContext, useState, useContext } from 'react';

const LearnerContext = createContext(null);

export const LearnerProvider = ({ children }) => {
    const [learner, setLearner] = useState(() => {
        const stored = localStorage.getItem('learner_session');
        return stored ? JSON.parse(stored) : null;
    });

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
        localStorage.setItem('learner_session', JSON.stringify(data));
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
        localStorage.setItem('learner_session', JSON.stringify(data));
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
