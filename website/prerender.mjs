// Writes one real HTML file per page (dist/about/index.html, ...) so the site works on any
// static host (Render, xneelo) and search engines see every page's content without running
// JavaScript. Runs after both Vite builds; see "build" in package.json.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const dist = path.join(root, 'dist');
const template = fs.readFileSync(path.join(dist, 'index.html'), 'utf-8');
const { render, routes } = await import(path.join(root, 'dist-server', 'entry-server.js'));
const siteUrl = (process.env.VITE_SITE_URL || 'https://www.adomtechnologies.co.za').replace(/\/+$/, '');

const esc = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

for (const route of routes) {
  const html = render(route.page, route.props || {});
  const head = [
    `<title>${esc(route.title)}</title>`,
    `<meta name="description" content="${esc(route.description)}">`,
    `<link rel="canonical" href="${siteUrl}${route.path}">`,
    `<meta property="og:title" content="${esc(route.title)}">`,
    `<meta property="og:description" content="${esc(route.description)}">`,
    `<meta property="og:image" content="${siteUrl}/assets/hero-study.jpg">`
  ].join('\n  ');
  const pageData = `<script>window.__PAGE__=${JSON.stringify({ page: route.page, props: route.props || {} }).replace(/</g, '\\u003c')}</script>`;
  const out = template.replace('<!--head-->', head).replace('<!--app-->', html).replace('<!--page-->', pageData);
  const file = path.join(dist, route.path, 'index.html');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, out);
}

// A friendly 404 that looks like the site: the home page's header and footer around a message.
const notFound = template
  .replace('<!--head-->', '<title>Page not found · Adom Technologies</title>\n  <meta name="robots" content="noindex">')
  .replace('<!--app-->', '<div style="min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:24px;padding:24px;text-align:center;color:#F4F3EE;font-family:Archivo,system-ui,sans-serif"><h1 style="font-size:64px;margin:0;text-transform:uppercase">Page not found</h1><p style="font-size:20px;color:#C9CAD6;margin:0">That page doesn\'t exist, or it has moved.</p><a href="/" style="padding:16px 26px;border-radius:10px;background:#22D3C5;color:#0E0F1A;font-weight:800;text-decoration:none">Go to the home page</a></div>')
  .replace('<!--page-->', '')
  .replace(/<script type="module"[^>]*><\/script>/, '');
fs.writeFileSync(path.join(dist, '404.html'), notFound);

fs.writeFileSync(path.join(dist, 'sitemap.xml'),
  '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
  + routes.map(r => `  <url><loc>${siteUrl}${r.path}</loc></url>`).join('\n') + '\n</urlset>\n');
fs.writeFileSync(path.join(dist, 'robots.txt'), `User-agent: *\nAllow: /\nSitemap: ${siteUrl}/sitemap.xml\n`);

// Apache (xneelo) reads this; Render ignores it. Serves 404.html for missing pages.
fs.writeFileSync(path.join(dist, '.htaccess'), 'ErrorDocument 404 /404.html\nOptions -Indexes\n');

fs.rmSync(path.join(root, 'dist-server'), { recursive: true, force: true });
console.log(`Prerendered ${routes.length} pages into dist/`);
