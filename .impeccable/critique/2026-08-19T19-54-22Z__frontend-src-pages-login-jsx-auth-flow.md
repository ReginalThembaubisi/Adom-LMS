---
target: auth flow (Login, Register, 4 role logins)
total_score: 25
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
timestamp: 2026-08-19T19-54-22Z
slug: frontend-src-pages-login-jsx-auth-flow
---
## Design Health Score — Auth Flow (Login, Register, AdminLogin, AssessorLogin, LecturerLogin, ModeratorLogin)

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Button text changes, inputs disable during submit |
| 2 | Match System / Real World | 3 | Realistic placeholders and domain terms |
| 3 | User Control and Freedom | 3 | Back to Main Entry present everywhere |
| 4 | Consistency and Standards | 2 | Consistent within auth, breaks from dark Orbital system elsewhere |
| 5 | Error Prevention | 2 | Submit-only validation; 4-char password minimum on PII-bearing form |
| 6 | Recognition Rather Than Recall | 3 | Password toggle, clear labels |
| 7 | Flexibility and Efficiency | 2 | No autoComplete, no remember-me |
| 8 | Aesthetic and Minimalist Design | 3 | Clean, no clutter, decoration lacks product meaning |
| 9 | Error Recovery | 3 | Field-level errors, form state preserved |
| 10 | Help and Documentation | 1 | No help link, no privacy note on ID field |
| **Total** | | **25/40** | **Acceptable** |

## Design Specificity Verdict
Auth shell is a generic SaaS-auth template (white card, ambient blobs, indigo/blue) reproduced identically across 6 pages; contradicts DESIGN.md's "not generic corporate LMS" anti-reference and the binding Orbital identity used elsewhere. detect.mjs returned clean (0 findings) — mechanical detector can't catch cross-screen identity mismatch.

## Priority Issues
[P1] Auth pages break Orbital identity at the product's most-visited screens — /impeccable polish
[P1] Password policy too weak for PII-bearing registration (4-char minimum) — /impeccable harden
[P2] No inline validation until submit on Register — /impeccable clarify
[P2] Generic, category-interchangeable visual language — /impeccable polish
[P3] No "remember me" for returning mobile learners — /impeccable optimize

## Persona Red Flags
Jordan (First-Timer): ID/Passport field has no privacy note — trust risk at the highest-stakes field.
Casey (Mobile/low-bandwidth): No autosave across 8-field Register form; data loss risk on flaky connections.

## Minor Observations
- "Forgot password?" route unconfirmed
- No autoComplete attributes on inputs
- Ambient blobs duplicated verbatim across all 6 files (maintenance note)
