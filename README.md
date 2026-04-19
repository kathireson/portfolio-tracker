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

## Screenshots of UI
<img width="1284" height="787" alt="image" src="https://github.com/user-attachments/assets/8aaf56e9-2879-4eb5-a2ef-dabdabe7481d" />
<img width="1280" height="646" alt="image" src="https://github.com/user-attachments/assets/d95c991d-5572-4edb-b032-85feccd2a061" />


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

Application settings are split into two files:

| File | Committed to Git? | Contains |
|---|---|---|
| `src/main/resources/application.properties` | ✅ Yes | Non-sensitive defaults (port, scheduler, DB, quote provider) |
| `application-secret.properties` | ❌ No (gitignored) | Credentials, API keys, environment-specific URIs |

### Initial Setup

1. Copy the template to create your secrets file:
   ```bash
   cp application-secret.properties.example application-secret.properties
   ```

2. Edit `application-secret.properties` and fill in your values:
   ```properties
   # Keycloak OAuth2
   KEYCLOAK_ISSUER_URI=https://<KEYCLOAK_HOST>:8180/realms/<YOUR_REALM>
   KEYCLOAK_CLIENT_SECRET=<your-client-secret>
   OAUTH_REDIRECT_URI=http://localhost:8080

   # Alpha Vantage (only needed if portfolio.quote-provider=ALPHA_VANTAGE)
   ALPHA_VANTAGE_API_KEY=<your-api-key>
   ```

### Secret Properties Reference

| Property | Required | Description |
|---|---|---|
| `KEYCLOAK_ISSUER_URI` | When OAuth enabled | Your Keycloak realm URL, e.g. `https://192.168.1.100:8180/realms/my-realm` |
| `KEYCLOAK_CLIENT_SECRET` | When OAuth enabled | Client secret from Keycloak (Clients → your-client → Credentials) |
| `OAUTH_REDIRECT_URI` | No | OAuth redirect URI. Defaults to `http://localhost:8080` |
| `ALPHA_VANTAGE_API_KEY` | No | Only needed if `portfolio.quote-provider=ALPHA_VANTAGE` |

### Application Properties Reference

These are in `application.properties` and generally don't need changing:

| Property | Default | Description |
|---|---|---|
| `portfolio.oauth.enabled` | `true` | Set to `false` to disable OAuth (allow all requests without login) |
| `portfolio.quote-provider` | `YAHOO` | `YAHOO` or `ALPHA_VANTAGE` |
| `portfolio.quote-fetch-cron` | `0 35 9 * * MON-FRI` | Cron for scheduled refresh |
| `portfolio.scheduler-timezone` | `America/New_York` | Timezone for the scheduler |
| `server.port` | `8080` | HTTP port |

### Switching to Alpha Vantage

1. Get a free key at https://www.alphavantage.co/support/#api-key
2. Set in `application.properties`:
   ```properties
   portfolio.quote-provider=ALPHA_VANTAGE
   ```
3. Set in `application-secret.properties`:
   ```properties
   ALPHA_VANTAGE_API_KEY=your_actual_key
   ```

Alpha Vantage free tier allows 25 requests/day — fine for small portfolios.

### Keycloak / OAuth2 Setup

This app uses Keycloak for authentication via OAuth2/OpenID Connect.

1. Create a realm and a client in your Keycloak instance
2. Set the client's **Access Type** to `confidential`
3. Add `http://localhost:8080/*` (or your server URL) as a **Valid Redirect URI**
4. Copy the client secret from the **Credentials** tab into `application-secret.properties`

To **disable OAuth** (e.g. for local development without Keycloak), set in `application.properties`:
```properties
portfolio.oauth.enabled=false
```
No secrets file is needed when OAuth is disabled.

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

> **Note:** Place your `application-secret.properties` file in the `WorkingDirectory`
> (e.g. `/opt/portfolio-tracker/application-secret.properties`) so Spring Boot picks it up at runtime.

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
