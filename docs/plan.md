
## Proposed Java implementation plan

I read `docs/design.md`. It defines 10 features, 16 use cases, the core classes and design patterns, and 10 sequence diagrams. I found no existing Java source or build configuration, so this is a greenfield plan; **no code has been written**.

### Proposed foundation

Use a **Java modular monolith** with **Spring Boot**, **Maven**, and a REST API. Keep the UI technology independent—the design specifies GUI interactions but not a frontend stack—and provide a Java CLI as a separate client. Use Spring Data JPA with PostgreSQL for persistent storage and H2 for fast tests; load the catalog from synthetic seed data, as the design specifies.

Organize the Java code around:

- **Domain:** products, listings, retailers, reviews, saved lists and items, profiles, requirements, and chat sessions.
- **Application services:** the design’s services and rules, including `AgentService`, `ProductMatcher`, and `PriceComparisonService`.
- **Infrastructure:** database repositories and the DeepSeek HTTP integration.
- **API and CLI:** thin adapters that call application services rather than duplicating business logic.

Use the specified patterns where they fit: `AgentService` as the facade; a `DeepSeekClient` adapter behind `LLMClient`; a retry decorator for transient model failures; a Spring-managed singleton HTTP client; and a cached review analyzer proxy.

### Implementation milestones

1. **Set up the Java project and domain model.** Define entities, value objects, enums, repository contracts, schema migrations, and synthetic seed data. Model chat states explicitly (`GATHERING`, `CONFIRMING`, `SEARCHING`, `PRESENTING`).
2. **Implement deterministic foundations.** Build profile validation, saved-list operations, product lookup, and price comparison. Filter unavailable, unpriced, or profile-excluded listings before selecting the lowest price.
3. **Add the DeepSeek integration.** Implement request/response DTOs, JSON parsing, timeouts, configuration, and bounded retries. Keep provider-specific formats inside the adapter and API keys out of source control.
4. **Build the agent workflow (F01–F03).** Extract requirements, show them for user confirmation, prevent searches before confirmation, rank candidate products, explain trade-offs, and offer buy/wait advice using price history when available.
5. **Complete saved-product workflows (F04, F07, F10).** Add similar-model suggestions with user confirmation, profile settings, notes, duplicate handling, and removal behavior.
6. **Add review analysis (F08–F09).** Analyze review trust and summarize product information/reviews. Cache results by content hash so changed reviews invalidate the cache and cached calls avoid unnecessary LLM requests.
7. **Add sharing (F06).** Generate links only for non-empty lists, render shared lists read-only, and handle invalid or unavailable tokens.
8. **Add client adapters and verify end-to-end flows.** Implement CLI commands against the same services as the API/UI. Test the main and alternative flows described in the sequence diagrams.

### Validation plan

Add JUnit tests for domain rules and state transitions; integration tests for persistence and REST endpoints; and mocked DeepSeek tests for parsing, transient retries, and failure handling. Cover the design’s important alternatives: vague requirements, rejected suggestions, no matching products/listings, missing price history, no reviews, invalid settings, empty lists, and unavailable share links. Finish with end-to-end tests for the agent search, saved-product, review, and sharing workflows.

### Decisions to resolve before implementation

The design doesn’t specify authentication details, the frontend technology, database deployment, product ranking/tie-breaking rules, or the fake-review scoring rubric. It also uses one `ReviewAnalyzer` concept for both trust labels and summaries; I recommend defining distinct result types and including the analysis type in the cache key so the two results cannot collide. These choices should be confirmed or documented before the affected milestones.