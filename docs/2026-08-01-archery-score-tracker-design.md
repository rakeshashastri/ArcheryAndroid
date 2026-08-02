# Compound Archery Score Tracker — Design

**Date:** 2026-08-01
**Status:** Draft for review
**Scope:** Single-archer score logging and performance analysis for outdoor compound archery at 50m.
**Platforms:** Mobile-first web (phase 1) and native Android (phase 2), backed by Zoho Catalyst. iOS is out of scope.

---

## 1. Purpose

Record every arrow of every 50m compound round, and use that history to answer four questions:

1. Am I improving over time?
2. Are my groups getting tighter, even when the total is flat?
3. Where inside a session do I lose points?
4. Does my practice form hold up in competition?

Score entry exists to feed the analysis. The analysis is the product.

---

## 2. Domain

### 2.1 Fixed conditions

Every round is shot under identical conditions. This is the single largest simplifying assumption in the design.

| Property | Value |
|---|---|
| Distance | 50m |
| Target face | 80cm, 6-ring (reduced face) |
| Bow type | Compound |
| Scoring zones | 10, 9, 8, 7, 6, 5, M |
| Arrows per end | 6 |
| Ends per round | 6 |
| Arrows per round | 36 (max 360) |

Because distance and face never vary, no round carries a distance or face-size field, and every round ever recorded is directly comparable to every other on a flat 360 scale. No normalisation, no per-format trend scoping.

### 2.2 Scoring rules

- Arrow values: `10`, `9`, `8`, `7`, `6`, `5`, and miss. Anything outside the 5-ring is a miss.
- A miss is stored as the number **0**, so every total is plain addition with no special case.
- **X (inner 10)** scores **10**. It is tracked as a separate count — a tiebreak value and a group-quality signal — but it never changes the numeric score.
- End total = sum of 6 arrows (max 60). Round total = sum of 6 ends (max 360). Session total = sum of its rounds.

> **Verify before implementing:** X-scores-10 is the outdoor compound convention and differs from indoor 18m compound, where the inner 10 scores 10 and the outer ten-ring drops to 9. Confirm against the current World Archery rulebook. The rule lives in one place per client, covered by the shared conformance fixture (§9), so a correction is a small change.

### 2.3 Arrow ordering

Arrows are scored **after** the end is shot, while pulling them from the face. There is therefore no shot-order information within an end — the six values are a multiset.

Storage keeps entry order as typed. Sorting is a **display concern only**: history scorecards render each end descending, matching scorecard convention, while a live editor shows slots in the order filled so that correcting a mistyped arrow does not make the row jump under the archer's thumb.

Consequence: within-session analysis operates on **end position** (1–6) and **round position** (1–4), never on arrow position within an end.

### 2.4 Session types

Two distinct types, kept separate throughout history and analysis.

| | Practice | Competition |
|---|---|---|
| Rounds per session | 1–4 | 2 |
| Arrows | 36–144 | 72 |
| Max score | 360 / 720 / 1080 / 1440 | 720 |
| Typical entry | Transcribed in phase 1; live at the range from phase 2 | Transcribed from a paper log — phones are banned at some competitions, so this never changes |

They are presented as separate kinds of thing — separate creation flows, separate trend series — but share one storage model and one set of scoring rules. The split is a presentation and analysis decision, not a storage one.

---

## 3. Architecture

```
┌─────────────────┐         ┌──────────────────┐
│  Web (React/TS) │         │ Android (Compose)│
│  mobile-first   │         │  Room local DB   │
│  online-only    │         │  offline-first   │
│  home: analysis │         │  range: scoring  │
│  + transcription│         │                  │
└────────┬────────┘         └────────┬─────────┘
         │  REST                     │  REST + batch sync
         └───────────┬───────────────┘
                     ▼
         ┌───────────────────────────┐
         │      Zoho Catalyst        │
         │  Authentication (Zoho ID) │
         │  Data Store  (2 tables)   │
         │  Functions   (statistics) │
         │  Hosting     (web client) │
         └───────────────────────────┘
```

### 3.1 Split of responsibility

| Concern | Where | Why |
|---|---|---|
| Scoring arithmetic — end/round totals, X count, validation | Both clients, duplicated | Trivial, must work offline, safe to duplicate when verified (§9) |
| Statistics — trends, rolling averages, distribution, spread, gap | **Catalyst Function, once** | Complex and driftable; only consulted with connectivity |
| Source of truth | Catalyst Data Store | Both clients converge here |
| Offline buffer | Android Room only | Web never scores in the field |

Centralise what is complex; duplicate-and-verify what is simple. Two TypeScript-and-Kotlin implementations of a rolling average could silently disagree, which is the worst kind of bug in an analysis app. Two implementations of "add six numbers" cannot, once a shared fixture proves it.

### 3.2 Authentication

Catalyst Authentication with Zoho sign-in. Single user; every row scoped to the authenticated user ID. Identity comes for free rather than being built.

### 3.3 Sync (Android → Catalyst)

- **Client-generated UUIDs**, so rows created offline have stable identity from birth.
- A dirty flag per row; batch upsert on reconnect via WorkManager.
- Conflict policy: **last-write-wins by `updated_at`**. With one archer and one scoring device, genuine conflicts are near-impossible; modelling anything richer would buy complexity that is never exercised.

### 3.4 Technology

| Component | Choice |
|---|---|
| Web | React + TypeScript, built with Vite, hosted on Catalyst |
| Web storage | None persistent — online-only, plus a `localStorage` draft guard (§8) |
| Android | Kotlin + Jetpack Compose, **plain Material 3** (no internal component library) |
| Android storage | Room |
| Backend | Catalyst Data Store, Functions, Authentication |

> **Verify before implementing:** exact Catalyst component names, quotas, ZCQL capabilities, and the current Android/JS SDK surface should be confirmed against Catalyst documentation. This design assumes only a table store with query support, serverless HTTP functions, hosted auth, and static web hosting.

---

## 4. Data model

Four conceptual levels — `Session → Round → End → Arrow` — and **two stored** ones.

An `End` carries an index and six arrows and *no data of its own*, which makes it pure derivation: end *N* is `arrows[6N ..< 6N+6]`. At roughly four rounds a day over several years the whole history is about **800 round rows**, so it fits in a single query and the statistics function computes everything in memory — no joins, no aggregation SQL, no pagination. Storing arrows as rows instead would mean tens of thousands of rows and a join on every analysis query.

### 4.1 `session`

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Client-generated, stable from birth |
| `user_id` | string | From Catalyst Authentication |
| `date` | date | Plain date, never a timestamp — a timestamp would let a timezone shift an evening session onto the wrong day |
| `type` | enum | `practice` \| `competition` |
| `time_of_day` | enum | `morning` \| `evening` (under artificial lights) |
| `arrow_set` | string | Pre-filled from the last session **of the same type** |
| `poundage` | number | Draw weight in lb; pre-filled from the previous session |
| `notes` | string? | Free text — new string, and similar |
| `updated_at` | timestamp | Sync ordering |

### 4.2 `round`

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Client-generated |
| `session_id` | UUID | Parent |
| `index` | int | 1–4 practice, 1–2 competition |
| `target_position` | enum | `A` \| `B` \| `C` \| `D` |
| `arrows` | array | 0–36 ordered entries of `{ value, isX }` |
| `notes` | string? | |
| `updated_at` | timestamp | |

`isX` is a boolean valid **only** when `value == 10`; the model rejects it otherwise.

### 4.3 Derived, never stored

End totals, round totals, session totals, X count, 10+X count, average arrow value, standard deviation, and **completeness** (`arrows.length == 36`).

Storing any of these creates a value that can disagree with the arrows after an edit. Completeness in particular sits in the same row as the array it is derived from, so computing it is free.

### 4.4 Field rationale

- **poundage** — changes rarely; pre-filled so it is effectively never typed.
- **arrow_set** — differs systematically between practice and competition (practice arrows vs Easton X10). Recorded so the practice-vs-competition gap can be separated from an equipment difference rather than confounded by it.
- **time_of_day** — a two-tap field that is plausibly a real effect at 50m under lights, and will never be skipped.
- **target_position** — does not affect scoring; exists purely as a filter and grouping dimension. Cheap to capture, impossible to backfill.

### 4.5 Explicitly excluded

Wind and weather (unreliable to record in the moment), mental and physical state, arrow position on the target face, sight marks, and tuning logs. Each was considered and cut.

---

## 5. API contract

Catalyst Functions, all authenticated and user-scoped.

| Endpoint | Purpose |
|---|---|
| `GET /sessions?from=&to=` | Sessions with their rounds, for history |
| `PUT /sessions/:id` | Upsert a session |
| `PUT /rounds/:id` | Upsert a round — the hot path for web entry |
| `POST /sync` | **Batch** upsert of dirty sessions and rounds; Android only, all-or-nothing |
| `GET /stats` | All four analysis payloads in one response. Accepts the same filters as history: `type`, `from`, `to`, `time_of_day`, `target_position`, `arrow_set` |
| `GET /export?format=json\|csv` | Full history dump |

**Why one stats endpoint:** the four views share identical inputs — the same ~800 rounds and the same filters — and the expensive part is fetching, not computing. Four endpoints would fetch the same data four times to produce four slices of one analysis.

**Why a separate batch sync:** web edits one round at a time with a live connection, so per-round PUT is the right granularity. Android may reconnect after a whole practice day needing to push a session and four rounds together — an all-or-nothing operation, not four PUTs that can half-fail.

### 5.1 Validation

Enforced identically on both clients and re-checked server-side:

- at most 6 arrows per end slice and 36 per round
- `isX` only on a value of 10
- practice sessions 1–4 rounds; competition sessions exactly 2
- a session must contain at least one started round to be saveable

---

## 6. Features and screens

### 6.1 Web — phase 1, mobile-first

Every layout decision is made for a phone-width screen; desktop is a consequence, not a target.

| Screen | Contents |
|---|---|
| **Home** | Practice-vs-competition gap as the hero card, last session summary, "New session" action |
| **New session** | Type, date, time of day, arrow set, poundage — all pre-filled except type and date |
| **Round entry** | 6×6 transcription grid; enter a value, focus auto-advances |
| **History** | Sessions grouped by month; filters for type, date range, time of day, target position, arrow set |
| **Session detail** | Scorecard per round — ends as rows, arrows descending, end totals and running total; context fields; edit and delete |
| **Analysis** | The four views (§7), sharing the history filters |
| **Settings** | JSON and CSV export, defaults, sign out |

Web has **no live end-by-end editor**. Bulk transcription from a paper log is its entry mode; live scoring belongs to Android, offline and in a pocket.

**Mobile-first UI rules:** touch targets ≥48px, primary actions within thumb reach at the bottom of the viewport, no hover-dependent affordances, oversized numerals, high contrast for daylight reading.

### 6.2 Android — phase 2

Its reason to exist is the one thing the web client cannot do.

| Screen | Contents |
|---|---|
| **Live scoring** | One end per screen, six slots, large `X 10 9 8 7 6 5 M` targets, running end and round totals, undo. **Writes to Room on every single tap** — not at end-of-round. The app may be backgrounded, locked, or killed mid-round, and scores must never be lost. This is the property that determines whether the app is trusted enough to use live |
| **Sessions + sync** | List with a clear indicator of what has not yet reached Catalyst |
| **Session detail** | Same scorecard; read and edit |
| **Analysis** | The same `GET /stats` payload rendered natively; requires a connection |

---

## 7. Analysis

Four views, all respecting the history filters. Computed server-side in the statistics function. **Incomplete rounds are excluded from every calculation** — a 4-end round is not a 360 score and must never be plotted as one.

### A. Practice vs competition gap — the hero

Rolling practice average and rolling competition average, each over that type's **last 5 complete rounds**, both on the 360 scale, plotted together. The headline number is the difference; a secondary chart tracks that difference over time. A closing gap means competition performance is catching up to practice.

Annotated with a caveat when the practice and competition arrow sets differ, since part of the gap may be equipment rather than pressure.

### B. Score trend and personal bests

Individual round scores on the 360 scale over time, practice and competition as separate series. Each series carries its own rolling average over its own last 5 complete rounds — the two are never mixed. Markers for **best ever** and **best in the last 12 months**, tracked separately per type. ("Season" is deliberately avoided — a rolling 12-month window needs no calendar configuration.)

### C. Consistency and distribution

Histogram of arrow values over the selected period, plus X-rate, 10+X rate, average arrow value, and standard deviation of arrow values per round. Standard deviation is charted over time; it typically improves before the total does, which makes it the earliest visible sign of progress.

### D. Within-session patterns

- Average score by **end position** (1–6) across all rounds — reveals a cold first end or a late fade.
- Average score by **round position** (1–4) within practice sessions — reveals fatigue across a long practice day.

---

## 8. Error handling and edge cases

| Case | Behaviour |
|---|---|
| **Save fails mid-transcription (web)** | The round is PUT after each completed end, and the in-progress round is mirrored to `localStorage` as a crash-and-refresh guard. This is insurance against losing 72 hand-transcribed arrows to a dropped connection — deliberately *not* a sync layer |
| Auth token expires mid-entry | Re-authenticate in place; the draft survives and nothing is lost |
| Incomplete round | `arrows.length < 36`. Shown in history, clearly marked, excluded from every trend and average |
| Practice session with 0 rounds | Not saveable |
| Competition session with 1 round | Saveable, marked incomplete, excluded from trends |
| Editing a historical session | Permitted; statistics recompute on the next fetch. No audit trail in v1 |
| Delete | Confirmation required; cascades from session to rounds |
| Not enough data for a chart | Explicit empty state naming exactly what is missing. Gap view: ≥1 complete competition round and ≥3 complete practice rounds. Trend, consistency, and within-session views: ≥3 complete rounds. Never an empty axis or a misleading single point |
| Android killed mid-entry | No data loss — arrows persist to Room individually; the session resumes on next launch |
| Android offline conflict | Last-write-wins by `updated_at` |
| First launch | Empty state guiding the archer to log a first session. Poundage and arrow set prompted once, then remembered |

---

## 9. Testing

| Target | Approach |
|---|---|
| **Statistics function** | The highest-value suite in the project. Hand-built fixtures with known expected outputs: rolling averages, standard deviation, the gap, per-end-position and per-round-position averages, and — critically — that incomplete rounds are excluded. Pure input-to-output; no database, no UI |
| **Scoring, both clients** | A **shared JSON conformance fixture** (`input arrows → expected total, X count, end totals`) consumed by both the TypeScript and the Kotlin test suites. This is what makes the accepted duplication in §3.1 safe: two implementations proven to agree against one authority, rather than two that ought to |
| API contract | Request and response shape tests per endpoint, including auth rejection |
| Web entry | Transcription flow, focus advance, and draft recovery after a forced refresh |
| Android sync | Create offline → reconnect → converge. Per-tap durability verified by killing the app mid-end |

Boundary cases for scoring: all Xs = 360, all misses = 0, `isX` rejected on a non-10, a 37th arrow rejected.

---

## 10. Phasing

| Phase | Deliverable |
|---|---|
| **1** | Catalyst backend — auth, two-table schema, six endpoints, statistics function — plus the full mobile-first web client. **Usable end to end:** paper at the range, transcribe at home, all four analysis views |
| **2** | Native Android — live end-by-end scoring on Room, offline, per-tap durability, batch sync, plain Material 3 |

Phase 1 front-loads the risk: the schema, the statistics function, and the analysis views are all built and validated against a client with no sync layer clouding the picture. By the time Android arrives, the only genuinely new problem is offline sync, and everything it talks to is already proven.

Phase 1 is also an honest product on its own — it is exactly the app described in §1, since analysis is the point and entry is the price of admission. Live scoring is a convenience upgrade, not the product.

---

## 11. Out of scope

- iOS. The `iOS/` scaffold stays untouched
- Arrow position plotting on the target face, group size, directional bias
- Indoor 18m, WA 1440, field, 3D, and any distance other than 50m
- Wind, weather, and mental-state logging
- Multiple archers, coaching views, sharing, leaderboards
- Head-to-head and elimination brackets
- Equipment tuning logs and sight-mark tables
- Audit trail on edits

---

## 12. Open decisions

None blocking. Two items to confirm against external sources during phase 1, both flagged inline: the World Archery X-scoring rule (§2.2) and the exact Catalyst component surface (§3.4).

---

## 13. Decision log

| Decision | Rationale |
|---|---|
| Analysis-first, not scorecard-first | The stated purpose is understanding performance over time; entry is the cost of admission |
| Per-arrow granularity | The ceiling on all analysis — enables distribution, consistency, and X-rate |
| 50m only, fixed face | Collapses format, distance, and normalisation complexity entirely |
| Session → Round → End → Arrow | A practice day contains up to four 36-arrow rounds; three levels could not express this |
| Practice and competition as separate types | Requested. Competition carries pressure and different arrows; the gap between them is the hero metric |
| Shared scoring rules beneath the split | Two presentations, one rule set |
| Ends not stored | They carry no data of their own; slicing the round's arrow array derives them |
| Arrows as an array on the round row | ~800 rows instead of ~29,000; whole history in one query, statistics computed in memory |
| Derived values never stored | Prevents totals disagreeing with arrows after an edit |
| Statistics on the server, scoring on the clients | Centralise what is complex and driftable; duplicate what is trivial and must work offline |
| Shared JSON conformance fixture | Makes the duplicated scoring provably consistent across TypeScript and Kotlin |
| Android offline-first, web online-only | Offline complexity lives only where it is needed; the phase-1 client stays simple |
| Last-write-wins sync | One archer, one scoring device — richer conflict resolution would never be exercised |
| Incomplete rounds excluded from trends | A partial round is not a 360 score |
| Session date as a plain date | A timestamp would let a timezone move an evening session onto the wrong day |
| Miss stored as 0 | Every total is plain addition, with no special case in any sum |
| Wind and mental state cut | Accurate-but-abandoned loses to cheap-and-consistent |
| Single archer, no profiles | Confirmed: one user, no switching, no filtering by person |
| Plain Material 3 on Android | No internal component-library dependency in a personal project |
