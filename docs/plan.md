## Java implementation plan

This plan follows the decisions recorded in [design.md](./design.md). The repository currently has no Java or frontend build configuration; this is a greenfield implementation plan. No code has been written.

### Architecture

- **Backend:** Java 21 modular monolith, Spring Boot 4.1.2 REST API, and Maven; set `maven.compiler.release` to `21`. Boot 4.1.2 is the stable 4.1 release, chosen over the available 4.2 release candidate. Its [published parent POM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/4.1.2/spring-boot-starter-parent-4.1.2.pom) targets Java 17, making Java 21 compatible. See the [4.1.2 release notes](https://github.com/spring-projects/spring-boot/releases/tag/v4.1.2).
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

- `analyzeTrust(productId)` returns `ReviewTrustResult` containing the agent's concise, non-deterministic trust phrase; no numeric score or fixed label rubric is imposed.
- `summarize(productId)` returns `ReviewSummaryResult` containing the product/review summary.
- `CachedReviewAnalyzer` keys each result by product ID, analysis type (`TRUST` or `SUMMARY`), and an input fingerprint. Trust fingerprints cover reviews; summary fingerprints cover both product information and reviews. This prevents cross-type collisions and invalidates results when relevant content changes.
- Keep “No reviews to check” as the no-review outcome for trust analysis. Do not mistake a cached result of the other analysis type for an empty or successful result.

### Implementation milestones

1. **Bootstrap the project and domain.** Create the Maven/Spring Boot backend, React frontend, database migrations, core entities/value objects, repository interfaces, synthetic seed data, and explicit chat-state transitions (`GATHERING`, `CONFIRMING`, `SEARCHING`, `PRESENTING`).
2. **Implement identity and persistence.** Add PostgreSQL-backed users, secure password hashing, registration/login/logout, authorization for registered-user operations, and local configuration. Add repository integration tests against PostgreSQL.
3. **Implement product schemas and deterministic services.** Add versioned schemas/registry, schema validation, profile validation, product lookup, saved-list operations, and price comparison. Filter out-of-stock, unpriced, and profile-excluded listings before selecting the lowest price.
4. **Add the DeepSeek adapter.** Implement typed request/response models, schema-guided prompts, response validation, timeouts, and bounded retry for transient failures. Keep provider JSON in `DeepSeekClient` and credentials in local environment/configuration, not source control.
5. **Build agent search (F01–F03).** Extract category-specific requirements, return them for user confirmation, enforce the confirmation state before searching, rank eligible candidates non-deterministically against their structured specs, explain trade-offs, and offer buy/wait advice using history when present.
6. **Complete saved-product workflows (F04, F07, F10).** Implement adding, duplicate detection, similar-model suggestions with user confirmation, product detail, profile settings, notes, and removal.
7. **Implement review trust and summaries (F08–F09).** Add typed result objects and typed analyzer operations, content fingerprints, analysis-type-aware cache keys, model-generated trust phrases, summaries, and no-review handling.
8. **Implement sharing (F06).** Generate links only for non-empty lists, render shared pages read-only, calculate current best prices, and handle invalid or deleted tokens.
9. **Connect the React UI and CLI.** Wire each client to the API/services and implement the flows in the use cases and sequence diagrams without duplicating domain logic.
10. **Verify end-to-end behavior.** Run backend unit/integration tests and frontend tests for the specified success, alternative, and error flows.

### Validation plan

- **Backend unit tests:** domain validation, state transitions, deterministic price filtering, schema validation, typed analysis results, cache-key separation/invalidation, and password hashing/auth behavior.
- **Backend integration tests:** PostgreSQL repositories/migrations, authentication, saved-list operations, public shared-list access, and API error responses.
- **DeepSeek adapter tests:** mock HTTP responses for valid and malformed JSON, transient errors, retry exhaustion, schema-conforming requirement extraction, candidate ranking explanations, and trust phrase/summary parsing.
- **Frontend tests:** login/logout, confirmation before search, profile settings, saved-list CRUD, summary/trust display, and share-link behavior.
- **Flow coverage:** vague requirements, rejected model suggestions, no candidate products, no eligible retailer listings, missing price history, no reviews, invalid settings, empty lists, invalid share tokens, and temporary DeepSeek failure.
- **Local acceptance:** start PostgreSQL locally, run migrations and seed data, start the backend and React app, then exercise the principal workflows in-browser and through the CLI.

### Remaining implementation details

The design decisions for authentication, UI, database location, candidate ranking, review phrasing, review-result/cache separation, initial product categories, and Java/Spring Boot versions are resolved. The remaining setup detail is local PostgreSQL connection settings. Extend the schema set when the synthetic catalog introduces additional categories.
