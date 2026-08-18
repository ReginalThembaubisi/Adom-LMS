import React, { useState } from 'react';

const SubmissionMarker = ({ submission, onClose, onSaveGrade, role }) => {
    const [grade, setGrade] = useState(submission.grade !== null && submission.grade !== undefined ? submission.grade.toString() : '');
    const [feedback, setFeedback] = useState(submission.feedback || '');
    const [markedFile, setMarkedFile] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setSuccess('');

        const gradeVal = parseInt(grade);
        if (isNaN(gradeVal) || gradeVal < 0 || gradeVal > 100) {
            setError('Grade must be a percentage between 0 and 100.');
            return;
        }

        setLoading(true);

        const formData = new FormData();
        formData.append('grade', gradeVal);
        formData.append('feedback', feedback);
        if (markedFile) {
            formData.append('file', markedFile);
        }

        try {
            await onSaveGrade(submission.submissionId, formData);
            setSuccess('Grade and feedback saved successfully!');
            setTimeout(() => {
                onClose();
            }, 1000);
        } catch (err) {
            setError(err.message || 'Failed to save grade.');
        } finally {
            setLoading(false);
        }
    };

    const isPdf = submission.originalFilename?.toLowerCase().endsWith('.pdf');
    const isDoc = submission.originalFilename?.toLowerCase().endsWith('.doc') || submission.originalFilename?.toLowerCase().endsWith('.docx');
    
    // Retrieve authentication token to allow document preview downloading without standard basic auth headers in iframe
    const token = sessionStorage.getItem('lecturer_auth') || 
                  sessionStorage.getItem('moderator_auth') || 
                  sessionStorage.getItem('assessor_auth') || 
                  sessionStorage.getItem('admin_auth');

    // Construct absolute public download URL for Google Docs Viewer in production
    const documentUrl = `${window.location.origin}/api/submissions/${submission.submissionId}/download` + (token ? `?authToken=${encodeURIComponent(token)}` : '');
    const googleDocsViewerUrl = `https://docs.google.com/gview?url=${encodeURIComponent(documentUrl)}&embedded=true`;

    const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';

    return (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full h-[92vh] max-w-7xl flex flex-col overflow-hidden shadow-2xl animate-fade-in text-slate-100">
                {/* Header */}
                <div className="px-6 py-4 bg-slate-950 border-b border-slate-800 flex items-center justify-between">
                    <div>
                        <h3 className="text-base font-extrabold text-white">In-App Grading Workspace</h3>
                        <p className="text-xs text-slate-400">
                            Student: <span className="font-semibold text-slate-200">{submission.learnerName} ({submission.learnerCode})</span> | File: <span className="font-semibold text-slate-200">{submission.originalFilename}</span>
                        </p>
                    </div>
                    <button 
                        onClick={onClose}
                        className="bg-slate-850 hover:bg-slate-800 text-slate-400 hover:text-white p-2 rounded-xl transition-colors cursor-pointer"
                    >
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>

                {/* Workspace Split Panes */}
                <div className="flex-1 flex overflow-hidden">
                    {/* Left Pane - Premium Document Viewer */}
                    <div className="w-3/4 bg-slate-950 flex flex-col relative border-r border-slate-800 p-4">
                        <div className="flex-1 bg-slate-900/40 rounded-2xl border border-slate-800 overflow-hidden relative flex flex-col items-center justify-center">
                            {isPdf ? (
                                <iframe 
                                    src={documentUrl} 
                                    className="w-full h-full border-none"
                                    title="PDF Document Preview"
                                />
                            ) : isDoc ? (
                                isLocalhost ? (
                                    <div className="p-6 text-center space-y-4 max-w-md">
                                        <div className="w-12 h-12 bg-amber-500/15 text-amber-500 rounded-full flex items-center justify-center mx-auto">
                                            <svg className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                                            </svg>
                                        </div>
                                        <h4 className="text-sm font-bold text-white">Local Word Document Preview Sandbox</h4>
                                        <p className="text-xs text-slate-400">
                                            Google Document Viewer requires a public URL to fetch and render Word documents. In the deployed production site, this will render the document inside the browser automatically.
                                        </p>
                                        <a 
                                            href={documentUrl}
                                            target="_blank"
                                            rel="noreferrer"
                                            className="inline-block bg-slate-800 hover:bg-slate-700 text-white font-semibold text-xs py-2 px-4 rounded-xl transition-all"
                                        >
                                            View document in new tab
                                        </a>
                                    </div>
                                ) : (
                                    <iframe 
                                        src={googleDocsViewerUrl}
                                        className="w-full h-full border-none"
                                        title="Word Document Preview"
                                    />
                                )
                            ) : (
                                <div className="text-center p-6 space-y-3">
                                    <div className="w-12 h-12 bg-red-500/10 text-red-500 rounded-full flex items-center justify-center mx-auto">
                                        <svg className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                                        </svg>
                                    </div>
                                    <h4 className="text-sm font-bold text-white">Unsupported file format preview</h4>
                                    <p className="text-xs text-slate-400">
                                        This document format cannot be rendered inside the grading workspace.
                                    </p>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Right Pane - Grading Console */}
                    <div className="w-1/4 bg-slate-900 p-6 flex flex-col justify-between overflow-y-auto">
                        <form onSubmit={handleSubmit} className="space-y-6">
                            <div className="space-y-1">
                                <h4 className="text-sm font-bold text-white uppercase tracking-wider text-slate-400">Grading Console</h4>
                                <p className="text-[11px] text-slate-500">Provide a score percentage and written feedback directly onto the student portal.</p>
                            </div>

                            {error && (
                                <div className="p-3 bg-red-500/10 border border-red-500/20 text-red-400 rounded-xl text-xs font-medium text-center">
                                    {error}
                                </div>
                            )}

                            {success && (
                                <div className="p-3 bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 rounded-xl text-xs font-medium text-center animate-pulse">
                                    {success}
                                </div>
                            )}

                            <div className="space-y-4">
                                <div className="space-y-1.5">
                                    <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">Score Percentage (0-100%)</label>
                                    <input
                                        type="number"
                                        min="0"
                                        max="100"
                                        value={grade}
                                        onChange={(e) => setGrade(e.target.value)}
                                        className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-600 transition-all font-sans"
                                        placeholder="e.g. 85"
                                        required
                                        disabled={loading}
                                    />
                                </div>

                                <div className="space-y-1.5">
                                    <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">Feedback Comments</label>
                                    <textarea
                                        rows="6"
                                        value={feedback}
                                        onChange={(e) => setFeedback(e.target.value)}
                                        className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 text-xs text-white focus:outline-none focus:border-blue-600 transition-all font-sans resize-none"
                                        placeholder="Write constructive evaluation notes here..."
                                        disabled={loading}
                                    />
                                </div>

                                <div className="space-y-1.5">
                                    <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">Return Marked File (Optional)</label>
                                    <div className="relative">
                                        <input
                                            type="file"
                                            onChange={(e) => setMarkedFile(e.target.files[0])}
                                            className="w-full text-xs text-slate-400 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-blue-600/10 file:text-blue-400 hover:file:bg-blue-600/20 transition-all"
                                            disabled={loading}
                                        />
                                    </div>
                                    <p className="text-[10px] text-slate-500 mt-1">Upload a graded version of their work if they need corrected details.</p>
                                </div>
                            </div>

                            <button
                                type="submit"
                                className="w-full bg-blue-600 hover:bg-blue-700 active:scale-[0.99] text-white font-semibold text-xs py-3 rounded-xl transition-all cursor-pointer shadow-md flex items-center justify-center gap-2"
                                disabled={loading}
                            >
                                {loading ? 'Saving...' : 'Save & Publish Grade'}
                            </button>
                        </form>

                        <div className="pt-6 border-t border-slate-800/80 text-center">
                            <span className="text-[10px] text-slate-500 uppercase tracking-widest">Logged in as {role}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default SubmissionMarker;
