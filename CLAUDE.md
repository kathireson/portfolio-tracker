# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development

### Build commands
```bash
# Run with Maven wrapper
./mvnw spring-boot:run

# Build fat JAR
./mvnw clean package

# Run tests
./mvnw test

# Run single test
./mvnw test -Dtest=RebalanceServiceTest
```

### Run H2 console
```bash
# Start app, then open http://localhost:8080/h2-console
./mvnw spring-boot:run
# JDBC: jdbc:h2:file:./data/portfoliodb
```

## Architecture

### Multi-Portfolio Design
This is a Spring Boot application that supports multiple portfolios. Each portfolio contains holdings with:
- Ticker symbol
- Shares count
- Target allocation %
- Price quotes (fetched from Yahoo Finance or Alpha Vantage)
- Type: ALLOCATED, UNTRACKED, or CASH

### Layer Structure
```
├── controller/          # REST and Thymeleaf endpoints
│   ├── DashboardController.java   # Main MVC + REST API
│   ├── RebalanceSuggestorController.java
│   └── DebugController.java
├── service/             # Business logic
│   ├── QuoteService.java              # Orchestrates quote fetching
│   ├── RebalanceService.java          # Drift calculation
│   ├── RebalanceSuggestorService.java
│   ├── QuoteProvider.java (interface)
│   ├── YahooFinanceQuoteProvider.java
│   └── AlphaVantageQuoteProvider.java
├── model/               # JPA entities
│   ├── Holding.java           # JPA entity with holdingType enum
│   ├── DailySnapshot.java     # Daily price records
│   └── Portfolio.java
├── repository/          # JPA repositories
├── dto/                 # View models and request/response DTOs
├── scheduler/           # @Scheduled quote refresh jobs
└── config/              # Spring configuration
    ├── KeycloakConfig.java     # OAuth2 Keycloak integration
    ├── WebClientConfig.java
    ├── DataInitializer.java    # Seeds example holdings
    └── SecurityConfig.java
```

### Data Model
- **Holding**: ticker, name, shares, targetAllocation, holdingType, lastPrice, currentValue
- **DailySnapshot**: date, portfolioId, holdingId, snapshotPrice (historical tracking)
- **Portfolio**: collection of holdings

### Quote Fetching Flow
1. `QuoteRefreshScheduler` runs at configurable cron (default: 9:35 AM ET Mon-Fri)
2. `QuoteService` fetches quotes for ALLOCATED and UNTRACKED holdings (skips CASH)
3. Quotes are persisted to `DailySnapshot` for historical tracking
4. Dashboard calculates allocations, drift, and rebalancing suggestions

### Holding Types
- **ALLOCATED**: Contributes to rebalancing calculations and allocation %
- **UNTRACKED**: Has price quotes but not tracked for allocation/drift
- **CASH**: Non-quoted holdings (e.g., cash). No price quotes fetched.

## Configuration

### Key properties (src/main/resources/application.properties)
```properties
# Quote provider
portfolio.quote-provider=YAHOO  # YAHOO or ALPHA_VANTAGE

# Alpha Vantage (if used)
portfolio.alpha-vantage.api-key=YOUR_KEY_HERE

# Scheduler
portfolio.quote-fetch-cron=0 35 9 * * MON-FRI
portfolio.scheduler-timezone=America/New_York

# Keycloak OAuth2 (secrets are in application-secret.properties, which is gitignored)
# See application-secret.properties.example for the template
spring.security.oauth2.client.provider.kathir-homelab.issuer-uri=${KEYCLOAK_ISSUER_URI}
spring.security.oauth2.client.registration.keycloak.client-id=portfolio-tracker
spring.security.oauth2.client.registration.keycloak.client-secret=${KEYCLOAK_CLIENT_SECRET}
```

### Database
- H2 file-based: `./data/portfoliodb.mv.db`
- Switch to PostgreSQL by adding drivers and changing `spring.datasource.*` properties

## Key Features

### Dashboard (/dashboard)
- Total portfolio value, per-holding allocations, drift vs target
- Three tables: Allocated Holdings, Untracked Holdings, Cash
- Historical portfolio value chart (separate lines for each holding type)
- Add/edit/remove holdings via form

### Rebalance Suggestor
- Calculates drift % and drift $ for each holding
- Suggests buying/selling amounts to rebalance to target

### Historical Tracking
- Daily snapshots stored for each holding
- Chart shows value trends over time with portfolio history view

## Recent Changes (IMPLEMENTATION_NOTES.md)
Added support for three holding types (ALLOCATED, UNTRACKED, CASH):
- CASH holdings skip quote fetching
- Dashboard separates holdings into three categories
- Historical chart shows 4 lines: allocated, untracked, cash, total
- New holding type selector in add/edit forms

## Test Strategy
- Integration tests verify end-to-end flows
- RebalanceServiceTest covers drift calculations
- Database is used in tests (no mocks)
