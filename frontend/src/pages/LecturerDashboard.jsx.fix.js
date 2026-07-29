const fs = require('fs');

const filePath = 'C:/Users/HP/.gemini/antigravity/scratch/learner-assignments/frontend/src/pages/LecturerDashboard.jsx';
let content = fs.readFileSync(filePath, 'utf8');

// Normalize line endings
content = content.replace(/\r\n/g, '\n');

// Replace the outer modules.length === 0 ternary
const targetToRemove = `                {modules.length === 0 ? (
                    /* Prominent "Create your first module to get started" welcome message */
                    <div className="bg-white border border-slate-200/80 rounded-2xl shadow-sm hover:shadow-md/50 transition-all duration-200 p-8 text-center max-w-xl mx-auto space-y-4">
                        <h2 className="text-xl font-bold text-slate-900">Create your first module to get started</h2>
                        <p className="text-sm text-slate-500 leading-relaxed">
                            Once you register your first module above under an assigned category, you'll be able to upload learning materials (Syllabus, Slides, Guides) and schedule submission sessions for your students.
                        </p>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">`;

const replacementForTernary = `                    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">`;

content = content.replace(targetToRemove, replacementForTernary);

// Also remove the closing parentheses on line 738
const closeTernary = `                                </div>
                            </div>
                        </div>
                    </div>
                )}`;

const newCloseTernary = `                                </div>
                            </div>
                        </div>
                    </div>`;

content = content.replace(closeTernary, newCloseTernary);

// Now wrap Card 3 form with modules.length check
const card3Start = `                                <form onSubmit={handleCreateSession} className="bg-slate-50/50 p-5 rounded-xl border border-slate-200/60 space-y-4">`;
const card3End = `                                    {/* Action Bar */}
                                    <div className="border-t border-slate-200 pt-4 mt-4 flex justify-end">
                                        <button type="submit" className="text-xs font-bold text-white bg-blue-600 hover:bg-blue-700 px-4 py-2.5 rounded-lg shadow-xs transition-colors">
                                            Open Session Window
                                        </button>
                                    </div>
                                </form>`;

const replacementCard3Start = `                                {modules.length === 0 ? (
                                    <div className="bg-slate-50/50 p-5 rounded-xl border border-slate-200/60 text-center py-8 text-xs text-slate-500 font-medium">
                                        Create a module first using the form above to schedule intake sessions.
                                    </div>
                                ) : (
                                    <form onSubmit={handleCreateSession} className="bg-slate-50/50 p-5 rounded-xl border border-slate-200/60 space-y-4">`;

const replacementCard3End = `                                    {/* Action Bar */}
                                    <div className="border-t border-slate-200 pt-4 mt-4 flex justify-end">
                                        <button type="submit" className="text-xs font-bold text-white bg-blue-600 hover:bg-blue-700 px-4 py-2.5 rounded-lg shadow-xs transition-colors">
                                            Open Session Window
                                        </button>
                                    </div>
                                </form>
                                )}`;

content = content.replace(card3Start, replacementCard3Start);
content = content.replace(card3End, replacementCard3End);

// Now wrap Card 2 list with modules.length check
const card2List = `                                <div className="space-y-6">
                                    {Array.from(new Set(modules.map(m => m.moduleType || 'CORE'))).map(type => {`;

const replacementCard2List = `                                <div className="space-y-6">
                                    {modules.length === 0 ? (
                                        <p className="text-xs text-slate-500 text-center py-6 font-medium">You have no modules created yet.</p>
                                    ) : Array.from(new Set(modules.map(m => m.moduleType || 'CORE'))).map(type => {`;

content = content.replace(card2List, replacementCard2List);

fs.writeFileSync(filePath, content, 'utf8');
console.log("Lecturer welcome logic successfully fixed!");
