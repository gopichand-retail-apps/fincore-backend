# FinCore Backend

Spring Boot API for the FinCore synthetic banking simulator. This backend is a standalone repository from `fincore-frontend`.

## Run locally

Requirements: Java 17 and Maven.

```sh
mvn verify
sh scripts/check-source-lines.sh
mvn spring-boot:run
```

The local profile uses an H2 file under `.local/` and listens on `127.0.0.1:5502`. The frontend runs at `127.0.0.1:5501`; mock mode uses its Vite proxy by default. When the frontend is configured with an explicit API origin, the browser connects directly and this service must allow the exact frontend origin through `FINCORE_FRONTEND_ORIGINS`. Do not bind to a public interface for this demo.

The Postgres profile is optional and requires a separately managed PostgreSQL instance:

```sh
SPRING_PROFILES_ACTIVE=postgres \
FINCORE_FRONTEND_ORIGINS=https://your-exact-frontend-origin.example \
FINCORE_DB_URL=jdbc:postgresql://127.0.0.1:5432/fincore \
FINCORE_DB_USERNAME=fincore_app \
FINCORE_DB_PASSWORD=replace-locally \
FINCORE_FRONTEND_ORIGINS=https://your-ui.example \
mvn spring-boot:run
```

Set secrets directly in the local shell or a secret manager; do not commit them. The Postgres profile requires an explicit frontend origin, enables secure session cookies, and expects HTTPS at the deployment boundary. The local profile allows only the two loopback aliases on port 5501.

## Demo profiles

- `customer-avery` and `customer-riley`: isolated synthetic customers; only customers may create internal transfers or manage beneficiaries.
- `operations`: read-only view across synthetic records.
- `auditor`: read-only audit view across synthetic records.

Demo login exists only in the `local` and `test` Spring profiles. This is a role-selection simulator, not identity verification.

## API outline

- `GET /health`, `GET /actuator/health`
- `GET /api/v1/auth/csrf`, `GET /api/v1/auth/demo-profiles`, `POST /api/v1/auth/demo-login`, `GET /api/v1/auth/me`, `POST /api/v1/auth/logout`
- `GET /api/v1/overview`, `GET /api/v1/accounts`, `GET /api/v1/accounts/{accountId}/transactions`
- `GET /api/v1/transfers`, `POST /api/v1/transfers`
- `GET/POST/DELETE /api/v1/beneficiaries`
- `GET /api/v1/statements`, `GET /api/v1/insights`

`GET /api/v1/auth/demo-profiles` is available only in the local and test profiles. It lists
synthetic demo keys and server-assigned roles from the seed database. Demo login resolves the key
against that database and rejects unknown profiles; clients cannot submit a trusted user ID or
role. Sessions remain server-managed. The OpenAPI contract is in [docs/openapi.yaml](docs/openapi.yaml).

Transfer commands use integer minor units, an `Idempotency-Key`, owner checks, account locks, and one database transaction for both ledger entries. HTTP logs contain route/status/timing and correlation IDs; request and response bodies are not logged.

## Quality status

The `Quality` GitHub Actions workflow runs `mvn -B -ntp -Dspring.profiles.active=test verify` and `sh scripts/check-source-lines.sh` on pushes and pull requests. `mvn verify` uses in-memory H2 and writes an HTML/XML JaCoCo report under `target/site/jacoco/`. The build enforces 90% bundle coverage for instructions (JaCoCo's statement-level bytecode proxy), branches, lines, and methods, plus 95% per-class coverage across those categories for account, transfer, beneficiary, demo-auth, and security services/controllers. No application classes are excluded.

The latest local run passed all 36 tests with 99.07% instruction, 96.72% branch, 98.94% line, and 98.09% method coverage. All configured critical-class gates pass. The source-line check enforces the 120-column Java policy. Checkstyle and Sonar are not configured. Do not treat passing tests or coverage as a security certification.

## Limitations

This project uses synthetic balances and internal ledger entries only. It has no bank connectivity, real money movement, OIDC provider, production account recovery, or externally reviewed compliance program. A passing test suite is not a security certification. Review [SECURITY.md](SECURITY.md) before extending it.
