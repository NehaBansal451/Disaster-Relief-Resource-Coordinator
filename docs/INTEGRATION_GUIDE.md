# Integration Guide: run DRRC on your own computer

Tested on Windows 11 with PowerShell, Docker Desktop, Java 21. Ports: database 15432 (see note), backend 8080, frontend 8081. Open every command in PowerShell **inside the repository folder** (the one that contains `backend`, `frontend`, `database`; if `git status` says "not a git repository" you are one folder too high).

## 1. One-time setup
1. Install Docker Desktop and start it (wait until it says "Engine running").
2. Install JDK 21. Check: `java -version` shows 21.
3. Clone the repository and `git checkout main`, `git pull`.

## 2. Secrets file (never commit it, never paste its contents anywhere)
```powershell
cd deployment\docker
Copy-Item .env.example .env
```
Open `.env` in an editor and set:
- `DB_PASSWORD` to a long random value (20+ characters, letters and digits).
- `JWT_SECRET` to a random value of at least 32 characters (the backend refuses to start with less).
- `DB_PORT=15432` (see note below).
Keep `SPRING_PROFILES_ACTIVE=dev` (loads the demo data) and `DRRC_BACKEND_URL=http://localhost:8080`.

Random value generator (prints one value):
```powershell
-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 40 | ForEach-Object {[char]$_})
```
`.env` is ignored by git. If a password was ever pasted into a chat, an issue or a commit, treat it as leaked and generate a new one.

**Port note:** Windows may block 5432 ("ports are not available"). Using `DB_PORT=15432` avoids this; the container still listens on 5432 inside Docker.

## 3. Start the database
```powershell
cd deployment\docker
docker compose up -d
docker compose ps
```
The container must show `healthy`. If you changed `DB_PASSWORD` after a first start, reset the volume (this deletes local data): `docker compose down -v` then `docker compose up -d`.

## 4. Load `.env` into the PowerShell window (once per window)
```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
  $k,$v = $_ -split '=',2; [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim(), 'Process') }
```
Do this in **both** the backend window and the frontend window.

## 5. Backend (window 1)
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
Success looks like: Flyway "Successfully applied 5 migrations" (V1-V5), then "Tomcat started on port 8080". Health: <http://localhost:8080/actuator/health> shows `UP`.

## 6. Frontend (window 2)
```powershell
cd frontend
$env:DRRC_BACKEND_URL = "http://localhost:8080"
.\mvnw.cmd spring-boot:run
```
Open <http://localhost:8081>. The first start downloads the Vaadin front-end build tools and takes a few minutes. (`vaadin-dev` is an optional dependency in `frontend/pom.xml`; without it dev mode fails with `'vaadin-dev-server' not found`.)

## 7. Demo logins (dev profile only, password `Demo@12345`)
| Email | Role | Can do |
|---|---|---|
| `admin@demo.drrc.local` | Administrator | System status; read everything; create disasters and areas |
| `officer@demo.drrc.local` | Relief officer | Create disasters/areas; verify, approve, reject, cancel requests |
| `field@demo.drrc.local` | Field worker | Submit requests; cancel own pending request |
| `ngo@demo.drrc.local` | NGO coordinator | Submit requests |
| `citizen@demo.drrc.local`, `volunteer@demo.drrc.local` | Citizen, volunteer | Cannot use the staff screens |
These accounts exist only with the `dev` profile. Never run `dev` on a public server.

## 8. Tests
```powershell
cd backend ; .\mvnw.cmd test          # unit tests; integration tests are skipped without DB_PASSWORD
cd ..\frontend ; .\mvnw.cmd test
```
To also run the database integration tests, load `.env` (step 4) in the backend window first, with the database running. They create a few rows described `IT-...` in the dev database.

## 9. Database self-checks (optional)
```powershell
docker compose exec -T db psql -U $env:DB_USER -d $env:DB_NAME -v ON_ERROR_STOP=1 < ..\..\database\queries\day4_checks.sql
```
(or run it with any `psql`). Every line must print `PASS`.

## 10. Troubleshooting
| Symptom | Fix |
|---|---|
| "ports are not available" for 5432 | Set `DB_PORT=15432`, `docker compose down`, `up -d` |
| `password authentication failed` | `.env` password differs from the one the volume was created with: `docker compose down -v`, `up -d` |
| Backend: JWT secret error | `JWT_SECRET` missing or shorter than 32 characters; reload `.env` (step 4) |
| Frontend page says backend not reachable | Start the backend first; check `DRRC_BACKEND_URL` |
| `cannot find symbol` during build | Pull the latest `main`; run `.\mvnw.cmd clean test` |
| "not a git repository" | `cd` into the inner repository folder |

## 11. Contracts between the applications
- Frontend -> backend: REST under `/api`, `Authorization: Bearer <JWT>`; errors are RFC 9457 `application/problem+json` with `code` and `errors[{field,message}]` (`API_CONTRACT.md`).
- The JWT never reaches the browser: the Vaadin server keeps it in the server-side session.
- Backend -> database: Flyway applies `database/migrations` at start-up; the dev seed is applied only with profile `dev`.
- Change order for a new feature: contract in `API_CONTRACT.md` -> migration -> backend -> frontend -> tests -> `PROJECT_STATUS.md`.
