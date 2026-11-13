# Demo Script

Final blueprint demo: a simulated flood, from request to confirmed delivery. This file maps each step to what exists **today** so nobody promises a feature that is not built. Update the status column whenever a day ships.

Preparation: database and both apps running (`INTEGRATION_GUIDE.md`), `dev` profile (demo data loaded). Use two browser windows (or one normal and one private) for two roles.

| # | Blueprint step | How to show it | Status |
|---|---|---|---|
| 1 | Create a simulated flood affecting three villages | Log in as `officer@demo.drrc.local`. Disasters -> New disaster (type Flood, severity High, epicentre optional). Open it, add three affected areas with populations. | **Works (Day 3)**. Seed already has "DEMO: Simulated Ghaggar River Flood" with three demo villages. |
| 2 | Show affected locations on the map | - | Day 7 (map). Until then, show coordinates in the area table. |
| 3 | Submit requests for drinking water, food, first-aid | Log in as `field@demo.drrc.local`. Requests -> New request: pick disaster, village, resource (Drinking water / Packaged food ration / First-aid kit), quantity, severity, description. Submit. Open the list as the officer to show it was saved. | **Works (Day 4)** |
| 4 | Verify requests | As officer open a Pending request: Verify, or Reject / Ask for clarification with a reason. Show the History table (who, when, why). | **Works (Day 4)** |
| 5 | Show priority scores with reasons | - | Day 6. Request detail shows "Not calculated yet" meanwhile. |
| 6 | Allocation centre: compare warehouses, distance, stock | - | Days 5-6 |
| 7 | Partial allocation and shortage explanation | - | Day 6 |
| 8 | Approve allocation, stock reserved exactly once | Today: officer **Approve** moves a Verified request to Approved (no stock yet). | Request approval works (Day 4); stock reservation Day 6 |
| 9 | Dispatch -> In Transit -> Delivered | - | Day 7 |
| 10 | Dashboard shows updated figures | - | Day 5 onward |
| 11 | Audit history shows who approved/changed | Request History table (per request). Central audit view comes Day 7/9. | Partly (Day 4) |

## Two-minute demo available now (Days 3-4)
1. Officer logs in and opens the flood disaster; area table shows three villages.
2. Field worker submits "Drinking water, 5,000 litres, Critical" for a village.
3. Officer sees it as Pending, clicks Verify, then Approve. History shows both steps with names and times.
4. Field worker submits a second request, then cancels it (own request, still Pending). Show that cancelling after verification is refused for the field worker.
5. NGO submits a request; officer rejects it with the reason "Duplicate of request #1"; reason is visible in History.
6. Log in as `citizen@demo.drrc.local`: staff screens are refused (403) - role enforcement on the server.

## Things not to say yet
No map, no priority scores, no stock or allocation, no dispatch, no AI, no Hindi/Punjabi, no PDF. Do not call the seed figures real data: they are labelled demo.
