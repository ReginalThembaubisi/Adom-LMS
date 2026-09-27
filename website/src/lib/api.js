import { ADOM } from './data.js';

// Connects the website to the Adom LMS. Every form on the site goes through here.
//
// The LMS's address comes from VITE_LMS_URL at build time (see .env.example), defaulting to
// the live LMS on Render. Build with VITE_LMS_URL= (empty) for demo mode, where forms show
// their success screens without sending anything and pages show sample content.
//
// Endpoints (all public, no sign-in): GET /api/learnerships/openings, GET /api/openings, POST /api/applications
// (multipart), POST /api/applications/status, POST /api/appointments, GET /api/registration-status.
export const LMS_URL = (import.meta.env.VITE_LMS_URL ?? 'https://adom-lms-portal.onrender.com').replace(/\/+$/, '');
/** Where the website's myAdom and e-Learning links go. */
export const LMS_LOGIN = LMS_URL ? LMS_URL + '/#/login' : '#';
/** Where the footer's Staff portal link goes. */
export const LMS_STAFF_LOGIN = LMS_URL ? LMS_URL + '/#/lecturer-login' : '#';

  const base = () => {
    let override = '';
    try { if (typeof localStorage !== 'undefined') override = localStorage.getItem('adom_api_base') || ''; } catch (e) { /* storage blocked */ }
    return (override || LMS_URL).replace(/\/+$/, '');
  };

  const SAMPLE_ADVERTS = [
    { id: 'sample-1', slug: 'sample-1', name: 'End-User Computing NQF3 (sample)', seta: 'MICT SETA', qualificationCode: '[code]', nqfLevel: 3, durationMonths: 12, stipend: null, intake: '[Intake]', closingDate: null,
      location: '10 Cameron Street, Lindokuhle House, Nelspruit', description: 'Sample advert shown in demo mode. Real adverts come from the LMS admin dashboard, under Learnership Adverts.', requirements: 'Grade 12\n[Other requirements]', status: 'Open' },
    { id: 'sample-2', slug: 'sample-2', name: 'IT Technical Support NQF4 (sample)', seta: 'MICT SETA', qualificationCode: '[code]', nqfLevel: 4, durationMonths: 12, stipend: null, intake: '[Intake]', closingDate: null,
      location: '10 Cameron Street, Lindokuhle House, Nelspruit', description: 'Sample advert shown in demo mode.', requirements: 'Grade 12 with Maths or Maths Literacy', status: 'Open' }
  ];

  // The LMS answers errors as {"message": "..."} written for the applicant to read.
  async function send(path, options) {
    let res;
    try {
      res = await fetch(base() + path, options);
    } catch (e) {
      throw new Error("We couldn't reach our servers. Check your connection and try again.");
    }
    const body = await res.json().catch(() => null);
    if (!res.ok) throw new Error((body && body.message) || 'Something went wrong (' + res.status + '). Please try again.');
    return body;
  }

  const demoReference = () => 'ADM-DEMO' + String(Date.now()).slice(-4);

export const AdomAPI = {
    isConnected: () => !!base(),

    /** Learnerships currently taking applications, in the shape the pages expect. */
    async list() {
      if (!base()) return SAMPLE_ADVERTS;
      const rows = await send('/api/learnerships/openings');
      return rows.map(a => ({ ...a, id: a.slug || String(a.id), requirements: (a.requirements || []).join('\n'), status: 'Open' }));
    },

    /**
     * Job and internship openings the admin has posted (dashboard: Jobs & Internships).
     * Demo mode shows the sample jobs from adom-data.js.
     */
    async listOpenings() {
      if (!base()) {
        const byLabel = { 'Internships': 'INTERNSHIP', 'Graduate programmes': 'GRADUATE_PROGRAMME', 'Grade 12 holders': 'GRADE_12', 'Entry-level jobs': 'ENTRY_LEVEL', 'Government jobs': 'GOVERNMENT' };
        return (ADOM.jobs || []).map((j, i) => ({
          id: null, title: j.title, category: byLabel[j.type] || 'ENTRY_LEVEL', categoryLabel: j.type, division: j.division,
          location: '10 Cameron Street, Lindokuhle House, Nelspruit', positions: j.roles, closingText: 'Closes ' + j.closing, description: '', requirements: []
        }));
      }
      const rows = await send('/api/openings');
      return rows.map(o => {
        const d = o.closingDate ? new Date(o.closingDate + 'T00:00:00') : null;
        return { ...o, closingText: d ? 'Closes ' + d.toLocaleDateString('en-ZA', { day: 'numeric', month: 'long' }) : 'Open until filled' };
      });
    },

    async registrationOpen() {
      if (!base()) return true;
      try { return (await send('/api/registration-status')).open; } catch (e) { return true; }
    },

    registerUrl: () => '/apply/?type=learnership',

    /**
     * Sends an application. `fields` holds the form's text fields by their LMS names
     * (programmeType, firstNames, idNumber, courseChoices…); `files` maps the LMS's file
     * names (idCopy, results, cv, transcript, registration, placementLetter, qualification,
     * other) to File objects. Resolves to { reference, appliedFor, ... }.
     */
    async submitApplication(fields, files) {
      if (!base()) return { reference: demoReference(), demo: true };
      const form = new FormData();
      Object.entries(fields).forEach(([k, v]) => {
        if (v === undefined || v === null || v === '') return;
        if (Array.isArray(v)) v.forEach(x => form.append(k, x));
        else form.append(k, v);
      });
      Object.entries(files || {}).forEach(([k, f]) => { if (f) form.append(k, f); });
      return send('/api/applications', { method: 'POST', body: form });
    },

    /** An applicant's own status: needs both the reference and the ID number. */
    async checkStatus(reference, idNumber) {
      if (!base()) {
        return {
          reference: (reference || 'ADM-DEMO').toUpperCase(), appliedFor: 'Course: Systems Development', status: 'SCREENING', statusLabel: 'Documents being checked',
          message: 'Demo mode: this is a sample status. Connect the site to the LMS to show real applications.',
          timeline: [
            { key: 'SUBMITTED', label: 'Application received', state: 'DONE' },
            { key: 'SCREENING', label: 'Documents being checked', state: 'CURRENT' },
            { key: 'SHORTLISTED', label: 'Shortlisted', state: 'UPCOMING' },
            { key: 'INTERVIEW', label: 'Invited to interview or assessment', state: 'UPCOMING' },
            { key: 'OUTCOME', label: 'Outcome', state: 'UPCOMING' }
          ]
        };
      }
      return send('/api/applications/status', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reference, idNumber })
      });
    },

    /** A Services page appointment request. */
    async bookAppointment(data) {
      if (!base()) return { demo: true };
      await send('/api/appointments', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      });
      return {};
    },

    /** South African ID numbers are 13 digits; anything else is treated as a passport. */
    idTypeFor: (idNumber) => /^\d{13}$/.test(String(idNumber || '').replace(/\s/g, '')) ? 'South African ID' : 'Passport'
  };
