# Demo Script

The full demo: a simulated flood, from a reported need to a confirmed delivery. About 10 minutes.

**Status of this script:** written from the code and the demo seed data, and not yet rehearsed by the team. Run it once end to end before presenting. Where something differs from the "Expected" column, fix the code or this script, and note it in the rehearsal log at the bottom.

## Preparation
1. Database, backend and frontend running with the `dev` profile (`INTEGRATION_GUIDE.md`). Demo password for every account: `Demo@12345`.
2. Two browser windows (or one normal and one private) so two roles can be shown side by side.
3. All data in the demo is fictional and labelled `DEMO`. Never present it as a real event.

Accounts used: `officer@demo.drrc.local`, `field@demo.drrc.local`, `warehouse1@demo.drrc.local`, `admin@demo.drrc.local`, `citizen@demo.drrc.local`.

## Part 1: the main chain (about 6 minutes)
| # | Who | Do this | Expected |
|---|---|---|---|
| 1 | Officer | **Disasters** -> open "DEMO: Simulated Ghaggar River Flood" | Detail page with three affected villages and their populations |
| 2 | Officer | **Live map** | Disaster and village markers; shelters and warehouses shown |
| 3 | Field worker | **Relief requests** -> **New request**: disaster Ghaggar flood, village Demo Khera Village, resource Drinking water, quantity 3,000, severity Critical, a short description -> **Submit request** | Request saved with status Pending |
| 4 | Officer | **Relief requests** -> open that request -> verify it | Status Verified; a priority score appears with written reasons |
| 5 | Officer | On the same request: approve it | Status Approved; History shows who did each step and when |
| 6 | Officer | **Allocation** -> click the approved request -> **Recommend allocation** | Recommendation naming warehouses, distance and quantities. If stock is lower than requested, it shows a **partial allocation with a shortage explanation**. That is intended, say so |
| 7 | Officer | **Approve** the recommendation | Stock is reserved exactly once. Open **Inventory** to see reserved quantity rise and the ledger row |
| 8 | Warehouse manager | **Dispatch** -> enter the approved allocation number -> create the dispatch. If it is refused, try `warehouse2@demo.drrc.local` (the stock may sit in the other depot) | Dispatch created, status Dispatched |
| 9 | Warehouse manager | **Mark as** in transit, then **Mark as** delivered | Request becomes Delivered; stock on hand drops by the delivered quantity |
| 10 | Officer | **Dashboard** | Figures reflect the delivery; Delivered count up |
| 11 | Officer | **Audit log** | Rows for every step above: who, what, when |

## Part 2: things already in the demo data (about 3 minutes)
| Show | Where | What to say |
|---|---|---|
| Two older requests, one delivered and one in transit | **Dispatch** | Delivery timeline for food (delivered) and water (in transit) |
| An overcrowded shelter | **Shelters** and the bell icon / **Alerts** | "Samana Relief Camp" exceeds capacity; the alert is raised automatically; the shelter page lists alternatives with free beds |
| Low first-aid stock | **Allocation** | The seeded medical recommendation leaves a 15-kit shortage and is not approved |
| Partner offers | **Partners** (as officer or NGO) | Food offer received, medicine offer awaiting review, first-aid offer declined for incomplete documents |
| Volunteers | **Volunteers** | Register a volunteer, set availability, assign a task |
| Report analyzer | **Report analyzer** | Seeded reports show suggestions and a "Review" step: a person must mark each one useful or not. If no AI key is configured, a new analysis answers "unavailable" and the failure is recorded. That is the safe behaviour |
| Situation report | **Analytics & reports** -> **Download situation-report.pdf** | One-page summary; each download is itself written to the audit log |

## Part 3: access control (about 1 minute)
1. Log in as `citizen@demo.drrc.local`: staff screens are not in the menu, and calling them directly is refused by the server (403).
2. Try three wrong passwords for any account, then a right one: after five failures sign-in is blocked for a few minutes (HTTP 429).

## Say this, honestly
- Priority weights, the fairness cap and who may approve allocations are **proposals awaiting approval** (`APPROVALS.md`).
- AI output is a suggestion. It never changes a request without a person.
- The demo accounts and data exist only in the `dev` profile; production refuses to start with them.
- Not in the project: Hindi/Punjabi screens, offline mode, weather, image assessment, awareness centre, AI assistant.

## Rehearsal log
| Date | Who ran it | Steps that differed from "Expected" | Fixed? |
|---|---|---|---|
| | | | |
