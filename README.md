# ArcheryAndroid

Native Android client for the archery score tracker — offline-first live scoring at the range. This is phase 2 of the project; phase 1 (Zoho Catalyst backend + mobile-first web client) is complete and deployed, living in [ArcheryBackend](https://github.com/rakeshashastri/ArcheryBackend) and [ArcheryWeb](https://github.com/rakeshashastri/ArcheryWeb).

Nothing has been built here yet — this repo currently holds only reference material carried over from phase 1.

## What's here

- `docs/2026-08-01-archery-score-tracker-design.md` — the full design spec for the system (scoring rules, data model, architecture, the four analysis views). Android wasn't planned in detail here — see its own section for the properties Android is expected to add (offline-first, per-tap durability, live end-by-end scoring) — but everything about domain rules, the data model, and the API contract it needs to talk to is authoritative.
- `fixtures/scoring-conformance.json` — a language-neutral scoring test fixture (arrows in, expected totals/X-count/end-totals out). The web/backend TypeScript implementation is proven against this fixture; the Kotlin scoring implementation here should be proven against the same one, so both languages are guaranteed to agree on scoring rules.

## Next step

Android implementation planning hasn't started yet.
