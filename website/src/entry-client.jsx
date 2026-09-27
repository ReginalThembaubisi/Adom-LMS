import React from 'react';
import { hydrateRoot, createRoot } from 'react-dom/client';
import './global.css';
import './generated/hover.css';

// Each prerendered page names its component and props in window.__PAGE__; only that page's
// code is downloaded.
const pages = import.meta.glob('./pages/*.jsx');

async function start() {
  const { page, props } = window.__PAGE__ || { page: 'HomePage', props: {} };
  const mod = await pages['./pages/' + page + '.jsx']();
  const Page = mod.default;
  const root = document.getElementById('root');
  if (root.hasChildNodes()) hydrateRoot(root, <Page {...props} />);
  else createRoot(root).render(<Page {...props} />);
}

start();
