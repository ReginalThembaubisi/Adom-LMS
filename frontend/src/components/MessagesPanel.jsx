import React, { useState, useEffect, useRef } from 'react';

// Shared thread-list + conversation UI for both the Facilitator dashboard and the
// Student portal. The caller supplies the API calls (different auth/paths on each
// side) and which sender type "you" are, so the bubbles align correctly.
const MessagesPanel = ({ theme = 'dark', currentSenderType, fetchThreads, fetchThread, sendMessage }) => {
    const [threads, setThreads] = useState([]);
    const [loadingThreads, setLoadingThreads] = useState(true);
    const [activePartnerId, setActivePartnerId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [draft, setDraft] = useState('');
    const [sending, setSending] = useState(false);
    const [error, setError] = useState('');
    const bottomRef = useRef(null);

    const loadThreads = async () => {
        try {
            const data = await fetchThreads();
            setThreads(data || []);
        } catch (e) {
            setError('Failed to load conversations.');
        } finally {
            setLoadingThreads(false);
        }
    };

    useEffect(() => {
        loadThreads();
    }, []);

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    const openThread = async (partnerId) => {
        setActivePartnerId(partnerId);
        setError('');
        try {
            const data = await fetchThread(partnerId);
            setMessages(data || []);
            loadThreads();
        } catch (e) {
            setError('Failed to load conversation.');
        }
    };

    const handleSend = async (e) => {
        e.preventDefault();
        const body = draft.trim();
        if (!body || !activePartnerId) return;
        setSending(true);
        setError('');
        try {
            await sendMessage(activePartnerId, body);
            setDraft('');
            const data = await fetchThread(activePartnerId);
            setMessages(data || []);
            loadThreads();
        } catch (e) {
            setError('Failed to send message.');
        } finally {
            setSending(false);
        }
    };

    const isDark = theme === 'dark';
    const panelBg = isDark ? 'bg-slate-900 border-slate-800' : 'bg-white border-slate-200/80';
    const listItemHover = isDark ? 'hover:bg-slate-800/50' : 'hover:bg-slate-50';
    const textPrimary = isDark ? 'text-white' : 'text-slate-900';
    const textMuted = isDark ? 'text-slate-400' : 'text-slate-500';
    const borderColor = isDark ? 'border-slate-800' : 'border-slate-200/80';

    return (
        <div className={`border rounded-2xl shadow-sm overflow-hidden ${panelBg}`} style={{ height: '600px' }}>
            <div className="grid grid-cols-1 md:grid-cols-3 h-full">
                {/* Thread list */}
                <div className={`md:col-span-1 border-r ${borderColor} overflow-y-auto`}>
                    <div className={`px-4 py-3 border-b ${borderColor}`}>
                        <h3 className={`text-sm font-bold ${textPrimary}`}>Conversations</h3>
                    </div>
                    {loadingThreads ? (
                        <p className={`text-xs p-4 ${textMuted}`}>Loading...</p>
                    ) : threads.length === 0 ? (
                        <p className={`text-xs p-4 ${textMuted}`}>No conversations yet.</p>
                    ) : (
                        threads.map(t => (
                            <button
                                key={t.partnerId}
                                onClick={() => openThread(t.partnerId)}
                                className={`w-full text-left px-4 py-3 border-b ${borderColor} transition-colors ${listItemHover} ${activePartnerId === t.partnerId ? (isDark ? 'bg-slate-800/70' : 'bg-blue-50/60') : ''}`}
                            >
                                <div className="flex justify-between items-center gap-2">
                                    <span className={`text-xs font-bold truncate ${textPrimary}`}>{t.partnerName}</span>
                                    {t.unreadCount > 0 && (
                                        <span className="flex-shrink-0 bg-blue-600 text-white text-[10px] font-bold rounded-full w-5 h-5 flex items-center justify-center">
                                            {t.unreadCount}
                                        </span>
                                    )}
                                </div>
                                <p className={`text-[11px] truncate mt-0.5 ${textMuted}`}>
                                    {t.lastMessage || 'No messages yet — say hello'}
                                </p>
                            </button>
                        ))
                    )}
                </div>

                {/* Conversation view */}
                <div className="md:col-span-2 flex flex-col h-full">
                    {!activePartnerId ? (
                        <div className={`flex-1 flex items-center justify-center text-xs ${textMuted}`}>
                            Select a conversation to view messages
                        </div>
                    ) : (
                        <>
                            <div className="flex-1 overflow-y-auto p-4 space-y-3">
                                {messages.map(m => {
                                    const isMine = m.senderType === currentSenderType;
                                    return (
                                        <div key={m.id} className={`flex ${isMine ? 'justify-end' : 'justify-start'}`}>
                                            <div className={`max-w-[75%] rounded-2xl px-3.5 py-2 text-xs ${
                                                isMine
                                                    ? 'bg-blue-600 text-white rounded-br-sm'
                                                    : isDark
                                                    ? 'bg-slate-800 text-slate-200 rounded-bl-sm'
                                                    : 'bg-slate-100 text-slate-800 rounded-bl-sm'
                                            }`}>
                                                <p className="whitespace-pre-wrap break-words">{m.body}</p>
                                                <p className={`text-[9px] mt-1 ${isMine ? 'text-blue-100' : textMuted}`}>
                                                    {new Date(m.createdAt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                                                </p>
                                            </div>
                                        </div>
                                    );
                                })}
                                <div ref={bottomRef} />
                            </div>
                            <form onSubmit={handleSend} className={`p-3 border-t ${borderColor} flex gap-2`}>
                                <input
                                    type="text"
                                    value={draft}
                                    onChange={(e) => setDraft(e.target.value)}
                                    placeholder="Type a message..."
                                    disabled={sending}
                                    className={`flex-1 rounded-xl px-3.5 py-2 text-xs focus:outline-none ${
                                        isDark
                                            ? 'bg-slate-950 border border-slate-800 text-white placeholder:text-slate-500'
                                            : 'bg-white border border-slate-200 text-slate-900 placeholder:text-slate-400'
                                    }`}
                                />
                                <button
                                    type="submit"
                                    disabled={sending || !draft.trim()}
                                    className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-semibold text-xs py-2 px-4 rounded-xl transition-all cursor-pointer"
                                >
                                    Send
                                </button>
                            </form>
                        </>
                    )}
                </div>
            </div>
            {error && <p className="text-[10px] text-rose-500 px-4 py-2">{error}</p>}
        </div>
    );
};

export default MessagesPanel;
