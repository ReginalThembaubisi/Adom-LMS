import React from 'react';
import { renderToString } from 'react-dom/server';

const pages = import.meta.glob('./pages/*.jsx', { eager: true });

export function render(page, props = {}) {
  const Page = pages['./pages/' + page + '.jsx'].default;
  return renderToString(<Page {...props} />);
}

export { default as routes } from './routes.js';
