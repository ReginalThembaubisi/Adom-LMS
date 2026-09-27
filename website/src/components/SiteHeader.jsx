// Generated from design/SiteHeader.dc.html by tools/convert_design.py. Do not edit by hand.
/* eslint-disable */
import React from 'react';
import { s as __style } from '../lib/style.js';
import { ADOM } from '../lib/data.js';
import { AdomAPI, LMS_LOGIN, LMS_STAFF_LOGIN } from '../lib/api.js';

class SiteHeader extends React.Component {
  state = { open: false };
  renderVals() {
    const a = this.props.active || '';
    const study = ['courses', 'internships', 'placements', 'prospective', 'apply', 'status', 'elearning'];
    return {
      studyOpen: this.state.open,
      toggleStudy: () => this.setState(s => ({ open: !s.open })),
      openStudy: () => this.setState({ open: true }),
      closeStudy: () => this.setState({ open: false }),
      studyLine: study.includes(a) ? '#22D3C5' : 'transparent',
      studyLinks: [
        { label: 'Courses', href: '/courses/' }, { label: 'Learnerships', href: '/learnerships/' },
        { label: 'Internships', href: '/internships/' }, { label: 'University placements', href: '/university-placements/' },
        { label: 'Prospective students', href: '/prospective-students/' },
        { label: 'Apply online', href: '/apply/' }, { label: 'Check status', href: '/status/' },
        { label: 'e-Learning', href: '/e-learning/' }
      ],
      links: [['learnerships', 'Learnerships', '/learnerships/'], ['services', 'Services', '/services/'], ['careers', 'Careers', '/careers/'], ['about', 'About', '/about/']]
        .map(([k, label, href]) => ({ label, href, line: k === a ? '#22D3C5' : 'transparent' }))
    };
  }

  render() {
    const v = this.renderVals();
    return (<header style={__style("position:sticky;top:0;z-index:30;background:#F4F3EE;color:#0E0F1A;font-family:'Archivo',system-ui,sans-serif")}><nav style={__style("max-width:1320px;margin:0 auto;padding:10px 28px;display:flex;align-items:center;justify-content:space-between;gap:24px;flex-wrap:wrap")}><a href={"/"} style={__style("display:block;flex:none")}><img src={"/assets/adom-logo.png"} alt={"Adom Technologies \u2014 Technology without limit"} style={__style("height:56px;display:block")} /></a><div style={__style("display:flex;align-items:center;gap:26px;flex-wrap:wrap;font-size:15px;font-weight:600")}><div style={__style("position:relative")} onMouseLeave={v.closeStudy}><button onClick={v.toggleStudy} onMouseEnter={v.openStudy} style={__style(`all:unset;cursor:pointer;display:flex;align-items:center;gap:4px;padding:8px 0;border-bottom:3px solid ${v.studyLine}`)} className="h-83103ab7">{"Study "}<svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2.75"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m6 9 6 6 6-6"}></path></svg></button>{v.studyOpen ? (<><div style={__style("position:absolute;top:100%;left:-16px;padding-top:8px")}><div style={__style("min-width:240px;background:#0E0F1A;border-radius:12px;padding:8px;display:flex;flex-direction:column;box-shadow:0 18px 40px rgba(14,15,26,.35)")}>{(v.studyLinks || []).map((l, $index) => (<React.Fragment key={$index}><a href={l.href} style={__style("padding:11px 14px;border-radius:8px;font-size:15px;font-weight:600;color:#F4F3EE;text-decoration:none")} className="h-956ebd7a">{l.label}</a></React.Fragment>))}</div></div></>) : null}</div>{(v.links || []).map((l, $index) => (<React.Fragment key={$index}><a href={l.href} style={__style(`color:#0E0F1A;text-decoration:none;padding:8px 0;border-bottom:3px solid ${l.line}`)} className="h-83103ab7">{l.label}</a></React.Fragment>))}</div><div style={__style("display:flex;gap:10px;flex:none")}><a href={LMS_LOGIN} style={__style("padding:12px 18px;border-radius:8px;font-size:15px;font-weight:700;color:#0E0F1A;text-decoration:none;white-space:nowrap;border:2px solid #0E0F1A")} className="h-8840149e">{"myAdom"}</a><a href={"/apply/"} style={__style("padding:12px 20px;border-radius:8px;font-size:15px;font-weight:700;background:#0E0F1A;color:#F4F3EE;text-decoration:none;white-space:nowrap;border:2px solid #0E0F1A")} className="h-407e14d1">{"Apply now"}</a></div></nav></header>);
  }
}

SiteHeader.defaultProps = {"active": ""};
export default SiteHeader;
