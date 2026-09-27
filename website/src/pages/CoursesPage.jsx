// Generated from design/Courses.dc.html by tools/convert_design.py. Do not edit by hand.
/* eslint-disable */
import React from 'react';
import { s as __style } from '../lib/style.js';
import { ADOM } from '../lib/data.js';
import { AdomAPI, LMS_LOGIN, LMS_STAFF_LOGIN } from '../lib/api.js';
import SiteHeader from '../components/SiteHeader.jsx';
import SiteFooter from '../components/SiteFooter.jsx';

class CoursesPage extends React.Component {
  state = { query: '', area: 'All' };
  componentDidMount() { if (!ADOM) this.iv = setInterval(() => { if (ADOM) { clearInterval(this.iv); this.forceUpdate(); } }, 50); }
  componentWillUnmount() { clearInterval(this.iv); }
  renderVals() {
    const s = this.state, q = s.query.trim().toLowerCase();
    const all = (ADOM && ADOM.courses) || [];
    const list = all.filter(c => (s.area === 'All' || c.area === s.area) && (!q || [c.name, c.area, c.outcome, ...c.careers].join(' ').toLowerCase().includes(q)));
    return {
      query: s.query,
      onQuery: (e) => this.setState({ query: e.target.value }),
      reset: () => this.setState({ query: '', area: 'All' }),
      areas: ['All', 'Programming', 'Technology', 'Art & Media', 'Business'].map(a => {
        const on = a === s.area;
        return { label: a, bg: on ? '#F4F3EE' : 'transparent', fg: on ? '#0E0F1A' : '#F4F3EE', border: on ? '#F4F3EE' : '#2A2D42', select: () => this.setState({ area: a }) };
      }),
      courses: list.map(c => ({ ...c, href: '/courses/' + c.id })),
      empty: all.length > 0 && list.length === 0
    };
  }

  render() {
    const v = this.renderVals();
    return (<div style={__style("min-height:100vh;background:#0E0F1A;color:#F4F3EE;font-family:'Archivo',system-ui,sans-serif;overflow-x:hidden")}><SiteHeader active={"courses"} /><section style={__style("max-width:1320px;margin:0 auto;padding:88px 28px 56px;display:flex;flex-direction:column;gap:28px")}><span style={__style("font-size:14px;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:#22D3C5")}>{"Study@Adom / Courses"}</span><h1 style={__style("font-size:clamp(52px,8vw,120px);line-height:.9;margin:0;font-weight:900;font-stretch:115%;letter-spacing:-.02em;text-transform:uppercase")}>{"12 months to "}<span style={__style("color:#22D3C5")}>{"a new job."}</span></h1><p style={__style("font-size:20px;line-height:1.5;margin:0;max-width:620px;color:#C9CAD6;text-wrap:pretty")}>{"Every course runs for a year, on campus or through e-Learning. Each one ends with a project you can show an employer."}</p></section><section style={__style("max-width:1320px;margin:0 auto;padding:0 28px 110px;display:flex;flex-direction:column;gap:32px")}><div style={__style("display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap")}><div style={__style("display:flex;gap:8px;flex-wrap:wrap")}>{(v.areas || []).map((a, $index) => (<React.Fragment key={$index}><button onClick={a.select} style={__style(`all:unset;cursor:pointer;padding:10px 18px;border-radius:8px;font-size:15px;font-weight:700;border:2px solid ${a.border};background:${a.bg};color:${a.fg}`)}>{a.label}</button></React.Fragment>))}</div><input value={v.query} onChange={v.onQuery} placeholder={"Search courses or jobs"} style={__style("font:inherit;font-size:16px;padding:12px 16px;border-radius:8px;border:2px solid #2A2D42;background:#181A2A;color:#F4F3EE;min-width:0;width:280px;max-width:100%")} /></div><div style={__style("display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,360px),1fr));gap:24px")}>{(v.courses || []).map((c, $index) => (<React.Fragment key={$index}><a href={c.href} style={__style("display:flex;flex-direction:column;background:#181A2A;border-radius:14px;overflow:hidden;text-decoration:none;color:#F4F3EE;border:2px solid #181A2A")} className="h-66f2e741"><div style={__style("aspect-ratio:16/9;background:#2A2D42")}><img src={c.img} alt={""} style={__style("width:100%;height:100%;object-fit:cover;display:block")} /></div><div style={__style("padding:24px;display:flex;flex-direction:column;gap:12px;flex:1")}><div style={__style("display:flex;justify-content:space-between;gap:12px;font-size:13px;font-weight:800;letter-spacing:.1em;text-transform:uppercase")}><span style={__style("color:#22D3C5")}>{c.area}</span><span style={__style("color:#7C7F96")}>{c.months}{" months"}</span></div><span style={__style("font-size:28px;font-weight:900;font-stretch:108%;line-height:1.02;text-transform:uppercase")}>{c.name}</span><span style={__style("font-size:16px;line-height:1.5;color:#A9ABBE")}>{c.outcome}</span><div style={__style("display:flex;gap:6px;flex-wrap:wrap;margin-top:auto;padding-top:8px")}>{(c.careers || []).map((j, $index) => (<React.Fragment key={$index}><span style={__style("padding:6px 10px;border-radius:6px;background:#0E0F1A;font-size:13px;font-weight:600;color:#C9CAD6")}>{j}</span></React.Fragment>))}</div></div></a></React.Fragment>))}</div>{v.empty ? (<><div style={__style("display:flex;flex-direction:column;gap:14px;align-items:flex-start;padding:24px 0")}><span style={__style("font-size:24px;font-weight:800")}>{"Nothing matches that yet."}</span><button onClick={v.reset} style={__style("all:unset;cursor:pointer;padding:12px 20px;border-radius:8px;background:#F4F3EE;color:#0E0F1A;font-weight:700")}>{"Show all courses"}</button></div></>) : null}</section><section style={__style("background:#F4F3EE;color:#0E0F1A")}><div style={__style("max-width:1320px;margin:0 auto;padding:80px 28px;display:flex;justify-content:space-between;align-items:center;gap:32px;flex-wrap:wrap")}><div style={__style("display:flex;flex-direction:column;gap:12px;max-width:640px")}><h2 style={__style("font-size:clamp(34px,4.4vw,60px);line-height:.95;margin:0;font-weight:900;font-stretch:112%;text-transform:uppercase")}>{"Not sure which one?"}</h2><p style={__style("font-size:18px;line-height:1.5;margin:0;color:#3A3C4E")}>{"Read the guide for new students, or call us on 013 7633 8331 and we'll talk it through."}</p></div><div style={__style("display:flex;gap:12px;flex-wrap:wrap")}><a href={"/prospective-students/"} style={__style("padding:18px 28px;border-radius:10px;background:#0E0F1A;color:#F4F3EE;font-weight:800;font-size:17px;text-decoration:none")} className="h-d5a474dd">{"Guide for new students"}</a><a href={"/apply/"} style={__style("padding:16px 26px;border-radius:10px;border:2px solid #0E0F1A;color:#0E0F1A;font-weight:800;font-size:17px;text-decoration:none")} className="h-8840149e">{"Apply now"}</a></div></div></section><SiteFooter /></div>);
  }
}

export default CoursesPage;
