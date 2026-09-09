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

### 4.2 Cloudinary learner files are publicly delivered

`CloudinaryService.uploadFile` uploads with `resource_type: "raw"` and default public delivery, returning `secure_url`, which is persisted in `Submission.filePath`. That URL works for anyone who holds it, with no authentication, forever.

`CloudinaryService.uploadBackup` in the same class already demonstrates the correct pattern: `type: "authenticated"` plus `getSignedBackupUrl()`. Apply that pattern to learner-facing files.

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

---

### Phase 4 — Cloudinary authenticated delivery

Tasks:

- Add `uploadLearnerFile(MultipartFile)` to `CloudinaryService` using `type: "authenticated"`, mirroring the existing `uploadBackup`. Return the **public_id**, not `secure_url`.
- Persist public_ids in `LearnerDocument.filePath`, and for new `Submission` and `ModuleFile` rows.
- Serving: keep the fetch-and-re-serve approach already used in `SubmissionController.viewSubmissionFile` (it exists because Cloudinary's raw delivery does not set an inline-renderable Content-Type). Resolve the signed URL server-side via the existing `getSignedBackupUrl` pattern, fetch, re-serve.
- Handle both storage shapes during transition: if `filePath` starts with `http`, treat it as a legacy public URL; otherwise treat it as a public_id.

Acceptance criteria:

- A newly uploaded learner file's stored value is not a URL.
- Pasting a Cloudinary URL for a new file into a logged-out browser fails.
- Existing files uploaded before this phase still open in the app.

---

### Phase 5 — Feedback draft/publish and visibility

Tasks:

- Facilitator marking writes `feedback_status = DRAFT`. Nothing is visible to the learner while draft.
- A "Release feedback" action on the session sets all its feedback to `PUBLISHED`, stamps `published_at`, and creates one notification per learner.
- `feedback_visibility`: `LEARNER` (default) or `INTERNAL`. Moderator reports are `INTERNAL` — they export to the PoE for SETA but never appear in the student portal.
- The learner-side query filters on `PUBLISHED` and `LEARNER` unconditionally.

Acceptance criteria:

- Draft feedback is invisible to the learner via API and portal.
- An `INTERNAL` record appears in the export but not in `GET /api/me/feedback`.
- Releasing a session creates exactly one notification per learner, containing no other learner's name.

---

### Phase 6 — Notifications and live push

Tasks:

```
notification    id, user_id, user_role, type, ref_type, ref_id, created_at, read_at
```

- Write notification rows on: guide published, feedback released, document rejected.
- Badge counts on portal load.
- `SseEmitter` endpoint for live push to the open portal. Fall back to polling every 45 seconds if the connection drops — learners are often on poor mobile connections.

Do not use WebSockets; one-way push is all this needs.

---

### Phase 7 — Completeness dashboard

Tasks:

- Per-learner checklist: required `PoeDocumentType`s, plus submission coverage across enrolled modules. Three states — green (accepted), amber (uploaded, pending review), red (missing).
- Cohort view: counts of complete vs incomplete, and the most common missing item.
- Add `required_from` (date) to document type configuration so a cohort that started before go-live does not show as entirely red. Work predating `required_from` is not counted as missing.

This screen replaces the admin manually opening folders to check readiness before a SETA submission. It is the highest-value screen in the project — build it properly.

---

### Phase 8 — Export

**This must be asynchronous.** A 40-learner cohort is roughly 600 file fetches from Cloudinary; it will time out in a request thread.

```
export_job    id, requested_by_id, requested_by_role, scope_type, scope_ref,
              status, learner_count, file_count, result_public_id,
              created_at, completed_at, error
```

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

---

## 7. Out of scope

- PKI / advanced electronic signatures via an accredited provider.
- Migrating to S3/MinIO (keep Cloudinary; just switch to authenticated delivery).
- Rewriting the existing lecturer or admin dashboards.
- Changing the Orbital design system.

---

## 8. Suggested order of work

Phase 0 is done and merged. Phase 1 next, and merged, before anything else — every later
phase depends on identity and scope being correct. Building the vault or the exporter on top
of unauthenticated learner routes means rewriting both.

Deploy each phase before starting the next. If something goes wrong, debugging against a
clean `main` beats debugging against one carrying half of the following phase.

**Deploy sequence, per phase:**

1. **Announce first, not after.** Phase 0's deploy signs every learner out once, because the
   stored session no longer carries a token. Say plainly that they will be signed out and
   need to log in again, and frame it as a security upgrade rather than maintenance — an
   unexplained sign-out reads as a fault and sends people to forgot-password before they
   read anything.
2. **Low-traffic hour.**
3. **Verify the data migration ran, rather than assuming.** For Phase 0 that is
   `LegacySubmissionFileMigration`. It is idempotent and refuses a bad directory config, so
   the realistic failure is a silent no-op, not a crash. It logs a completion line on every
   run including when it moves nothing (`"Legacy submission file migration complete: scanned
   N ..."`), so the *absence* of that line means it did not run. Then spot-check that one
   moved file still opens in the portal.
4. **Log in as a test learner.** Open a submission and a marked file.
5. **Confirm forgot-password end to end against a real inbox**, not just the test — the test
   mocks the mail sender, so it proves the flow and not the relay.

Rollback is clean as long as `main` was green before the phase merged.

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
