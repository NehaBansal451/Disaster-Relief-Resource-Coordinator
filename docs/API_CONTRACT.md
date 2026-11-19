# DRRC API Contract v0.2 (draft, pending approval by Neha and Divay)

Changes from v0.1: added request approval (`VERIFIED -> APPROVED`), single canonical state machine, full consistency review, audit event catalogue. See section 6.

Base: `/api`. JSON. Auth: `Authorization: Bearer <JWT>` unless "Public".
Errors: `application/problem+json` `{type,title,status,detail,code,errors[{field,message}]}`.
Lists: `?page=0&size=20&sort=field,desc` -> `{content[],page,size,totalElements,totalPages}`.
Roles: ADM=ADMIN, OFF=OFFICER, WH=WAREHOUSE_MANAGER, FLD=FIELD_WORKER, NGO, VOL, CIT. `FIELD_WORKER` exists only after migration V2 (Divay approval D2).

## 1. Canonical request state machine
Request status is changed only by the actions below. Statuses after APPROVED are **derived** by allocation/dispatch actions; no request endpoint sets them directly.

| From | To | Action (endpoint) | Actor | Reason required |
|---|---|---|---|---|
| (new) | PENDING | `POST /requests` | OFF, FLD, NGO, CIT* | - |
| PENDING | VERIFIED | `PATCH /requests/{id}/verify` `VERIFY` | OFF | no |
| PENDING | REJECTED | `PATCH /requests/{id}/verify` `REJECT` | OFF | yes |
| PENDING | PENDING | `verify` `REQUEST_CLARIFICATION` (history event only) | OFF | yes |
| VERIFIED | **APPROVED** | `PATCH /requests/{id}/approve` | OFF | no (optional note) |
| VERIFIED | REJECTED | `PATCH /requests/{id}/reject` | OFF | yes |
| PENDING, VERIFIED, APPROVED | CANCELLED | `PATCH /requests/{id}/cancel` | OFF; requester while PENDING | yes |
| APPROVED | ALLOCATED | `POST /allocations/{id}/approve` (system side effect) | OFF | - |
| ALLOCATED | DISPATCHED | `POST /dispatches` (system side effect) | WH | - |
| DISPATCHED | IN_TRANSIT | `PATCH /dispatches/{id}/status` (side effect) | WH, FLD, OFF | - |
| IN_TRANSIT | DELIVERED | `PATCH /dispatches/{id}/status` (side effect) | WH, FLD, OFF | - |
Any other transition -> `409 INVALID_STATE`. Cancelling an ALLOCATED or later request is **not supported in MVP** (it would require releasing reserved stock; see open decision O3).
*CIT only if public reporting is enabled (open decision O4).

## 2. Request approval (new) - separate from allocation approval
**Meaning:** an officer accepts a *verified* request as valid and eligible for allocation. It reserves no stock and picks no warehouse. Allocation approval (section 4) is a different action, on a different resource.

`PATCH /api/requests/{id}/approve`
- **Auth/permission:** role OFFICER only (ADMIN excluded by default; open decision O1).
- **Request body:** `{ "note": "optional, max 1000 chars" }`
- **Validation (server-side, in one transaction, row locked `FOR UPDATE`):**
  1. Request exists (404 `NOT_FOUND`).
  2. Current status is `VERIFIED` (else 409 `INVALID_STATE`).
  3. Priority score and category are present (else 409 `PRIORITY_NOT_CALCULATED`); priority is calculated automatically during verification.
  4. Request quantity > 0 and its resource, area and disaster are still active (else 409 `INVALID_STATE`).
  5. Optional `note` <= 1000 chars (400 `VALIDATION_ERROR`).
  6. Self-approval rule PROPOSED, not decided: approver may not be the requester (403 `FORBIDDEN`) - open decision O2.
- **Effects:** status `VERIFIED -> APPROVED`; `request_history` row; audit event; alert to officers (low priority) optional.
- **Response 200:** `RequestResponse` (same DTO as `GET /requests/{id}`) with `status:"APPROVED"`, `approvedBy`, `approvedAt`.
- **Audit event `REQUEST_APPROVED`:** actor user id/role, entity `need_requests`/id, timestamp, before `{status:VERIFIED}`, after `{status:APPROVED}`, metadata `{priorityScore, priorityCategory, note}`.
- **Idempotency:** a second approve returns 409 `INVALID_STATE`; no duplicate history or audit rows.

Related small endpoints (same pattern):
- `PATCH /requests/{id}/reject` body `{ "reason": "required, 1-1000" }`, OFF, from VERIFIED (PENDING rejection goes through `verify`). Audit `REQUEST_REJECTED`.
- `PATCH /requests/{id}/cancel` body `{ "reason": "required" }`, audit `REQUEST_CANCELLED`. Cancelling an APPROVED request marks its `RECOMMENDED` allocations `STALE`.

## 3. Endpoints
| Method | Path | Roles | Purpose | Codes | Detail due |
|---|---|---|---|---|---|
| POST | /auth/login | Public | Login | 200,400,401 | Day 3 |
| GET | /auth/me | any | Current user + role | 200,401 | Day 3 |
| GET | /dashboard | ADM,OFF,WH | KPIs, series, recent activity | 200 | Day 5 |
| GET/POST | /disasters | GET staff; POST ADM,OFF | List/create | 200,201,400 | Day 3 |
| GET/PUT | /disasters/{id} | as above | Detail/update | 200,404 | Day 3 |
| GET/POST | /affected-areas | GET staff; POST ADM,OFF | List (`disasterId`)/create | 200,201 | Day 3 |
| GET | /requests | staff | List (`status,severity,disasterId,areaId`) | 200 | Day 4 |
| POST | /requests | OFF,FLD,NGO,CIT* | Submit need -> PENDING | 201,400 | Day 4 |
| GET | /requests/{id} | staff | Detail, history, priority reasons | 200,404 | Day 4 |
| PATCH | /requests/{id}/verify | OFF | VERIFY / REJECT / REQUEST_CLARIFICATION (PENDING only); calculates priority | 200,400,409 | Day 4 (score Day 6) |
| PATCH | /requests/{id}/approve | OFF | VERIFIED -> APPROVED (section 2) | 200,403,404,409 | Day 4 |
| PATCH | /requests/{id}/reject | OFF | VERIFIED -> REJECTED | 200,400,409 | Day 4 |
| PATCH | /requests/{id}/cancel | OFF; requester | Cancel with reason | 200,400,403,409 | Day 4 |
| PATCH | /requests/{id}/priority-override | OFF | Override category, reason required; VERIFIED or APPROVED only | 200,400,409 | Day 6 |
| GET | /inventory | ADM,OFF,WH | Stock by resource/warehouse | 200 | Day 5 |
| POST | /inventory/receipts | WH | Record receipt (ledger entry) | 201,400 | Day 5 |
| GET | /allocations/recommendations | OFF | List persisted recommendations (`requestId,status`) | 200 | Day 6 |
| POST | /allocations/recommendations | OFF | Generate for `{requestId}`; request must be APPROVED | 201,404,409 | Day 6 |
| POST | /allocations/{id}/approve | OFF | Approve/modify, reserve stock transactionally | 200,409 | Day 6 |
| POST | /allocations/{id}/reject | OFF | Reject with reason | 200,400,409 | Day 6 |
| POST | /dispatches | WH | Create dispatch for an APPROVED allocation; request -> DISPATCHED | 201,409 | Day 7 |
| PATCH | /dispatches/{id}/status | WH,FLD,OFF | `IN_TRANSIT` or `DELIVERED` | 200,409 | Day 7 |
| GET | /shelters | staff | Shelters, occupancy | 200 | Day 5 |
| GET | /volunteers | ADM,OFF,NGO | Search volunteers | 200 | Day 8 |
| GET | /analytics | ADM,OFF | Filtered analytics | 200 | Day 9 |
| GET | /audit-logs | ADM,OFF | Audit history (filters `entity,actor,from,to`) | 200 | Day 9 |
| POST | /ai/report-analysis | OFF,FLD | Unverified structured suggestions | 200,422,503 | Day 8 |

Common error codes: 400 `VALIDATION_ERROR`; 401 `UNAUTHENTICATED`; 403 `FORBIDDEN`; 404 `NOT_FOUND`; 409 `INVALID_STATE`, `PRIORITY_NOT_CALCULATED`, `STALE_RECOMMENDATION`, `INSUFFICIENT_STOCK`; 429; 500.

## 4. Allocation workflow (consistent with the state machine)
1. Precondition: request is `APPROVED` (not merely VERIFIED).
2. `POST /allocations/recommendations {requestId}` creates an allocation `RECOMMENDED`; any earlier `RECOMMENDED` allocation for that request becomes `STALE`. No stock is touched.
3. `POST /allocations/{id}/approve`: one transaction -> lock inventory rows (ascending id) -> re-check available stock -> write `RESERVE` ledger rows and raise `quantity_reserved` -> allocation `APPROVED` -> request `APPROVED -> ALLOCATED` -> audit `ALLOCATION_APPROVED`. If stock changed: 409 `STALE_RECOMMENDATION` / `INSUFFICIENT_STOCK`, allocation set `STALE`, nothing reserved.
4. `POST /allocations/{id}/reject`: allocation `REJECTED`; request stays `APPROVED`; audit `ALLOCATION_REJECTED`.
5. `POST /dispatches {allocationId, ...}`: allocation must be `APPROVED` and not already dispatched; writes `DISPATCH` ledger rows (reduces on_hand and reserved); dispatch `DISPATCHED`; request `ALLOCATED -> DISPATCHED`.
6. `PATCH /dispatches/{id}/status`: `DISPATCHED -> IN_TRANSIT -> DELIVERED`; request follows; `delivery_updates` row; audit.
Enums: AllocationStatus `RECOMMENDED, APPROVED, REJECTED, STALE`; DispatchStatus `DISPATCHED, IN_TRANSIT, DELIVERED` (matches request statuses one-to-one; dispatch cancellation not in MVP, open decision O3).

## 5. Frozen core DTOs
**POST /auth/login** `{email, password}` -> `{accessToken, tokenType:"Bearer", expiresIn:3600, user:{id, fullName, email, role}}`

**POST /requests**
```json
{ "disasterId": 1, "affectedAreaId": 2, "resourceId": 3, "quantity": 500,
  "affectedPopulation": 1200, "severity": "HIGH", "description": "No drinking water" }
```
Rules: quantity > 0; description 1-2000; area belongs to disaster. Response `RequestResponse {id, status, severity, priorityScore|null, priorityCategory|null, priorityReasons[], quantity, unit, approvedBy|null, approvedAt|null, createdAt}`.

**PATCH /requests/{id}/verify** `{ "decision": "VERIFY|REJECT|REQUEST_CLARIFICATION", "reason": "required unless VERIFY" }`
**PATCH /requests/{id}/approve** `{ "note": "optional" }` (section 2)
**POST /allocations/recommendations** `{ "requestId": 10 }` -> `{id, requestId, status, requestedQty, allocatedQty, unfilledQty, items:[{warehouseId, warehouseName, quantity, distanceKm, reason}], notSelected:[{warehouseId, reason}], version}`
**POST /allocations/{id}/approve** `{ "version": 3, "items": [{warehouseId, quantity}] (optional edit), "note": "optional" }`
Remaining DTOs are fixed on the day each endpoint is built and appended by PR reviewed by both owners.

## 6. Consistency review: findings and resolutions
| # | Finding in v0.1 | Resolution in v0.2 |
|---|---|---|
| 1 | No endpoint for VERIFIED -> APPROVED | Added `PATCH /requests/{id}/approve` (section 2) |
| 2 | No endpoint for VERIFIED -> REJECTED or cancellation; transition list also omitted APPROVED -> CANCELLED | Added `reject`, `cancel`; full table in section 1 |
| 3 | Allocation could start from VERIFIED, which would skip request approval | Recommendation requires `APPROVED` (409 otherwise) |
| 4 | Who moves ALLOCATED/DISPATCHED/IN_TRANSIT/DELIVERED was undefined | Derived only from allocation approve, dispatch create, dispatch status |
| 5 | DispatchStatus had `CREATED`, which does not map to request status DISPATCHED | DispatchStatus aligned 1:1 |
| 6 | "Allocation approve" and "request approve" were both just "approve" | Separate resources and audit events (`REQUEST_APPROVED` vs `ALLOCATION_APPROVED`) |
| 7 | Priority timing undefined (verify and approve both depend on it) | Calculated during verification; approval requires it (`PRIORITY_NOT_CALCULATED`); recalculation policy is open decision O5 |
| 8 | Rejected allocation effect on request unclear | Request stays APPROVED |
| 9 | Priority override allowed on any status | Restricted to VERIFIED or APPROVED |
| 10 | Role `FIELD_WORKER` used but absent in DB V1 | Contract depends on migration V2 (Divay D2) |
| 11 | Audit events not enumerated | Catalogue below |

Audit event catalogue (each row: actor, role, entity, id, timestamp, before/after): `REQUEST_CREATED, REQUEST_VERIFIED, REQUEST_REJECTED, REQUEST_CLARIFICATION_REQUESTED, REQUEST_APPROVED, REQUEST_CANCELLED, PRIORITY_CALCULATED, PRIORITY_OVERRIDDEN, ALLOCATION_RECOMMENDED, ALLOCATION_APPROVED, ALLOCATION_REJECTED, STOCK_RECEIVED, STOCK_RESERVED, DISPATCH_CREATED, DELIVERY_STATUS_UPDATED, AI_ANALYSIS_CREATED`.

## 7. Open contract decisions
O1 ADMIN may approve requests? (default no) | O2 self-approval ban (proposed) | O3 cancellation after ALLOCATED, dispatch cancellation, stock release | O4 public citizen reporting | O5 priority recalculation as waiting time grows | O6 residual quantity after partial allocation (request is ALLOCATED while `unfilledQty` > 0; how the remainder is served) | O7 may the same officer verify and approve one request (currently yes).

## 8. Endpoints added on Days 5-10 (backend and frontend built)
All errors are `application/problem+json` with a stable `code`. Lists use `{content[], page, size, totalElements, totalPages}`. Roles: ADM admin, OFF officer, WHM warehouse manager, FLD field worker, NGO, VOL volunteer. "staff" = ADM, OFF, WHM, FLD, NGO.

| Area | Endpoint | Roles | Notes |
|---|---|---|---|
| Resources/warehouses | GET `/api/resources`, `/api/warehouses` | staff | |
| Inventory | GET `/api/inventory`, GET `/api/inventory/{id}/transactions`, POST `/api/inventory/receipts` | staff read; WHM (own warehouse) or ADM receive | batchRef optional (required for perishable); ledger is append-only |
| Shelters | GET/POST `/api/shelters`, GET `/{id}`, GET `/{id}/alternatives`, PATCH `/{id}/occupancy` | staff (create ADM/OFF) | overcrowded = occupancy above capacity; raises an alert |
| Dashboard | GET `/api/dashboard` | staff | |
| Priority | POST `/api/requests/{id}/priority`, PATCH `/api/requests/{id}/priority-override` | OFF | automatic on verify; override needs a reason |
| Allocation | POST `/api/allocations/recommendations`, POST `/{id}/approve`, POST `/{id}/reject`, GET `/{id}`, GET `?requestId=` | OFF (read: ADM/OFF/WHM) | approve needs `version`; 409 `STALE_VERSION`, `INSUFFICIENT_STOCK`, `INVALID_STATE` |
| Dispatch | GET/POST `/api/dispatches`, PATCH `/{id}/status` | WHM (own warehouse)/ADM/OFF | DISPATCHED -> IN_TRANSIT -> DELIVERED; 409 `ALREADY_DISPATCHED` |
| Audit/alerts/map | GET `/api/audit` (ADM/OFF); GET `/api/alerts`, PATCH `/api/alerts/{id}/read` (staff); GET `/api/map` | | |
| Volunteers | POST/GET `/api/volunteers`, GET `/{id}`, PATCH `/{id}/availability`, POST/GET `/{id}/assignments`, PATCH `/api/assignments/{id}/status` | ADM/OFF/NGO (VOL: own record) | assigning needs AVAILABLE (409 `VOLUNTEER_UNAVAILABLE`) |
| Partners | POST/GET `/api/partner-contributions`, PATCH `/{id}/status` | NGO offers; ADM/OFF decide | OFFERED -> ACCEPTED/DECLINED; ACCEPTED -> RECEIVED |
| Report analyzer | POST/GET `/api/ai/analyses`, GET `/{id}`, PATCH `/{id}/review` | staff; review ADM/OFF | 503 `AI_UNAVAILABLE`, 502 `AI_BAD_OUTPUT`, 429 `RATE_LIMITED`; result is advisory only |
| Analytics | GET `/api/analytics/summary`, `/api/analytics/shortages` | ADM/OFF | optional `disasterId` |
| Report | GET `/api/reports/situation.pdf` | ADM/OFF | `application/pdf`, optional `disasterId` |
| Login | POST `/api/auth/login` | public | 429 `TOO_MANY_ATTEMPTS` after repeated failures |
