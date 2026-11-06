## Java implementation plan

This plan follows the decisions recorded in [design.md](./design.md). The project scaffold and milestone 1 domain bootstrap are complete; the remaining milestones describe work that is still to be implemented.

### Architecture

- **Backend:** Java 21 modular monolith, Spring Boot 4.1.2 REST API, and Maven; set `maven.compiler.release` to `21`. Boot 4.1.2 is the stable 4.1 release, chosen over the available 4.2 release candidate. Its [published parent POM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/4.1.2/spring-boot-starter-parent-4.1.2.pom) targets Java 17, making Java 21 compatible. See the [4.1.2 release notes](https://github.com/spring-projects/spring-boot/releases/tag/v4.1.2). The backend tests use JUnit 5 (Jupiter)—for example, they import org.junit.jupiter.api.Test. The JUnit testing dependencies are managed through Spring Boot’s spring-boot-starter-test in pom.xml.
- **Frontend:** React application for chat, product details, saved lists, shared lists, and settings. Treat UI components as API clients, not as the owners of business rules.
- **Persistence:** Local PostgreSQL only, with migrations and synthetic catalog seed data. No hosted database or deployment work.
- **Authentication:** Simple registration/login/logout backed by PostgreSQL users. Store a unique username and a secure password hash (for example, BCrypt), never a plaintext password. Protect registered-user routes; allow public read-only access to shared-list links. Use session-based authentication for the local React/API application.
- **CLI:** Retain the designed Java CLI as another client of the same services; do not duplicate application logic.

Suggested project layout:

```text
backend/
  src/main/java/.../domain/
  src/main/java/.../application/
  src/main/java/.../infrastructure/
  src/main/java/.../api/
  src/main/resources/product-schemas/
  src/main/resources/db/migration/
frontend/
  src/
```

### Product schemas and matching

1. Define versioned JSON Schema product-spec schemas in `backend/src/main/resources/product-schemas/`, with a registry that loads and validates the schema for a product category.
2. Design versioned schemas for smartphones, laptops, tablets, televisions, headphones, and clothing (tops, bottoms, and footwear). Include typed attributes, canonical units, validation rules, and required/optional distinctions. Provide representative synthetic catalog entries that conform to those schemas.
3. Use the same category schema when extracting requirements and preparing candidate products for the model. Validate model-produced structured requirements before storing them in a chat session.
4. Apply deterministic eligibility checks for explicit requirements and profile filters, then let the AI agent decide non-deterministically how to rank and tie-break eligible candidates. Do not implement fixed score weights or deterministic AI tie-break rules. Require the agent to explain its choice using the structured product attributes.
5. Test schema validation, category-specific attributes, malformed model output, and that hard requirements/profile exclusions are not bypassed by ranking.

### Review-analysis contract

Use the shared `ReviewAnalyzer` facade with typed operations and distinct results:

- `analyzeTrust(productId)` returns `ReviewTrustResult` containing the agent's concise trust label. Do not mention synthetic/generated data in the label; for widely recognized, established brands, favor "Trustworthy Reviews" in about 90% of ordinary cases, overriding this prior only for clear, strong suspicious review patterns.
- `summarize(productId)` returns `ReviewSummaryResult` containing the product/review summary.
- `CachedReviewAnalyzer` keys each result by product ID, analysis type (`TRUST` or `SUMMARY`), and an input fingerprint. Trust fingerprints cover reviews; summary fingerprints cover both product information and reviews. This prevents cross-type collisions and invalidates results when relevant content changes.
- Keep “No reviews to check” as the no-review outcome for trust analysis. Do not mistake a cached result of the other analysis type for an empty or successful result.

### Implementation milestones

1. **Complete — bootstrap the project and domain.** Set up the Maven/Spring Boot backend and React frontend; add catalog, user/profile, and saved-list entities, repository interfaces, a session-store port, PostgreSQL migrations, an idempotent startup seeder for a generated 2,500-entry synthetic catalog across all eight categories, versioned category JSON Schemas, and guarded chat-state transitions (`GATHERING`, `CONFIRMING`, `SEARCHING`, `PRESENTING`). Backend tests pass against H2, and the migration/JPA startup was verified against local PostgreSQL.
2. **Complete — implement identity and persistence.** Add PostgreSQL-backed registration, login and logout with BCrypt password hashes, session-based authorization, CSRF protection for state-changing requests, local session/database settings, and repository integration coverage against a disposable PostgreSQL 17 Testcontainers instance.
3. **Complete — implement product schemas and deterministic services.** Load and validate product specifications against the eight versioned category schemas; validate and persist profile settings; provide product lookup, saved-list add/remove, and best-price services. Price comparison excludes out-of-stock, unpriced, disallowed-condition, and profile-excluded-brand listings before selecting the lowest price. The service and schema paths are covered by H2 and PostgreSQL integration tests.
4. **Complete — add the DeepSeek adapter.** Add the application-owned typed LLM contract, category/schema-guided prompt construction and JSON Schema response validation; isolate DeepSeek request/response mapping behind `DeepSeekClient` and a shared `RestClient`; configure API credentials, model, base URL, timeouts, and bounded retry/backoff through environment-backed settings. Adapter tests cover successful mapping, malformed provider JSON, schema rejection, 429 retry behavior, non-retryable authentication failures, retry exhaustion, missing credentials, and the configured read timeout. Blank completion diagnostics include the provider finish reason and whether reasoning content was present.
5. **Complete — build agent search (F01–F03).** Add schema-constrained category inference and requirements extraction, validate category attributes before confirmation, enforce explicit confirmation before searching, filter candidates against structured requirements and profile budget/brand/condition settings, let the model non-deterministically rank only eligible candidates and explain trade-offs, and provide typed buy/wait advice using price history when available. Expose the workflow through authenticated chat API endpoints. Chat state is held in an in-memory `SessionStore` and resets when the backend process restarts.
6. **Complete — saved-product workflows (F04, F07, F10).** Implement authenticated list/add/remove by human-readable product name, duplicate detection, similar-name suggestions that require explicit user confirmation, saved-item details and best eligible offers, profile settings with catalog brand validation, and sanitized bounded notes. F04 suggestions bypass DeepSeek when all normalized query tokens occur in a candidate's brand/name; otherwise, the model chooses from at most eight compact candidates with a 1,024-token completion limit. Incomplete model completions remain retryable and are not reported as catalog misses. Review analysis invoked by UC07 remains in milestone 7 (F08).
7. **Complete — implement review trust and summaries (F08–F09).** Add distinct typed trust/summary results and analyzer operations, SHA-256 content fingerprints, analysis-type-aware cache keys, model-generated trust labels persisted to saved items, product/review summaries, no-review handling, and an owner-checked summary API. F08 labels must not mention synthetic/generated data. For widely recognized, established brands, favor "Trustworthy Reviews" in about 90% of ordinary cases; only clear, strong suspicious review patterns override this positive prior. Trust-cache fingerprints include product name, brand, category, and review content. Exclude reviews from retailers without priced offers when summarizing.
8. **Complete — implement sharing API (F06).** Generate and reuse secure share tokens only for non-empty lists; expose an anonymous, read-only shared-list API with current best offers; and reject invalid or revoked tokens. The React shared-page rendering, link-copying, and navigation remain in milestone 9.
9. **Complete — connect the React UI and CLI.** Wire React authentication, chat and requirement confirmation, product matching, buy/wait advice, saved products and explicit suggestions, product details and review insights, notes, profile settings, and read-only shared-list pages to the backend API. Add an interactive Java CLI that calls the same application services for these workflows, including `clear`/`cls` terminal-screen commands. Share URLs use the configurable React frontend base URL (`FRONTEND_URL`, default `http://localhost:5173`) and target `/shared/{token}`; domain behavior remains in backend services.
10. **Complete — verify end-to-end behavior.** Run the full backend unit and PostgreSQL integration suites plus the React/Vitest workflow suite. Cover authentication, confirmation gating and vague requests, profile validation errors, saved-product CRUD and rejected/accepted suggestions, trust/summary/notes, buy/wait without history, no eligible candidates, temporary agent errors, empty-list share prevention, public sharing, and invalid tokens. Browser acceptance verified registration, saved-list navigation, and the public invalid-token page against local PostgreSQL. DeepSeek HTTP and failure behavior is validated with controlled adapter tests; no live provider call or API credential is required by this test suite.

### Validation plan

- **Backend unit tests:** domain validation, state transitions, deterministic price filtering, schema validation, typed analysis results, cache-key separation/invalidation, and password hashing/auth behavior.
- **Backend integration tests:** PostgreSQL repositories/migrations, authentication, saved-list operations, public shared-list access, and API error responses.
- **DeepSeek adapter tests (milestone 4):** mock HTTP responses for request/response mapping, malformed provider JSON, structured output validation, transient and permanent HTTP errors, bounded retries, and missing credentials; verify the configured read timeout against a delayed local HTTP response, and verify diagnostics for blank completion content. Schema-conforming agent results, ranking explanations, and trust phrase/summary parsing remain covered as their feature milestones are implemented.
- **Agent-search tests (milestone 5):** verify category/schema-guided extraction and follow-up questions, rejection of invalid category attributes, confirmation gating and retry state, hard candidate/profile filters before model ranking, rejection of a model-selected ineligible product, price-history statistics, and buy/wait advice with and without history.
- **Saved-product tests (milestone 6):** verify exact product-name add, duplicate handling, deterministic query-token candidate suggestions without an LLM call, model-selected similar-name candidates and explicit confirmation, candidate-ID validation, the configured completion-token budget, owner-only product details, discontinued-item removal, note sanitization and limits, and profile validation against catalog brands.
- **Review-analysis tests (milestone 7):** verify schema-constrained trust labels and summaries, the established-brand positive prior, that labels must not mention synthetic/generated data, product-brand context, no-review handling without an LLM call, cache hits, analysis-type separation, invalidation when trust-relevant product or review content changes, missing-price retailer filtering, saved-item trust labels, and owner-only summary access.
- **Frontend tests:** Vitest and Testing Library cover registration/login/logout, vague follow-up and confirmation before search, profile updates and error display, saved-list CRUD and explicit suggestion acceptance/rejection, no-match and temporary-error handling, buy/wait advice without history, product trust/summary/notes, empty-list share prevention, share-link copying, anonymous shared-list rendering, and invalid-token display.
- **Flow coverage:** vague requirements, rejected model suggestions, no candidate products, no eligible retailer listings, missing price history, no reviews, invalid settings, empty lists, invalid share tokens, and temporary DeepSeek failure.
- **Local acceptance:** start PostgreSQL locally, run migrations and seed data, start the backend and React app, then exercise the principal workflows in-browser and through the CLI.

### Remaining implementation details

The design decisions for authentication, UI, database location, candidate ranking, review phrasing, review-result/cache separation, initial product categories, and Java/Spring Boot versions are resolved. Local PostgreSQL settings are provided through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, with development defaults in the application configuration and Compose file. Extend the schema set when the synthetic catalog introduces additional categories. `Requirements` includes an `attributes: Map<String, Object>` field for category-specific constraints, validated against the selected category's schema before extracted requirements are accepted. Chat sessions currently live only in the backend process memory; they are not durable across restarts.
