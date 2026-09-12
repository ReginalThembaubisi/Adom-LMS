# Adom-LMS — Portfolio of Evidence (PoE) Implementation Brief

> **Status: Phase 0 complete and merged.** Phase 1 is next; its task order has been
> revised — see that section. This file is the working document: amend it here rather
> than keeping a second copy, so the plan and the code stay in the same history.

Repo: `github.com/ReginalThembaubisi/Adom-LMS`
Base package: `com.example.learnerassignments`
Stack: Java 17, Spring Boot, Spring Data JPA, Spring Security, MySQL, React (Vite) in `frontend/`, Cloudinary for file storage.

---

## 1. Goal

Learners on this learnership currently keep their Portfolio of Evidence (PoE) on USB sticks and personal laptops. The admin keeps a duplicate copy of every learner's folder and sends it to MICT SETA for verification.

We are moving that storage into the LMS. The system must be able to rebuild the exact folder structure SETA expects, on demand, as a downloadable zip.

Three outcomes:

1. All PoE files live in the system, scoped so each learner sees only their own.
2. Learners, assessors and moderators sign declarations electronically.
3. One button exports a learner (or a cohort) as a zip in the SETA folder structure.

---

## 2. The target folder structure

This is the real structure taken from a current learner's laptop. The exporter must reproduce it exactly.

```
AMANDA MNDAWE/
  1. PERSONAL DETAILS/
      Amanda Randy Mndawe CV.pdf
      Amanda Randy Mndawe ID Copy.pdf
  2. QUALIFICATION DETAILS/
      Amanda Randy Mndawe Agreement.pdf
      Amanda Randy Mndawe Matric.pdf
  3. ASSESSMENT GUIDELINES/
      Fundamentals/
      Cores/
      Electives/
  4. ASSESSMENT ACTIVITIES/
      Fundamentals/
      Cores/
      Electives/
  5. FEEDBACK/
      Fundamentals/
      Cores/
      Electives/
  6. ADDITIONAL EVIDENCE/          <-- new; catches loose files
  00_INDEX.pdf                     <-- new; generated manifest
```

Notes:

- The existing folders use inconsistent casing (`cores` vs `Cores`). The exporter generates canonical names from the database, so this problem disappears.
- Loose files currently sit in the learner's root folder (an "Assessment Instrument" Word doc, stray images). Section 6 exists so nothing is lost.
- Section 3/4/5 subfolders map to `Category.categoryType` — `FUNDAMENTAL` → `Fundamentals`, `CORE` → `Cores`, `ELECTIVE` → `Electives`.

---

## 3. What already exists — do not rebuild

The domain spine is already correct:

```
Learnership -> Category -> Module -> Assignment -> SubmissionSession -> Submission
```

| PoE need | Existing code | Status |
|---|---|---|
| Fundamental/Core/Elective axis | `Category.categoryType` | Done |
| Learner enrolment in modules | `learner_modules` many-to-many on `Learner.modules` | Done |
| Facilitator guides | `ModuleFile` (title, filePath, originalFilename, fileType) | Needs columns |
| Learner submissions | `Submission.filePath` | Done |
| Marked work / feedback | `Submission.markedFilePath`, `.feedback`, `.marksAwarded`, `SubmissionGradingHistory` | Needs columns |
| Roles | `Admin`, `Lecturer`, `Assessor`, `Moderator`, `Learner` | Exist, need scoping |
| Audit trail | `AuditLog` + `AuditLogService` | Reuse, add event types |
| Async infrastructure | `AsyncConfig` | Reuse for export |
| Email | `EmailService` (Brevo SMTP) | Reuse for export notification |
| Remote file storage | `CloudinaryService` | Needs auth mode change |

**Only two genuinely new subsystems are required: the learner document vault (sections 1, 2, 6) and the export assembler.**

---

## 4. Findings to fix before building on top

### 4.1 Learner routes are unauthenticated — BLOCKER — **RESOLVED (Phase 0)**

`SecurityConfig` opens the learner API:

```java
.requestMatchers(HttpMethod.GET, "/api/learners/*/submissions").permitAll()
.requestMatchers(HttpMethod.GET, "/api/learners/*/messages").permitAll()
.requestMatchers(HttpMethod.GET, "/api/learners/*").permitAll()
```

`LearnerController.getLearnerSubmissions` reads the identity straight from the path:

```java
@GetMapping("/{studentNumber}/submissions")
public ResponseEntity<List<StudentSubmissionHistoryDto>> getLearnerSubmissions(
        @PathVariable String studentNumber) { ... }
```

Anyone can request any learner's submissions, marks and feedback. `GET /api/learners` returns the full roster. Learner codes are emailed and WhatsApped to learners, so they are not secret.

`SubmissionController.checkAccess` is the right shape but accepts `learnerCode` as a **query parameter**, putting a credential into browser history, server logs and referrer headers.

This must be fixed first. Feedback isolation cannot be enforced on top of it.

**Resolved.** Identity comes from a bearer token resolved to a `LearnerPrincipal`; every
learner route moved to `/api/me/**`. The `learnerCode` query parameter is gone. Two further
holes of the same shape were found and closed while doing it: `POST /api/submissions` took
the learner code as a form field, so anyone could submit as anyone, and `/api/chatbot/ask`
answered with one learner's deadlines from a code in the request body while unauthenticated.

### 4.2 Cloudinary learner files are publicly delivered — **RESOLVED for new files (Phase 4)**

`CloudinaryService.uploadFile` uploads with `resource_type: "raw"` and default public delivery, returning `secure_url`, which is persisted in `Submission.filePath`. That URL works for anyone who holds it, with no authentication, forever.

`CloudinaryService.uploadBackup` in the same class already demonstrates the correct pattern: `type: "authenticated"` plus `getSignedBackupUrl()`. Apply that pattern to learner-facing files.

**What Phase 4 did and did not fix.** Files uploaded from Phase 4 onward are authenticated
resources addressed by public_id, so the URL alone no longer opens them. Files uploaded
*before* Phase 4 are still public resources at their existing `secure_url`, and nothing in
Phase 4 changes that: the stored URLs keep working precisely so the 298 submissions already in
production keep opening. Those URLs cannot be retracted without re-uploading each file as an
authenticated resource and rewriting its row — a bulk operation over other people's assessment
evidence, with a half-finished state that leaves part of the cohort's work unreachable. That
is a deliberate deferral, not an oversight. If a specific legacy file is known to have leaked,
the remedy is to re-upload that one file, which supersedes it through the normal path.

**The transition rule.** A value in a `filePath` column is a Cloudinary public_id only if it
starts with `CloudinaryService.SECURE_PREFIX`; anything else is a legacy URL (if it starts
`http`) or a path on disk. The rule is prefix-based rather than "not a URL means public_id"
because a stored path can legitimately be relative — `LegacySubmissionFileMigration` resolves
exactly that case — and `uploads/x.pdf` is shaped identically to a public_id. Every existing
row therefore keeps resolving the way it did before. `StoredFileService` is the only place
that decides this; do not re-derive it inline.

### 4.2b Files uploaded before Phase 4 are still publicly readable — **OPEN, accepted for now**

Phase 4 changed how files are *stored*; it did not change what was already stored. Roughly 298
submissions, plus their marked copies, sit at Cloudinary `upload` (public) URLs that work for
anyone holding them, with no session, indefinitely. Those URLs travelled through the app, and
before Phase 1 some of them travelled through a Word preview that handed the document to
Google.

**Why it was not fixed with Phase 4.** Retracting them means downloading each file and
re-uploading it as an authenticated resource, then rewriting its row — a bulk operation over
other people's assessment evidence, on a free-tier instance that sleeps, where a failure
part-way through leaves some of the cohort's work unreachable while the database still claims
it is there. The reversal record would have to be as careful as the file migration's.

**What makes it acceptable meanwhile.** The URLs are long, random and not enumerable; nothing
new is being created in this shape; and the exposure is unchanged from what it has been since
the system was built. It is not getting worse.

**What would change that.** Any evidence a URL has leaked, any subject access or SETA request
that turns on it, or the cohort growing large enough that the tail of old files matters more
than the risk of a bulk rewrite. The per-file remedy already exists and needs no new code: a
learner re-uploading a document, or an assessor re-uploading a marked copy, supersedes the old
row through the normal path.

**If it is done properly**, do it as a resumable job with a completion log line and a reversal
record, like `LegacySubmissionFileMigration` — not a script someone runs once and cannot
answer questions about afterwards.

### 4.2c Uploads broke on 3 September when they were switched to streaming — **RESOLVED**

`CloudinaryService.uploadFile` passed `file.getBytes()` from 30 July. Commit `1688809`
("Fix N+1 queries, unbounded findAll, **streaming upload**, async thread"), merged to main in
`788c3e8` on 2026-09-03, changed it to `file.getInputStream()` to avoid buffering the file.

Cloudinary's `Uploader.upload(Object, Map)` dispatches on `String`, `File` and `byte[]`, and
throws `IOException("Unrecognized file parameter")` for anything else. `InputStream` is handled
only by the separate `uploadLarge`. Verified against the pinned SDK 1.39.0 by bytecode and by
running it:

```
                    byte[]                  InputStream
plain upload        reaches the network     Unrecognized file parameter
authenticated       reaches the network     Unrecognized file parameter
```

So the request was never made, and it presented as "That file could not be uploaded. Please
try again." with a 400, because `LearnerDocumentService.store` caught the `IOException` and
discarded it.

**`type: "authenticated"` is not involved.** It makes no difference at this layer, and the
production delivery probe completed a full authenticated round trip — upload, signed URL,
fetch, byte comparison. The stream is the whole cause. Do not "fix" this by moving learner
files back to public delivery.

**The fix**: `file.getBytes()` on both uploaders, with the parameter typed `byte[]` rather than
`Object` so passing a stream is a compile error rather than a runtime failure naming no cause.

**Why it went unnoticed for a week**: `uploadBackup` already took `byte[]`, so backups and the
delivery health check were unaffected — the probe took a branch inside the SDK that no real
caller reached. A probe that does not enter where the caller enters proves nothing about the
caller. The check now goes through `uploadLearnerFile(MultipartFile)`.

**Memory note**: streaming was the point of the original change. `getBytes()` holds the file in
memory — 20 MB for a submission, 10 MB for a document. If that becomes a problem on a small
instance, the answer is `uploadLarge`, which does accept an `InputStream`, not a bare `upload`.

### 4.2d Submitted work could be invisible to the marker — **RESOLVED**

The grading console built its "submitted" list by walking the module's enrolled learners
(`learnerRepository.findByModules_Id`) and attaching each one's submission. That assumed
everyone who can submit is in the `learner_modules` join table. Nothing enforces it:
registration never writes those rows, and `submitAssignment` never checks them — it validates
the learner code and the session, and nothing else.

A learner scoped to a module by their **learnership** — which is the fallback
`ModuleService.isEnrolledOn` uses, and how the portal decides what they can see — could open
the session, submit, be shown "Submitted", and never appear in the facilitator's console. The
file was in storage, the row was in the database, and no screen anywhere showed it. Neither the
learner nor the facilitator had a way to notice.

Reproduced in a container: a learner registered through the normal flow has zero
`learner_modules` rows, submits successfully, and the session's submitted count was 0.

**The fix**: the submitted list is derived from the submissions themselves. The roster is still
used for the *unsubmitted* list, which is the question it can actually answer. Assessor and
moderator scoping is applied to both, so Phase 1's rule is unchanged: no assignment rows still
means nothing visible.

**The general rule**: two rules that decide the same thing will eventually disagree, and the
disagreement is where work gets lost. Visibility of a submission must not depend on enrolment
bookkeeping that nothing maintains.

**Worth checking against production data**, since this has been true for as long as the console
has existed:

```sql
SELECT COUNT(*) FROM submissions s
 WHERE NOT EXISTS (SELECT 1 FROM learner_modules lm
                    WHERE lm.learner_id = s.learner_id
                      AND lm.module_id = (SELECT a.module_id FROM submission_sessions ss
                                            JOIN assignments a ON a.id = ss.assignment_id
                                           WHERE ss.id = s.session_id));
```

**Measured in production on 2026-09-10: 114 of 299 submissions — 38% — were invisible to the
marker.** The affected rows belong to named cohort learners (Banele Mgwambi, Simlindile
Mazibuko, Sibusiso khoza, Amanda Randy Mndawe among them), most submitted on 2026-09-02, none
graded. The work was accepted, stored, and shown to the learner as "Submitted", and no
facilitator screen ever listed it.

They are visible now, so the remedy is to mark them, not to repair data. But **nobody should
assume the cohort's assessment record is complete until those are worked through** — a learner
who submitted on 2 September and was never assessed looks identical, in every report, to one
who never submitted.

Two follow-ups this exposes:

- **`learner_modules` was not populated for a large share of learners — RESOLVED.** The cause
  was not, as first recorded here, that registration never writes those rows: it does, and has
  since 2026-08-18. It enrols a learner on the modules that exist *at that moment*, and nothing
  went back when a module was created afterwards — so every module added after a cohort
  registered left that cohort off its roster. `LearnerModuleEnrolmentBackfill` fills the gap
  from two sources (a submission proves enrolment; the learnership implies it, which is the
  same rule registration and `isEnrolledOn` already apply), and `EnrolmentService` keeps it
  closed: module creation enrols the learnership's existing learners, and accepting a
  submission records enrolment as a safety net.
- **`submitAssignment` does not check enrolment at all.** Any valid learner code can submit to
  any session. That is why the two rules could drift this far apart without anything failing.

### 4.2e Marks replayed in the wrong place — **RESOLVED for new marks**

Annotation strokes were stored as raw canvas pixels and replayed without conversion, but the
two viewers do not render at the same scale: `PdfAnnotator` fits the page to its container
(so the scale depends on window width and whether the sidebar is open) while `PdfReplay` uses
a fixed 1.5. Every mark was therefore displaced by the ratio between those scales, growing with
distance from the top-left — a tick placed beside one table row appeared beside another. The
same applied to a marker's own marks if they resized their window mid-session, because the
annotator re-renders at the new scale and replayed old pixels unchanged.

On a portfolio that gets audited this is wrong evidence, not a cosmetic glitch: the position of
a tick is part of what the assessor asserted.

**The fix**: each stroke records the scale it was drawn at (`s`), and every draw converts into
the scale being rendered now. Position, stamp size and pen thickness all convert.

**Removing one mark.** Undo is last-in-first-out, so correcting an early tick meant undoing
every mark placed after it and redoing them — not a reasonable thing to ask of someone marking
a cohort. An Erase tool removes the mark under the pointer, searching newest-first so the mark
drawn on top is the one that goes. Hit-testing converts the stroke into the current scale
first, for the same reason drawing does.

**Marks saved before this stay approximate.** They carry no recorded scale, so there is nothing
to convert from; they are drawn unconverted, exactly as before. Any submission whose placement
matters should be re-marked. Nothing can recover a scale that was never written down.

### 4.3 Learner code generation can collide — **RESOLVED (Phase 0)**

`Learner.onCreate` generates the code with `Math.random()` against a `unique = true` column, while `LearnerCodeSequence` and `LearnerCodeSequenceRepository` exist but are unused. A collision surfaces as a raw constraint violation during self-registration. Use the sequence entity.

**Resolved.** Registration already allocated from the sequence; the `Math.random()` fallback
in `@PrePersist` now fails loudly instead of colliding quietly.

### 4.4 Learner submissions were served as static files — **RESOLVED (Phase 0)**

Not in the original survey, and worse than 4.1 in one respect: it needed no API call at all.

`SubmissionService` wrote local-disk submissions into `uploads/`, which `WebConfig` maps as
a public resource handler at `/uploads/**`. A stored file was readable by anyone who guessed
a filename — and the names were built from the learner code and session id, neither secret.

**Resolved.** Submissions write outside the served tree; `LegacySubmissionFileMigration`
moves the files collected before that change and rewrites their stored paths at startup.
`/uploads/**` now requires authentication.

### 4.5 Staff credentials travel in a query string — **OPEN, now Phase 1 task 1**

`GET /api/submissions/{id}/view?authToken=` accepts base64 HTTP Basic credentials as a query
parameter. This is the same defect as the `learnerCode` parameter in 4.1 and strictly worse:
`learnerCode` leaked an identifier, this leaks reusable lecturer and admin **passwords** into
access logs, browser history and referrer headers.

It is also the only reason `GET /api/submissions/*/view` is still `permitAll` — the security
filter cannot see credentials in a query string, so the controller's own check is currently
the only defence rather than the second layer. Closing it lets that line become
`.authenticated()`.

If access logs are retained anywhere beyond the container, they need scrubbing.

### 4.6 Duplicate source for module files

`Module` has both a `filePath` column and a `files` list of `ModuleFile`. `filePath` appears to be legacy. Confirm nothing reads it, then remove it, so section 3 of the export has exactly one source.

---

## 5. Non-negotiable rules

These apply to every phase. Violating any of them is a defect regardless of whether tests pass.

1. **Identity comes from the authenticated principal, never from a path variable, query parameter or request body.** No endpoint accepts a learner identifier as an argument to decide whose data to return.
2. **Guides fan out by enrolment. Feedback resolves by ownership. Never mix the queries.** There must be no repository method that returns feedback for a module without a learner filter.
3. **Return 404, not 403, when a learner requests a record belonging to someone else.** A 403 confirms the record exists.
4. **File references are Cloudinary public_ids, not URLs.** Files are streamed through an authenticated controller after the ownership check. No static serving, no redirects to Cloudinary.
5. **Notifications never contain another person's name.** "Your feedback for Systems Analysis is ready", never "Feedback ready for Amanda Mndawe".
6. **Signature and audit records are append-only.** No updates, no deletes. Withdrawal sets `revokedAt` and creates a new row.
7. **Bulk imports never commit without a human confirming a preview.** Unmatched files go to section 6; they are never guessed into place.
8. **The exporter reuses the same scoped service methods the portal uses.** It must not have its own queries, or the two can disagree.

---

## 6. Phases

Each phase is independently shippable. Do not start a later phase before the earlier ones are merged.

---

### Phase 0 — Learner authentication (BLOCKER) — **COMPLETE**

**Goal:** learners authenticate properly; identity is derived from a token.

Shipped in PR #10. Opaque server-side session tokens were chosen over JWT: a signed token
stays valid until it expires, and password reset is the recovery path after a suspected
compromise, so revocation had to be real. Only the SHA-256 digest of a token is stored.
33 tests, against a fixture where two learners hold marked work on the same module — an
unscoped query cannot pass by returning the only row in the database.

Tasks:

- Issue a session token on `POST /api/learners/login` (learner code + password against the existing `passwordHash`). Either a server-side session table or a signed JWT — pick one and be consistent.
- Add a filter/resolver that populates the `Authentication` principal for learners with role `ROLE_LEARNER`.
- Replace `/api/learners/{code}/...` with `/api/me/...` endpoints that read the learner from the principal.
- Remove every learner `permitAll` from `SecurityConfig` except: registration, login, forgot-password, reset-password, public learnership list, registration-status.
- Remove the `learnerCode` query parameter from `SubmissionController.checkAccess`. Keep the lecturer and admin branches; add learner identity from the principal.
- `GET /api/learners` (full roster) becomes `hasAnyRole("ADMIN","LECTURER")`.

Acceptance criteria:

- Learner A authenticated; `GET /api/me/submissions` returns only A's submissions.
- Learner A cannot retrieve learner B's submission file by id — expect 404.
- Unauthenticated request to any `/api/me/**` endpoint — expect 401.
- Existing learner portal flows in `frontend/src` still work end to end after being updated to send the token.

Write these as integration tests before changing the UI. `spring-security-test` is already a dependency.

Also done, beyond the original list:

- The Google Docs viewer is gone. It rendered Word submissions by having *Google* fetch the
  file, sending learner assessment work to a third party under no operator agreement — an
  operator relationship under POPIA that was never established. Files are fetched with the
  learner's token and rendered from an object URL; Word files are saved to open locally. If
  inline preview of Word matters later, convert server-side with PDFBox (arriving in Phase 8),
  not through a third-party viewer.
- `ViewTicketService` is built and tested but deliberately unused: a short-lived signature
  scoped to one resource is the right primitive for Phase 8's emailed export link, where the
  recipient's browser follows a bare URL with no header to send.

Two scope gaps knowingly left open, recorded in comments beside the rules themselves:

- `/uploads/**` is authenticated but unscoped — any signed-in learner can read any guide by
  path. Low severity while it serves course material rather than personal information.
  **Phase 4 resolves it.** Do not put anything personal there first.
- `/api/modules/**` and `/api/sessions/**` admit ASSESSOR and MODERATOR because neither role
  has an assignment table to narrow them to, so every assessor currently sees every module.
  **Phase 1 resolves it** — see below.

---

### Phase 1 — Assessor and moderator scoping

**Goal:** assessors and moderators see only their assigned learners.

Currently `Assessor` and `Moderator` are credential-only entities with no link to any learner, so `hasRole("ASSESSOR")` grants access to everything under `/api/assessor/**`.

New entities:

```
assessor_assignment    id, assessor_id, learner_id (nullable), category_id (nullable), assigned_at
moderator_assignment   id, moderator_id, learnership_id, cohort (nullable), scope, assigned_at
```

Tasks, **in this order** — the order matters, and it is not the order this section
originally gave:

**1. Remove the `authToken` query parameter (see 4.5).** Staff dashboards pass base64 HTTP
Basic credentials in the URL of `GET /api/submissions/{id}/view`. This goes first for the
same reason Phase 0 went first: it is a live credential leak, it is the only thing keeping
that route on `permitAll`, and every task below adds callers to the code that carries it.
Building scoping on top of it means doing this work twice.

The fix has the same shape as Phase 0, and the mechanism already exists. Staff already
authenticate properly, so the browser context that cannot send a header — an `<iframe>` src
— needs either the object-URL treatment the learner portal now uses, or a `ViewTicketService`
ticket minted after the staff access check. Prefer the object URL where the dashboard
controls the fetch; the ticket is there for the cases where it does not.

When it is gone, change `GET /api/submissions/*/view` to `.authenticated()` in
`SecurityConfig`, so the controller's own check becomes defence in depth rather than the
only defence. Scrub access logs if they are retained beyond the container.

**2. Entities, repositories, admin CRUD screens** for `assessor_assignment` and
`moderator_assignment`.

**3. A `ScopeService`** with methods like `canAccessLearner(principal, learnerId)` used by
every assessor/moderator controller method. Follow the pattern already used for lecturers in
`SubmissionController.checkAccess`, which resolves through `Module -> Category -> Lecturer`.

Two constraints on `ScopeService`'s shape, both easier to honour up front than to retrofit:

- **Do not assume an HTTP request is in scope.** It will be called from controllers *and*
  from the Phase 8 exporter, which runs on an `@Async` worker with no request bound to the
  thread. So it takes the identity it is scoping as a parameter; it does not reach into
  `SecurityContextHolder` itself. (Phase 0's `CurrentLearner` does read the context, and is
  deliberately a thin controller-side helper for that reason — the scoping decision it feeds
  belongs somewhere callable without a request.)
- **No assignment rows must resolve to nothing, not everything.** An assessor with an empty
  assignment set sees no learners. This governs every newly created account in the window
  before an admin assigns anyone to them, which is the state most such accounts are in when
  they first log in — and a permissive default there is indistinguishable from the bug this
  phase exists to fix. Write the test for the unassigned assessor first.

`ScopeService` must also narrow `/api/modules/**` and `/api/sessions/**`, not only the
learner-facing endpoints. Phase 0 restricted both to staff roles and had to admit ASSESSOR
and MODERATOR wholesale, because at that point neither role had anything to be scoped to —
so an assessor currently sees every module in the system. That is the second half of this
phase's job, and it is easy to miss because those two lines live in `SecurityConfig` rather
than in an assessor controller.

Acceptance criteria:

- No endpoint accepts credentials as a query parameter. `GET /api/submissions/*/view` is
  `.authenticated()`.
- Assessor assigned to learners A and B cannot read learner C's submission — expect 404.
- Moderator assigned to cohort 2026-01 sees no learner outside it.
- An assessor listing modules sees only those their assigned learners are enrolled on.
- Admin is unaffected.

---

### Phase 2 — Schema additions

Add columns; no behaviour change yet. Keep this phase mechanical.

`ModuleFile`:
- `poe_section` (int, default 3)
- `version` (int, default 1)
- `is_current` (boolean, default true)
- `visible_from` (datetime, nullable)
- `sha256` (varchar 64, nullable)

`Submission`:
- `guide_version_id` (bigint, nullable) — the `ModuleFile` version the learner worked from
- `feedback_status` (varchar 20, default `PUBLISHED` for existing rows, `DRAFT` for new)
- `feedback_visibility` (varchar 20, default `LEARNER`)
- `sha256` (varchar 64, nullable)
- `marked_sha256` (varchar 64, nullable)

Backfill script (one run, no manual filing needed):

```sql
UPDATE submissions SET feedback_status = 'PUBLISHED'
  WHERE marked_file_path IS NOT NULL OR feedback IS NOT NULL;
UPDATE module_files SET poe_section = 3, version = 1, is_current = TRUE;
```

Compute `sha256` at upload time going forward. Do not attempt to backfill hashes by re-downloading from Cloudinary.

**As shipped, every one of these columns is nullable, including the ones listed above with a
default.** That was not a preference. A `NOT NULL` column cannot be added to a table that
already holds rows without a database default, and this schema is managed by
`ddl-auto=update` where the generated DDL is not ours to write — on a deployment with
`SPRING_SQL_INIT_MODE=never`, `schema.sql` never runs and Hibernate emits the DDL itself. A
`NOT NULL` add would have failed the boot that shipped it, and a failed boot on auto-deploy
is a portal that is down for learners.

Two things follow, and both are easy to lose.

**Follow-up: tighten the columns.** Not yet done. The condition is that no nulls remain in
`module_files.poe_section`, `version`, `is_current` and in `submissions.feedback_status`,
`feedback_visibility` — the backfill's completion log reports this on every boot — *and* that
`SPRING_SQL_INIT_MODE` is known for the deployment, so the DDL path is understood rather than
guessed. Until both hold, leave them nullable. Without this written down, the next person
reads a nullable column and reasonably concludes that null means something.

**Until then, every query over these columns must treat null defensively.** `WHERE is_current
= true` silently drops rows where `is_current` is null, and `feedback_status = 'PUBLISHED'`
drops rows that were never backfilled. That is the worst failure shape available here: the
data exists, the query succeeds, and the row simply does not appear. Phase 3 onwards writes
those queries. Write them as `is_current IS NOT FALSE` / `(feedback_status IS NULL OR
feedback_status = ...)`, or establish first that the backfill has run against the database
you are querying.

---

### Phase 3 — Learner document vault (sections 1, 2, 6)

New entities:

```java
enum PoeDocumentType {
    CV(1, true), ID_COPY(1, true),
    AGREEMENT(2, true), MATRIC(2, true),
    OTHER(6, false);
    // fields: poeSection, required
}

enum ReviewStatus { PENDING, ACCEPTED, REJECTED }

@Entity @Table(name = "learner_documents")
class LearnerDocument {
    Long id;
    @ManyToOne Learner learner;
    @Enumerated(STRING) PoeDocumentType documentType;
    String filePath;            // Cloudinary public_id
    String originalFilename;
    String sha256;
    Integer version;
    boolean current;
    @Enumerated(STRING) ReviewStatus status;
    String reviewNote;
    LocalDateTime uploadedAt;
    String uploadedByRole;
}
```

Tasks:

- `POST /api/me/documents` (multipart) — learner uploads their own. New upload creates a new version; previous row sets `current = false`. Never overwrite.
- `GET /api/me/documents` — learner's own list with status.
- `GET /api/admin/learners/{id}/documents` and an accept/reject endpoint with a note.
- Extension allowlist: pdf, jpg, jpeg, png, docx. Size cap 10 MB. Reuse `InvalidFileException`.
- React: a "My documents" page in the student portal showing the four required slots, upload state, and reviewer notes.

**Do not build an admin bulk-upload path for these.** Learners upload their own; this removes roughly 160 manual uploads for a 40-learner cohort.

Acceptance criteria:

- Learner uploads a CV twice; two rows exist, one `current`.
- Learner A cannot read learner B's document by id — expect 404.
- Rejected document shows the reviewer note in the portal.

**A gap closed later, not during this phase's own build:** the two admin endpoints above
(`GET /api/admin/learners/{id}/documents` and the accept/reject endpoint) shipped with Phase 3,
but no admin screen ever called them — the task list's React line only ever covered the
learner-facing "My documents" page. For a long stretch after Phase 3 "shipped", an admin had no
way to accept or reject a learner's document at all except by hand-editing the database. Found
and fixed alongside the Portfolio Completeness dashboard (Phase 7): the per-learner drill-down
that dashboard already opens is where an admin naturally lands to see what a learner is
missing, so the review screen was built into that same drill-down rather than as a new one —
each required document's checklist row is enriched, where a document exists, with the file
itself and accept/reject actions. See Phase 7's own note below.

---

### Phase 4 — Cloudinary authenticated delivery — **COMPLETE**

Tasks:

- Add `uploadLearnerFile(MultipartFile)` to `CloudinaryService` using `type: "authenticated"`, mirroring the existing `uploadBackup`. Return the **public_id**, not `secure_url`.
- Persist public_ids in `LearnerDocument.filePath`, and for new `Submission` and `ModuleFile` rows.
- Serving: keep the fetch-and-re-serve approach already used in `SubmissionController.viewSubmissionFile` (it exists because Cloudinary's raw delivery does not set an inline-renderable Content-Type). Resolve the signed URL server-side via the existing `getSignedBackupUrl` pattern, fetch, re-serve.
- Handle both storage shapes during transition: if `filePath` starts with `http`, treat it as a legacy public URL; otherwise treat it as a public_id.

Acceptance criteria:

- A newly uploaded learner file's stored value is not a URL.
- Pasting a Cloudinary URL for a new file into a logged-out browser fails.
- Existing files uploaded before this phase still open in the app.

Shipped alongside, in a follow-up PR: a boot-time delivery health check
(`DeliveryHealthCheck`) that uploads a few bytes through the authenticated path, reads them
back through a signed URL, compares them, deletes the probe, and logs the outcome in every
case. It exists because this failure is silent: if signed delivery is rejected, uploads keep
succeeding and rows keep being written, and the vault collects documents nobody can read while
telling learners they were received. The check gates nothing — a transient Cloudinary problem
must not take the vault down — but the log line answers the question without anyone having to
go and test by hand.

Left for later, deliberately:

- **`ModuleFile` was not converted.** The brief's task list named it alongside `Submission`,
  but `ModuleFile.filePath` is consumed by the frontend as a direct `href` in three places
  (`AdminDashboard`, `LecturerDashboard`, `StudentPortal`). Storing a public_id there breaks
  all three until there is a fetch-and-re-serve endpoint for module files, which is a
  different change with a different blast radius. Facilitator guides are also course material
  rather than a named learner's personal evidence, so the exposure is not the same as an ID
  copy. Do it when the serving endpoint exists.
  Phase 4's authenticated round trip is **confirmed live**: a document uploaded after the byte
fix was stored as an `lms_secure/` public_id and opened through signed delivery in production,
which is the one claim the phase shipped unverified.

**The learner half is now done.** A facilitator's `.docx` brief downloaded as
  `1789044791251_Practical_2_-_LSUMS_IoT...` with no extension, because the browser was sent
  straight to the stored URL and named the file after the extension-less public_id. Learners
  now download through `/api/me/module-files/{id}/download` and
  `/api/me/sessions/{id}/brief`, which check enrolment, fetch through `StoredFileService` and
  send the real filename back. **The staff dashboards still link to `filePath` directly and
  still produce the extension-less download** — same fix, staff-scoped, not yet done.
  **Phase 8 will meet this too.** The export reads module files for section 3 (Assessment
  Guidelines), so its worker encounters `ModuleFile.filePath` in both a public-URL and a
  `/uploads/...` web-path shape. Read them through `StoredFileService` like everything else.
- **Submission DTOs shipped `filePath` and `markedFilePath` to clients — RESOLVED.** For
  files stored after Phase 4 these were harmless public_ids, but for legacy rows they were
  working public URLs, handed to every client that lists submissions — including a lecturer
  listing a whole session. Access control on the view endpoint does not help retrospectively:
  once a URL is out it is out. The frontend only ever used these fields as presence flags, so
  `SubmittedLearnerDto` and `StudentSubmissionHistoryDto` now carry `hasMarkedCopy`, and
  neither they nor `SubmissionResponse` carry `filePath` at all. **Do not add a storage path
  back to a DTO.** A client that needs the file fetches `/api/submissions/{id}/view`, which
  checks ownership before it reads anything.

---

### Phase 5 — Feedback draft/publish and visibility — **COMPLETE**

Tasks:

- Facilitator marking writes `feedback_status = DRAFT`. Nothing is visible to the learner while draft.
- A "Release feedback" action on the session sets all its feedback to `PUBLISHED`, stamps `published_at`, and creates one notification per learner.
- `feedback_visibility`: `LEARNER` (default) or `INTERNAL`. Moderator reports are `INTERNAL` — they export to the PoE for SETA but never appear in the student portal.
- The learner-side query filters on `PUBLISHED` and `LEARNER` unconditionally.

Acceptance criteria:

- Draft feedback is invisible to the learner via API and portal.
- An `INTERNAL` record appears in the export but not in `GET /api/me/feedback`.
- Releasing a session creates exactly one notification per learner, containing no other learner's name.

Decisions taken while building it, because the tasks above do not settle them:

- **A moderator's report does not replace the facilitator's feedback.** A submission has one
  feedback field but several reports over its life. Marking the submission INTERNAL because a
  moderator wrote a report would hide the facilitator's comment from the learner too, which is
  the opposite of what moderation is for. Visibility therefore also lives on
  `SubmissionGradingHistory`, per report: the moderator's outcome and marks update the
  submission, their written report is INTERNAL and stays in the history for the export, and the
  learner keeps reading what the facilitator wrote.
- **Re-marking released work stays released.** Retracting feedback somebody has already read is
  worse than showing them a correction, so a re-mark on published work publishes immediately
  rather than dropping back to draft.
- **Releasing is ADMIN and LECTURER only**, because `POST /api/sessions/**` already is.
  Assessors mark; the facilitator decides when a cohort sees its results. If assessors should
  release too, that is a `SecurityConfig` change and a deliberate one.
- **The whole of marking is withheld together** — outcome, marks, comment, marked copy and
  annotations. Releasing an outcome with no explanation would be worse than releasing nothing,
  and the status a learner sees while marking is withheld is derived from the due date rather
  than remembered, since grading overwrites it.
- **The Grading Console says the marking is held, and releases it.** A facilitator who does not
  know feedback is being withheld will not go looking for a button, so the console shows how
  many marked submissions are not visible to learners and offers the release beside that. The
  confirmation names how many *learners* will be notified rather than asking "are you sure":
  from the learner's side this is one-way, because once somebody has seen a result they have
  told somebody. Counts are in people, not submissions — a learner who submitted twice is one
  person told.
- **`notifications` is Phase 6's table, built to its shape here.** Phase 5 only writes the
  feedback-released row; Phase 6 adds the badge counts and the live push that read it. Building
  a temporary notification to throw away would have cost more than agreeing the schema early.

**A publication backfill ships with it, and is the part that matters most.** Until this phase
nothing read `feedback_status`, so every marked submission was visible whatever it said. Phase
2's backfill tried to prevent Phase 5 retracting that, and mostly did, but it decided by looking
for a rasterized marked copy or a non-empty comment — which misses work graded with an outcome
and marks but no written comment, and everything marked after that backfill ran, which takes the
entity default of DRAFT. `FeedbackPublicationBackfill` publishes anything with a `graded_at`,
which is the honest record that marking happened and the learner could see it, stamping
`published_at` with the date it was marked rather than the date of the backfill. Internal
reports are never published.

**Found during Phase 8's verification, fixed here: the backfill ran on every boot, not once.**
Its condition — graded, not yet published, not internal — was written to describe historical
rows from before this phase existed, but it describes a submission graded five minutes ago
through the real marking screen exactly as well: `gradeSubmission()` sets `graded_at` and
`DRAFT` together on every grading action, forever, by design. A data-shaped condition that
never stops matching ran on every boot, and on a free-tier instance that spins down when idle,
"every boot" can mean hours after a facilitator marked something and deliberately left it held,
with no deploy in between. Phase 5's whole point — release is a decision, not a default — was
silently undone by its own backfill.

A date cutoff has the same ambiguity by another route: it has to be right forever, on every
environment this runs in, and a row graded one second after the cutoff looks identical to one
graded one second before it. The fix instead is a completion marker — a row in
`system_settings` (key `POE_FEEDBACK_PUBLICATION_BACKFILL_DONE`) written once the first real run
finishes, in the same transaction as the publishes it made. Every later boot finds the marker
and does nothing at all: it does not ask "is this row old enough", it asks "has this task
already run", which is a fact about the task rather than a guess about any one row.

The marker is only as durable as the table it lives in, so `system_settings` was added to
`BackupService`'s table list — it was not there before, meaning even the unrelated
registration-status flag was previously one restore away from silently resetting. Verified with
a real `pg_dump`/`pg_restore` cycle: grade a submission through the real endpoint, leave it
DRAFT, restart (marker present, nothing touched), dump the database, drop it, restore it, start
again — still DRAFT, marker still present, `already ran (marker present)` in the log both times.

Residual risk, stated rather than hidden: a restore from a backup taken *before* this table was
added to `BackupService`, or from anywhere outside this app's own backup/restore path, would
still come back with no marker and be indistinguishable from a fresh install. There is no way to
recover the original distinction after the fact once fresh, legitimately-held drafts exist
alongside old, genuinely-historical ones — which is exactly the ambiguity this fix exists to
close going forward, not retroactively.

---

### Phase 6 — Notifications and live push — **COMPLETE**

Tasks:

```
notification    id, user_id, user_role, type, ref_type, ref_id, created_at, read_at
```

- Write notification rows on: guide published, feedback released, document rejected.
- Badge counts on portal load.
- `SseEmitter` endpoint for live push to the open portal. Fall back to polling every 45 seconds if the connection drops — learners are often on poor mobile connections.

Do not use WebSockets; one-way push is all this needs.

Decisions taken while building it:

- **The poll is the delivery mechanism; the stream is an optimisation on top of it.** The
  client polls every 45 seconds whether or not the stream is connected, rather than only when
  it drops. Learners are on mobile connections that fail silently, behind proxies that buffer,
  and on phones that sleep through events; if the stream never connected at all the badge would
  still be right within 45 seconds. If `NotificationStream` stopped working entirely the
  product would be slower and still correct.
- **The push carries no content.** It says "go and look" and the client fetches through the
  authenticated endpoints, so one place decides what a learner may see and a stale connection
  can never deliver something they should no longer have.
- **The client reads the stream with `fetch`, not `EventSource`.** `EventSource` cannot set a
  header, and the only alternative would be the session token in the query string — the exact
  leak removed in Phase 1, which puts credentials into access logs, browser history and
  referrer headers. Streaming `fetch` keeps it in a header.
- **Only a rejection notifies, not an acceptance.** A badge that lights up for things needing
  no action stops being read, which then hides the one that mattered. The rejection carries the
  reviewer's note, because being told a document was rejected without the reason means
  guessing, re-uploading the same thing, and being rejected again.
- **`NotificationService.stripLinks` removes URLs from any body.** Belt to the braces of
  writing them carefully: the rule matters more than any one call site, and the next person
  adding a notification will not read that class first.
- **The stream can be switched off from the environment.** `NOTIFICATIONS_STREAM_ENABLED=false`
  makes the endpoint answer 503 and the portal keeps polling — a performance switch, not a
  feature switch, because the badge is delivered by the poll either way. This instance has
  512MB and the stream holds one connection per open portal; if that turns out to be the wrong
  trade on a busy day, turning it off should be a restart rather than a code change, a review
  and a deploy while people are trying to use the thing.
- **Publishing module material is the only fan-out**, so it is the only place a mistake reaches
  a whole cohort at once. It reads enrolment from the join table — which is why populating that
  table mattered; before it was fixed, a new guide would have reached nobody.

**Found in production, root-caused and fixed after the admin document review screen shipped:**
rejecting a document came back "the database rejected that change because it would leave the
data inconsistent. Nothing was saved" — the reviewer's own decision was lost, not just the
notification. Reproduced against a real PostgreSQL container by recreating the exact table
shape production almost certainly has: Hibernate's `ddl-auto=update` generates a native `CHECK`
constraint for an `@Enumerated(EnumType.STRING)` column from whichever enum values exist *at the
moment the table is first created*, and never widens that constraint when the enum gains new
values later — it only ever adds missing tables and columns. `notifications` was very likely
created at Phase 5's deploy, when `NotificationType` had only `FEEDBACK_RELEASED`; Phase 6 added
`DOCUMENT_REJECTED` and `GUIDE_PUBLISHED` to the enum and the code that writes them, but nothing
in a later `ddl-auto=update` boot would ever go back and add those two values to a `CHECK`
constraint that already exists. The result: a `DOCUMENT_REJECTED` (and probably a
`GUIDE_PUBLISHED`) notification has likely never been insertable in production at all, and every
attempt failed inside the same transaction as the write it rode along with — for document
review, that means the reviewer's decision itself, not merely the notification.

Confirmed by recreating this exact shape locally (a `notifications_type_check` constraint
narrowed to `('FEEDBACK_RELEASED')`) and replaying the failing request: same error, same lost
decision. Fixed at the code level, not by trying to patch the constraint from application code
(which can't run arbitrary DDL against a table it doesn't own the migration for): `notifyLearner`
now runs in its own transaction (`REQUIRES_NEW`), and every caller that cannot afford to lose its
own work over a notification problem — `LearnerDocumentService.review`,
`FeedbackReleaseService.release`, and `notifyModuleLearners`'s own fan-out loop — catches and
logs a failure from it rather than letting it propagate. This makes the fix permanent regardless
of what the constraint says, including for any *future* `NotificationType` value that hits the
same trap. `GlobalExceptionHandler`'s `DataIntegrityViolationException` handler was also silently
swallowing the real exception with no log line at all; it now logs the full detail, which is what
made this diagnosable in the first place.

**Still open, and not something application code can fix:** the live `notifications` table's
`CHECK` constraint itself. Until someone with production database access runs
`ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;` (Hibernate does not
require the constraint to exist and will not object to it being gone), a document rejection or a
guide publication will keep failing to write its notification row — the review or the publish
will now always still succeed, but the learner will not get a badge or a live push for either
until that constraint is corrected. This is worth confirming directly: query `notifications` for
any `DOCUMENT_REJECTED` or `GUIDE_PUBLISHED` rows at all; if there are none, every rejection and
every guide publication to date has silently failed to notify anyone.

---

### Phase 7 — Completeness dashboard — **COMPLETE**

Tasks:

- Per-learner checklist: required `PoeDocumentType`s, plus submission coverage across enrolled modules. Three states — green (accepted), amber (uploaded, pending review), red (missing).
- Cohort view: counts of complete vs incomplete, and the most common missing item.
- Add `required_from` (date) to document type configuration so a cohort that started before go-live does not show as entirely red. Work predating `required_from` is not counted as missing.

This screen replaces the admin manually opening folders to check readiness before a SETA submission. It is the highest-value screen in the project — build it properly.

**More than one learnership runs at a time.** The model already supports it — each learnership
owns its own Fundamental/Core/Elective categories, and a learner belongs to exactly one
learnership — but this phase as written assumes there is only one, in two places.

- **Required document types are a global enum.** `PoeDocumentType` fixes CV, ID copy,
  agreement and matric as required for everybody. Different qualifications require different
  evidence, so the required set belongs to the learnership rather than to the codebase. The
  lighter change is to keep the enum as the vocabulary of document *types* and move the
  *required set* — with the `required_from` date this section already calls for — into
  per-learnership configuration; a learnership that does not need a matric certificate then
  stops showing every learner as red for a document it never asked for. The heavier change,
  only if a learnership needs a type the enum does not have, is to make the type itself a table
  rather than an enum. Decide which before building the checklist, because the checklist reads
  this on every row.
- **The dashboard must filter by learnership and cohort.** "How many are complete" is not a
  meaningful number across two qualifications with different requirements, and the admin
  preparing a SETA submission is looking at one of them, not both.

Decisions taken while building it:

- **The enum stayed the vocabulary; the required set moved to a table.** The lighter of the two
  options above. `poe_document_requirements` holds (learnership, document type, required,
  required_from); `PoeDocumentType` still names the types. No learnership so far wants a type
  the enum does not have, and making the type a table would have put a join on every row of the
  highest-traffic screen in the project to buy nothing anybody has asked for.
- **Every count says whether it counts people or things, and there is no field named
  `outstanding` on its own.** Two bugs in this build came from counting rows where people were
  meant. `documentsOutstanding` and `learnersWithDocumentsOutstanding` are different numbers on
  the same screen — 8 and 3 on the verification data — and a facilitator does different work
  with each. "Most commonly outstanding" counts learners per item, not rows, for the same
  reason.
- **The four learner-state counts are mutually exclusive and sum to the learners in scope**, so
  the screen can be checked by adding up. The two "learners short of a document / short of a
  submission" figures deliberately overlap, and the screen says so where they appear.
- **Exempt is a state of its own, shown and counted, never folded into green or red.** A learner
  who registered before a requirement took effect was never asked for the document, so counting
  it missing is an accusation; but a portfolio of exempt items is not one to send to a SETA, so
  exempt items keep a learner out of Complete and land them in "not fully in scope". The failure
  this avoids is the dangerous one: a screen that says complete, a submission that gets rejected.
- **`required_from` is seeded to the day a learnership is first seeded.** For learnerships that
  existed before this shipped, that is the deploy date — which is exactly what stops the current
  cohort appearing as a wall of red for documents nobody has asked them for yet. An admin who
  wants that cohort chased clears the date under "Required documents", *after* the cohort has
  actually been asked. Clearing it is one click and takes effect immediately.
- **The same principle applies to submissions, which the phase as written did not mention.** A
  session that closed before a learner registered is exempt too. Without it, every learner who
  joined a running cohort would show red for every task that closed before they arrived — the
  same wall of red, arriving through the other half of the checklist.
- **Requirements are seeded when a learnership is created, not only at boot.** Otherwise a
  learnership created between deploys has learners and no requirements, and the fallback would
  have to guess. The fallback still exists for a learnership that reaches production unseeded,
  and it over-reports rather than under-reports — the screen names any learnership it applies
  to. Reporting somebody complete because nobody configured anything is the failure that reaches
  a SETA; reporting them incomplete when they are fine is a phone call.
- **A learner with no learnership gets an explicit red row rather than an empty checklist.**
  There is no requirement set to measure them against, and an empty checklist would report them
  complete.
- **Admin only, deliberately.** The screen reports on every learner in a learnership, which is
  more than any one facilitator is scoped to see. Opening it to facilitators means routing it
  through `ScopeService` first; that is a decision to take on purpose rather than by leaving a
  path off the deny list.
- **`marked` wins over `submitted` when a session has more than one submission row.** A
  resubmission leaves two; reporting the session as still awaiting marking would send a
  facilitator after work already assessed.

Known limitation: a session with no closing date is reported "not yet due" rather than missing.
The column is NOT NULL so this cannot arise today; if it ever does, the non-accusatory reading is
the right default, and the learner still shows as "not fully in scope" rather than complete.

**Added later: the per-learner drill-down is also the Phase 3 document review screen.** Phase
3 shipped `GET /api/admin/learners/{id}/documents` and an accept/reject endpoint with no admin
UI ever built for either — the review had to be done by hand-editing the database. Rather than
add a separate screen, the drill-down this dashboard already opens per learner (the one showing
`documentItems`/`submissionItems`) is where an admin naturally lands to see what is missing, so
that is where the review actions went: each required document's checklist row now shows the
file itself (fetched with this component's own admin credential and opened from an object URL,
never a direct link) with Accept and Reject buttons, where a document exists to act on. A
`MISSING` or `EXEMPT` row has nothing to open, so it renders exactly as it did before. The
existing checklist endpoint (`GET /api/admin/poe/completeness/learners/{id}`) already names
which document type each row is (`ChecklistItem.key`, e.g. `"CV"`) in exactly the same string
`LearnerDocument.documentType` uses, so the two responses join client-side with no backend
change at all — the drill-down fetches both in parallel and matches them by that key. `OTHER`
evidence is never a required type, so it never appears as a checklist row; it is listed and
reviewable underneath the four, the same way the learner's own "My documents" page lists it.
Rejecting without a note was already refused server-side (`LearnerDocumentService.review`); the
screen also disables its own "Confirm rejection" button until a note is typed, so the refusal is
felt as a UI constraint rather than a failed request, but the server-side refusal is what
actually matters and was not touched.

**Bug found in production, fixed same day: "Open" 403'd for a real admin.** The first version
of `openDocumentFile` used `utils/staffAuth.js`'s `fetchStaffBlobUrl`, a shared helper written
for `SubmissionMarker`/`SubmissionViewer` that guesses which staff role is signed in by checking
`sessionStorage` keys in a fixed order (`lecturer_auth`, `moderator_auth`, `assessor_auth`,
`admin_auth`) and using the first one it finds. That guess is only safe when a browser session
has ever held exactly one staff credential; the moment it has held more than one — which an
admin testing multiple role logins in the same browser will do without thinking about it — the
helper hands an admin-only request a real but non-admin credential. That authenticates fine and
then fails authorization, which is a 403, not a 401 — it reads as a permissions bug, not a
wrong-credential one, and every other call in this same component (the checklist, the document
list, every accept/reject) kept working throughout, because they all use this component's own
`authHeaders()` built from its own `token` prop rather than going through that shared helper.
Fixed by doing the same: `openDocumentFile` now fetches with `authHeaders()` directly, the exact
credential already proven correct by everything else on this screen.

---

---

### Phase 8 — Export — **COMPLETE**

**This must be asynchronous.** A 40-learner cohort is roughly 600 file fetches from Cloudinary; it will time out in a request thread.

```
export_job    id, requested_by_id, requested_by_role, scope_type, scope_ref,
              status, learner_count, file_count, result_public_id,
              created_at, completed_at, error
```

Export scopes: a single learner, a cohort, **a whole learnership**, a moderation sample, or a
single section. The learnership scope is the one SETA actually asks for — verification happens
per qualification, so the admin exports one learnership at a time rather than every learner the
system holds. It is also the largest job by far, which is worth knowing while sizing the worker
and the zip.

Flow:

1. `POST /api/poe/export` validates scope against `ScopeService`, creates a `QUEUED` job, returns immediately.
2. `@Async` worker (reuse `AsyncConfig`) streams each file into a `ZipOutputStream`.
3. Upload the finished zip as an `authenticated` raw resource.
4. Email a signed, short-expiry link via `EmailService`.
5. Write an `AuditLog` entry: who, scope, learner count, file count.

Path generation, derived not stored:

```
{learner.fullName}/1. PERSONAL DETAILS/
{learner.fullName}/2. QUALIFICATION DETAILS/
{learner.fullName}/3. ASSESSMENT GUIDELINES/{Categories}/{module.moduleName}/
{learner.fullName}/4. ASSESSMENT ACTIVITIES/{Categories}/{module.moduleName}/
{learner.fullName}/5. FEEDBACK/{Categories}/{module.moduleName}/
{learner.fullName}/6. ADDITIONAL EVIDENCE/
```

`{Categories}` maps `FUNDAMENTAL -> Fundamentals`, `CORE -> Cores`, `ELECTIVE -> Electives`.

Canonical filenames inside the zip:

```
{learnerCode}_{Surname}_{Initials}_{DocumentType}_v{n}.pdf
202600004_Mndawe_AR_ID-Copy_v1.pdf
```

Keep `originalFilename` in the database and show it in the UI; export with the canonical name.

Section 3 pulls the **pinned** `guide_version_id` for that learner where one exists, not the current version — so a moderator sees the brief the learner actually worked from.

Also generate `00_INDEX.pdf` (add PDFBox to `pom.xml`): learner details, every file listed with section, upload date, version, and signature status. Plus `SIGNATURES.csv` listing every signature event with its verification code.

Export scopes: single learner, whole cohort, moderation sample (selected learner subset), single section.

Acceptance criteria:

- Exported zip opens and the structure matches section 2 of this document exactly.
- An assessor's export contains only their assigned learners.
- Section 5 of learner A's zip contains no file belonging to learner B.
- `INTERNAL` moderator reports appear in the zip.

Decisions taken while building it, and two places this needed to depart from the brief as
written:

- **"Signature status" and `SIGNATURES.csv` assume Phase 9 exists. It doesn't yet.**
  `signature_events` is not built. `00_INDEX.pdf` prints "Not signed" against every row with a
  line explaining why, and `SIGNATURES.csv` carries the header format SETA expects with a
  comment and no data rows. The alternative — inventing signature data, or silently dropping
  the file — was worse either way: a fabricated signature is the one thing this export must
  never contain, and a missing file breaks the format an auditor is told to expect.
- **"Email a signed, short-expiry link" is not what ships.** This system has an established
  rule that a learner's stored files are never handed out as a direct URL, signed or not —
  every read goes through this server, which fetches and re-serves the bytes. An export bundle
  is a whole learnership's personal documents in one zip; it is that same rule under more
  pressure, not less. The completion notice instead says the export is ready and to sign in and
  download it from the Exports panel — no link, ever. Admin has no email column in this system
  at all, so an admin-requested export is status-only regardless; assessors and moderators do
  get an email, worded the same way.
- **The required set moved from the enum to a table (Phase 7's decision) is reused here.**
  `PoeDocumentType` still names sections 1, 2 and 6; which of them a learnership actually
  requires does not change what an export *contains* — a document that was supplied still
  exports even if the learnership no longer requires it, and nothing here reads
  `poe_document_requirements` at all. Completeness and export answer different questions on
  purpose: one is "is this ready", the other is "hand over what exists".
- **Every version of every document and submission is exported, not only the current one.**
  `LearnerDocument`'s own class doc says why current-only exists — "what a rejected-then-
  resubmitted document needs in order to show its own history" — and an export is exactly the
  audit trail that needs that history, more than the portal ever does.
- **One bad file does not fail the job.** A 600-file learnership export that aborted on the
  first unreadable row would report failure for 599 learners whose evidence was fine. Every
  file fetch is wrapped; a failure is skipped, counted, and logged, and the job still completes.
  Only a failure in the zip stream itself is allowed to fail the whole job, because at that
  point nothing further can be written regardless.
- **The zip is streamed to a temp file on disk, never held whole in memory**, and the upload to
  Cloudinary is handed the `File` directly rather than read into a `byte[]` first — the same
  512MB-instance discipline as everywhere else in this brief. The export worker's thread pool
  is capped at one job at a time for the same reason: two whole-learnership exports running
  together is the more realistic way this instance runs out of memory than any one export
  alone.
- **"Moderation sample" resolves through `ScopeService.accessibleLearnerIds`, not a second
  copy of the scoping rule.** An assessor or moderator's export is always their own reach,
  self-only even when the request tries to name someone else's id — so "an assessor's export
  contains only their assigned learners" holds by construction, off the same code already
  proven for their ordinary reads, rather than a rule that could quietly drift from it.
- **Cohort, whole-learnership and single-section exports are admin only.** These are the
  qualification-wide, SETA-facing scopes; a single learner and a moderation sample are open to
  the three roles who can request an export at all.
- **A section export means one category (Fundamental/Core/Elective), not one PoE folder
  number.** The brief names it "single section" without saying which; a category is what this
  codebase already calls a section elsewhere, and it is the one grouping an admin would
  plausibly want to re-run in isolation after fixing something in just that folder. It narrows
  what a learnership export contains to that category's modules only, and skips sections 1, 2
  and 6 entirely — a section re-run is not a substitute for the personal document folders.
- **Two learners with the same full name get distinct folders.** `{learner.fullName}/` as
  written collides the moment two learners share a name, which is not a rare event in a cohort
  drawn from a small set of common surnames. Folders are named
  `{learner.fullName} ({learnerCode})/` instead.
- **A caught bug worth naming: `@Async` calling `@Transactional` on the same object, from
  inside the same class, silently drops the transaction.** Self-invocation bypasses the Spring
  proxy that makes either annotation do anything, and it only surfaces once something runs
  through a real thread pool — every test in this codebase calls a service's synchronous half
  directly for exactly that reason, and this phase is the first to actually need the asynchronous
  one. Fixed with a `@Lazy` self-injected reference so the internal call still goes through the
  proxy. Caught, and the fix confirmed, only by running the whole thing in a container rather
  than trusting MockMvc.
- **A second bug the same run caught: an admin-only scope requested by a non-admin came back
  500, not 403.** `AccessDeniedException` is Spring Security's textbook signal for "not
  authorised", and the textbook answer is that the security filter chain turns it into a 403 —
  which is true only when nothing upstream already caught it. This codebase's
  `GlobalExceptionHandler` has a catch-all `@ExceptionHandler(Exception.class)`, and Spring MVC
  resolves that handler before the exception can ever reach the filter chain. Fixed with an
  explicit handler for `AccessDeniedException` returning 403. A unit test asserting the service
  throws the right exception type could not see this gap; only a request through the whole
  stack could.

Three more decisions, added on review before merge:

- **DRAFT marking is exported, not filtered — labelled, not hidden.** Phase 5 made release a
  learner-visibility gate, not an existence gate, and a moderator reviewing a portfolio needs
  the assessor's judgement whether or not the learner has been told yet. Filtering DRAFT out
  would mean section 5 arrives at the SETA missing marking that exists; including it silently
  would risk it read as final when it is not. So it is included, and said twice: a banner at
  the top of the rendered feedback text, and a release tag on that entry's own line in
  `00_INDEX.pdf` — `[released]` or `[DRAFT - NOT YET RELEASED]`. The tag is carried as its own
  field on the index entry rather than folded into the free-text label, specifically so a long
  session name being truncated in the index can never clip the tag itself — a truncated "not
  yet released" reads as a corrupted line, not a status, which is worse than not tagging it at
  all.
- **A failed job now says which stage it failed in** — resolving scope, building the bundle, or
  storing the result — rather than one generic message for every failure. A job stuck at
  RUNNING with no reason is the state an admin cannot act on: they cannot tell whether a retry
  is worth trying from one that will fail identically. The RUNNING transition itself is now
  inside the same try/catch as the rest of the job, closing the one narrow window where an
  early failure could previously leave a job with no recorded reason at all. Still uncaught:
  the JVM process itself dying mid-export (killed, OOM) leaves no chance to record anything —
  no code can catch that, and it is a different, harder problem than a job that fails cleanly.
- **The zip was already streamed to a temp file rather than held in memory**, and the temp file
  was already cleaned up in every outcome — this was checked, not changed, and is now also
  covered by a test that asserts no `poe-export-*` file survives a run, success or failure.

**Found while verifying this, outside Phase 8's own scope: `FeedbackPublicationBackfill` (Phase
5) republishes DRAFT marking on every boot, not only once.** Its condition — graded, not yet
published, not INTERNAL — matches any submission graded through the real `gradeSubmission()`
path today exactly as well as it matches the historical rows it was written for, because
`gradeSubmission()` sets `gradedAt` and `DRAFT` together on every grading action. On an instance
that restarts (a redeploy, a free-tier spin-down), any marking held as a draft is at real risk
of being auto-published on the next boot with no facilitator action. This needs its own fix and
its own decision about how to make the backfill one-shot rather than perpetual; it is not fixed
in Phase 8's PR.

---

### Phase 9 — Digital signatures

Legal context: section 13 of the Electronic Communications and Transactions Act 25 of 2002 governs electronic signatures in South Africa. An *advanced* electronic signature (accredited process) is required only where a signature is required by law and the law does not specify the type. PoE declarations are required by SETA policy and by agreement between the parties, so an ordinary electronic signature is sufficient — provided the method identifies the person, indicates approval, and is reliable for the purpose. Confirm with the ETQA before go-live; keep a "print signature page" fallback for wet ink.

```java
@Entity @Table(name = "signature_events")
class SignatureEvent {
    UUID id;
    String signableType;      // SUBMISSION, LEARNER_DOCUMENT, MODERATION_REPORT
    Long signableId;
    Long signerId;
    String signerRole;        // LEARNER, LECTURER, ASSESSOR, MODERATOR
    String declarationText;   // store the actual wording agreed to, not a template ref
    String specimenPath;
    String documentSha256;    // copied from the target row at signing time
    LocalDateTime signedAt;
    String ipAddress;
    String userAgent;
    String verificationCode;
    LocalDateTime revokedAt;
    String revokedReason;
}
```

Signing flow:

1. Document preview in the browser.
2. Declaration text with an explicit checkbox (intent).
3. Draw signature on canvas (`signature_pad`) or adopt a saved specimen.
4. Re-authenticate: password re-entry or emailed OTP (identity).
5. Server copies the stored `sha256` onto the event, writes the row, stamps a signature block plus QR into a PDF via PDFBox.
6. Record locks. A resubmission creates a new version and revokes the old signature.

Because `sha256` is stored at upload (Phase 2), signing requires no file fetch.

Verification page `/verify/{verificationCode}` — publicly reachable, shows only: document type, signer role, signature date, and whether the hash still matches. **No learner name, no ID number, no file content.**

Follow the append-only pattern already established by `SubmissionGradingHistory`.

**Built. Decisions made shipping this, where they differ from the sketch above:**

- **`MODERATION_REPORT` is not in `SignableType`.** There is no `ModerationReport` entity in
  this codebase — a moderator's judgement lives on a `SubmissionGradingHistory` row,
  distinguished from a facilitator's only by `feedbackVisibility == INTERNAL`, not as a document
  of record with its own identity the way a `Submission` or `LearnerDocument` is. Signing that
  row would sign a log entry, not an artifact. Revisit if moderation reports ever become their
  own entity; until then only `SUBMISSION` and `LEARNER_DOCUMENT` are signable, and only a
  learner signs, their own submission or their own document. Staff-side signing (a lecturer,
  assessor or moderator countersigning something) has no frontend built for it — the backend's
  role check does not stop a staff caller structurally, but nothing calls it as one yet, and it
  is not exercised or claimed as working.
- **`id` is a `Long`, not a `UUID`.** Every other entity in this codebase uses an
  identity-column `Long`; `verificationCode` — a separate, unguessable, URL-safe random token —
  already carries the property a UUID primary key would have been for here, so there was no
  reason to introduce the one UUID primary key in the schema.
- **`specimenPath` became `specimenImage`, a base64 PNG data URI stored inline, not a file.** A
  canvas signature is a few KB. Storing it as a normal `TEXT` column avoids adding a second
  fetch-and-re-serve path for one small image, and it never needs to be served
  unauthenticated — the public verify endpoint never returns it.
- **Re-authentication is password re-entry only; no emailed OTP.** The brief offers either.
  Password re-entry needed no new infrastructure and answers the same identity question; an OTP
  path can be added later without changing the event shape.
- **The certificate is a standalone one-page PDF (declaration, signer role, date, hash, QR to
  the verify page), not a stamp applied to the original file.** The brief's step 5 says "stamps
  a signature block plus QR into a PDF via PDFBox", which reads as editing the submitted file in
  place. That would mean parsing whatever the learner uploaded — a scanned JPEG, a DOCX, a PDF
  of unknown structure — and touching the one thing an audit trail most needs untouched: the
  original evidence. A separate certificate sidesteps both problems and means signing (and
  generating the certificate) needs no file fetch at all, not even the "cheap because sha256 is
  already stored" fetch the brief describes for the original file.
- **The certificate is generated by an `@Async` worker (`signatureTaskExecutor`) after the
  `SignatureEvent` row is already committed, and the row's `stampedFilePath` is filled in once
  the PDF is stored.** This is the one field ever written after insert besides
  `revokedAt`/`revokedReason`; every audit-relevant field (signer, hash, declaration, timestamps)
  is fixed at creation. Confirmed against a running PostgreSQL 16 instance, not just the H2 test
  suite: `SignatureService.stampAsync` calls back into `self.generateAndStoreCertificate` through
  a `@Lazy`-injected self-reference, the same fix Phase 8 needed after discovering that an
  `@Async` method calling a `@Transactional` one on the same object, from inside the same class,
  bypasses the Spring proxy that makes either annotation do anything. Signing through the real
  HTTP API against Postgres showed the certificate committed and downloadable (a real `%PDF`
  file) within about 300ms of the signing request returning, generated on a genuinely different
  thread (`signature-stamp-1`) than the request thread. The JUnit suite calls
  `generateAndStoreCertificate` directly for the same reason `PoeExportServiceTest` calls
  `runExport` rather than `runExportAsync` — an `@Async` fire-and-forget call racing a test's own
  uncommitted transaction cannot see the row it needs, which is exactly what a container run
  showed happening on every other signing call in that same manual verification (worth stating
  plainly: this is expected and not a bug — the test's transaction, unlike a committed HTTP
  request, never becomes visible to another connection).
- **Verifying a signature never fetches the file, matching the brief's own reasoning for
  signing.** "Whether the hash still matches" compares `documentSha256` (captured at signing)
  against the *current* `sha256` column on the underlying `Submission` or `LearnerDocument` row
  — not a re-fetch-and-re-hash of the actual bytes. Both rows are immutable once created (a
  resubmission or a new document version is always a new row, never an edit to an existing one),
  so this catches the case that matters — the row vanishing, or ever being mutated out of band —
  without giving a public, unauthenticated endpoint a way to make this server fetch a stored
  file on every hit.
- **Revocation is system-triggered, not learner-initiated.** `LearnerDocumentService.upload()`
  revokes the signature on whichever version it just superseded; `SubmissionService.
  submitAssignment()` revokes the signature on any other submission the same learner has on the
  same session (each submission row is immutable and resubmission is always a new row, so
  "other" rather than "previous version" is the right query). There is no endpoint for a learner
  to withdraw their own signature at will — nothing in the brief calls for one, and the two
  hooks above are the only paths that change what a signed row still represents.

Found while verifying this in a container: registering a learner returns a session token
directly (no separate login round trip needed), which made end-to-end verification faster than
expected — sign in, upload, sign, poll for `certificateReady`, verify unauthenticated, upload a
new version, confirm the old code now reads `revoked: true, valid: false` — all against a real
PostgreSQL 16 database, not H2.

---

### Phase 10 — Legacy folder import

For historical files on the admin's PC that predate the system.

Tasks:

- `POST /api/admin/poe/import` accepting a zip of one learner folder or a parent folder of many.
- Parse entry paths. Normalise case: `cores`, `Cores`, `CORES` all resolve to `CORE`. Match top-level folder names against `Learner.fullName` (fuzzy, flagged as "likely match" — the folder says `AMANDA MNDAWE`, the record says `Amanda Randy Mndawe`).
- Map `1. PERSONAL DETAILS` → `PoeDocumentType` by filename keyword (CV, ID, Matric, Agreement); unrecognised → `OTHER`.
- **Render a preview table before committing:** every file, target learner, target section, with unmatched rows flagged. Nothing is written until the admin confirms.
- Never auto-create learners from folder names. An unmatched learner folder waits for a human.
- Unmatched files land in section 6.

Acceptance criteria:

- Importing Amanda's folder produces the same file set the export would emit for her.
- A folder for a learner not in the database is reported, not created.
- Cancelling the preview writes nothing.

**Built. Decisions made shipping this, where they narrow or differ from the sketch above:**

- **Scope is personal documents only — PoE sections 1, 2 and 6.** The acceptance criterion
  "produces the same file set the export would emit" is read against that scope, not against
  the whole export: this importer never creates a `Submission` or a `ModuleFile`, only
  `LearnerDocument` rows, going through `LearnerDocumentService.upload` exactly the way a
  learner's own browser upload does. Module-based content — facilitator guides, submissions,
  feedback (sections 3-5), and the Fundamentals/Cores/Electives category folders those files
  sit under — is *recognised*, specifically so it is never misfiled into section 6 as if it
  were an unrecognised loose file, but it is reported as out of this importer's scope rather
  than imported. Reconstructing which module a historical submission belongs to, and whether it
  was on time, graded, or superseded, is a materially different and larger problem than sorting
  four kinds of personal document by filename keyword, and the brief's own instructions for this
  phase (map filenames to `PoeDocumentType` by keyword; unmatched files land in section 6) only
  ever describe the personal-document case to begin with.
- **A zip is classified as "one learner's own folder" or "a parent of many" by inspecting its
  top level**, not by an admin toggle alone (though the admin can still say so directly via
  `learnerId`, which always wins). If any top-level entry normalises to a recognised section or
  category folder name, or is a loose file sitting at the zip root, the whole zip is treated as
  one learner's folder — a parent-of-many zip always has an actual directory per learner, never
  a bare file at the top. When that shape is detected without `learnerId` given, the preview
  call is rejected outright (400) rather than guessing which learner it might be; this is the
  same "never auto-pick" discipline the brief asks for folder-name matching, applied to the one
  other place this importer could otherwise guess.
- **Fuzzy matching is exact-or-unique-subset, never a similarity score.** "AMANDA MNDAWE" against
  "Amanda Randy Mndawe" matches because every word in the folder name is a word in the learner's
  name (or vice versa) — not because they are 80% alike by some string-distance metric, which
  would eventually match two different people's names by coincidence with no way for an admin to
  know it had happened. The moment more than one learner fits, or none do, the row is
  `UNMATCHED` and waits for a human; two learners sharing a name is exactly the case a real
  cohort produces, not a rare edge case.
- **Filename keyword matching is token-based, not a raw substring check.** Classifying "ID" by
  `contains("ID")` would misfire on a file like `Validated_certificate.pdf` (which contains
  "id" at position 3) — precisely the kind of silent misfile this whole phase exists to prevent.
  Filenames are split on non-alphanumeric characters into whole tokens first, and only a whole
  token like `ID`, `IDCOPY` or `IDENTITY` counts.
- **The uploaded zip is stored to private disk for the life of the preview, keyed by batch id,
  and deleted the moment the batch leaves `PREVIEWED`** — on confirm (nothing left to import
  twice from) and on cancel (nothing left to keep). This is what lets a preview survive an app
  restart on this free-tier instance while an admin reviews it, the same reasoning `ExportJob`
  already relies on for a finished export bundle. Found while verifying this against a real
  PostgreSQL container: the validation path that rejects a mis-shaped zip (single-learner shape
  with no `learnerId`) threw before the temp file was cleaned up, and `@Transactional`'s
  automatic rollback of the batch row does nothing for a file already written to disk — a leak
  on every rejected upload. Fixed by cleaning up the stored zip on that path too, and confirmed
  fixed by triggering the same rejection against the running container and checking the
  directory was empty afterwards.
- **Confirm and cancel are both idempotent-refusing, not idempotent.** Calling either a second
  time on the same batch is a 400, not a silent no-op — a repeated confirm silently doing
  nothing would read to an admin as "it imported again", and a repeated cancel doing nothing
  hides that the batch was already gone. A cancelled batch's bookkeeping rows (which learner,
  which file, what would have been imported) are kept, marked `CANCELLED`; only the zip bytes
  and the potential `LearnerDocument` writes are the things "cancel writes nothing" promises
  never to have happened, and verifying that promise — not just that the row said `CANCELLED` —
  was the actual point of testing this path, both in the JUnit suite and against a running
  container: after cancelling, the database was queried directly for a `learner_documents` row
  count, not merely the API's own report of what happened.

---

## 7. Out of scope

- PKI / advanced electronic signatures via an accredited provider.
- Migrating to S3/MinIO (keep Cloudinary; just switch to authenticated delivery).
- Rewriting the existing lecturer or admin dashboards.
- Changing the Orbital design system.

### A learner belongs to exactly one learnership

`Learner.learnership` is a single `@ManyToOne`, so the model cannot represent someone who
finishes one qualification and starts another. They would need a second learner record, with a
second learner code and no connection to their own history.

**This is a deliberate constraint, not an oversight.** Every learner in the system today is on
one qualification, the SETA verifies one qualification at a time, and a portfolio is assembled
per qualification — so a single learnership per learner is the honest shape of the data as it
stands. Running several learnerships concurrently, which the system already does, is a
different thing from one learner holding several.

It becomes wrong the first time somebody progresses from one qualification to the next. What
that would touch, so whoever picks it up can size it rather than discover it:

- **`Learner.learnership`** becomes a collection, and almost certainly an enrolment entity
  rather than a bare join table — a progression has a start date, an end date and an outcome,
  and "which qualification was this learner on in March" is a question the SETA can ask.
- **`LearnerCodeSequence`** allocates a code per learnership. One learner on two learnerships
  either keeps their original code across both, or takes a code per enrolment. The first keeps
  their history intact and is almost certainly right; it means a learner code no longer
  identifies a learnership, which some code assumes.
- **`ModuleService.isEnrolledOn`** falls back to the learner's single learnership. It would
  have to match any of them, and — more subtly — only those active at the time being asked
  about, or a learner would keep seeing material from a qualification they have finished.
- **`EnrolmentService`** enrols a learnership's learners on a new module via
  `findByLearnership_Id`, which becomes a join through the enrolment entity.
- **`LearnerService` registration** enrols against one learnership's modules, and would need to
  know which enrolment is being created.
- **`ScopeService`** scopes moderators by learnership and cohort. A learner on two learnerships
  is visible to two sets of moderators, but only for the work belonging to each — which is a
  scoping rule this code does not currently have to express.
- **The completeness dashboard and the export** both become per enrolment rather than per
  learner. "Is this learner complete" stops being a question with one answer.

None of that is hard on its own. It is spread across enough places that finding out the hard
way, mid-phase, would be expensive — which is why it is written down here.

---

## 8. Suggested order of work

Phase 0 is done and merged. Phase 1 next, and merged, before anything else — every later
phase depends on identity and scope being correct. Building the vault or the exporter on top
of unauthenticated learner routes means rewriting both.

Deploy each phase before starting the next. If something goes wrong, debugging against a
clean `main` beats debugging against one carrying half of the following phase.

**Deploy sequence, per phase:**

1. **Announce first, not after** — and again an hour before, and once more when it is done.
   Phase 0's deploy signs every learner out once, because the stored session no longer
   carries a token. Say plainly that they will be signed out and need to log in again, and
   frame it as a security upgrade rather than maintenance — an unexplained sign-out reads as
   a fault and sends people to forgot-password before they read anything. The closing message
   matters as much as the first two: whoever missed those finds themselves signed out with no
   explanation, and that is the group that generates the support load. See **learner comms**
   below for the rules the wording has to satisfy.
2. **Low-traffic hour.**
3. **Verify the data migration ran, rather than assuming.** For Phase 0 that is
   `LegacySubmissionFileMigration`. It is idempotent and refuses a bad directory config, so
   the realistic failure is a silent no-op, not a crash. It logs a completion line on every
   run including when it moves nothing (`"Legacy submission file migration complete: scanned
   N ..."`), so the *absence* of that line means it did not run. Then spot-check that one
   moved file still opens in the portal.
4. **Log in as a test learner.** Run `./scripts/verify-deploy.sh <base-url> <learner-code>
   <password>` against a test account — it asserts the access-control behaviour this phase
   exists to produce, including the cases that have already been wrong once (a learner
   denied a staff endpoint must get 403, not a session-clearing 401), and exits non-zero so
   it can gate the announcement rather than merely inform it. Then open a submission and a
   marked file in the UI, which the script cannot do.
5. **Confirm forgot-password end to end against a real inbox**, not just the test — the test
   mocks the mail sender, so it proves the flow and not the relay.

**Rollback is only clean for phases that change code alone.** Phase 0 is not one of them,
and this line used to claim otherwise.

`LegacySubmissionFileMigration` moves files on disk and rewrites `file_path` to match. Once
it has run, redeploying the previous jar does not undo it: the old code looks in `uploads/`
for files that are no longer there, against rows pointing somewhere it knows nothing about.
The result is a healthy database, a healthy filesystem, and no learner able to open a single
document. So, before any deploy that carries a data migration:

- **Back up the database.**
- **Back up the `uploads/` directory.** This is the half people forget, and it is the half
  that cannot be reconstructed from anything else.
- **Understand that rolling back means restoring both together**, in step with each other —
  not redeploying the old jar.
- **After any restore, resync `learner_code_sequences` before letting anyone register.** The
  sequence table comes back with the backup. If its value is behind the highest existing code
  in `learners` — which is what happens when the backup predates registrations that the
  restore does not — the next self-registration collides on a unique constraint. Set
  `last_seq` for the current year to the highest sequence number already issued, then test one
  registration before reopening. This is cheap to do and hard to diagnose afterwards, since it
  surfaces to a learner as a failed registration rather than to you as an error.

- **Capture the migration's output and store it with the backups.** Every move is logged as
  `Moved submission file out of the public directory: from -> to`, and a run that will move
  anything warns first — but that log is only a reversal record for as long as it exists.
  Application logs rotate, and a container restart loses them entirely. Confirm stdout is
  being written to a file before you deploy, and copy that file off the host afterwards, into
  the same place as the database and `uploads/` backups. The record of what changed and the
  thing you would restore to belong together; a reversal needs both halves and the map
  between them.

The migration is idempotent and refuses a bad directory configuration, so needing this is
unlikely. Unlikely and recoverable are different properties, and only one of them is in your
control.

**Rehearse against a restore of the production database, not just the test suite.** The tests
run on H2 with data they invented. Production MySQL has the real schema, real row counts, and
`file_path` values accumulated across eras — a mix of local paths and Cloudinary URLs, with
whatever inconsistencies came with them. The migration handles that mix by design, but by
design is not the same as observed. Restore a dump locally, point the jar at it, run it, read
the log. It costs an hour and it is the difference between testing the code and testing the
data.

**Verify the artifact before you trust its logs.** A missing log line means the step did not
happen — but it means that just as loudly when the build is simply the wrong one. Confirm the
change is actually in the jar first (`unzip -l <jar> | grep LegacySubmissionFileMigration`),
then read what it says. This was learned the hard way: a stale build produced exactly the
silence the completion line exists to rule out.

**Send the first password reset to your own address.** The ten-second SMTP timeout means a
bad relay credential surfaces fast — which is what you want when the person waiting on it is
you rather than a learner.

**Learner comms — rules, not wording.** These outlive any one announcement, and the phases
that bring them back are already on the list: Phase 4 changes how files are delivered, and
Phase 8 emails export links. Both will need a message to somebody, and both will be written
by someone to whom a link seems perfectly reasonable in isolation.

- **Never put a link in a message to learners.** A message that says "you have been signed
  out, log in again, check your email" is the exact shape of a phishing lure, and a cohort
  primed to expect it will click whatever arrives next. Tell them to open the portal the way
  they normally do. The defence is not one careful message; it is that our messages never
  carry links, so one that does is visibly not ours.
- **Send from the number the cohort already receives learnership messages on.** A new sender
  asking them to log in is indistinguishable from an attacker.
- **Never ask for a password**, and say so in the message.
- **Say plainly that nothing has been deleted.** These learners keep their portfolios on USB
  sticks; they have well-earned reason to believe files vanish. A sign-out reads as data loss
  to that audience in a way it would not to someone raised on cloud storage.
- **Ask them to verify email access in advance**, in the first message. Anyone who cannot get
  into their registered address needs a human, not forgot-password, and you want to know who
  they are while there is still a day to fix it.
- **Name who to contact, and check the channel is two-way first.** "Message us here" is no
  use on a broadcast list nobody can reply to. Confirm whether it is a group or a broadcast
  before sending, and name a person or role either way.

After that: Phase 2 (mechanical), then Phase 3 and Phase 5 in parallel if convenient, then 4, 6, 7, 8, 9, 10.

Phases 1–3 alone get files off USB sticks, which is the immediate operational win. Phase 10 is the least urgent — legacy import runs in the background and blocks nobody.

---

## 9. Testing note

Write the ownership tests before the UI for each phase:

```
learner A submits, assessor uploads feedback for A
learner B requests A's feedback file by id        -> 404
learner B lists feedback for the same module      -> empty
learner B's export                                -> no section 5 files from A
unauthenticated request to /api/me/**             -> 401
```

Manual click-through will not catch an unscoped query that happens to return correct data because only one learner has data in the test database.

Test the operational paths too, not only the security ones. The flows real users take cross
subsystem boundaries that unit tests do not, and that is where this kind of fault hides.
Phase 0's password-reset test was written out of a support-queue concern about deploy day and
caught a data-integrity bug in a different subsystem: `revokeAllForLearner` was bulk JPQL,
which writes past the persistence context, so a session already loaded stayed cached as live
and a check made after the revoke — in the same transaction — was told the session still
worked. Invisible per request, since each request gets a fresh context. Not invisible to a
caller that revokes and then acts on that fact, which is the natural thing to write.
