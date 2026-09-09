import React, { createContext, useState, useContext, useCallback } from 'react';

const LearnerContext = createContext(null);

const STORAGE_KEY = 'learner_session';

// The stored session is now {token, expiresAt, learner}. The token is what actually
// authorises anything — the learner profile alongside it is only there so the portal can
// render a name and code without a round trip. The server decides what a token may read;
// nothing here is trusted.
const readStoredSession = () => {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) return null;

    try {
        const parsed = JSON.parse(stored);
        if (!parsed.token || !parsed.learner) {
            localStorage.removeItem(STORAGE_KEY);
            return null;
        }
        if (parsed.expiresAt && Date.now() > new Date(parsed.expiresAt).getTime()) {
            localStorage.removeItem(STORAGE_KEY);
            return null;
        }
        return parsed;
    } catch {
        localStorage.removeItem(STORAGE_KEY);
        return null;
    }
};

const storeSession = (session) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
};

export const LearnerProvider = ({ children }) => {
    const [session, setSession] = useState(readStoredSession);

    const startSession = (data) => {
        const next = {
            token: data.token,
            expiresAt: data.expiresAt,
            learner: data.learner
        };
        setSession(next);
        storeSession(next);
        return next.learner;
    };

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
        return startSession(data);
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
        return startSession(data);
    };

    const clearSession = useCallback(() => {
        setSession(null);
        localStorage.removeItem(STORAGE_KEY);
    }, []);

    const logoutStudent = useCallback(async () => {
        const token = readStoredSession()?.token;
        clearSession();
        if (token) {
            // Best effort: the local session is already gone either way, but telling the
            // server lets it revoke the token rather than leaving it live until it expires.
            try {
                await fetch('/api/me/logout', {
                    method: 'POST',
                    headers: { Authorization: `Bearer ${token}` }
                });
            } catch {
                // Offline logout still counts as a logout on this device.
            }
        }
    }, [clearSession]);

    /**
     * fetch() with the learner's bearer token attached.
     *
     * A 401 means the token is gone — expired, revoked, or the password was reset — so the
     * local session is dropped and the caller can redirect to the login screen.
     */
    const authFetch = useCallback(async (url, options = {}) => {
        const token = readStoredSession()?.token;
        if (!token) {
            clearSession();
            return new Response(null, { status: 401 });
        }

        const headers = new Headers(options.headers || {});
        headers.set('Authorization', `Bearer ${token}`);

        const res = await fetch(url, { ...options, headers });
        if (res.status === 401) {
            clearSession();
        }
        return res;
    }, [clearSession]);

    return (
        <LearnerContext.Provider value={{
            learner: session?.learner ?? null,
            token: session?.token ?? null,
            setLearner: (learner) => setSession((prev) => (prev ? { ...prev, learner } : prev)),
            loginStudent,
            registerStudent,
            logoutStudent,
            authFetch
        }}>
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
