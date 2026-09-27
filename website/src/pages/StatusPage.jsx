// Generated from design/Status.dc.html by tools/convert_design.py. Do not edit by hand.
/* eslint-disable */
import React from 'react';
import { s as __style } from '../lib/style.js';
import { ADOM } from '../lib/data.js';
import { AdomAPI, LMS_LOGIN, LMS_STAFF_LOGIN } from '../lib/api.js';
import SiteHeader from '../components/SiteHeader.jsx';
import SiteFooter from '../components/SiteFooter.jsx';

class StatusPage extends React.Component {
  state = { refNo: '', idnum: '', loading: false, error: '', result: null };
  async check(e) {
    e.preventDefault();
    const s = this.state;
    if (!s.refNo.trim() || !s.idnum.trim()) { this.setState({ error: 'Enter both your reference number and your ID or passport number.' }); return; }
    this.setState({ loading: true, error: '' });
    try {
      const result = await AdomAPI.checkStatus(s.refNo.trim(), s.idnum.trim());
      this.setState({ result, loading: false });
    } catch (err) {
      this.setState({ result: null, loading: false, error: err.message });
    }
  }
  renderVals() {
    const s = this.state, r = s.result || { timeline: [] };
    const good = ['ACCEPTED', 'ENROLLED'].includes(r.status), bad = ['DECLINED', 'WITHDRAWN'].includes(r.status);
    const stages = r.timeline || [];
    return {
      refNo: s.refNo, idnum: s.idnum, shown: !!s.result, hasError: !!s.error, error: s.error, r,
      checkLabel: s.loading ? 'Checking…' : 'View status',
      onRef: (e) => this.setState({ refNo: e.target.value }),
      onId: (e) => this.setState({ idnum: e.target.value }),
      check: (e) => this.check(e),
      badge: r.statusLabel || '', badgeBg: good ? '#22D3C5' : bad ? '#FF4D4D' : '#F4F3EE',
      declined: bad,
      timeline: stages.map((t, i) => {
        const done = t.state === 'DONE', cur = t.state === 'CURRENT', fail = t.state === 'FAILED', skip = t.state === 'SKIPPED';
        const last = i === stages.length - 1;
        return { title: t.label, note: skip ? 'Not needed' : done ? 'Done' : cur ? 'In progress' : fail ? '' : 'Still to come',
          mark: fail ? '✕' : done ? '✓' : skip ? '–' : String(i + 1),
          dotBg: fail ? '#FF4D4D' : done ? '#22D3C5' : 'transparent', dotFg: done || fail ? '#0E0F1A' : cur ? '#22D3C5' : '#7C7F96',
          dotBorder: fail ? '#FF4D4D' : done || cur ? '#22D3C5' : '#2A2D42', fg: done || cur || fail ? '#F4F3EE' : '#7C7F96',
          line: last ? 'transparent' : done ? '#22D3C5' : '#2A2D42' };
      })
    };
  }

  render() {
    const v = this.renderVals();
    return (<div style={__style("min-height:100vh;background:#0E0F1A;color:#F4F3EE;font-family:'Archivo',system-ui,sans-serif;overflow-x:hidden")}><SiteHeader active={"status"} /><section style={__style("max-width:1320px;margin:0 auto;padding:88px 28px 110px;display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,480px),1fr));gap:64px;align-items:start")}><div style={__style("display:flex;flex-direction:column;gap:28px")}><span style={__style("font-size:14px;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:#22D3C5")}>{"Study@Adom / Check status"}</span><h1 style={__style("font-size:clamp(52px,7.4vw,108px);line-height:.9;margin:0;font-weight:900;font-stretch:115%;letter-spacing:-.02em;text-transform:uppercase")}>{"Where's my "}<span style={__style("color:#22D3C5")}>{"application?"}</span></h1><p style={__style("font-size:20px;line-height:1.5;margin:0;max-width:500px;color:#C9CAD6")}>{"Enter the reference number from your confirmation email and the ID or passport number you applied with."}</p><form onSubmit={v.check} style={__style("display:flex;flex-direction:column;gap:12px;max-width:520px;width:100%")}><label style={__style("display:flex;flex-direction:column;gap:6px;font-size:14px;font-weight:700;color:#C9CAD6")}>{"Reference number "}<input value={v.refNo} onChange={v.onRef} placeholder={"e.g. ADM-7KQ2M9XP"} style={__style("all:unset;font-size:18px;padding:16px 18px;color:#F4F3EE;letter-spacing:.04em;border:2px solid #F4F3EE;border-radius:10px;text-transform:uppercase")} /></label><label style={__style("display:flex;flex-direction:column;gap:6px;font-size:14px;font-weight:700;color:#C9CAD6")}>{"ID or passport number "}<input value={v.idnum} onChange={v.onId} placeholder={"e.g. 0203155123089"} style={__style("all:unset;font-size:18px;padding:16px 18px;color:#F4F3EE;letter-spacing:.04em;border:2px solid #F4F3EE;border-radius:10px")} /></label><button type={"submit"} style={__style("all:unset;cursor:pointer;align-self:flex-start;padding:17px 28px;border-radius:10px;background:#22D3C5;color:#0E0F1A;font-weight:800;font-size:17px")} className="h-bbf0fe71">{v.checkLabel}</button></form>{v.hasError ? (<><span role={"alert"} style={__style("font-size:16px;font-weight:700;color:#FF4D4D")}>{v.error}</span></>) : null}<span style={__style("font-size:15px;color:#7C7F96")}>{"Haven't applied yet? "}<a href={"/apply/"} style={__style("font-weight:800")}>{"Apply online"}</a></span></div>{v.shown ? (<><div style={__style("background:#181A2A;border-radius:16px;padding:clamp(24px,4vw,44px);display:flex;flex-direction:column;gap:28px")}><div style={__style("display:flex;justify-content:space-between;align-items:flex-start;gap:16px;flex-wrap:wrap")}><div style={__style("display:flex;flex-direction:column;gap:4px")}><span style={__style("font-size:13px;font-weight:800;letter-spacing:.1em;text-transform:uppercase;color:#7C7F96")}>{"Application "}{v.r.reference}</span><span style={__style("font-size:24px;font-weight:800")}>{v.r.appliedFor}</span></div><span style={__style(`padding:8px 14px;border-radius:8px;font-size:14px;font-weight:800;text-transform:uppercase;letter-spacing:.06em;background:${v.badgeBg};color:#0E0F1A`)}>{v.badge}</span></div><div style={__style("display:flex;flex-direction:column")}>{(v.timeline || []).map((t, $index) => (<React.Fragment key={$index}><div style={__style("display:grid;grid-template-columns:36px minmax(0,1fr);gap:16px")}><div style={__style("display:flex;flex-direction:column;align-items:center")}><span style={__style(`width:32px;height:32px;border-radius:50%;display:grid;place-items:center;font-size:14px;font-weight:900;background:${t.dotBg};color:${t.dotFg};border:2px solid ${t.dotBorder};box-sizing:border-box`)}>{t.mark}</span><span style={__style(`flex:1;width:2px;min-height:24px;background:${t.line}`)}></span></div><div style={__style("display:flex;flex-direction:column;gap:4px;padding-bottom:22px")}><span style={__style(`font-size:18px;font-weight:800;color:${t.fg}`)}>{t.title}</span><span style={__style("font-size:15px;color:#A9ABBE")}>{t.note}</span></div></div></React.Fragment>))}</div><p style={__style("margin:0;font-size:17px;line-height:1.5;color:#C9CAD6")}>{v.r.message}</p>{v.declined ? (<><p style={__style("margin:0;font-size:16px;line-height:1.5;color:#C9CAD6")}>{"You can apply for something else, or call us on 013 7633 8331 to talk about your options."}</p></>) : null}</div></>) : null}</section><SiteFooter /></div>);
  }
}

export default StatusPage;
