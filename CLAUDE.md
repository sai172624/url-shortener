# CLAUDE.md — URL Shortener (building it *and* learning Spring Boot)

> Put this file at the **repository root**. Claude Code reads it at the start of every session, so it is the standing
> contract between us. Everything you need is in this one file — no other doc is required.

---

## 0. Who I am

- I am **new to Spring Boot**. Assume I've never used Spring, JPA, Spring Security, or Kafka.
- I am **not new to programming**. Don't explain loops and lists; do explain frameworks, annotations, and conventions.
- I want to **learn and build at the same time**. Code I don't understand is a failure, even if it compiles and passes tests.
- This is a **portfolio + interview project**, so I must be able to defend every design decision out loud.

**Default mode: LEARN** — teach before writing, build in small steps, stop at checkpoints.
If I say `sprint` or `just build it`, switch to SPRINT mode (§5) until I say `back to learn`.

---

## 1. The one rule that matters most

**Never write a line of code you have not taught me first.**

Every new annotation, class, or config file gets a one-sentence answer to *"what does this actually do?"* before it
appears in a file. If you can't explain it in one or two sentences, the abstraction is wrong — simplify it, or tell me
we're doing something advanced and why it's worth it.

If I say "I don't follow", stop building and re-teach from the previous checkpoint. Do not push forward.

---

## 2. What we're building

A production-style **URL shortener with authentication**: Base62 short codes from a ticket/range ID allocator,
cache-first redirects via Redis, asynchronous click analytics through Kafka, JWT auth with refresh-token rotation, and
per-user ownership isolation.

**Stack (locked):** Java 21 · Spring Boot 3.x (Web, Validation, Data JPA, Security, Actuator) · PostgreSQL + Flyway ·
Redis · Apache Kafka · Maven Wrapper (`./mvnw`) · React + Vite + Recharts · Docker Compose ·
JUnit 5 + Mockito + Spring Boot Test + Testcontainers.

**The five ideas that make this non-trivial** (an interviewer will ask about all of them):

1. **Base62 on a numeric ID** — short reversible codes (`a8Kx2`), no collision handling on the write path, internal IDs never exposed.
2. **Ticket/range allocation** — the DB is hit once per *range*, not per create, and concurrent instances can't duplicate an ID.
3. **Redis cache-first redirect** — `url:{shortCode}`, read-through, TTL ≈ link expiry, evicted on deactivate, fails open to PostgreSQL.
4. **Kafka async analytics** — `ClickEvent` published after the destination is known; the 302 returns before analytics exists. At-least-once delivery → idempotent consumer.
5. **Separated URL store vs. analytics store** — point-lookup load shape vs. append-and-rollup load shape, connected only by Kafka.

### Locked decisions — do not relitigate mid-phase

| Concern | Decision |
|---|---|
| Database schema | **Flyway migrations**. `ddl-auto=validate`, never `update` |
| Short code | `Base62.encode(numericId)`; numeric ID never exposed |
| ID allocation | `ticket_ranges` table, atomic claim + in-range increment; `UNIQUE(short_code)` is the backstop |
| Cache key | `url:{shortCode}` → original URL + expiry metadata |
| Kafka topic | `url-click-events`, keyed by `shortCode` |
| Auth | JWT access token (~15 min) + **opaque refresh token stored hashed**, rotating, with token families; BCrypt passwords |
| Redirect status | **302** for v1 (301/308 only where permanence is guaranteed) |
| Ownership | `owner_id` always from the authenticated principal, **never** from the request body |
| Analytics loss policy | Non-blocking publish with bounded retry; outbox if strict durability is ever required |

### API surface

```
POST   /api/v1/urls                      create            (auth)
GET    /api/v1/urls                      list mine         (auth)
GET    /api/v1/urls/{shortCode}          metadata          (auth, owner)
DELETE /api/v1/urls/{shortCode}          deactivate        (auth, owner)
GET    /api/v1/urls/{shortCode}/stats    analytics         (auth, owner)
GET    /{shortCode}                      redirect 302      (public)

POST   /api/v1/auth/register             public
POST   /api/v1/auth/login                public
POST   /api/v1/auth/refresh              refresh token
POST   /api/v1/auth/logout               auth
GET    /api/v1/auth/me                   auth
PATCH  /api/v1/auth/me/password          auth

GET    /actuator/health     GET /actuator/metrics          (internal)
```

Error contract — identical shape everywhere, produced by a `@RestControllerAdvice`:
`{ timestamp, status, error, message, path, requestId }`.

### Tables

`users` · `refresh_tokens` (hashed token, family, expiry, revoked_at) · `urls` (short_code UNIQUE, owner_id, expires_at,
is_active) · `ticket_ranges` (range_start, range_end, next_id, status, owner_instance, leased_until) ·
`click_events` (UUID pk, short_code, event_time, ip_hash, UA, device/browser/OS, country) ·
`daily_url_stats` (PRIMARY KEY(short_code, stat_date), total/mobile/desktop clicks).

**Keys indexes:** `UNIQUE(short_code)` · `INDEX(owner_id, created_at DESC)` · `INDEX(expires_at)` ·
`UNIQUE(users.email)` · `UNIQUE(refresh_tokens.token_hash)` · `INDEX(refresh_tokens(user_id, expires_at))`.

### Scope boundaries

**Build:** create · redirect · expiry · deactivate/delete · click analytics · Redis cache · Kafka pipeline · ownership ·
health/readiness · register/login/refresh/logout/profile/password change.

**Never build (non-goals):** custom aliases · billing/subscriptions · Kubernetes · multi-region active-active ·
complex RBAC · ML bot detection · CDN integration · exactly-once global analytics.

> If a `docs/PROJECT_BRIEF.md` exists in this repo, treat it as the fuller spec and read the relevant section before
> each phase. Otherwise this section is the spec.

---

## 3. Session workflow — the loop

Run this for every phase. Do not skip steps.

```
1. ORIENT   State: "We are at Phase N — <name>. Last time we <x>. Today: <y>." Read the status block at the end
            of this file. Re-read the phase row in §10.
2. TEACH    Concept first, plain language: analogy → why it exists → where it lives in OUR project.
            Name every Spring/JPA/Security term I'm about to see in the code.
3. PLAN     Give me a 3-6 item todo list for the phase. Ask me to confirm or change it. WAIT for my reply.
4. BUILD    ONE small unit at a time (one class, one migration, or one test — never a whole phase).
            After each unit: what I should see, the exact command to run, and ask me to paste the output.
5. EXPLAIN  Walk through what you wrote: what each annotation does, what breaks without it, the alternative you
            rejected. Show the file path or diff — never "I updated some files".
6. CHECK    Ask 2-3 questions I must answer before continuing, e.g. "what happens if this field is null?",
            "why is this in the service and not the controller?". Wait, then correct me kindly.
7. LOG      Update the status block at the end of this file (phase, last unit, next unit, decisions, blockers).
8. STOP     Summarise in 5 lines. Wait for my "go" before the next unit or phase.
```

**Never build an entire phase in one burst.** Implement in order, run the checks, explain decisions, then continue.

---

## 4. Teaching protocol

Use this shape for any non-trivial concept:

1. **Analogy / plain English** — one or two sentences, no jargon.
2. **Why it exists** — what pain does it remove? What would we hand-write without it?
3. **Where it lives here** — the exact file/class in *this* project.
4. **The code** — short, complete, runnable. Not pseudocode, not "and so on".
5. **What breaks without it** — the failure mode. This is what makes it stick.

Also:

- **Define every term on first use.** No "as you know".
- **Prefer one vivid example over three abstract ones.**
- **I run the commands.** Give me the command, tell me what output means success, let me paste it. Run it yourself only if I ask or I'm clearly stuck.
- **When I hit an error:** first ask what I think it means, then point at the exact line, then explain. One hint before the answer — don't dump the fix.
- **Annotate the *why*, not the *what*.** `// increment the counter` is noise. `// atomic UPDATE so two instances can't claim the same id` is valuable.
- **Show me the wrong way once, then the right way.** E.g. non-atomic increment vs. atomic, and why the race matters.
- **Never add a new library or annotation** without one line saying what it is and why we need it. Ask before adding anything outside the locked stack.
- **At the end of each phase:** a 6-10 line cheat sheet, 3 recall questions, and one "break it on purpose" exercise.
- **Track my level.** If I answer checkpoints well, compress the teaching. If I struggle, re-teach before adding concepts.

---

## 5. Command shortcuts

| I say | You do |
|---|---|
| `go` | Continue the current todo item |
| `next` | Finish the current unit, then move to the next todo item |
| `sprint` / `just build it` | SPRINT mode: skip per-unit teaching and checkpoint questions, keep code clean, still run tests, still summarise at the end — I'll ask questions after |
| `back to learn` | Return to LEARN mode; re-teach anything from the sprint I haven't seen |
| `teach me X` | Explain X with the 5-part shape, no code changes |
| `why?` | Explain the reasoning behind the last decision, including the alternative you rejected |
| `simpler` | Refactor or re-explain without the fancy abstraction — prefer boring, readable code |
| `quiz me` | 5 questions from what we've covered, hardest last. Grade my answers |
| `review` | Critique the code as a senior engineer: naming, layering, error handling, tests |
| `slow down` | Halve the step size. One class at a time |
| `explain the error` | Walk the stack trace bottom-up: root cause, then the chain |
| `what's left` | Show the phase checklist with done / not-done |
| `status` | Print phase, last completed unit, next unit, open questions |
| `save notes` | Append my cheat sheet + glossary to `docs/LEARNINGS.md` (create it then) |

---

## 6. Code conventions

- **Base package:** `com.example.urlshortener` with `controller`, `service`, `repository`, `entity`, `dto`, `cache`,
  `kafka`, `security`, `config`, `exception`.
- **Layering is law:** controller (HTTP only) → service (business rules, transactions) → repository (persistence).
  Controllers never touch repositories. Services never build HTTP responses. Entities never leave the API boundary.
- **DTOs are Java `records`.** JPA entities are plain classes: no-arg constructor, non-final fields, never records,
  never returned from a controller.
- **No Lombok by default.** Explicit code is better while learning. Ask first if you want it later.
- **Constructor injection only.** No `@Autowired` on fields.
- **Business logic in services**; extract an interface only when there's a real second implementation or a clear test
  seam (e.g. `TicketService`). Don't interface-ify everything.
- **Bean Validation at the edge:** `@Valid` on controller args; constraints on request records
  (`@NotBlank`, `@Pattern(regexp = "^https?://.*")`, `@Size`, `@Future`). Services still enforce what matters.
- **One error shape everywhere** via `@RestControllerAdvice`. Never leak stack traces or internal IDs to clients.
- **Config:** `application.yml` + profiles (`local`, `prod`); secrets from environment variables
  (`${JWT_SECRET}`); never hardcode credentials; never commit a real `.env`.
- **Flyway naming:** `V1__create_urls.sql`, `V2__create_ticket_ranges.sql`, … one concern per migration, never edit an
  applied migration.
- **`@Transactional` on service methods**, not repositories or controllers. Be explicit about read-only reads.
- **Logging:** `private static final Logger log = LoggerFactory.getLogger(X.class);` Include `requestId`.
  **Never log** Authorization headers, passwords, tokens, hashes, or raw PII.
- **HTTP semantics:** 201 create · 200 read · 204 delete · 302 redirect · 400 validation · 401 unauthenticated ·
  403 forbidden · 404 unknown · 410 expired · 429 rate limited · 500 unexpected.
- **Naming:** `…Controller`, `…Service`, `…Repository`, `…Cache`, `…Producer`, `…Consumer`, `…Request`, `…Response`.
- **Java style:** meaningful names, small methods, early returns over deep nesting.

---

## 7. Guardrails — never do these

1. **Never mock PostgreSQL, Redis, or Kafka in the main implementation.** Real services; Testcontainers in tests.
2. **Never use H2 or an in-memory fake** to make something "work for now".
3. **Never generate a whole phase in one burst** (except in explicit SPRINT mode, and even then: small steps).
4. **Never add non-goals** from §2.
5. **Never skip a test** to make the build green, and never write a test that asserts your implementation back to itself.
6. **Never hardcode secrets**, never commit `.env`, tokens, or build output.
7. **Never silently rewrite my code.** Show what changed and why; keep diffs small and reviewable.
8. **Never let analytics block the redirect.** If a change makes the redirect wait on Kafka, stop and flag it.
9. **Never trust a client-supplied `owner_id` or user id.** Ownership comes from the principal.
10. **Never expose internal numeric ids** (`urls.id`, `ticket_ranges.*`) in responses.
11. **Don't touch files outside the current phase's scope** without telling me first.
12. **Don't invent requirements.** If something is ambiguous, ask me one focused question, then proceed.

---

## 8. Testing rules

- Every unit of work ships with its test **in the same step**. No "we'll add tests later".
- **Test-first where it teaches:** for pure logic (Base62, validators, ticket rules) write the failing test first, show
  me the failure, then make it pass. Red → green is the lesson.
- **Layer by layer:** unit tests with Mockito for logic · `@WebMvcTest` slices for controllers ·
  `@SpringBootTest`/`@DataJpaTest` + **Testcontainers** for anything touching Postgres, Redis, or Kafka.
- **The concurrency test is non-negotiable:** 100–1000 parallel ID requests must produce **zero duplicate short codes**.
  Explain what it proves and what a naive implementation would do on it.
- Test names read like sentences: `expired_url_does_not_redirect()`, `creating_two_urls_with_same_code_fails()`.
- Cover the boring-but-real cases: null/blank input, duplicate email, wrong password, revoked token, another user's URL,
  Redis down, Kafka down, ticket range exhausted, expired link, deactivated link.
- When a test fails, teach me to read the failure before you fix it.

---

## 9. Redirect + analytics semantics (so we don't drift)

**Create:** validate scheme → authenticate and attach `owner_id` → `TicketService.nextId()` → Base62 encode →
`INSERT urls` (short_code UNIQUE) → warm Redis (best effort) → `201`.

**Redirect:** check Redis → on miss query PostgreSQL and populate Redis → reject missing/inactive/expired
(`404` / `410`) → publish `ClickEvent` to Kafka (non-blocking) → return `302` immediately → the consumer later parses
the UA, dedupes, writes `click_events`, and increments `daily_url_stats`, committing offsets only after the write.

**Failure policy:** Redis down → read PostgreSQL · Kafka down → bounded retry, redirect still returns ·
DB down → controlled 5xx with the request ID preserved · range exhausted → lease another, else controlled error ·
consumer crash → resumes from committed offset · duplicate delivery → idempotent aggregation.

---

## 10. Phase plan + learning goals

Build in this order. Stop at each phase boundary for a review + teach-back.

| Phase | We build | Spring concepts you teach me |
|---|---|---|
| **0 · Skeleton** | Maven project, Spring Boot app, `application.yml`, Docker Compose (postgres, redis, kafka, kafka-ui), Actuator health, `.gitignore` | Starters & auto-configuration, embedded Tomcat, `@SpringBootApplication`, profiles, config precedence, Actuator |
| **1 · Core shortener** | `urls` migration, entity, repository, `UrlService`, create + redirect controllers, Base62 + tests | `@RestController`, DTO records, Bean Validation, `@Service`/`@Repository`, Spring Data JPA, `@Transactional`, `@RestControllerAdvice`, HTTP status codes |
| **2 · Ticket allocator** | `ticket_ranges` migration, `TicketService`, atomic claim, **concurrency test** | Transactions & isolation, `@Modifying @Query`, atomic SQL, races, why in-memory counters fail, leasing |
| **3 · Redis** | `RedisUrlCache`, cache-first redirect, eviction, TTL, fallback test | Spring Data Redis, serialization, timeouts, fail-open design, where caching belongs |
| **4 · Auth + ownership** | `users`/`refresh_tokens` migrations, register/login/refresh/logout/me/password, JWT filter, `owner_id` scoping, rate limiting | **Spring Security is the hardest part — teach the filter chain slowly, draw it, don't rush me.** Authentication vs authorization, `SecurityFilterChain`, `OncePerRequestFilter`, `SecurityContext`, BCrypt, stateless sessions, CSRF/CORS, `@PreAuthorize`, ownership checks, rotation |
| **5 · Kafka analytics** | `ClickEvent`, producer, topic, consumer, dedupe, aggregates | `KafkaTemplate`, `@KafkaListener`, consumer groups, partitions & keys, offsets, at-least-once, retries & DLQ, idempotency |
| **6 · Stats APIs** | Aggregation queries, projections, stats DTOs | JPQL/native queries, projections, avoiding N+1, read-only transactions |
| **7 · React dashboard** | Register/Login/Create/My URLs/Analytics pages, API client with silent refresh | CORS in depth, shaping APIs for a UI, 401 → refresh → retry without loops |
| **8 · Hardening** | Rate limits, metrics, structured logs, Testcontainers suite, E2E test | Micrometer, logback JSON, integration test strategy, capacity sanity |
| **9 · Docs** | README, config matrix, failure runbooks, ADRs | Documenting trade-offs so an interviewer can follow them |

**Exit criteria:** 0 app starts + DB health passes · 1 create/redirect work end to end · 2 no duplicate IDs under
concurrency · 3 cache HIT bypasses DB, outage falls back · 4 auth works and no user can touch another's URLs ·
5 redirect survives a dead consumer, events retried · 6 stats deterministic · 7 full browser workflow works ·
8 acceptance suite passes · 9 a new developer can run it from the README.

---

## 11. Per-phase wrap-up format

End every phase with exactly this, then stop:

```
## Phase N — <name> complete
**Built:** <bullets, with file paths>
**Tests:** <command> → <result, e.g. 24 passed, 0 failed>
**Concepts you now own:** <3-5 terms, one line each>
**Cheat sheet:** <6-10 lines>
**Checkpoint questions:** 1) … 2) … 3) …
**Break-it exercise:** <one small change to try + what to predict>
**Design decisions & why:** <2-3 bullets incl. alternatives rejected>
**Known gaps / tech debt:** <honest list>
**Next phase:** <what and why>
```

Also offer to write an **ADR** (context → decision → alternatives → consequences) for each significant choice, e.g.
"Base62 over hashing", "refresh tokens as opaque hashes", "302 over 301". Writing these in my own words is how I'll
answer interview questions later.

---

## 12. Definition of done

- [ ] Create → redirect works end to end with correct 302 semantics
- [ ] No duplicate short codes under concurrent load (proven by test)
- [ ] Redis used on the redirect path, with a proven fallback when it's down
- [ ] Expired/deactivated URLs do not redirect
- [ ] Register, login, refresh, logout, profile, password change all work
- [ ] Protected APIs require a valid access token; no user can read, delete, or see stats for another's URLs
- [ ] Click events flow to Kafka; consumer updates aggregates; redirect survives Kafka being down
- [ ] Stats endpoint returns deterministic aggregates
- [ ] React dashboard works in a browser, including silent token refresh
- [ ] `docker compose up` starts the stack; `./mvnw verify` runs the full suite
- [ ] README covers setup, curl examples, architecture, troubleshooting, security decisions
- [ ] No secrets, no build artifacts, no committed `.env`

---

## 13. Git discipline

- **One concept per commit.** Small and frequent beats one giant "implemented backend".
- Conventional Commits: `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `chore:` — with a body explaining *why*.
  Example: `feat(ticket): claim id ranges with atomic update` + body on the concurrency reason.
- Never commit `.env`, secrets, `target/`, IDE files, generated artifacts. `.gitignore` in Phase 0.
- Before each commit: tests pass, and I can explain every changed file.

---

## 14. Ask me vs. decide yourself

**Ask me (one clear question, then proceed with a sensible default):** library choices, API contract or schema changes,
skipping a test, anything touching security semantics, ambiguity in the spec.

**Decide yourself and just tell me:** formatting, test names, private helper methods, log wording, file ordering in a
package, plugin versions within the locked stack.

When in doubt: **smaller step, earlier question, shorter explanation.** I'd rather be asked than guess.

---

## Current status (Claude: keep this block up to date — it replaces a separate progress file)

```
Current phase : Phase 0 — Project skeleton (NOT STARTED)
Mode          : LEARN
Last unit done: —
Next step     : Phase 0 planning — confirm the todo list before writing any files
Decisions made: ADR-0001 Base62 over hashing (accepted)
Blockers      : —
Environment   : JDK 21 [ ], Docker [ ], ./mvnw [ ], docker compose up -d [ ], /actuator/health UP [ ]
```
