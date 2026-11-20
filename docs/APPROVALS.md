# Decisions awaiting approval

The project rule is that nothing below counts as final until **Neha** and **Divay** each record a decision. This page explains each decision in plain language, says what the code does today, and gives a recommended answer. To approve, change the cell to `approved` (or `changed: <what>`) with your initials and the date, then update the matching row in the register in `PROJECT_STATUS.md`.

Full wording of every assumption is in `PROJECT_STATUS.md` (Day 3 to Day 10 sections). Nothing here has been approved by anyone yet.

## A. Rules the software already applies (Neha decides, Divay reviews)
| # | Decision | What the code does today | Recommended | Neha | Divay |
|---|---|---|---|---|---|
| A1 | Priority formula | Score = 0.40 severity + 0.30 people affected + 0.15 waiting time + 0.15 resource criticality, each scaled 0 to 100 (caps: 5,000 people, 72 hours) | Approve. Note the blueprint names the last input "time sensitivity"; the code uses "resource criticality". Pick one name and use it everywhere | pending | pending |
| A2 | Priority categories | 80 and above Critical, 60 to 79 High, 40 to 59 Medium, below 40 Low | Approve | pending | pending |
| A3 | Who approves allocations | Only the relief officer can recommend, approve or reject an allocation. Administrators cannot | Approve. Keeps one clear accountable role | pending | pending |
| A4 | Fairness cap | When several verified requests compete for the same resource, each gets at most its proportional share of available stock | Approve | pending | pending |
| A5 | Low-stock alert | Raised when available stock falls to 10% or less of on-hand | Approve, or change the percentage | pending | pending |
| A6 | Request roles | Officer, field worker and NGO submit; only the officer verifies, approves, rejects. Field worker may cancel only their own pending request | Approve | pending | pending |
| A7 | Who sees what | Staff roles (admin, officer, warehouse manager, field worker, NGO) read disaster data; citizens and volunteers do not. All staff see all alerts | Approve | pending | pending |
| A8 | Stock rules | Available = on hand minus reserved. Reserved stock stays on hand until dispatch. Perishables need a future expiry date. "Expiring soon" means within 30 days | Approve | pending | pending |
| A9 | Volunteers and partners | Admin, officer and NGO register volunteers; only admin and officer assign tasks. An NGO offers only for its own organisation; admin or officer decide; a received offer does not add stock by itself | Approve | pending | pending |
| A10 | Report analyzer | Any staff role may analyse a report; only admin or officer review; 10 analyses per user per hour; personal identifiers are removed first | Approve | pending | pending |
| A11 | Analytics and PDF | Only admin and officer. Shortage = demand from verified and approved requests minus free, unexpired stock | Approve | pending | pending |
| A12 | UI time zone | Times are shown in Asia/Kolkata | Approve | pending | pending |

## B. Open questions (nobody has decided yet)
| # | Question | Current behaviour | Recommended | Neha | Divay |
|---|---|---|---|---|---|
| B1 | May an administrator verify and approve requests? | No | Keep no | pending | pending |
| B2 | Self-approval (O2, O7): may an officer verify and approve a request they submitted themselves, or both verify and approve the same request? | Not blocked in the code. `API_CONTRACT.md` proposes banning approval of your own request | Ban approving your own request (needs a small code change); allow one officer to do both steps for the prototype | pending | pending |
| B3 | May a request be cancelled after allocation or dispatch? | No | Keep no for now | pending | pending |
| B4 | Public reporting by citizens? | Not allowed | Keep not allowed | pending | pending |
| B5 | Should priority scores be recalculated as time passes? | Only when someone asks | Keep manual | pending | pending |
| B6 | What happens to the unmet part after a partial allocation (O6)? | The allocation explanation shows the unmet quantity as a shortage | Keep | pending | pending |

## C. Database and deployment (Divay decides, Neha reviews)
| # | Decision | Recommended | Neha | Divay |
|---|---|---|---|---|
| D1 | Who runs Flyway and how migrations reach the backend | Backend runs them at start-up, from `database/migrations` (current setup) | pending | pending |
| D2 | Migration V2 (users and organisations adjustments) | Approve as applied | pending | pending |
| D3 | Database ER design for Days 3 to 5 tables | Approve as the baseline | pending | pending |
| D4 | Inventory constraints (reserved never above on-hand, append-only ledger) | Approve | pending | pending |
| D5 | Demo data stays out of production | Approve. Now enforced: the `prod` profile never loads it and refuses to start if demo users exist | pending | pending |
| D6 | Deployment architecture: two app services plus a PostGIS database, hosting provider, backups, monitoring | Needs Divay's proposal; not yet written | pending | pending |

## D. Tooling policy
| Decision | Recommended | Neha | Divay |
|---|---|---|---|
| Node.js allowed only as build tooling for the Vaadin frontend (no Node server, no TypeScript or React application code) | Approve. Vaadin 25 needs it to build, and CI already uses it this way | pending | pending |

## Known limits that are accepted for the prototype (no approval needed, but know them)
Login rate limits reset when the server restarts, and tokens cannot be revoked before they expire (1 hour). Fine for one server and a demo; change before any public launch (`SECURITY_REVIEW.md`).
