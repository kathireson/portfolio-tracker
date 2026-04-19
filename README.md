# Portfolio Tracker

A Spring Boot homelab server that tracks your investment portfolio, fetches daily quotes,
and shows a dashboard with current allocations and rebalancing drift.

## Features

- Dashboard showing total portfolio value, per-holding allocations, and drift vs target
- Automatic quote fetch at 9:35 AM ET on trading days (configurable)
- Manual "Refresh Quotes" button for on-demand updates
- Add / edit / remove holdings via the UI
- H2 file-based database — data persists across restarts
- Pluggable quote providers: Yahoo Finance (default, no key needed) or Alpha Vantage

## Requirements

- Java 21+
- Maven 3.8+

## Quick start

```bash
# Clone / download the project, then:
./mvnw spring-boot:run
```

Open http://localhost:8080 — three example holdings (VOO, VXUS, BND) are pre-loaded.
Click **Refresh Quotes** to fetch live prices immediately.

## Configuration

All settings live in `src/main/resources/application.properties`.

| Property | Default | Description |
|---|---|---|
| `portfolio.quote-provider` | `YAHOO` | `YAHOO` or `ALPHA_VANTAGE` |
| `portfolio.alpha-vantage.api-key` | — | Required if using Alpha Vantage |
| `portfolio.quote-fetch-cron` | `0 35 9 * * MON-FRI` | Cron for scheduled refresh |
| `portfolio.scheduler-timezone` | `America/New_York` | Timezone for the scheduler |
| `server.port` | `8080` | HTTP port |

### Switching to Alpha Vantage

1. Get a free key at https://www.alphavantage.co/support/#api-key
2. Set in `application.properties`:
   ```
   portfolio.quote-provider=ALPHA_VANTAGE
   portfolio.alpha-vantage.api-key=YOUR_KEY_HERE
   ```

Alpha Vantage free tier allows 25 requests/day — fine for small portfolios.

## Database

The H2 database is stored in `./data/portfoliodb.mv.db`.
The H2 console is available at http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:file:./data/portfoliodb`).

To switch to PostgreSQL:
1. Add the PostgreSQL driver to `pom.xml`
2. Replace the `spring.datasource.*` properties
3. Change `spring.jpa.database-platform` to `org.hibernate.dialect.PostgreSQLDialect`

## Building a fat JAR

```bash
./mvnw clean package
java -jar target/portfolio-tracker-0.0.1-SNAPSHOT.jar
```

## Running as a systemd service

Create `/etc/systemd/system/portfolio-tracker.service`:

```ini
[Unit]
Description=Portfolio Tracker
After=network.target

[Service]
Type=simple
User=youruser
WorkingDirectory=/opt/portfolio-tracker
ExecStart=/usr/bin/java -jar /opt/portfolio-tracker/portfolio-tracker.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now portfolio-tracker
```

## Project structure

```
src/main/java/com/homelab/portfolio/
├── PortfolioTrackerApplication.java   Entry point
├── config/
│   ├── DataInitializer.java           Seeds example holdings on first run
│   └── WebClientConfig.java           WebClient bean
├── controller/
│   └── DashboardController.java       MVC + REST endpoints
├── dto/
│   ├── DashboardViewModel.java        Dashboard view model
│   └── HoldingRow.java                Per-holding row with drift data
├── model/
│   ├── Holding.java                   JPA entity: ticker, shares, target %
│   └── DailySnapshot.java             JPA entity: daily price record
├── repository/
│   ├── HoldingRepository.java
│   └── DailySnapshotRepository.java
├── scheduler/
│   └── QuoteRefreshScheduler.java     @Scheduled job
└── service/
    ├── QuoteProvider.java             Interface for quote fetching
    ├── YahooFinanceQuoteProvider.java Default implementation
    ├── AlphaVantageQuoteProvider.java Alternative implementation
    ├── QuoteService.java              Orchestrates fetch + persist
    └── RebalanceService.java          Drift calculation + dashboard model

src/main/resources/
├── application.properties
├── static/css/dashboard.css
├── static/js/dashboard.js
└── templates/dashboard.html
```
