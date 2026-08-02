# Archery Android Client — Design

**Date:** 2026-08-02
**Status:** Draft for review
**Scope:** Native Android client for the archery score tracker — phase 2 of the project. Offline-first live scoring, session history, session detail, and the four analysis views, talking to the same deployed Zoho Catalyst backend the web client uses.
**Platform:** Android, Kotlin + Jetpack Compose, plain Material 3 (no internal component library).

---

## 1. Purpose

Phase 1 (web + backend) is complete and deployed. It answers the same four questions this whole project exists for — am I improving, are groups tightening, where do I lose points, does practice hold up under pressure — but its one structural gap is that score entry at the range requires a live connection and a browser tab, or transcription from paper after the fact.

Android's reason to exist is the one thing the web client structurally cannot do: **live, offline, per-tap-durable scoring at the range**, syncing to the same backend the moment a connection exists.

---

## 2. Relationship to phase 1

This client talks to the same deployed backend (`ArcheryBackend`) over the same seven HTTP endpoints the web client uses, with the same JSON shapes. It does not reimplement statistics (rolling averages, gap, consistency, patterns) — that stays centralized server-side, per the phase-1 design's own principle: centralize what's complex and driftable, duplicate what's trivial and must work offline.

**What Android duplicates**: scoring arithmetic and validation (end/round totals, X counting, the 6-arrows-per-end / 36-per-round / round-count-per-session-type rules) — proven against the same language-neutral `scoring-conformance.json` fixture the TypeScript side already passes, so both implementations are provably consistent rather than assumed consistent.

**What Android does not duplicate**: the four analysis views' computation. `GET /stats` is called live, matching the web client exactly; there is no offline analysis cache.

**Auth**: none. The backend's authentication was deliberately removed during phase 1 (single-user personal app, no login flow was ever wired up, and Zoho auth would have added real complexity — a mobile OAuth flow — for a benefit that only matters with multiple users). Every request is a plain, unauthenticated HTTPS call to the deployed function URL.

---

## 3. Architecture

Single Gradle module (`app`), organized by package — not split into separate Gradle modules. This is a solo project with one screen flow; module boundaries would add build-graph overhead with no real payoff at this size.

```
app/
├── core/                  pure Kotlin — no Android dependencies
│   ├── types            Arrow, Round, Session, enums
│   ├── scoring           endTotals, roundTotal, runningTotals, xCount, isRoundComplete
│   └── validation        arrow/round/session validation rules
├── data/
│   ├── local/            Room: entities, DAOs, database, TypeConverters
│   ├── remote/           Retrofit API interface + DTOs (mirror backend JSON exactly)
│   └── repository/       merges local + remote; owns dirty-flag bookkeeping
├── sync/                  WorkManager worker: batch-upload dirty rows via POST /sync
└── ui/                    Compose screens + plain ViewModels, one package per screen
    ├── livescoring
    ├── history
    ├── sessiondetail
    └── analysis
```

### 3.1 Networking

Retrofit + kotlinx.serialization (via `retrofit2-kotlinx-serialization-converter`), not Moshi or Gson — kotlinx.serialization avoids reflection-based (de)serialization, integrates cleanly with Kotlin data classes with no annotation-processing step of its own, and is the more modern default for a Kotlin-first codebase. DTOs mirror the backend's JSON field names exactly (the same `snake_case` query-parameter convention used by the web client's filters applies here too, for any request that passes `type`/`time_of_day`/`target_position`/`arrow_set`/`from`/`to`).

### 3.2 Why no DI framework

Hilt (or similar) is the conventional choice for modern Android, but its annotation-processing overhead buys nothing here: one data source, a handful of screens, one person maintaining it. A small hand-written container (a few `by lazy` properties wiring `Database` → `Repository` → `ViewModel factories`) is easier to read top-to-bottom than tracing Hilt's generated graph, and matches phase 1's own precedent of avoiding framework ceremony (Express over a framework, Vite over a meta-framework, no component library).

### 3.3 Why no separate Gradle modules

Multi-module Android builds pay off when build times or team boundaries demand it. Neither applies: this is a single developer, and the whole app is small enough that package-level separation (not build-level) gives the same readability without the Gradle configuration overhead.

---

## 4. Data model

Mirrors the phase-1 backend's `Session`/`Round` shape exactly, since these rows sync to the same Data Store tables.

### 4.1 Room entities

**`SessionEntity`**

| Field | Type | Notes |
|---|---|---|
| `id` | String | Client-generated UUID |
| `date` | String | Plain `YYYY-MM-DD` — same reasoning as phase 1: never a timestamp, so a timezone can never shift which day a session lands on |
| `type` | String | `practice` \| `competition` |
| `timeOfDay` | String | `morning` \| `evening` |
| `arrowSet` | String | |
| `poundage` | Double | |
| `notes` | String? | |
| `updatedAt` | String | ISO-8601 instant, used only for last-write-wins ordering |
| `dirty` | Boolean | True after any local write not yet confirmed synced |

**`RoundEntity`**

| Field | Type | Notes |
|---|---|---|
| `id` | String | Client-generated UUID |
| `sessionId` | String | Foreign key |
| `index` | Int | 1–4 practice, 1–2 competition |
| `targetPosition` | String | `A`–`D` |
| `arrows` | String | JSON-serialized `List<Arrow>` via a `TypeConverter` — **the wire format** (`[{"value":10,"isX":true}, ...]`), not the compact codec string the Catalyst adapter uses internally for its own Text column. Those are two different concerns: the codec is a backend storage optimization it already unwraps before any client ever sees a `Round`; Android's local storage has no equivalent size constraint, so there's no reason to duplicate that codec here. |
| `notes` | String? | |
| `updatedAt` | String | |
| `dirty` | Boolean | |

Rewriting the whole `arrows` array on every tap (rather than a normalized per-arrow table) matches how every layer of the system already treats a round's arrows as one atomic unit — `Round.arrows` is a whole array on the wire, in the TS domain model, and in the Catalyst Data Store's single `ARROWS` column. A per-arrow table would be a normalization this app's actual read/write patterns never ask for.

### 4.2 No separate outbox table

The `dirty` boolean on each row **is** the sync queue — no separate table listing "things to sync." Simpler to reason about, and the row count here (a few hundred sessions over years, matching phase 1's own scale assumption) never approaches a size where this would matter.

---

## 5. Sync

- **Trigger**: a `WorkManager` `OneTimeWorkRequest`, constrained to `NetworkType.CONNECTED`, enqueued after every local write and on reconnect (Android's network callback).
- **Payload**: all `dirty = true` sessions and rounds, batched into one `POST /sync` call — matching the endpoint's existing all-or-nothing validation (already built and deployed in phase 1; no backend change needed).
- **Success**: response is 200 → clear `dirty` on every row included in that batch.
- **Failure**: rows stay `dirty`; the next trigger (next write, or next reconnect) retries the same batch. No partial-success bookkeeping — the endpoint is genuinely all-or-nothing, so a batch either fully clears or fully stays queued.
- **Conflict policy**: last-write-wins by `updatedAt`, which is already how the backend's `putSession`/`putRound` behave (plain upserts, no version check). No client-side merge logic. This is a deliberate non-solution: with one archer on one phone, a genuine conflict (two devices editing the same row concurrently) is not a real scenario, and building conflict resolution for a case that can't happen would be pure waste.

---

## 6. Screens

| Screen | Contents |
|---|---|
| **Live scoring** | One end per screen: six slots, large `X 10 9 8 7 6 5 M` targets, running end and round totals, undo. Writes to Room on **every single tap**, not at end-of-round or end-of-end — the property that makes this trustworthy to use live, mirroring the exact same non-negotiable requirement the web client's `ScoreGrid`/draft-guard already satisfies via `localStorage`, just backed by a real local database instead. |
| **Sessions + sync** | List read from Room (instant, offline-first); a small per-row indicator shows anything still `dirty`. |
| **Session detail** | Same scorecard concept as the web client's `Scorecard` (ends rendered in descending order, per spec §2.3) — read and edit. |
| **Analysis** | Renders the same `GET /stats` payload the web client's four analysis views consume. Requires a live connection; shows a plain message instead of a stale cache when offline. |

---

## 7. Error handling and edge cases

| Case | Behaviour |
|---|---|
| App killed mid-round | No data loss — every tap already persisted to Room individually; the round resumes at its last-known state on next launch. |
| Sync fails (no connection, backend error) | Rows stay `dirty`; retried on the next trigger. No user-facing error for a transient failure — the sync-status indicator on the session list is the only signal. |
| Analysis screen offline | Explicit "requires a connection" message, never a stale or misleading cached chart. |
| Deleting a session | Confirm-before-delete (same pattern as the web client), cascades to local rounds, queues a delete for the next sync. |
| Incomplete round | Same rule as phase 1: `arrows.size < 36` is shown as incomplete and excluded from anything analysis-related (enforced server-side already; Android just doesn't need to pretend otherwise locally). |

---

## 8. Testing

| Target | Approach |
|---|---|
| `core` (scoring + validation) | JUnit, pure Kotlin, no Android/Robolectric dependency. Parameterized against the **same** `fixtures/scoring-conformance.json` already proven against the TypeScript implementation — this is what makes "both languages agree on scoring" a checked fact, not an assumption. |
| `data/repository` + sync worker | JUnit with Room's in-memory database and a fake/mocked Retrofit backend (MockWebServer or a hand-written fake) — no real network in any test. |
| `ui/livescoring` | Compose UI tests covering the one genuinely novel interaction: tap-to-score, running totals, undo, and — critically — that a tap is durably persisted before any network concern enters the picture. |

---

## 9. Out of scope

- iOS. Not part of this project at all (confirmed in the phase-1 design).
- Reimplementing statistics on-device — analysis is server-computed, always.
- Multi-device conflict resolution beyond last-write-wins.
- A login/auth flow of any kind — the backend has none.
- Arrow position/group-size plotting, wind/weather logging, equipment tuning — all explicitly out of scope in the phase-1 design and unchanged here.

---

## 10. Decision log

| Decision | Rationale |
|---|---|
| No DI framework, no multi-module split | Matches phase-1's own "no framework ceremony" precedent; one developer, one data source, doesn't need it |
| Retrofit + plain ViewModel/StateFlow | Boring, standard, well-supported — same spirit as choosing Express/Vite over heavier alternatives in phase 1 |
| Arrows stored as a JSON-serialized array in Room, not a normalized per-arrow table | Matches how every other layer of the system already treats arrows as one atomic array |
| Dirty flag on the row is the sync queue, no separate outbox table | Simpler, and the data volume never justifies more |
| Statistics not duplicated on-device | Centralize what's complex and driftable; phase 1's own stated principle, unchanged |
| No auth flow | The backend has none — this is a personal single-user app, decided explicitly during phase 1 |
| Scoring proven against the shared conformance fixture | The one place cross-language correctness is mechanically checked, not eyeballed |
