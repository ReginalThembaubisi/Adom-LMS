#!/usr/bin/env bash
#
# Post-deploy verification for the learner portal.
#
# Checks the things that would be wrong if Phase 0 had gone wrong, against a running
# instance. Every assertion is one an operator would otherwise make by hand, in a browser,
# and therefore make inconsistently or skip.
#
# It deliberately includes cases that have already broken once:
#   - a learner denied a staff endpoint must get 403, not a session-clearing 401
#     (this was wrong in a real container while every unit test said otherwise)
#   - the legacy public uploads path must not serve anything anonymously
#   - the old learner-code routes must be closed
#
#   ./scripts/verify-deploy.sh https://portal.example.com 202600001 'the-password'
#
# Exits non-zero if any check fails, so it can gate a deploy rather than merely inform one.

set -uo pipefail

BASE="${1:-}"
LEARNER_CODE="${2:-}"
LEARNER_PASSWORD="${3:-}"

if [ -z "$BASE" ] || [ -z "$LEARNER_CODE" ] || [ -z "$LEARNER_PASSWORD" ]; then
    echo "usage: $0 <base-url> <learner-code> <password>" >&2
    echo "  Use a test learner account, not a real learner's credentials." >&2
    exit 2
fi

BASE="${BASE%/}"
PASS=0
FAIL=0

# status <expected> <description> <curl args...>
status() {
    local expected="$1"; shift
    local what="$1"; shift
    local got
    got=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 30 "$@" 2>/dev/null)
    if [ "$got" = "$expected" ]; then
        printf '  \033[32mok\033[0m   %-58s %s\n' "$what" "$got"
        PASS=$((PASS + 1))
    else
        printf '  \033[31mFAIL\033[0m %-58s expected %s, got %s\n' "$what" "$expected" "$got"
        FAIL=$((FAIL + 1))
    fi
}

echo
echo "Verifying $BASE"
echo

echo "Reachability"
status 401 "an unauthenticated learner endpoint answers at all" "$BASE/api/me"

echo
echo "Authentication"
TOKEN=$(curl -sS --max-time 30 -X POST "$BASE/api/learners/login" \
    -H 'Content-Type: application/json' \
    -d "{\"studentNumber\":\"$LEARNER_CODE\",\"password\":\"$LEARNER_PASSWORD\"}" \
    2>/dev/null | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
    printf '  \033[31mFAIL\033[0m %-58s no token returned\n' "log in as the test learner"
    echo
    echo "Cannot continue without a session. Check the learner code and password, and that"
    echo "the deployed build is the one carrying Phase 0."
    exit 1
fi
printf '  \033[32mok\033[0m   %-58s token issued\n' "log in as the test learner"
PASS=$((PASS + 1))

AUTH=(-H "Authorization: Bearer $TOKEN")

echo
echo "The learner's own data"
status 200 "read own profile"                     "${AUTH[@]}" "$BASE/api/me"
status 200 "list own submissions"                 "${AUTH[@]}" "$BASE/api/me/submissions"
status 200 "list own modules"                     "${AUTH[@]}" "$BASE/api/me/modules"
status 200 "read own timeline"                    "${AUTH[@]}" "$BASE/api/me/timeline"

echo
echo "Everything the learner must not reach"
# 403 rather than 401 matters: the portal treats 401 as a dead session and signs them out.
status 403 "staff roster refuses a learner (403, not 401)" "${AUTH[@]}" "$BASE/api/learners"
status 401 "no token is unauthenticated"          "$BASE/api/me/submissions"
status 401 "a junk token is unauthenticated"      -H 'Authorization: Bearer not-a-real-token' "$BASE/api/me"
status 404 "another record reads as not found"    "${AUTH[@]}" "$BASE/api/me/modules/999999"

echo
echo "Routes this phase closed"
status 401 "old learner-code submissions route"   "$BASE/api/learners/$LEARNER_CODE/submissions"
status 401 "old learner-code messages route"      "$BASE/api/learners/$LEARNER_CODE/messages"
status 401 "old learner-by-code lookup"           "$BASE/api/learners/$LEARNER_CODE"
status 401 "public uploads directory"             "$BASE/uploads/${LEARNER_CODE}_1_probe.pdf"

echo
echo "Session lifecycle"
curl -sS -o /dev/null --max-time 30 -X POST "${AUTH[@]}" "$BASE/api/me/logout" 2>/dev/null
status 401 "logout actually revokes the token"    "${AUTH[@]}" "$BASE/api/me"

echo
echo "-----------------------------------------------------------------------"
if [ "$FAIL" -eq 0 ]; then
    printf '  \033[32m%d passed, 0 failed.\033[0m Portal behaving as Phase 0 intends.\n' "$PASS"
    echo
    echo "  Still to check by hand — this script cannot:"
    echo "    - open a submission and a marked file in the portal UI"
    echo "    - the migration log line, on the host"
    echo "    - forgot-password against a real inbox (send the first one to yourself)"
    echo
    exit 0
fi
printf '  \033[31m%d failed\033[0m, %d passed.\n' "$FAIL" "$PASS"
echo
echo "  Do not announce the deploy as complete. If the failures are broad, check the"
echo "  deployed artifact is the build you meant to ship before debugging anything else."
echo
exit 1
