// Staff authenticate with HTTP Basic, and the browser holds those credentials in
// sessionStorage under a per-role key. Everything that reads a protected resource on their
// behalf goes through here, so there is one place that knows how the credential is stored
// and one place that decides how it travels.
//
// It travels in a header. It used to travel in the query string, as ?authToken=<base64>,
// which put lecturer and admin passwords into access logs, browser history and referrer
// headers — and, because the Word preview handed that same URL to Google's document viewer
// to fetch, into Google's request logs as well.

const STAFF_AUTH_KEYS = ['lecturer_auth', 'moderator_auth', 'assessor_auth', 'admin_auth'];

/** The stored base64 credential for whichever staff role is signed in, or null. */
export const getStaffAuthToken = () => {
    for (const key of STAFF_AUTH_KEYS) {
        const value = sessionStorage.getItem(key);
        if (value) return value;
    }
    return null;
};

/** Headers carrying the staff credential, or an empty object when nobody is signed in. */
export const staffAuthHeaders = (extra = {}) => {
    const token = getStaffAuthToken();
    return token ? { ...extra, Authorization: `Basic ${token}` } : { ...extra };
};

/** fetch() with the staff credential attached as a header. */
export const staffFetch = (url, options = {}) =>
    fetch(url, { ...options, headers: staffAuthHeaders(options.headers || {}) });

/**
 * Fetches a protected file and returns an object URL for it, plus its content type.
 *
 * An <iframe> or <img> cannot send an Authorization header, which is the whole reason the
 * credential used to be in the URL. Fetching here and handing the element a blob: URL keeps
 * the credential in the header where it belongs, and keeps the file on our infrastructure.
 *
 * The caller owns the returned URL and must revokeObjectURL it when done.
 */
export const fetchStaffBlobUrl = async (url) => {
    const res = await staffFetch(url);
    if (!res.ok) {
        throw new Error(`Could not load document (${res.status})`);
    }
    const blob = await res.blob();
    return { objectUrl: URL.createObjectURL(blob), contentType: blob.type };
};
