# URL Cutter

URL Cutter is a small URL-shortening service built with Java 21, Spring Boot, PostgreSQL, and Redis. It provides a browser form for creating short links, a REST endpoint, and a redirect endpoint.

## Requirements

- Java 21
- Docker Desktop with Docker Compose
- Visual Studio Code with the Java extensions (optional, if using the included launch configuration)

## Start the project

### 1. Configure the local environment

The project reads settings from an optional `.env` file in the project root. Keep this file private; it is ignored by Git. Set the PostgreSQL password there, and use the same password for the database container and the application:

```properties
URL_CUTTER_DB_URL=jdbc:postgresql://localhost:5433/url_cutter_db
URL_CUTTER_DB_USERNAME=tristan
URL_CUTTER_DB_PASSWORD=replace-with-a-local-password
REDIS_HOST=localhost
REDIS_PORT=6379
PUBLIC_BASE_URL=http://localhost:8080
JPA_SHOW_SQL=false
```

The Compose file uses `URL_CUTTER_DB_PASSWORD` when initializing PostgreSQL. The default in `docker-compose.yml` is only a development fallback. If the `pgdata` volume has already been initialized, changing the password in `.env` does not change the existing PostgreSQL user's password.

### 2. Start PostgreSQL and Redis

From the project directory, run:

```powershell
docker compose up -d
docker compose ps
```

PostgreSQL is available on `localhost:5433`; Redis is available on `localhost:6379`.

### 3. Start the Spring Boot application

In VS Code, open **Run and Debug** and start **Launch URL Cutter** with **F5**. The project targets Java 21. Wait until Spring Boot reports that it started before opening the site.

### 4. Open the site

Visit [http://localhost:8080](http://localhost:8080). Enter a destination URL, optionally enter a custom alias and expiration in days, then select **Shorten URL**. Open the generated short URL to test the redirect.

## Use the REST API

Create a link with PowerShell:

```powershell
$body = @{
  longUrl = "https://example.com/articles/a-long-page"
  customAlias = $null
  ttlDays = $null
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://localhost:8080/api/urls" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body
```

The response includes `shortCode` and `shortUrl`. To use a custom alias or expiration, replace `$null` with a value, for example `customAlias = "example"` or `ttlDays = 30`.

The API is `POST /api/urls`. `longUrl` is required and must be an HTTP or HTTPS URL. `customAlias` is optional and accepts 1–20 letters, digits, hyphens, or underscores. `ttlDays` is optional and must be between 1 and 36,500. A successful request returns HTTP `201 Created`.

Opening `GET /{shortCode}` redirects to the destination with HTTP `302 Found`. Unknown or expired links return HTTP `404 Not Found`.

## View saved links and usage

PostgreSQL is the durable store. Connect to it from a database GUI or the VS Code PostgreSQL extension with:

| Setting | Value |
| --- | --- |
| Host | `localhost` |
| Port | `5433` |
| Database | `url_cutter_db` |
| User | `tristan` |
| Password | The value of `URL_CUTTER_DB_PASSWORD` in `.env` |

The application table is `public.url_mappings`. To list saved links and click counts, run:

```sql
SELECT short_code, long_url, created_at, expires_at, click_count
FROM url_mappings
ORDER BY created_at DESC;
```

You can also open a PostgreSQL shell in Docker:

```powershell
docker exec -it url_cutter_db psql -U tristan -d url_cutter_db
```

Redis contains cached URL values under keys like `url:<shortCode>`. It is a cache, so it may contain fewer links than PostgreSQL. Inspect it with:

```powershell
docker exec -it url_cutter_redis redis-cli
```

Then, in the Redis prompt:

```redis
SCAN 0 MATCH url:* COUNT 100
GET url:<shortCode>
TTL url:<shortCode>
```

## System design

```text
Browser
  |  GET /
  v
Spring Boot app (one local instance; serves static/index.html)
  |
  +-- POST /api/urls --> UrlController -> UrlService
  |                                      | validate URL, alias, expiration
  |                                      | custom alias: check uniqueness
  |                                      | generated code: Redis INCR range -> Base62
  |                                      | save mapping -----------------> PostgreSQL
  |                                      | cache URL (up to 24 hours) --> Redis
  |                                      +-- HTTP 201 with short URL --> Browser
  |
  +-- GET /{shortCode} --> UrlController -> UrlService
                          |                  |
                          |                  +-- lookup -------------> Redis URL cache
                          |                  |                         |
                          |                  | cache miss              v
                          |                  +--------------------> PostgreSQL fallback
                          |                  | active mapping: cache in Redis
                          |                  +-- HTTP 302 Location: destination URL --> Browser
                          +-- best-effort async click_count update -> PostgreSQL

PostgreSQL: one database instance; durable mappings and click counts (no sharding/replicas)
Redis: one instance; URL cache and ID counter (no Redis cluster)
Click tracking: direct asynchronous database update (no message queue or analytics worker)
```

### Link creation

1. The browser submits the destination URL and optional settings to `POST /api/urls`.
2. `UrlController` validates the request shape. `UrlService` verifies that the destination is a valid HTTP or HTTPS URL and checks the alias and expiration settings.
3. If no custom alias is supplied, `IdGeneratorService` reserves IDs from a Redis counter in ranges of 1,000. The numeric ID is encoded as Base62 to make the short code.
4. The mapping is saved in PostgreSQL. The service also writes the destination into Redis, with a cache lifetime of at most 24 hours and no later than the link's expiration.
5. The API returns the short code and full short URL.

### Redirect

1. A request to `/{shortCode}` checks Redis first.
2. On a cache miss, the service reads PostgreSQL, checks expiration, and repopulates Redis for an active mapping.
3. The controller returns HTTP `302 Found` with the destination in the `Location` header.
4. Click counting is a best-effort asynchronous database update; a click-count scheduling failure does not block the redirect.

### Data and persistence

- PostgreSQL stores the durable URL mappings and click counts in `url_mappings`.
- Hibernate uses `spring.jpa.hibernate.ddl-auto=update` to create or update the table from the `UrlMapping` entity when the app starts. This is convenient for local development; use managed schema migrations before production.
- Docker Compose stores PostgreSQL data in the named volume `pgdata`. Stopping or removing the containers with `docker compose down` keeps this volume. **Do not use `docker compose down -v` unless you intend to delete the database volume and its data.**
- Redis stores temporary URL cache entries and the ID allocation counter. The Compose configuration does not persist Redis data to a volume. PostgreSQL remains the source of truth for links, but Redis is currently required to allocate new generated IDs.

## Stop the services

Stop and remove the Compose containers while retaining PostgreSQL data:

```powershell
docker compose down
```

Start them again later with `docker compose up -d`. Avoid `docker compose down -v` if you want to keep the PostgreSQL data.

## Configuration reference

| Variable | Default | Purpose |
| --- | --- | --- |
| `URL_CUTTER_DB_URL` | `jdbc:postgresql://localhost:5433/url_cutter_db` | JDBC connection URL |
| `URL_CUTTER_DB_USERNAME` | `tristan` | PostgreSQL username |
| `URL_CUTTER_DB_PASSWORD` | `raypassword` | Local development fallback; set a private value in `.env` |
| `REDIS_HOST` | `localhost` | Redis host as seen by the Java app |
| `REDIS_PORT` | `6379` | Redis port as seen by the Java app |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | Base address returned in generated short URLs |
| `JPA_SHOW_SQL` | `false` | Whether Hibernate prints SQL statements |

When the Spring Boot app itself runs in Docker in the future, `localhost` will refer to that app container; use the Compose service names (`postgres` and `redis`) as the hosts instead.
