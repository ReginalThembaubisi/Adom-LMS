# Adom Technologies website

The public website: home, courses, learnerships, internships, university placements, careers, services, about, apply online and check status. Its forms send applications, job applications, appointment bookings and questions to the Adom LMS, where staff handle them in the admin dashboard.

It builds to plain static files (HTML, CSS, JavaScript, images), with one real HTML page per address. That means it can be hosted anywhere that serves files, **including xneelo's ordinary web hosting**, and search engines can read every page.

## Build

Needs Node.js 20 or newer.

```bash
cd website
npm ci
npm run build        # output in website/dist/
npm run preview      # look at the built site on http://localhost:4173
```

Settings, read at build time (see `.env.example`):

| Variable | What it is | Default |
|---|---|---|
| `VITE_LMS_URL` | The LMS the forms send to | `https://adom-lms-portal.onrender.com` |
| `VITE_SITE_URL` | The website's own address, used in the sitemap and link previews | `https://www.adomtechnologies.co.za` |

Building with `VITE_LMS_URL=` (empty) gives **demo mode**: forms show their success screens without sending anything.

The LMS must also allow the website to call it. On the LMS's Render service, set `CORS_ALLOWED_ORIGINS` to the website's address(es), e.g. `https://www.adomtechnologies.co.za,https://adomtechnologies.co.za`.

## Deploy

### Option A: Render static site (updates itself on every push)

In the Render dashboard: **New → Static Site**, choose this repository, then:

| Setting | Value |
|---|---|
| Branch | `main` |
| Root directory | `website` |
| Build command | `npm ci && npm run build` |
| Publish directory | `dist` |
| Environment | `VITE_SITE_URL` = the site's address (and `VITE_LMS_URL` if the LMS moves) |

Then add the custom domain under **Settings → Custom Domains** and create the DNS records Render shows you in xneelo's control panel. Email hosted at xneelo is unaffected, because email uses different DNS records (MX).

### Option B: xneelo hosting

1. Run `npm run build` on any computer with Node.js.
2. Upload **the contents** of `website/dist/` (not the folder itself) into the site's `public_html` folder with xneelo's File Manager or FTP. Include the hidden `.htaccess` file, which makes missing pages show the site's 404 page.
3. Repeat after every change to the site.

## Changing the design

The pages are generated from the Claude Design canvas. The design's source files live in `design/`.

1. Change the design on the canvas.
2. Export the changed `.dc.html` files into `website/design/`.
3. Run `npm run convert` (needs Python 3), which regenerates `src/pages/`, `src/components/` and `src/generated/`.
4. Build and check.

Don't edit the generated files by hand, because the next conversion overwrites them. The hand-written parts are `src/lib/` (the LMS connection, course data, style helper), `src/routes.js` (page addresses, titles and descriptions), `src/entry-*.jsx`, `index.html` and `prerender.mjs`.

## How it fits together

- `src/routes.js` lists every page: its address, component, title and description. Each course gets its own page at `/courses/<course-id>/`.
- `prerender.mjs` renders each page to HTML at build time, and writes `sitemap.xml`, `robots.txt`, `404.html` and `.htaccess`.
- In the browser, the page's React code takes over the prerendered HTML, so menus, filters and forms work.
- `src/lib/api.js` is the only place that talks to the LMS.
