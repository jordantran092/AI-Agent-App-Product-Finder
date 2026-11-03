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

Stop PostgreSQL but keep its data with `docker compose down`. To intentionally remove the database and all persisted local data, run `docker compose down --volumes`.

## Backend

With the database healthy, start the backend from `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

Flyway applies database migrations automatically on startup. The backend health endpoint is available at <http://localhost:8080/actuator/health>.

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

The backend context smoke test uses an in-memory PostgreSQL-compatible H2 database. The application itself is configured for local PostgreSQL.

To verify startup against the actual local PostgreSQL container, start the container as above, then run `.\mvnw.cmd spring-boot:run` from `backend/` and check `http://localhost:8080/actuator/health`.
