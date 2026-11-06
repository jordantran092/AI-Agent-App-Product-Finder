# Product Finder

Product Finder is a local product-discovery application with a Java 21 / Spring Boot backend and a React frontend. On startup, the backend seeds its local catalog to 2,500 entries across the eight supported categories. It uses familiar product families and Canadian retailer names, but generated product configurations, model identifiers, inventory, prices, price history, and reviews are synthetic and are not live offers or a verified manufacturer SKU list. All prices and budgets are in Canadian dollars (CAD); the project does not scrape retailer sites.

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

DeepSeek calls are disabled until `DEEPSEEK_API_KEY` is set in the backend process environment. The backend defaults to the `deepseek-flash` model and `https://api.deepseek.com`. Optional settings include `DEEPSEEK_MODEL`, `DEEPSEEK_BASE_URL`, `DEEPSEEK_CONNECT_TIMEOUT`, `DEEPSEEK_READ_TIMEOUT`, `DEEPSEEK_MAX_ATTEMPTS` (1–5 total attempts), `DEEPSEEK_INITIAL_BACKOFF`, and `DEEPSEEK_MAX_BACKOFF`. Do not commit API keys; structured responses are checked against the requested JSON Schema before they are returned to application callers.

Stop PostgreSQL but keep its data with `docker compose down`. To intentionally remove the database and all persisted local data, run `docker compose down --volumes`.

## Backend

With the database healthy, start the backend from `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

Flyway applies database migrations automatically on startup. The backend health endpoint is available at <http://localhost:8080/actuator/health>.

Authentication uses JSON endpoints under `/api/auth`. First request `GET /api/auth/csrf`; send the returned token using its `headerName` on POST requests. Register with `POST /api/auth/register` using `{"username":"...","password":"..."}`, then log in at `POST /api/auth/login` with the same shape. The HTTP session cookie maintains authentication; `GET /api/auth/me` returns the current username and `POST /api/auth/logout` invalidates the session. Usernames are case-insensitive; passwords must be at least 12 characters and no more than 72 UTF-8 bytes.

The authenticated agent chat API is under `/api/chat`: `GET /api/chat` reads the current flow; `POST /api/chat/messages` accepts `{"text":"..."}` and returns requirements for confirmation or a follow-up question; `POST /api/chat/confirm` runs the confirmed search; `POST /api/chat/cancel` cancels a pending confirmation; `POST /api/chat/retry` reruns the current search after a presented recommendation; and `POST /api/chat/buy-or-wait` accepts `{"urgency":"..."}` after a match is presented. Include the authenticated session cookie and CSRF header on each POST. Chat history and state are currently stored in backend memory and are cleared when the backend restarts.

Saved-product workflows use the authenticated `/api/saved-list` and `/api/products` endpoints. `GET /api/saved-list` returns the current user's items. `POST /api/saved-list/items` accepts `{"productName":"iPhone 15"}`; it adds an exact name match, reports duplicates, or returns a similar product-name suggestion without adding it. Confirm a suggestion explicitly with `POST /api/saved-list/items/{productId}`. `DELETE /api/saved-list/items/{productId}` removes an item, and `PUT /api/saved-list/items/{productId}/note` accepts `{"note":"..."}`. Unsupported control characters are removed except tabs and line breaks; the response reports whether sanitization occurred and returns the saved text. Notes are limited to 4,000 characters. Send the authenticated session cookie and CSRF header on each state-changing request.

`GET /api/products/{productId}` returns product details, the best eligible offer, and the saved review-trust label only when the product belongs to the current user's saved list. Trust analysis runs when a product is added and refreshes when its product page is opened; products with no reviews receive the label `No reviews to check`. `POST /api/products/{productId}/summary` returns a cached or newly generated product-and-review summary for an owned saved product. Reviews from retailers without a priced offer are excluded from summaries. These analysis endpoints require `DEEPSEEK_API_KEY` when a cache miss requires a model call; provider errors are returned explicitly. If the catalog marks a product discontinued, the detail endpoint removes it and returns `410 Gone`. `GET /api/profile` and `PUT /api/profile` read and update budget, excluded brands, and allowed conditions; excluded brands must exist in the catalog.

## Frontend

In another terminal:

```powershell
Set-Location frontend
npm.cmd install
npm.cmd run dev
```

Vite serves the frontend at <http://localhost:5173> and proxies API and health requests to the backend at port 8080. Register or sign in to use the product-search chat, review and confirm extracted requirements, request buy/wait advice, manage saved products and notes, update profile filters, or create a read-only share link. Share links open at `/shared/{token}` and can be viewed without signing in.

## CLI

The Java CLI uses the same application services as the REST API and can be started instead of the web server after packaging:

```powershell
Set-Location backend
.\mvnw.cmd package
java -jar target\product-finder-0.1.0-SNAPSHOT.jar --app.cli.enabled=true
```

Use `register` or `login` first. The interactive CLI supports chat and requirement confirmation, buy/wait advice, saved-list add/remove and product details, best prices, review summaries, personal notes, profile settings, and share-link creation. Enter `help` to see all commands; use `exit` to quit. The `share` command prints a frontend link using `app.frontend-url` (default `http://localhost:5173`), so keep the React development server available when sharing.

## Tests and checks

```powershell
Set-Location backend
.\mvnw.cmd test

Set-Location ..\frontend
npm.cmd test
npm.cmd run build
npm.cmd run lint
```

Most backend tests use an in-memory PostgreSQL-compatible H2 database. `PostgresRepositoryIntegrationTests` starts a disposable PostgreSQL 17 Testcontainers instance to verify migrations and repository operations; Docker must be running for this test. The frontend test suite uses Vitest, jsdom, and Testing Library to exercise the primary user flows and alternatives. DeepSeek interactions are tested with controlled HTTP responses; the automated suite does not make live provider requests.

To verify startup against the actual local PostgreSQL container, start the container as above, then run `.\mvnw.cmd spring-boot:run` from `backend/` and check `http://localhost:8080/actuator/health`.
