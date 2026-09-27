// Generated from design/Careers.dc.html by tools/convert_design.py. Do not edit by hand.
/* eslint-disable */
import React from 'react';
import { s as __style } from '../lib/style.js';
import { ADOM } from '../lib/data.js';
import { AdomAPI, LMS_LOGIN, LMS_STAFF_LOGIN } from '../lib/api.js';
import SiteHeader from '../components/SiteHeader.jsx';
import SiteFooter from '../components/SiteFooter.jsx';

class CareersPage extends React.Component {
  state = { cat: 'INTERNSHIP', openings: [], job: null, applied: false, files: {}, v: { experience: 'None' }, sending: false, error: '' };
  fileObjs = {};
  async submit(e) {
    e.preventDefault();
    const s = this.state, v = s.v, job = s.job;
    if (!this.fileObjs.id) { this.setState({ error: 'Please upload a certified copy of your ID.' }); return; }
    this.setState({ sending: true, error: '' });
    try {
      const res = await AdomAPI.submitApplication({
        openingId: job.id, programmeType: job.category === 'INTERNSHIP' ? 'INTERNSHIP' : 'JOB', positionTitle: job.title,
        firstNames: v.names, surname: v.surname, idNumber: v.idNumber, idType: AdomAPI.idTypeFor(v.idNumber),
        phone: v.cell, email: v.email, experience: v.experience, motivation: v.why, popiaConsent: 'true', website: ''
      }, { cv: this.fileObjs.cv, idCopy: this.fileObjs.id, results: this.fileObjs.g12, qualification: this.fileObjs.qual, other: this.fileObjs.other });
      this.setState({ applied: true, sending: false, ref: res.reference });
    } catch (err) {
      this.setState({ sending: false, error: err.message });
    }
  }
  componentDidMount() {
    const load = () => AdomAPI.listOpenings().then(openings => this.setState({ openings })).catch(() => {});
    if (AdomAPI && ADOM) load();
    else this.iv = setInterval(() => { if (AdomAPI && ADOM) { clearInterval(this.iv); load(); } }, 50);
  }
  componentWillUnmount() { clearInterval(this.iv); }
  renderVals() {
    const s = this.state;
    const categories = [['INTERNSHIP', 'Internships'], ['GRADUATE_PROGRAMME', 'Graduate programmes'], ['GRADE_12', 'Grade 12 holders'], ['ENTRY_LEVEL', 'Entry-level jobs'], ['GOVERNMENT', 'Government jobs']];
    const jobs = s.openings.filter(j => j.category === s.cat);
    const roles = (j) => j.positions || 1;
    const docDefs = [['cv', 'CV'], ['id', 'Certified ID (required)'], ['g12', 'Grade 12 certificate'], ['qual', 'Highest qualification'], ['other', 'Other (optional)']];
    return {
      cats: categories.map(([key, name]) => {
        const on = key === s.cat;
        const count = s.openings.filter(j => j.category === key).reduce((t, j) => t + roles(j), 0);
        return { name, count, bg: on ? '#22D3C5' : '#181A2A', fg: on ? '#0E0F1A' : '#F4F3EE', border: on ? '#22D3C5' : '#181A2A', pick: () => this.setState({ cat: key }) };
      }),
      listTitle: (categories.find(c => c[0] === s.cat) || [])[1],
      listCount: jobs.reduce((t, j) => t + roles(j), 0) + ' open roles',
      jobs: jobs.map(j => ({ ...j,
        meta: [j.division, (j.location || '').split(',').pop().trim() || 'Nelspruit', roles(j) + (roles(j) === 1 ? ' role' : ' roles'), j.closingText].filter(Boolean).join(' · '),
        open: () => { this.fileObjs = {}; this.setState({ job: j, applied: false, files: {}, v: { experience: 'None' }, error: '' }); } })),
      noJobs: jobs.length === 0,
      hasJob: !!s.job,
      job: s.job ? { ...s.job, kicker: [s.job.categoryLabel, s.job.division].filter(Boolean).join(' · '),
        where: [s.job.location, s.job.closingText].filter(Boolean).join(' · '),
        hasDesc: !!s.job.description, hasReqs: (s.job.requirements || []).length > 0 } : { requirements: [] },
      close: () => this.setState({ job: null }), stop: (e) => e.stopPropagation(),
      applied: s.applied, notApplied: !s.applied,
      submit: (e) => this.submit(e),
      set: (e) => { const { name, value } = e.target; this.setState(p => ({ v: { ...p.v, [name]: value } })); },
      ref: s.ref, hasError: !!s.error, error: s.error, submitLabel: s.sending ? 'Sending…' : 'Submit application',
      docs: docDefs.map(([key, label]) => ({ key, label, status: s.files[key] || 'Choose file', border: s.files[key] ? '#0A8C83' : '#A9ABBE' })),
      setFile: (e) => { const { name, files } = e.target; if (files && files[0]) { this.fileObjs[name] = files[0]; this.setState(p => ({ files: { ...p.files, [name]: files[0].name } })); } }
    };
  }

  render() {
    const v = this.renderVals();
    return (<div style={__style("min-height:100vh;background:#0E0F1A;color:#F4F3EE;font-family:'Archivo',system-ui,sans-serif;overflow-x:hidden")}><SiteHeader active={"careers"} /><section style={__style("max-width:1320px;margin:0 auto;padding:88px 28px 72px;display:flex;flex-direction:column;gap:28px")}><span style={__style("font-size:14px;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:#22D3C5")}>{"Careers"}</span><h1 style={__style("font-size:clamp(52px,8vw,120px);line-height:.9;margin:0;font-weight:900;font-stretch:115%;letter-spacing:-.02em;text-transform:uppercase")}>{"Start your career "}<span style={__style("color:#22D3C5")}>{"with us."}</span></h1><p style={__style("font-size:20px;line-height:1.5;margin:0;max-width:620px;color:#C9CAD6")}>{"Internships, learnerships and jobs at Adom and with our partners across South Africa."}</p></section><section style={__style("max-width:1320px;margin:0 auto;padding:0 28px 80px")}><div style={__style("display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,200px),1fr));gap:12px")}>{(v.cats || []).map((c, $index) => (<React.Fragment key={$index}><button onClick={c.pick} style={__style(`all:unset;cursor:pointer;display:flex;flex-direction:column;gap:6px;padding:22px;border-radius:14px;border:2px solid ${c.border};background:${c.bg};color:${c.fg}`)}><span style={__style("font-size:44px;font-weight:900;font-stretch:115%;line-height:1")}>{c.count}</span><span style={__style("font-size:16px;font-weight:800")}>{c.name}</span></button></React.Fragment>))}</div><p style={__style("margin:20px 0 0;font-size:17px;color:#C9CAD6")}>{"Looking for a learnership? "}<a href={"/learnerships/"} style={__style("font-weight:800")}>{"See open learnerships"}</a></p></section><section style={__style("background:#F4F3EE;color:#0E0F1A")}><div style={__style("max-width:1320px;margin:0 auto;padding:88px 28px 110px;display:flex;flex-direction:column;gap:32px")}><div style={__style("display:flex;justify-content:space-between;align-items:flex-end;gap:16px;flex-wrap:wrap")}><h2 style={__style("font-size:clamp(36px,4.6vw,64px);line-height:.95;margin:0;font-weight:900;font-stretch:112%;text-transform:uppercase")}>{v.listTitle}</h2><span style={__style("font-size:16px;color:#3A3C4E")}>{v.listCount}</span></div><div style={__style("display:flex;flex-direction:column;border-top:4px solid #0E0F1A")}>{(v.jobs || []).map((j, $index) => (<React.Fragment key={$index}><div style={__style("display:grid;grid-template-columns:minmax(0,1fr) auto;gap:20px;align-items:center;padding:24px 0;border-bottom:2px solid #E2E0D8")}><div style={__style("display:flex;flex-direction:column;gap:6px;min-width:0")}><span style={__style("font-size:clamp(22px,2.4vw,32px);font-weight:800;font-stretch:108%")}>{j.title}</span><span style={__style("font-size:15px;color:#3A3C4E")}>{j.meta}</span></div><button onClick={j.open} style={__style("all:unset;cursor:pointer;padding:14px 22px;border-radius:10px;background:#0E0F1A;color:#F4F3EE;font-weight:800;white-space:nowrap")} className="h-f8be57db">{"View & apply"}</button></div></React.Fragment>))}{v.noJobs ? (<><div style={__style("padding:28px 0;font-size:18px;color:#3A3C4E")}>{"No openings listed here right now. Check back soon."}</div></>) : null}</div></div></section><SiteFooter />{v.hasJob ? (<><div onClick={v.close} style={__style("position:fixed;inset:0;z-index:50;background:rgba(14,15,26,.75);display:flex;justify-content:flex-end")}><div onClick={v.stop} style={__style("width:min(640px,100%);height:100%;overflow:auto;background:#F4F3EE;color:#0E0F1A;padding:clamp(24px,4vw,44px);box-sizing:border-box;display:flex;flex-direction:column;gap:22px")}><div style={__style("display:flex;justify-content:space-between;align-items:flex-start;gap:16px")}><div style={__style("display:flex;flex-direction:column;gap:6px")}><span style={__style("font-size:13px;font-weight:800;letter-spacing:.1em;text-transform:uppercase;color:#0A8C83")}>{v.job.kicker}</span><span style={__style("font-size:clamp(30px,3.6vw,44px);font-weight:900;font-stretch:112%;text-transform:uppercase;line-height:.95")}>{v.job.title}</span><span style={__style("font-size:15px;color:#3A3C4E")}>{v.job.where}</span></div><button onClick={v.close} aria-label={"Close"} style={__style("all:unset;cursor:pointer;flex:none;width:44px;height:44px;border-radius:50%;border:2px solid #0E0F1A;display:grid;place-items:center")}><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2.75"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M18 6 6 18"}></path><path d={"m6 6 12 12"}></path></svg></button></div>{v.job.hasDesc ? (<><p style={__style("margin:0;font-size:17px;line-height:1.6;color:#3A3C4E;white-space:pre-line")}>{v.job.description}</p></>) : null}{v.job.hasReqs ? (<><div style={__style("display:flex;flex-direction:column;gap:10px")}><span style={__style("font-size:20px;font-weight:900;font-stretch:108%;text-transform:uppercase")}>{"You'll need"}</span><div style={__style("display:flex;flex-direction:column;border-top:2px solid #0E0F1A")}>{(v.job.requirements || []).map((r, $index) => (<React.Fragment key={$index}><span style={__style("padding:12px 0;border-bottom:2px solid #E2E0D8;font-size:16px")}>{r}</span></React.Fragment>))}</div></div></>) : null}{v.applied ? (<><div style={__style("background:#0E0F1A;color:#F4F3EE;border-radius:14px;padding:28px;display:flex;flex-direction:column;gap:10px")}><span style={__style("font-size:26px;font-weight:900;font-stretch:110%;text-transform:uppercase;color:#22D3C5")}>{"Application sent"}</span><span style={__style("font-size:16px;color:#C9CAD6")}>{"Your reference number is "}<strong style={__style("color:#22D3C5")}>{v.ref}</strong>{". We'll contact shortlisted candidates after the closing date."}</span></div></>) : null}{v.notApplied ? (<><form onSubmit={v.submit} style={__style("display:flex;flex-direction:column;gap:14px")}><div style={__style("display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,200px),1fr));gap:12px")}><label style={__style("display:flex;flex-direction:column;gap:6px;font-size:14px;font-weight:700")}>{"Name"}<input name={"names"} required onChange={v.set} style={__style("padding:13px;border:2px solid #0E0F1A;border-radius:8px;background:#fff;font-size:16px")} /></label><label style={__style("display:flex;flex-direction:column;gap:6px;font-size:14px;font-weight:700")}>{"Surname"}<input name={"surname"} required onChange={v.set} style={__style("padding:13px;border:2px solid #0E0F1A;border-radius:8px;background:#fff;font-size:16px")} /></label><label style={__style("display:flex;flex-direction:column;gap:6px;font-size:14px;font-weight:700")}>{"ID number"}<input name={"idNumber"} required onChange={v.set} style={__style("padding:13px;border:2px solid #0E0F1A;border-radius:8px;background:#fff;font-size:16px")} /></label><label style={__style("display:flex;flex-direction:column;gap:6px;font-size:14px;font-weight:700")}>{"Cellphone"}<input name={"cell"} type={"tel"} required onChange={v.set} style={__style("padding:13px;border:2px solid #0E0F1A;border-radius:8px;background:#fff;font-size:16px")} /></label><label style={__style("display:flex;flex-direction:column;gap:6px;font-size:14px;font-weight:700")}>{"Email"}<input name={"email"} type={"email"} required onChange={v.set} style={__style("padding:13px;border:2px solid #0E0F1A;border-radius:8px;background:#fff;font-size:16px")} /></label><label style={__style("display:flex;flex-direction:column;gap:6px;font-size:14px;font-weight:700")}>{"Experience"}<select name={"experience"} onChange={v.set} style={__style("padding:13px;border:2px solid #0E0F1A;border-radius:8px;background:#fff;font-size:16px")}><option>{"None"}</option><option>{"Under 1 year"}</option><option>{"1\u20133 years"}</option><option>{"3+ years"}</option></select></label></div><label style={__style("display:flex;flex-direction:column;gap:6px;font-size:14px;font-weight:700")}>{"Why are you right for this role?"}<textarea name={"why"} onChange={v.set} style={__style("padding:13px;border:2px solid #0E0F1A;border-radius:8px;background:#fff;font-size:16px;min-height:100px")}></textarea></label><span style={__style("font-size:14px;font-weight:700")}>{"Documents"}</span><div style={__style("display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,180px),1fr));gap:8px")}>{(v.docs || []).map((d, $index) => (<React.Fragment key={$index}><label style={__style(`display:flex;flex-direction:column;gap:2px;padding:14px;border-radius:10px;border:2px dashed ${d.border};background:#fff;cursor:pointer`)}><span style={__style("font-size:15px;font-weight:800")}>{d.label}</span><span style={__style("font-size:13px;color:#3A3C4E;overflow:hidden;text-overflow:ellipsis;white-space:nowrap")}>{d.status}</span><input type={"file"} name={d.key} onChange={v.setFile} style={__style("display:none")} /></label></React.Fragment>))}</div><label style={__style("display:flex;gap:12px;align-items:flex-start;font-size:15px;line-height:1.45;cursor:pointer")}><input type={"checkbox"} required style={__style("width:22px;height:22px;flex:none;accent-color:#0E0F1A")} />{"I confirm my information is true, and I agree that Adom processes it under POPIA to consider my application."}</label>{v.hasError ? (<><div role={"alert"} style={__style("padding:14px 18px;border-radius:10px;background:#FFE1E1;color:#8A1C1C;font-size:15px;font-weight:700")}>{v.error}</div></>) : null}<button type={"submit"} style={__style("all:unset;cursor:pointer;align-self:flex-start;padding:17px 28px;border-radius:10px;background:#0E0F1A;color:#F4F3EE;font-weight:800;font-size:17px")} className="h-f8be57db">{v.submitLabel}</button></form></>) : null}</div></div></>) : null}</div>);
  }
}

export default CareersPage;
