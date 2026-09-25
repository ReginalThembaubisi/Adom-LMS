# Learnership adverts and applications

How the public website and the LMS share learnerships and applications.

## Flow

1. **Admin publishes an advert.** In the admin dashboard, open *Learnership Adverts*, fill in the advert and set its status to **Open**. It shows on the website at once and drops off automatically after its closing date.
2. **The public applies on the website.** The website posts the application form and documents to the LMS. The applicant gets a reference number such as `ADM-7KQ2M9XP`.
3. **Admissions staff review it.** The *Applications* tab moves applications through `SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW → ACCEPTED / WAITLISTED / DECLINED`. Each move is recorded in the application's history and, by default, emailed to the applicant.
4. **Accepted applicants are enrolled.** *Enrol* creates the learner: a 9-digit student number, the learnership, its modules and a cohort (defaulting to the advert's intake). The uploaded ID copy, results and CV become the first versions in the learner's document vault. The welcome email tells the learner to set a password through *Forgot password*.

An existing learnership becomes an advert just by filling in its advert fields. Learnerships that are only used inside the LMS stay **Draft** and never appear publicly.

## Public API (no sign-in)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/learnerships/openings` | Adverts currently taking applications, soonest closing first |
| GET | `/api/learnerships/openings/{slug}` | One advert. 404 once it stops taking applications |
| POST | `/api/applications` | Submit an application (multipart form, see below). Limited to 5 per hour per IP |
| POST | `/api/applications/status` | `{"reference", "idNumber"}` → the applicant's status and timeline. Limited to 20 per 15 minutes per IP |

`GET /api/learnerships` still returns every learnership's `id`, `name` and `qualificationCode`, for the LMS registration page.

### `POST /api/applications` form fields

Required: `learnershipSlug` (or `learnershipId`), `idType` (`South African ID` or `Passport`), `idNumber`, `firstNames`, `surname`, `email`, `phone`, `popiaConsent=true`, and the file `idCopy`.

Optional: `title`, `dateOfBirth` (`YYYY-MM-DD`), `gender`, `race`, `disability`, `disabilityDetails`, `homeLanguage`, `streetAddress`, `suburb`, `town`, `postalCode`, `province`, `kinName`, `kinRelationship`, `kinPhone`, `highestGrade`, `schoolName`, `matricYear`, `subjectsJson` (e.g. `[{"name":"Mathematics","mark":72}]`), `currentActivity`, `previousStudy`, `motivation`, and the files `results` and `cv`.

Files may be PDF, JPG, PNG or DOCX, up to 10 MB each.

The form must also include a field called `website`, hidden from people with CSS. Real applicants leave it empty. Bots tend to fill in every field, so a submission with `website` filled in gets a normal-looking answer but is not saved.

Errors come back as JSON `{"message": "..."}`, written to be shown to the applicant: 400 for invalid input, 409 for a duplicate application, 429 for too many attempts.

## Admin API (`/api/admin`, admin sign-in)

- `GET/POST /learnerships`, `GET/PUT/DELETE /learnerships/{id}`: learnerships with their advert fields and application counts. Delete only works for a learnership with no learners, categories, moderator assignments or applications.
- `GET /applications?learnershipId=&status=&q=`, `GET /applications/{id}`
- `POST /applications/{id}/status` `{"status", "note", "notifyApplicant"}`, `POST /applications/bulk-status` `{"ids", "status", "note"}`
- `PUT /applications/{id}/notes`, `POST /applications/{id}/enrol` `{"cohort"}`
- `GET /applications/{id}/documents/{docId}/view`, `GET /applications/export.csv?learnershipId=&status=`

## Configuration

`PORTAL_URL` (optional) is the LMS's public address, used in the welcome email, e.g. `https://adom-lms.onrender.com`.
