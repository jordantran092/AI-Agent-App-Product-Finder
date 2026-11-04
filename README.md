# Product Finder

Product Finder is a local product-discovery application with a Java 21 / Spring Boot backend and a React frontend. Product data is synthetic; the project does not scrape retailer sites.

## Requirements

- Java 21
- Node.js 20.19+ and npm
- Docker Desktop with Docker Compose

## Local PostgreSQL

Start PostgreSQL from the repository root:

```powershell
docker compose up -d
docker compose ps
```

Compose publishes PostgreSQL only on `127.0.0.1:5432`, persists its data in the `postgres_data` named volume, and reports readiness through `pg_isready`. The local-development defaults are database `product_finder`, user `product_finder`, and password `product_finder_local`. You can override the user and password with `DB_USERNAME` and `DB_PASSWORD` environment variables. These defaults are for local development only.

The backend reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for its connection. `SESSION_TIMEOUT` controls the authenticated session lifetime (default `30m`); set `SESSION_COOKIE_SECURE=true` when serving over HTTPS. Keep the cookie secure flag disabled only for local HTTP development.

Stop PostgreSQL but keep its data with `docker compose down`. To intentionally remove the database and all persisted local data, run `docker compose down --volumes`.

## Backend

With the database healthy, start the backend from `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

Flyway applies database migrations automatically on startup. The backend health endpoint is available at <http://localhost:8080/actuator/health>.

Authentication uses JSON endpoints under `/api/auth`. First request `GET /api/auth/csrf`; send the returned token using its `headerName` on POST requests. Register with `POST /api/auth/register` using `{"username":"...","password":"..."}`, then log in at `POST /api/auth/login` with the same shape. The HTTP session cookie maintains authentication; `GET /api/auth/me` returns the current username and `POST /api/auth/logout` invalidates the session. Usernames are case-insensitive; passwords must be at least 12 characters and no more than 72 UTF-8 bytes.

## Frontend

In another terminal:

```powershell
Set-Location frontend
npm.cmd install
npm.cmd run dev
```

Vite serves the frontend at <http://localhost:5173> and proxies API and health requests to the backend at port 8080.

## Tests and checks

```powershell
Set-Location backend
.\mvnw.cmd test

Set-Location ..\frontend
npm.cmd run build
npm.cmd run lint
```

Most backend tests use an in-memory PostgreSQL-compatible H2 database. `PostgresRepositoryIntegrationTests` starts a disposable PostgreSQL 17 Testcontainers instance to verify migrations and repository operations; Docker must be running for this test.

To verify startup against the actual local PostgreSQL container, start the container as above, then run `.\mvnw.cmd spring-boot:run` from `backend/` and check `http://localhost:8080/actuator/health`.
