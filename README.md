# Product Finder App

Product Finder is a product-discovery application with a Java 21 / Spring Boot backend and a React frontend. On startup, the backend seeds its local catalog to 2,500 entries across the eight supported categories. It uses familiar product families and Canadian retailer names, but generated product configurations, model identifiers, inventory, prices, price history, and reviews are synthetic and are not live offers or a verified manufacturer SKU list. All prices and budgets are in Canadian dollars (CAD). 

Feature demonstration videos can be seen in the **Feature-to-implementation mapping** section of the **stage2-report** PDF.

## Requirements

- Java 21
- Node.js 20.19+ and npm
- Docker Desktop
- A DeepSeek API key

The commands below use Windows PowerShell. Run them from the repository root unless a different directory is specified. The Maven wrapper downloads Maven and backend dependencies when needed; a separate Maven installation is not required.

## Install dependencies

From the repository root, install the frontend dependencies and download/build the backend dependencies:

```powershell
Set-Location frontend
npm.cmd ci
Set-Location ..\backend
.\mvnw.cmd -DskipTests package
Set-Location ..
```

The first Maven run downloads the wrapper and dependencies. Docker is needed to run the full backend integration test suite, but `-DskipTests` skips those tests during this initial build.

## Local PostgreSQL

Start PostgreSQL from the repository root:

```powershell
docker compose up -d
docker compose ps
```


**Additional details:**

Compose publishes PostgreSQL only on `127.0.0.1:5432`, persists its data in the `postgres_data` named volume, and reports readiness through `pg_isready`. The local-development defaults are database `product_finder`, user `product_finder`, and password `product_finder_local`. You can override the user and password with `DB_USERNAME` and `DB_PASSWORD` environment variables. These defaults are for local development only.

The backend reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for its connection. `FRONTEND_URL` sets the base URL used when generating public share links (default `http://localhost:5173`); configure it to the address where the React app is reachable by link recipients.


## Setting the DeepSeek API key

Set the DeepSeek API key in the **same PowerShell terminal** that will start the backend or CLI. This prompt hides the key as you enter it:

```powershell
$secureKey = Read-Host "DeepSeek API key" -AsSecureString
$env:DEEPSEEK_API_KEY = [System.Net.NetworkCredential]::new("", $secureKey).Password
Remove-Variable secureKey
```

Environment variables set this way last only for that terminal session. 





**Additional details:**

If you override the database credentials, use matching values for Docker Compose and the backend. An existing PostgreSQL data volume retains its original credentials when environment variables change.

Stop PostgreSQL but keep its data with `docker compose down`. To intentionally remove the database and all persisted local data, run `docker compose down --volumes`.

## Backend

With the database healthy and the DeepSeek key set in this terminal, start the backend from the repository root:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

**Additional details:**

Flyway applies database migrations automatically on startup. The backend health endpoint is available at <http://localhost:8080/actuator/health>.





## Frontend

With the backend running, open another terminal at the repository root:

```powershell
Set-Location frontend
npm.cmd run dev
```

Vite serves the frontend at <http://localhost:5173>

### GUI example

1. Open <http://localhost:5173>, create an account with a password of at least 12 characters, and sign in.
2. In the product-search chat, enter a request. Answer any follow-up, review the extracted requirements, and confirm them to search.
3. If a recommendation is returned, review its price and trade-offs, then select **Save product**.
4. Open the product from the saved list. Its saved recommendation offer is a snapshot; select **Find best price** for a fresh comparison. You can also request a summary or add a note.


## CLI

The Java CLI uses the same application services as the REST API and runs instead of the web backend. With PostgreSQL running, set the DeepSeek key in this terminal as described above, then start from the repository root:

Start from the repository root.

```powershell
Set-Location backend
.\mvnw.cmd package
java -jar target\product-finder-0.1.0-SNAPSHOT.jar --app.cli.enabled=true
```

Wait for the `price-agent>` prompt and use `register` or `login` first. The interactive CLI supports chat and requirement confirmation, buy/wait advice, saved-list add/remove and product details, best prices, review summaries, personal notes, profile settings, and share-link creation. Enter `help` to see all commands; use `clear` or `cls` to clear the terminal screen and `exit` to quit. Windows uses its native `cls` command; other platforms use ANSI escape sequences, so use an ANSI-compatible terminal there. The `share` command prints a frontend link using `app.frontend-url` (default `http://localhost:5173`); viewing that link requires the web backend and React frontend to be running.

### CLI example

At `price-agent>`, enter `register` and follow the username and password prompts, or enter `login` for an existing account. Then:

```text
price-agent> chat
chat> enter what you want here
chat> /confirm
chat> /back
price-agent> add iPhone 15
price-agent> list
```

Answer any follow-up questions before `/confirm`. If `add` returns a suggestion, use `confirm-add <product-id>` with the ID it prints. Use the ID shown by `list` with commands such as `product <product-id>`, `price <product-id>`, `summary <product-id>`, and `note <product-id> Check warranty`.

## Tests and checks

Start from the repository root.

```powershell
Set-Location backend
.\mvnw.cmd test

Set-Location ..\frontend
npm.cmd test
npm.cmd run build
npm.cmd run lint
```

Most backend tests use an in-memory PostgreSQL-compatible H2 database. `PostgresRepositoryIntegrationTests` starts a disposable PostgreSQL 17 Testcontainers instance to verify migrations and repository operations; Docker must be running for this test. The frontend test suite uses Vitest, jsdom, and Testing Library to exercise the primary user flows and alternatives. DeepSeek interactions are tested with controlled HTTP responses.

To verify startup against the actual local PostgreSQL container, start the container as above, then run `.\mvnw.cmd spring-boot:run` from `backend/` and check `http://localhost:8080/actuator/health`.



## REST API

### Authentication — `/api/auth`

| Method | Endpoint | Purpose / body |
|---|---|---|
| `GET` | `/api/auth/csrf` | Get a CSRF token. Use the returned `headerName` and token on POST requests. |
| `POST` | `/api/auth/register` | Register with `{"username":"...","password":"..."}` |
| `POST` | `/api/auth/login` | Sign in with the same body format |
| `GET` | `/api/auth/me` | Get the signed-in username |
| `POST` | `/api/auth/logout` | Sign out and invalidate the session |


### Agent chat — `/api/chat`

| Method | Endpoint | Purpose / body |
|---|---|---|
| `GET` | `/api/chat` | Read the current chat flow |
| `POST` | `/api/chat/messages` | Send a message: `{"text":"..."}` |
| `POST` | `/api/chat/confirm` | Confirm requirements and search |
| `POST` | `/api/chat/cancel` | Cancel a pending confirmation |
| `POST` | `/api/chat/retry` | Retry the current search |
| `POST` | `/api/chat/buy-or-wait` | Request advice: `{"urgency":"..."}` |


### Saved products — `/api/saved-list`

| Method | Endpoint | Purpose / body |
|---|---|---|
| `GET` | `/api/saved-list` | Get the current user’s saved products |
| `POST` | `/api/saved-list/items` | Add by name: `{"productName":"iPhone 15"}`. May return a suggestion without adding it. |
| `POST` | `/api/saved-list/items/{productId}` | Confirm and add a suggested product |
| `DELETE` | `/api/saved-list/items/{productId}` | Remove a saved product |
| `PUT` | `/api/saved-list/items/{productId}/note` | Save a note: `{"note":"..."}` |
| `POST` | `/api/saved-list/recommendation` | Save the current recommendation and its displayed offer: `{"productId":...}` |


### Products — `/api/products`

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/products/{productId}` | Get saved-product details and its review-trust label |
| `POST` | `/api/products/{productId}/best-price` | Compare current eligible offers using the user’s profile |
| `POST` | `/api/products/{productId}/summary` | Get a cached or generated product-and-review summary |


### Profile — `/api/profile`

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/profile` | Read budget, excluded brands and allowed conditions |
| `PUT` | `/api/profile` | Update those preferences |
