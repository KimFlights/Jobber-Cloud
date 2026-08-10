# Jobber — Job-Search Helper

A small event-driven microservices system that scrapes jobs, compresses/enriches them, indexes
them for search, and matches them against your resume by embedding similarity. See
[ARCHITECTURE.md](ARCHITECTURE.md) for the design and the C4 diagrams — this README covers how to
run it and what the APIs are.

## Services

| Service | Port | Role | Stores |
|---|---|---|---|
| **Gateway** | 8080 | Frontend entry point; routes to Resume/Search, load-balanced over Consul; auth stub | — |
| **ResumeService** | 8081 | Upload/parse/embed resume; one LLM call for suggested queries; serve resume embedding | Postgres + pgvector |
| **ScraperService** | 8082 | Consume `scrape-request`; per-site scrape; freshness + dedupe; emit `scraped-job` | MongoDB |
| **JobCompressionService** | 8083 | Consume `scraped-job`; LLM structure/summarize + embed; emit `enriched-job` (stateless) | none |
| **SearchService** | 8084 | Search ES (stale-now); trigger scrapes; index `enriched-job`; on-demand cosine match; per-user state | Elasticsearch + Postgres |

**Pipeline:** `scrape-request` → ScraperService → `scraped-job` → JobCompressionService →
`enriched-job` → SearchService (Kafka topics).

**Edge & discovery:** all services register with **Consul**; the **Gateway** (Spring Cloud
Gateway Server WebMVC) fronts the two REST services and load-balances to them client-side via
`lb://`. Reach the system through the gateway at `http://localhost:8080` (routes below work with
the `/api/...` paths unchanged).

## Prerequisites

- JDK 25+ (built/tested on Temurin 26)
- Docker (for the backing stores)
- An OpenAI API key (embeddings + the two LLM calls)

## Run it locally

1. **Start infrastructure** (Postgres+pgvector, MongoDB, Elasticsearch, Kafka):

   ```bash
   docker compose up -d
   ```

2. **Export your OpenAI key** (ResumeService and JobCompressionService need it):

   ```bash
   export OPENAI_API_KEY=sk-...
   ```

3. **Run each service** (separate terminals), e.g.:

   ```bash
   cd ResumeService && ./mvnw spring-boot:run
   cd ScraperService && ./mvnw spring-boot:run
   cd JobCompressionService && ./mvnw spring-boot:run
   cd SearchService && ./mvnw spring-boot:run
   cd Gateway && ./mvnw spring-boot:run
   ```

   Each service registers with Consul on startup (`http://localhost:8500` shows the registry).
   To run **SearchService without Consul**, set `RESUME_CLIENT_LB=false` and
   `RESUME_SERVICE_URL=http://localhost:8081` so it calls ResumeService directly instead of via
   `lb://`.

   Every connection string and model name has an env-var override with a local default (see each
   `application.yaml`). The embedding model/dimension is pinned identically in ResumeService and
   JobCompressionService (`text-embedding-3-small` / 1536) — they **must** match or cosine scores
   are meaningless.

> **Auth is the deferred piece** (Cognito — ARCHITECTURE.md §5). The gateway's auth filter is a
> documented pass-through today; services trust an `X-User-Sub` header that the gateway will
> eventually populate from a validated Cognito JWT. Pass it yourself for now:
> `-H "X-User-Sub: user-123"`.

## API quick reference

**ResumeService**
- `POST /api/resumes` `{ "text": "..." }` — upload/replace resume (parse + embed + suggested queries)
- `GET /api/resumes/me` — your resume view (no vector)
- `GET /api/resumes/{sub}/embedding` — resume embedding (consumed by SearchService)

**SearchService**
- `GET /api/jobs?query=...` — search (stale-now); triggers a background scrape if thin/stale
- `POST /api/jobs/{jobId}/match` — cosine match → a single percentage (also stored as your rating)
- `POST /api/jobs/{jobId}/save` — save a job
- `PUT /api/jobs/{jobId}/applied?applied=true` — set applied status
- `GET /api/jobs/saved` — your saved jobs (rating + applied)

**ScraperService**
- `POST /api/scrape?query=...` — manual scrape trigger (same path as a `scrape-request` event)

All services expose `/actuator/health`.

## Scrapers & adding a site

ScraperService runs **one `SiteScraper` bean per site**, fanned out by the orchestrator. A demo
generator (`SampleSiteScraper`) is on by default so the whole pipeline runs without any live site.
A real **LinkedIn** scraper ships disabled (`SCRAPER_LINKEDIN_ENABLED=true`).

To add a site (Indeed, Handshake, …): extend `AbstractJsoupSiteScraper`, supply `siteName()`, the
search URL, and the CSS `SiteSelectors`, annotate `@Component @ConditionalOnProperty("scraper.<site>.enabled")`.
No orchestrator changes needed.

### A note on scraping legality

Scraping job boards touches each site's **Terms of Service and robots.txt**. LinkedIn, Indeed, and
Handshake all restrict automated access, gate listings behind auth/JS, and rate-limit aggressively.
The `SiteScraper` seam is deliberately "fetch jobs for a query," not "parse HTML," so a production
deployment can move any site to an official/partner **API** without touching the rest of the
system. Enable real scrapers only where you are authorized to; the default demo generator keeps the
pipeline fully exercisable without hitting anyone's site.

## Notes

- **Resilience:** Resilience4j circuit breakers protect the synchronous Search→Resume embedding
  fetch and outbound site scrapes; both degrade gracefully (empty result) rather than failing hard.
- **Freshness:** a query re-scrapes if it has `<10` results or its newest result is `>7 days` old
  (enforced on both the Search trigger side and the Scraper guard side).
- **Edge/discovery:** API gateway + Consul discovery + client-side LoadBalancer are wired.
- **Deferred:** only Cognito auth (the gateway auth filter is a pass-through stub until then —
  ARCHITECTURE.md §5).
