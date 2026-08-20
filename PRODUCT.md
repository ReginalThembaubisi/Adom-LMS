# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Primary user: **learners** — students enrolled in a South African learnership (a structured, cohort-based vocational training programme), who register with personal/ID details, get assigned a 9-digit student number, and use the portal to submit assignments, view learning resources, and track submission/task status.

Four other roles use their own portals against the same system: **facilitators/lecturers** (publish resources, open assignment slots, grade submissions), **moderators**, **assessors**, and **admin/back-office staff** (manage learner registrations, learnerships, modules, categories, and role assignments, and control app-wide switches). These are confirmed as real, distinct roles in the product, but the learner experience is the one the product is built around.

## Product Purpose

Digitizes and manages the day-to-day workflow of a South African learnership programme: learner registration and enrollment into a learnership/cohort ("intake"), module and resource delivery, assignment submission, and grading/oversight across the facilitator, moderator, assessor, and admin roles.

## Positioning

Purpose-built around South African learnership/SETA-style vocational training structures — learnerships, cohort intakes, ID-number-based registration, and the specific facilitator/moderator/assessor/admin role split — rather than a generic assignment-submission or LMS product.

## Operating Context

- Learners register with full name, email, ID number, phone number, cohort/intake (e.g. "2026 Intake A"), and a chosen learnership; they receive a 9-digit student number used to log in thereafter.
- Learners may access the portal on mobile devices and lower-bandwidth connections — this should constrain asset weight and performance in future visual work.
- Registration can be toggled open/closed by admins (`/api/registration-status`).
- Backend: Java 17, Spring Boot, Spring Security, Spring Data JPA/Hibernate, MySQL. Frontend: React 19 (Vite 8), Tailwind CSS 4, React Router 7. Built with Maven; frontend is built and served as static assets by the Spring Boot app.

## Capabilities and Constraints

- Five separate login flows/portals: Learner, Admin, Facilitator/Lecturer, Moderator, Assessor.
- Learner-facing concepts: learnerships, modules, categories, assignments, submission sessions.
- Admin-facing concepts: managing lecturers, moderators, assessors, learners, learnerships, and modules; an admin overview/dashboard.
- A repo-root `src/main/frontend` (plain CSS components: Landing, Login, FacilitatorDashboard, StudentPortal, SubmissionForm, LearnerRegistration) appears to be an earlier/legacy frontend, distinct from the active `frontend/` Vite app described in the README. Not yet confirmed with the user which is authoritative going forward — treat as an open question before doing work that touches both.
- Data privacy: learner registration collects ID numbers and other PII. POPIA (South Africa's data-protection law) or equivalent compliance is a real constraint on how this data is handled and displayed, not yet elaborated in detail.

## Brand Commitments

The **Orbital** design system is a binding brand identity for this product (confirmed, not just incumbent implementation to be reconsidered by default): glassmorphism cards, slate-indigo gradients, and Outfit typography, with centered entry cards, frosted blur, dynamic light-blue contrast, and background gradient/blob elements. Future visual work should preserve this identity unless the user explicitly requests a redesign later.

## Evidence on Hand

No customer testimonials, case studies, press, or benchmark data on hand — none should be fabricated. `frontend/src/assets/hero.png` exists as an existing visual asset; not yet reviewed for reuse.

## Product Principles

1. The learner's registration-to-submission journey is the product's center of gravity; other role portals support the operation around it but are secondary in design priority.
2. The terminology and workflow (learnerships, cohorts/intakes, facilitator/moderator/assessor/admin split) are the product's real differentiation — don't genericize it into a generic "LMS" or "course platform" vocabulary.
3. Design for real mobile/low-bandwidth learner access, not just desktop admin use.
4. Preserve the Orbital identity (glassmorphism, slate-indigo, Outfit) as the established brand rather than treating it as disposable incumbent styling.
5. Learner PII (ID numbers, contact details) is sensitive; treat data handling and display as a POPIA-relevant constraint even before a formal policy is documented.

## Accessibility & Inclusion

No formal accessibility standard confirmed yet. Given mobile/low-bandwidth learner access is a known constraint, treat performance and legibility on real-world mobile conditions as a practical accessibility concern until a formal standard is set.
