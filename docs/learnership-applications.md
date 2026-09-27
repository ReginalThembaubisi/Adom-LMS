# Learnership adverts, applications and appointments

How the public website and the LMS share learnerships, applications and appointment requests.

People apply for five kinds of thing: a **course**, a **learnership**, an **internship**, a **job**, or a **university placement** (final-year work-integrated learning for students at TUT, DUT and similar). All five go through the same pipeline and appear in the admin dashboard's *Applications* tab, which can be filtered by type. Only learnership applicants can be enrolled on the LMS. For internships and placements, staff record which company the applicant was placed at.

## Flow

1. **Admin publishes an advert.** In the admin dashboard, open *Learnership Adverts*, fill in the advert and set its status to **Open**. It shows on the website at once and drops off automatically after its closing date.
2. **The public applies on the website.** The website posts the application form and documents to the LMS. The applicant gets a reference number such as `ADM-7KQ2M9XP`. Courses, internships, jobs and placements don't need an advert first.
3. **Admissions staff review it.** The *Applications* tab moves applications through `SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW → ACCEPTED / WAITLISTED / DECLINED`. Each move is recorded in the application's history and, by default, emailed to the applicant.
4. **Accepted applicants are enrolled.** *Enrol* creates the learner: a 9-digit student number, the learnership, its modules and a cohort (defaulting to the advert's intake). The uploaded ID copy, results and CV become the first versions in the learner's document vault. The welcome email tells the learner to set a password through *Forgot password*.

**Jobs and internships** work the same way as learnership adverts. The admin posts them under *Jobs & Internships* (category, division, positions, closing date, description, requirements) and sets them to **Open**. They then appear on the Careers page, and applications to them arrive in *Applications* linked to the opening. Internship openings create INTERNSHIP applications; every other category creates JOB applications.

An existing learnership becomes an advert just by filling in its advert fields. Learnerships that are only used inside the LMS stay **Draft** and never appear publicly.

## Public API (no sign-in)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/learnerships/openings` | Adverts currently taking applications, soonest closing first |
| GET | `/api/learnerships/openings/{slug}` | One advert. 404 once it stops taking applications |
| POST | `/api/applications` | Submit an application (multipart form, see below). Limited to 5 per hour per IP |
| GET | `/api/openings` | Job and internship openings taking applications, soonest closing first |
| POST | `/api/appointments` | Services page appointment request (JSON, see below). Limited to 5 per hour per IP |
| POST | `/api/applications/status` | `{"reference", "idNumber"}` → the applicant's status and timeline. Limited to 20 per 15 minutes per IP |

`GET /api/learnerships` still returns every learnership's `id`, `name` and `qualificationCode`, for the LMS registration page.

### `POST /api/applications` form fields

Required for every type: `programmeType` (`COURSE`, `LEARNERSHIP`, `INTERNSHIP`, `JOB` or `PLACEMENT`; it can be left out when a learnership is named), `idType` (`South African ID` or `Passport`), `idNumber`, `firstNames`, `surname`, `email`, `phone`, `popiaConsent=true`, and the file `idCopy`.

Required by type:

| Type | Fields |
|---|---|
| `LEARNERSHIP` | `learnershipSlug` or `learnershipId` |
| `COURSE` | `courseChoices`, 1 to 3 values, one per choice |
| `INTERNSHIP`, `JOB` | `openingId` for an advertised opening (this sets the type and title), or `positionTitle`; optionally `experience` |
| `PLACEMENT` | `university`, `qualification` (and optionally `placementStart`, e.g. `2027-02`, and `placementLength`, e.g. `3 months`) |

Other files a form can send: `results` (school results), `cv`, `transcript` (academic record), `registration` (proof of university registration), `placementLetter` (the university's placement letter), `qualification` (highest qualification) and `other`.

Optional: `title`, `dateOfBirth` (`YYYY-MM-DD`), `gender`, `race`, `disability`, `disabilityDetails`, `homeLanguage`, `streetAddress`, `suburb`, `town`, `postalCode`, `province`, `kinName`, `kinRelationship`, `kinPhone`, `highestGrade`, `schoolName`, `matricYear`, `subjectsJson` (e.g. `[{"name":"Mathematics","mark":72}]`), `currentActivity`, `previousStudy`, `motivation`, and the files `results` and `cv`.

### `POST /api/appointments`

JSON: `fullName`, `email`, `phone` and `service` are required. `company`, `preferredDate` (`YYYY-MM-DD`), `preferredTime`, `message` and the hidden `website` honeypot field are optional.

Files may be PDF, JPG, PNG or DOCX, up to 10 MB each.

The form must also include a field called `website`, hidden from people with CSS. Real applicants leave it empty. Bots tend to fill in every field, so a submission with `website` filled in gets a normal-looking answer but is not saved.

Errors come back as JSON `{"message": "..."}`, written to be shown to the applicant: 400 for invalid input, 409 for a duplicate application, 429 for too many attempts.

## Admin API (`/api/admin`, admin sign-in)

- `GET/POST /learnerships`, `GET/PUT/DELETE /learnerships/{id}`: learnerships with their advert fields and application counts. Delete only works for a learnership with no learners, categories, moderator assignments or applications.
- `GET/POST /openings`, `PUT/DELETE /openings/{id}`: job and internship openings with application counts. An opening can only be deleted while it has no applications.
- `GET /applications?type=&learnershipId=&openingId=&status=&q=`, `GET /applications/{id}`
- `POST /applications/{id}/status` `{"status", "note", "notifyApplicant"}`, `POST /applications/bulk-status` `{"ids", "status", "note"}`
- `PUT /applications/{id}/notes` `{"staffNotes", "hostCompany"}`, `POST /applications/{id}/enrol` `{"cohort"}` (learnerships only)
- `GET /applications/{id}/documents/{docId}/view`, `GET /applications/export.csv?type=&learnershipId=&status=`
- `GET /appointments?status=`, `PUT /appointments/{id}` `{"status", "staffNotes"}`: appointment requests move `NEW → CONFIRMED → COMPLETED`, or `CANCELLED`

## Configuration

`PORTAL_URL` (optional) is the LMS's public address, used in the welcome email, e.g. `https://adom-lms.onrender.com`.
