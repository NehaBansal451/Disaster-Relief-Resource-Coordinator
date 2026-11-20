# Security review (Day 9) - what was checked and what is still open

Scope: the Spring Boot backend as of Day 9. This is a code review plus automated tests, not a penetration test.

## In place and tested
| Area | What the code does | Test |
|---|---|---|
| Authentication | Stateless JWT (HS256), 1-hour life, secret only from `JWT_SECRET` | `JwtServiceTest`, `ApiSecurityIntegrationTest` |
| Authorization | Every endpoint carries a role rule enforced on the server (`@PreAuthorize`/annotations); a missing token gives 401, a wrong role 403 | `SecurityConfigTest`, integration tests per module |
| Ownership | A warehouse manager can only receive or dispatch for their own warehouse; a volunteer only touches their own record | `InventoryServiceTest`, `DispatchServiceTest`, `VolunteerServiceTest` |
| SQL injection | All SQL uses bound parameters; the only text added to SQL is `ORDER BY`, chosen from a fixed whitelist (unknown sort gives 400) | `PageQueryTest`, `inventoryIsRoleRestricted` |
| Brute force | 5 failed sign-ins per e-mail or 30 per address in 15 minutes give 429 | `LoginThrottleTest`, `loginIsThrottledAfterRepeatedFailures` |
| Concurrency | Two officers cannot reserve the same stock (row locks, guarded updates, database CHECK) | `twoOfficersCannotOverAllocateTheSameStock` |
| Audit | `audit_logs` is append-only (database trigger); who/what/when is written in the same transaction as the change | `day7_checks.sql` |
| Personal data and AI | Phone, e-mail, Aadhaar-style and PAN numbers are removed before storage and before the text goes to the model; the key is read from `AI_API_KEY`; output must match a strict schema; a result never changes a request without a person | `PiiScrubberTest`, `AnalysisParserTest`, `AnalysisServiceTest` |
| Error leakage | Unexpected errors return a generic 500 problem document; the stack trace stays in the server log | `GlobalExceptionHandler` |
| Secrets | No password, key or secret in the repository; `.env` is git-ignored | manual review |

## Open items (not done, owner in brackets)
1. Login throttle and the analyzer rate limit are in memory: they reset on restart and do not work across several servers. Use a shared store if more than one instance runs (backend/Divay).
2. No token revocation or refresh: a stolen token works until it expires (1 hour). Acceptable for the prototype; revisit before public use (backend).
3. Demo users share a published password. The `dev` profile seeds them; production must not load `R__dev_seed*.sql` (Divay).
4. HTTPS, CORS origins, database backups and secret storage belong to deployment (Divay). Nothing in the backend forces HTTPS.
5. Names and addresses typed into free text cannot be removed by the scrubber; staff must not paste them into the analyzer (training/UI warning).
6. No dependency vulnerability scan (for example OWASP dependency-check) and no penetration test were run (team).
7. The Helvetica PDF font prints only Latin characters; any non-Latin text appears as `?` (backend, needs an embedded font).
8. The proposed rules in `PROJECT_STATUS.md` (priority weights, fairness cap, who may approve) are assumptions until Divay/mentor approve them.
