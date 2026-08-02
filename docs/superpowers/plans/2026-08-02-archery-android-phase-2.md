# Archery Android Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a native Android client for the archery score tracker — offline-first live scoring, session history, session detail, and the four analysis views, talking to the same deployed Zoho Catalyst backend the web client uses.

**Architecture:** Single Gradle module (`app`), organized by package: `core` (pure Kotlin scoring/validation, no Android deps), `data/local` (Room), `data/remote` (Retrofit), `data/repository` (merge + dirty-flag bookkeeping), `sync` (WorkManager), `ui` (Compose + plain ViewModels, one package per screen). No DI framework, no multi-module split — a hand-written container wires everything.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.01), Material 3, Room 2.6.1, Retrofit 2.11.0 + kotlinx-serialization-converter, kotlinx.serialization 1.7.3, WorkManager 2.9.1, JUnit4 + Robolectric 4.13 (for Room/repository tests), Compose UI testing (androidTest).

**Spec:** `docs/superpowers/specs/2026-08-02-archery-android-design.md`

---

## Global Constraints

- Kotlin only, no Java source files.
- `core` package has zero Android SDK dependencies — must compile and test as plain JVM Kotlin.
- Arrow values are exactly `10 9 8 7 6 5 0`. A miss is stored as the integer `0` — every total is plain addition, no special case.
- `isX` is a boolean valid **only** when `value == 10`. X scores 10 and is counted separately from the plain ten count.
- 6 arrows per end, 6 ends per round, 36 arrows and 360 points per complete round.
- Practice sessions hold 1–4 rounds; competition sessions hold exactly 2 — except a session with fewer than its type's round count is still **valid and saveable**, just incomplete (matches the phase-1 backend's `validateSession`, spec §8: a competition session with 1 of 2 rounds must never be rejected).
- A round is complete when `arrows.size == 36`. Incomplete rounds are never treated as a real 360 score anywhere in the UI.
- `Round.arrows` is stored in Room as a JSON-serialized `List<Arrow>` — the wire format, **not** the compact codec string the Catalyst backend uses internally for its own storage.
- `Session.date` is a plain `YYYY-MM-DD` string, never a timestamp.
- IDs are client-generated UUIDs (`java.util.UUID.randomUUID().toString()`).
- `updatedAt` is a plain ISO-8601 instant string (`java.time.Instant.now().toString()`), compared only as a string for last-write-wins — never parsed for arithmetic.
- Every `SessionEntity`/`RoundEntity` row has a `dirty: Boolean` column; it **is** the sync queue — no separate outbox table.
- The backend has no authentication — every network call is a plain unauthenticated HTTPS request. Never add an auth header, login screen, or token anywhere in this plan.
- Statistics (rolling averages, gap, consistency, patterns) are never recomputed on-device — `GET /stats` is called live and requires connectivity; there is no offline analysis cache.
- The deployed backend's base URL is `https://archeryapp-60081207448.development.catalystserverless.in/server/api/`.
- Query parameters for filters use snake_case on the wire (`time_of_day`, `target_position`, `arrow_set`), matching the web client's convention — even though Kotlin field/property names stay camelCase.
- minSdk 26, targetSdk 35, compileSdk 35.
- Commit after every task. Conventional Commits style (`feat:`, `test:`, `chore:`).
- All Compose screens use Material 3 components only — no custom design system, no third-party UI library.

---

## File Structure

```
app/
├── build.gradle.kts
├── src/main/AndroidManifest.xml
├── src/main/java/com/archery/tracker/
│   ├── core/
│   │   ├── Types.kt            Arrow, Round, Session, SessionType, TimeOfDay, TargetPosition
│   │   ├── Scoring.kt           ends, endTotals, roundTotal, runningTotals, xCount, tenCount, isRoundComplete
│   │   └── Validation.kt        ValidationError, validateArrow, validateRound, validateSession
│   ├── data/
│   │   ├── local/
│   │   │   ├── SessionEntity.kt
│   │   │   ├── RoundEntity.kt
│   │   │   ├── ArrowListConverter.kt
│   │   │   ├── ArcheryDao.kt
│   │   │   └── ArcheryDatabase.kt
│   │   ├── remote/
│   │   │   ├── Dto.kt            SessionDto, RoundDto, ArrowDto, StatsResponseDto, SyncRequestDto, SyncResponseDto
│   │   │   └── ArcheryApi.kt     Retrofit interface
│   │   └── repository/
│   │       ├── Mappers.kt        Entity <-> Dto <-> domain conversions
│   │       └── ArcheryRepository.kt
│   ├── sync/
│   │   └── SyncWorker.kt
│   ├── di/
│   │   └── AppContainer.kt
│   └── ui/
│       ├── MainActivity.kt
│       ├── AppNav.kt
│       ├── newsession/NewSessionScreen.kt, NewSessionViewModel.kt
│       ├── livescoring/LiveScoringScreen.kt, LiveScoringViewModel.kt
│       ├── history/HistoryScreen.kt, HistoryViewModel.kt
│       ├── sessiondetail/SessionDetailScreen.kt, SessionDetailViewModel.kt
│       └── analysis/AnalysisScreen.kt, AnalysisViewModel.kt
├── src/test/java/com/archery/tracker/     JUnit unit tests (core, mappers, repository via Robolectric)
└── src/androidTest/java/com/archery/tracker/   Compose UI tests
```

**Responsibility boundaries:** `core` knows arrows and rules but nothing about Room, Retrofit, or Compose. `data/local` knows Room but nothing about the network. `data/remote` knows the wire format but nothing about storage. `data/repository` is the only place that talks to both, and the only place that knows about `dirty`. `sync` only talks to the repository, never to Room or Retrofit directly. `ui` only talks to the repository via ViewModels, never to Room or Retrofit directly.

---

### Task 1: Gradle project scaffold and core domain types

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/archery/tracker/core/Types.kt`
- Test: `app/src/test/java/com/archery/tracker/core/TypesTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `ArrowValue` (typealias `Int`), `Arrow`, `SessionType`, `TimeOfDay`, `TargetPosition`, `Round`, `Session`, `SessionWithRounds`, constants `ARROWS_PER_END`/`ENDS_PER_ROUND`/`ARROWS_PER_ROUND`/`MAX_ROUND_SCORE`/`VALID_ARROW_VALUES`/`ROUNDS_PER_SESSION`.

- [ ] **Step 1: Create the version catalog**

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
compose-bom = "2024.10.01"
core-ktx = "1.13.1"
lifecycle = "2.8.6"
activity-compose = "1.9.3"
navigation-compose = "2.8.3"
room = "2.6.1"
retrofit = "2.11.0"
kotlinx-serialization = "1.7.3"
retrofit-serialization-converter = "1.0.0"
okhttp = "4.12.0"
work = "2.9.1"
junit4 = "4.13.2"
robolectric = "4.13"
androidx-test-ext = "1.2.1"
espresso = "3.6.1"

[libraries]
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization-converter = { group = "com.jakewharton.retrofit", name = "retrofit2-kotlinx-serialization-converter", version.ref = "retrofit-serialization-converter" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging-interceptor = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
junit4 = { group = "junit", name = "junit", version.ref = "junit4" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidx-test-ext" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.21-1.0.28" }
```

- [ ] **Step 2: Create the root Gradle files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ArcheryAndroid"
include(":app")
```

`build.gradle.kts` (root, empty — plugins are declared per-module with `apply false` here):

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 3: Create the app module manifest and build file**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:label="Archery Tracker"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.archery.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.archery.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.work.runtime.ktx)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

- [ ] **Step 4: Write the failing domain types test**

`app/src/test/java/com/archery/tracker/core/TypesTest.kt`:

```kotlin
package com.archery.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypesTest {

    @Test
    fun `valid arrow values are exactly the seven scoring zones`() {
        assertEquals(setOf(0, 5, 6, 7, 8, 9, 10), VALID_ARROW_VALUES.toSet())
    }

    @Test
    fun `arrows per round is 36`() {
        assertEquals(36, ARROWS_PER_ROUND)
        assertEquals(6, ARROWS_PER_END)
        assertEquals(6, ENDS_PER_ROUND)
        assertEquals(360, MAX_ROUND_SCORE)
    }

    @Test
    fun `rounds per session type matches practice 4 competition 2`() {
        assertEquals(4, ROUNDS_PER_SESSION[SessionType.PRACTICE])
        assertEquals(2, ROUNDS_PER_SESSION[SessionType.COMPETITION])
    }

    @Test
    fun `an arrow with isX true and value 10 is constructible`() {
        val arrow = Arrow(value = 10, isX = true)
        assertTrue(arrow.isX)
        assertEquals(10, arrow.value)
    }

    @Test
    fun `sessionWithRounds carries a session and its rounds together`() {
        val session = Session(
            id = "s1", date = "2026-01-01", type = SessionType.PRACTICE,
            timeOfDay = TimeOfDay.MORNING, arrowSet = "ACC", poundage = 50.0,
            notes = null, updatedAt = "2026-01-01T00:00:00Z",
        )
        val round = Round(
            id = "r1", sessionId = "s1", index = 1, targetPosition = TargetPosition.A,
            arrows = emptyList(), notes = null, updatedAt = "2026-01-01T00:00:00Z",
        )
        val withRounds = SessionWithRounds(session, listOf(round))
        assertEquals("s1", withRounds.session.id)
        assertEquals(1, withRounds.rounds.size)
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.core.TypesTest"`
Expected: FAIL — compilation error, `Types.kt` does not exist yet.

- [ ] **Step 6: Implement the domain types**

`app/src/main/java/com/archery/tracker/core/Types.kt`:

```kotlin
package com.archery.tracker.core

typealias ArrowValue = Int

data class Arrow(
    val value: ArrowValue,
    val isX: Boolean,
)

enum class SessionType { PRACTICE, COMPETITION }
enum class TimeOfDay { MORNING, EVENING }
enum class TargetPosition { A, B, C, D }

data class Round(
    val id: String,
    val sessionId: String,
    val index: Int,
    val targetPosition: TargetPosition,
    val arrows: List<Arrow>,
    val notes: String?,
    val updatedAt: String,
)

data class Session(
    val id: String,
    val date: String,
    val type: SessionType,
    val timeOfDay: TimeOfDay,
    val arrowSet: String,
    val poundage: Double,
    val notes: String?,
    val updatedAt: String,
)

data class SessionWithRounds(
    val session: Session,
    val rounds: List<Round>,
)

const val ARROWS_PER_END = 6
const val ENDS_PER_ROUND = 6
const val ARROWS_PER_ROUND = 36
const val MAX_ROUND_SCORE = 360

val VALID_ARROW_VALUES: List<ArrowValue> = listOf(0, 5, 6, 7, 8, 9, 10)

val ROUNDS_PER_SESSION: Map<SessionType, Int> = mapOf(
    SessionType.PRACTICE to 4,
    SessionType.COMPETITION to 2,
)
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.core.TypesTest"`
Expected: PASS — 5 tests.

- [ ] **Step 8: Commit**

```bash
git add .
git commit -m "feat: add Gradle scaffold and core domain types"
```

---

### Task 2: Scoring functions

**Files:**
- Create: `app/src/main/java/com/archery/tracker/core/Scoring.kt`
- Test: `app/src/test/java/com/archery/tracker/core/ScoringTest.kt`

**Interfaces:**
- Consumes: `Arrow`, `ARROWS_PER_END`, `ARROWS_PER_ROUND` from Task 1.
- Produces: `ends(arrows)`, `endTotals(arrows)`, `roundTotal(arrows)`, `runningTotals(arrows)`, `xCount(arrows)`, `tenCount(arrows)`, `isRoundComplete(arrows)`.

- [ ] **Step 1: Write the failing scoring tests**

`app/src/test/java/com/archery/tracker/core/ScoringTest.kt`:

```kotlin
package com.archery.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {

    private fun repeat(value: ArrowValue, isX: Boolean, n: Int): List<Arrow> =
        List(n) { Arrow(value, isX) }

    @Test
    fun `ends slices arrows into groups of six`() {
        assertEquals(2, ends(repeat(9, false, 12)).size)
    }

    @Test
    fun `ends keeps a trailing partial end`() {
        val result = ends(repeat(9, false, 8))
        assertEquals(2, result.size)
        assertEquals(2, result[1].size)
    }

    @Test
    fun `ends returns nothing for no arrows`() {
        assertTrue(ends(emptyList()).isEmpty())
    }

    @Test
    fun `roundTotal sums a perfect round to 360`() {
        assertEquals(360, roundTotal(repeat(10, true, 36)))
    }

    @Test
    fun `roundTotal scores an all-miss round as 0`() {
        assertEquals(0, roundTotal(repeat(0, false, 36)))
    }

    @Test
    fun `roundTotal counts an X as ten not eleven`() {
        val arrows = listOf(Arrow(10, true), Arrow(10, false))
        assertEquals(20, roundTotal(arrows))
    }

    @Test
    fun `endTotals returns one total per end`() {
        val arrows = repeat(10, false, 6) + repeat(9, false, 6)
        assertEquals(listOf(60, 54), endTotals(arrows))
    }

    @Test
    fun `runningTotals accumulates end totals`() {
        val arrows = repeat(10, false, 6) + repeat(9, false, 6)
        assertEquals(listOf(60, 114), runningTotals(arrows))
    }

    @Test
    fun `counts Xs separately from tens`() {
        val arrows = listOf(Arrow(10, true), Arrow(10, true), Arrow(10, false), Arrow(9, false))
        assertEquals(2, xCount(arrows))
        assertEquals(3, tenCount(arrows))
    }

    @Test
    fun `isRoundComplete is true at exactly 36 arrows`() {
        assertTrue(isRoundComplete(repeat(9, false, 36)))
    }

    @Test
    fun `isRoundComplete is false below 36 arrows`() {
        assertFalse(isRoundComplete(repeat(9, false, 35)))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.core.ScoringTest"`
Expected: FAIL — `Scoring.kt` does not exist yet.

- [ ] **Step 3: Implement scoring**

`app/src/main/java/com/archery/tracker/core/Scoring.kt`:

```kotlin
package com.archery.tracker.core

fun ends(arrows: List<Arrow>): List<List<Arrow>> =
    arrows.chunked(ARROWS_PER_END)

fun roundTotal(arrows: List<Arrow>): Int =
    arrows.sumOf { it.value }

fun endTotals(arrows: List<Arrow>): List<Int> =
    ends(arrows).map { roundTotal(it) }

fun runningTotals(arrows: List<Arrow>): List<Int> {
    var carried = 0
    return endTotals(arrows).map { total -> carried += total; carried }
}

fun xCount(arrows: List<Arrow>): Int =
    arrows.count { it.isX }

fun tenCount(arrows: List<Arrow>): Int =
    arrows.count { it.value == 10 }

fun isRoundComplete(arrows: List<Arrow>): Boolean =
    arrows.size == ARROWS_PER_ROUND
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.core.ScoringTest"`
Expected: PASS — 11 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/archery/tracker/core/Scoring.kt app/src/test/java/com/archery/tracker/core/ScoringTest.kt
git commit -m "feat: add scoring functions"
```

---

### Task 3: Validation rules

**Files:**
- Create: `app/src/main/java/com/archery/tracker/core/Validation.kt`
- Test: `app/src/test/java/com/archery/tracker/core/ValidationTest.kt`

**Interfaces:**
- Consumes: `Arrow`, `Round`, `Session`, `SessionType`, `VALID_ARROW_VALUES`, `ARROWS_PER_ROUND`, `ROUNDS_PER_SESSION` from Tasks 1–2.
- Produces: `ValidationError(code: String, message: String)`, `validateArrow(arrow)`, `validateRound(round, sessionType)`, `validateSession(session, rounds)` — each returning `List<ValidationError>`, empty when valid.

**Important — matches the phase-1 backend exactly:** a competition session with **1** of its 2 required rounds is **valid**, not rejected — it's simply incomplete, excluded from analysis elsewhere. Only **more than** the session type's round limit is a validation error. (Phase 1's plan got this wrong on the first pass and had to fix it after the fact — this plan starts from the corrected rule directly.)

- [ ] **Step 1: Write the failing validation tests**

`app/src/test/java/com/archery/tracker/core/ValidationTest.kt`:

```kotlin
package com.archery.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    private fun round(
        index: Int = 1,
        arrows: List<Arrow> = emptyList(),
    ) = Round(
        id = "r1", sessionId = "s1", index = index, targetPosition = TargetPosition.A,
        arrows = arrows, notes = null, updatedAt = "2026-08-01T00:00:00Z",
    )

    private fun session(type: SessionType = SessionType.PRACTICE) = Session(
        id = "s1", date = "2026-08-01", type = type, timeOfDay = TimeOfDay.MORNING,
        arrowSet = "Easton X10", poundage = 50.0, notes = null,
        updatedAt = "2026-08-01T00:00:00Z",
    )

    private fun codes(errors: List<ValidationError>): List<String> = errors.map { it.code }

    @Test
    fun `validateArrow accepts a legal value`() {
        assertTrue(validateArrow(Arrow(9, false)).isEmpty())
    }

    @Test
    fun `validateArrow rejects a value outside the scoring zones`() {
        assertTrue(codes(validateArrow(Arrow(4, false))).contains("ARROW_INVALID_VALUE"))
    }

    @Test
    fun `validateArrow rejects isX on anything but a ten`() {
        assertTrue(codes(validateArrow(Arrow(9, true))).contains("ARROW_X_ON_NON_TEN"))
    }

    @Test
    fun `validateArrow accepts isX on a ten`() {
        assertTrue(validateArrow(Arrow(10, true)).isEmpty())
    }

    @Test
    fun `validateRound accepts an empty in-progress round`() {
        assertTrue(validateRound(round(), SessionType.PRACTICE).isEmpty())
    }

    @Test
    fun `validateRound rejects a 37th arrow`() {
        val arrows = List(37) { Arrow(9, false) }
        assertTrue(codes(validateRound(round(arrows = arrows), SessionType.PRACTICE)).contains("ROUND_TOO_MANY_ARROWS"))
    }

    @Test
    fun `validateRound rejects a fifth round in a practice session`() {
        assertTrue(codes(validateRound(round(index = 5), SessionType.PRACTICE)).contains("ROUND_INDEX_OUT_OF_RANGE"))
    }

    @Test
    fun `validateRound rejects a third round in a competition session`() {
        assertTrue(codes(validateRound(round(index = 3), SessionType.COMPETITION)).contains("ROUND_INDEX_OUT_OF_RANGE"))
    }

    @Test
    fun `validateRound surfaces invalid arrows from within the round`() {
        val errors = validateRound(round(arrows = listOf(Arrow(4, false))), SessionType.PRACTICE)
        assertTrue(codes(errors).contains("ARROW_INVALID_VALUE"))
    }

    @Test
    fun `validateSession rejects a session with no rounds`() {
        assertTrue(codes(validateSession(session(), emptyList())).contains("SESSION_NO_ROUNDS"))
    }

    @Test
    fun `validateSession accepts a practice session with four rounds`() {
        val rounds = (1..4).map { round(index = it) }
        assertTrue(validateSession(session(), rounds).isEmpty())
    }

    @Test
    fun `validateSession accepts an incomplete competition session with one round`() {
        val errors = validateSession(session(SessionType.COMPETITION), listOf(round(index = 1)))
        assertTrue(codes(errors).none { it == "SESSION_ROUND_COUNT" })
    }

    @Test
    fun `validateSession rejects a competition session with more than two rounds`() {
        val rounds = listOf(round(index = 1), round(index = 2), round(index = 3))
        val errors = validateSession(session(SessionType.COMPETITION), rounds)
        assertTrue(codes(errors).contains("SESSION_ROUND_COUNT"))
    }

    @Test
    fun `validateSession rejects duplicate round indexes`() {
        val rounds = listOf(round(index = 1), round(index = 1))
        assertTrue(codes(validateSession(session(), rounds)).contains("SESSION_DUPLICATE_ROUND_INDEX"))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.core.ValidationTest"`
Expected: FAIL — `Validation.kt` does not exist yet.

- [ ] **Step 3: Implement validation**

`app/src/main/java/com/archery/tracker/core/Validation.kt`:

```kotlin
package com.archery.tracker.core

data class ValidationError(val code: String, val message: String)

fun validateArrow(arrow: Arrow): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (arrow.value !in VALID_ARROW_VALUES) {
        errors.add(ValidationError("ARROW_INVALID_VALUE", "${arrow.value} is not a scoring zone on an 80cm 6-ring face"))
    }
    if (arrow.isX && arrow.value != 10) {
        errors.add(ValidationError("ARROW_X_ON_NON_TEN", "X can only be recorded on a 10"))
    }
    return errors
}

fun validateRound(round: Round, sessionType: SessionType): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (round.arrows.size > ARROWS_PER_ROUND) {
        errors.add(ValidationError("ROUND_TOO_MANY_ARROWS", "A round holds at most $ARROWS_PER_ROUND arrows"))
    }
    val maxIndex = ROUNDS_PER_SESSION.getValue(sessionType)
    if (round.index < 1 || round.index > maxIndex) {
        errors.add(ValidationError("ROUND_INDEX_OUT_OF_RANGE", "A $sessionType session holds rounds 1 to $maxIndex"))
    }
    round.arrows.forEach { errors.addAll(validateArrow(it)) }
    return errors
}

fun validateSession(session: Session, rounds: List<Round>): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (rounds.isEmpty()) {
        errors.add(ValidationError("SESSION_NO_ROUNDS", "A session must contain at least one started round"))
    }
    // A session with fewer rounds than its type's limit is valid — just incomplete.
    // Only exceeding the limit is a real error.
    val limit = ROUNDS_PER_SESSION.getValue(session.type)
    if (rounds.size > limit) {
        errors.add(ValidationError("SESSION_ROUND_COUNT", "A ${session.type} session holds at most $limit rounds"))
    }
    val indexes = rounds.map { it.index }
    if (indexes.toSet().size != indexes.size) {
        errors.add(ValidationError("SESSION_DUPLICATE_ROUND_INDEX", "Round indexes within a session must be unique"))
    }
    rounds.forEach { errors.addAll(validateRound(it, session.type)) }
    return errors
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.core.ValidationTest"`
Expected: PASS — 13 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/archery/tracker/core/Validation.kt app/src/test/java/com/archery/tracker/core/ValidationTest.kt
git commit -m "feat: add domain validation rules"
```

---

### Task 4: Scoring conformance fixture test

The repo already carries `fixtures/scoring-conformance.json` (copied from phase 1) — a language-neutral fixture the TypeScript scoring implementation is already proven against. This task proves the Kotlin implementation agrees with the exact same fixture, so "both languages agree on scoring" is a checked fact rather than an assumption.

**Files:**
- Test: `app/src/test/java/com/archery/tracker/core/ConformanceTest.kt`

**Interfaces:**
- Consumes: `Arrow`, `ArrowValue`, `roundTotal`, `endTotals`, `xCount`, `tenCount`, `isRoundComplete` from Tasks 1–2; reads `fixtures/scoring-conformance.json` from the repo root at test time (Gradle's default unit-test working directory is the module directory, `app/`, so the fixture is reached via `../fixtures/scoring-conformance.json`).
- Produces: nothing consumed by later tasks — this is a standalone correctness check.

- [ ] **Step 1: Write the failing conformance test**

`app/src/test/java/com/archery/tracker/core/ConformanceTest.kt`:

```kotlin
package com.archery.tracker.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@Serializable
private data class ConformanceExpected(
    val total: Int,
    val xCount: Int,
    val tenCount: Int,
    val endTotals: List<Int>,
    val complete: Boolean,
)

@Serializable
private data class ConformanceCase(
    val name: String,
    val arrows: List<JsonArray>,
    val expected: ConformanceExpected,
)

@Serializable
private data class ConformanceFixture(
    val description: String,
    val cases: List<ConformanceCase>,
)

class ConformanceTest {

    private fun decode(pairs: List<JsonArray>): List<Arrow> = pairs.map { pair ->
        Arrow(
            value = pair[0].jsonPrimitive.int,
            isX = pair[1].jsonPrimitive.boolean,
        )
    }

    @Test
    fun `contains cases`() {
        val fixture = loadFixture()
        assertTrue(fixture.cases.isNotEmpty())
    }

    @Test
    fun `every fixture case matches the Kotlin scoring implementation`() {
        val fixture = loadFixture()
        for (case in fixture.cases) {
            val arrows = decode(case.arrows)
            assertEquals("${case.name}: total", case.expected.total, roundTotal(arrows))
            assertEquals("${case.name}: xCount", case.expected.xCount, xCount(arrows))
            assertEquals("${case.name}: tenCount", case.expected.tenCount, tenCount(arrows))
            assertEquals("${case.name}: endTotals", case.expected.endTotals, endTotals(arrows))
            assertEquals("${case.name}: complete", case.expected.complete, isRoundComplete(arrows))
        }
    }

    private fun loadFixture(): ConformanceFixture {
        val file = File("../fixtures/scoring-conformance.json")
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(file.readText())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.core.ConformanceTest"`
Expected: FAIL only if `roundTotal`/`endTotals`/etc. disagree with the fixture — since Tasks 1–3 are already correct, this should actually PASS on first run. Run it anyway to confirm the file loads and decodes correctly (a `FileNotFoundException` here means the working-directory assumption above is wrong for your Gradle version — adjust the relative path and re-run).

- [ ] **Step 3: Run the tests to confirm they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.core.ConformanceTest"`
Expected: PASS — 2 tests. If any case fails, the Kotlin scoring implementation has a real bug relative to the proven TypeScript one — fix the Kotlin code, never the fixture.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/archery/tracker/core/ConformanceTest.kt
git commit -m "test: verify Kotlin scoring against the shared conformance fixture"
```

---

### Task 5: Room entities, DAO, and database

**Files:**
- Create: `app/src/main/java/com/archery/tracker/data/local/SessionEntity.kt`
- Create: `app/src/main/java/com/archery/tracker/data/local/RoundEntity.kt`
- Create: `app/src/main/java/com/archery/tracker/data/local/ArrowListConverter.kt`
- Create: `app/src/main/java/com/archery/tracker/data/local/ArcheryDao.kt`
- Create: `app/src/main/java/com/archery/tracker/data/local/ArcheryDatabase.kt`
- Test: `app/src/test/java/com/archery/tracker/data/local/ArcheryDaoTest.kt`

**Interfaces:**
- Consumes: `Arrow` from Task 1.
- Produces: `SessionEntity`, `RoundEntity` (Room entities, plain-string enum fields, `dirty: Boolean`), `ArrowListConverter`, `ArcheryDao` with `upsertSession`, `upsertRound`, `getAllSessions()` (Flow), `getRoundsForSession(sessionId)`, `getDirtySessions()`, `getDirtyRounds()`, `clearSessionDirty(id)`, `clearRoundDirty(id)`, `deleteSession(id)`, `deleteRoundsForSession(sessionId)`; `ArcheryDatabase`.

- [ ] **Step 1: Write the failing DAO test**

`app/src/test/java/com/archery/tracker/data/local/ArcheryDaoTest.kt`:

```kotlin
package com.archery.tracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.core.Arrow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArcheryDaoTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var dao: ArcheryDao

    private fun session(id: String = "s1", dirty: Boolean = true) = SessionEntity(
        id = id, date = "2026-08-01", type = "practice", timeOfDay = "morning",
        arrowSet = "ACC", poundage = 50.0, notes = null,
        updatedAt = "2026-08-01T00:00:00Z", dirty = dirty,
    )

    private fun round(id: String = "r1", sessionId: String = "s1", dirty: Boolean = true) = RoundEntity(
        id = id, sessionId = sessionId, index = 1, targetPosition = "A",
        arrows = listOf(Arrow(9, false)), notes = null,
        updatedAt = "2026-08-01T00:00:00Z", dirty = dirty,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ArcheryDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.archeryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsertSession then getAllSessions returns it`() = runBlocking {
        dao.upsertSession(session())
        val all = dao.getAllSessions().first()
        assertEquals(1, all.size)
        assertEquals("s1", all[0].id)
    }

    @Test
    fun `upsertSession overwrites on repeated insert with the same id`() = runBlocking {
        dao.upsertSession(session())
        dao.upsertSession(session().copy(poundage = 52.0))
        val all = dao.getAllSessions().first()
        assertEquals(1, all.size)
        assertEquals(52.0, all[0].poundage, 0.0)
    }

    @Test
    fun `round-trips the arrows list through the type converter`() = runBlocking {
        dao.upsertSession(session())
        val arrows = listOf(Arrow(10, true), Arrow(9, false), Arrow(0, false))
        dao.upsertRound(round().copy(arrows = arrows))
        val rounds = dao.getRoundsForSession("s1")
        assertEquals(arrows, rounds[0].arrows)
    }

    @Test
    fun `getDirtySessions and getDirtyRounds return only dirty rows`() = runBlocking {
        dao.upsertSession(session(id = "s1", dirty = true))
        dao.upsertSession(session(id = "s2", dirty = false))
        dao.upsertRound(round(id = "r1", sessionId = "s1", dirty = true))
        dao.upsertRound(round(id = "r2", sessionId = "s1", dirty = false))

        assertEquals(listOf("s1"), dao.getDirtySessions().map { it.id })
        assertEquals(listOf("r1"), dao.getDirtyRounds().map { it.id })
    }

    @Test
    fun `clearSessionDirty and clearRoundDirty flip dirty to false`() = runBlocking {
        dao.upsertSession(session())
        dao.upsertRound(round())
        dao.clearSessionDirty("s1")
        dao.clearRoundDirty("r1")
        assertTrue(dao.getDirtySessions().isEmpty())
        assertTrue(dao.getDirtyRounds().isEmpty())
    }

    @Test
    fun `deleteSession removes the session and deleteRoundsForSession removes its rounds`() = runBlocking {
        dao.upsertSession(session())
        dao.upsertRound(round())
        dao.deleteRoundsForSession("s1")
        dao.deleteSession("s1")
        assertTrue(dao.getAllSessions().first().isEmpty())
        assertTrue(dao.getRoundsForSession("s1").isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.data.local.ArcheryDaoTest"`
Expected: FAIL — none of the Room classes exist yet.

- [ ] **Step 3: Implement the type converter**

`app/src/main/java/com/archery/tracker/data/local/ArrowListConverter.kt`:

```kotlin
package com.archery.tracker.data.local

import androidx.room.TypeConverter
import com.archery.tracker.core.Arrow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArrowListConverter {
    @TypeConverter
    fun fromArrowList(arrows: List<Arrow>): String = Json.encodeToString(arrows)

    @TypeConverter
    fun toArrowList(json: String): List<Arrow> =
        if (json.isBlank()) emptyList() else Json.decodeFromString(json)
}
```

- [ ] **Step 4: Implement the entities**

`app/src/main/java/com/archery/tracker/data/local/SessionEntity.kt`:

```kotlin
package com.archery.tracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val date: String,
    val type: String,
    val timeOfDay: String,
    val arrowSet: String,
    val poundage: Double,
    val notes: String?,
    val updatedAt: String,
    val dirty: Boolean,
)
```

`app/src/main/java/com/archery/tracker/data/local/RoundEntity.kt`:

```kotlin
package com.archery.tracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.archery.tracker.core.Arrow

@Entity(tableName = "rounds")
@TypeConverters(ArrowListConverter::class)
data class RoundEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val index: Int,
    val targetPosition: String,
    val arrows: List<Arrow>,
    val notes: String?,
    val updatedAt: String,
    val dirty: Boolean,
)
```

- [ ] **Step 5: Implement the DAO**

`app/src/main/java/com/archery/tracker/data/local/ArcheryDao.kt`:

```kotlin
package com.archery.tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArcheryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRound(round: RoundEntity)

    @Query("SELECT * FROM sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM rounds WHERE sessionId = :sessionId ORDER BY `index` ASC")
    suspend fun getRoundsForSession(sessionId: String): List<RoundEntity>

    @Query("SELECT * FROM sessions WHERE dirty = 1")
    suspend fun getDirtySessions(): List<SessionEntity>

    @Query("SELECT * FROM rounds WHERE dirty = 1")
    suspend fun getDirtyRounds(): List<RoundEntity>

    @Query("UPDATE sessions SET dirty = 0 WHERE id = :id")
    suspend fun clearSessionDirty(id: String)

    @Query("UPDATE rounds SET dirty = 0 WHERE id = :id")
    suspend fun clearRoundDirty(id: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM rounds WHERE sessionId = :sessionId")
    suspend fun deleteRoundsForSession(sessionId: String)
}
```

- [ ] **Step 6: Implement the database**

`app/src/main/java/com/archery/tracker/data/local/ArcheryDatabase.kt`:

```kotlin
package com.archery.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [SessionEntity::class, RoundEntity::class], version = 1, exportSchema = false)
@TypeConverters(ArrowListConverter::class)
abstract class ArcheryDatabase : RoomDatabase() {
    abstract fun archeryDao(): ArcheryDao
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.data.local.ArcheryDaoTest"`
Expected: PASS — 6 tests.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/archery/tracker/data/local app/src/test/java/com/archery/tracker/data/local
git commit -m "feat: add Room entities, DAO, and database"
```

---

### Task 6: Retrofit API interface and DTOs

The backend's real JSON body fields are camelCase throughout (verified live: `{"id":"...","userId":"archer","date":"2026-08-02",...}`) — only **query parameters** use snake_case (`time_of_day`, `target_position`, `arrow_set`), matching the web client's convention. DTOs below map 1:1 to Kotlin property names with no `@SerialName` needed for body fields; query parameter names are set via Retrofit's `@Query("time_of_day")` annotations, not in the DTOs.

**Files:**
- Create: `app/src/main/java/com/archery/tracker/data/remote/Dto.kt`
- Create: `app/src/main/java/com/archery/tracker/data/remote/ArcheryApi.kt`
- Test: `app/src/test/java/com/archery/tracker/data/remote/DtoTest.kt`

**Interfaces:**
- Consumes: nothing (DTOs are wire-format types, independent of `core`).
- Produces: `ArrowDto`, `SessionDto`, `RoundDto`, `SessionWithRoundsDto`, `SeriesPointDto`, `RoundPointDto`, `BestMarkersDto`, `GapViewDto`, `TrendViewDto`, `DistributionBucketDto`, `ConsistencyViewDto`, `PositionAverageDto`, `PatternsViewDto`, `StatsResponseDto`, `SyncRequestDto`, `SyncResponseDto`; `ArcheryApi` (Retrofit interface).

- [ ] **Step 1: Write the failing DTO serialization test**

`app/src/test/java/com/archery/tracker/data/remote/DtoTest.kt`:

```kotlin
package com.archery.tracker.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SessionDto round-trips through encode and decode`() {
        val original = SessionDto(
            id = "s1", userId = "archer", date = "2026-08-01", type = "practice",
            timeOfDay = "morning", arrowSet = "ACC", poundage = 50.0,
            notes = null, updatedAt = "2026-08-01T00:00:00Z",
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SessionDto>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `RoundDto round-trips arrows through encode and decode`() {
        val original = RoundDto(
            id = "r1", sessionId = "s1", index = 1, targetPosition = "A",
            arrows = listOf(ArrowDto(value = 10, isX = true), ArrowDto(value = 9, isX = false)),
            notes = null, updatedAt = "2026-08-01T00:00:00Z",
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<RoundDto>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `StatsResponseDto decodes a realistic empty-data payload from the live backend`() {
        val body = """
            {"roundCount":0,"gap":{"practiceAverage":null,"competitionAverage":null,"gap":null,
            "gapOverTime":[],"arrowSetMismatch":false,"insufficient":"Needs at least 1 complete competition round and 3 complete practice rounds."},
            "trend":{"practice":[],"competition":[],"practiceRollingAverage":[],"competitionRollingAverage":[],
            "bestEver":{"practice":null,"competition":null},"bestLast12Months":{"practice":null,"competition":null},
            "insufficient":"Needs at least 3 complete rounds."},
            "consistency":{"distribution":[{"value":10,"count":0},{"value":9,"count":0},{"value":8,"count":0},
            {"value":7,"count":0},{"value":6,"count":0},{"value":5,"count":0},{"value":0,"count":0}],
            "xRate":0,"tenPlusXRate":0,"averageArrowValue":0,"standardDeviationOverTime":[],
            "insufficient":"Needs at least 3 complete rounds."},
            "patterns":{"byEndPosition":[],"byRoundPosition":[],"insufficient":"Needs at least 3 complete rounds."}}
        """.trimIndent()

        val stats = json.decodeFromString<StatsResponseDto>(body)
        assertEquals(0, stats.roundCount)
        assertNull(stats.gap.gap)
        assertEquals(7, stats.consistency.distribution.size)
        assertEquals("Needs at least 3 complete rounds.", stats.trend.insufficient)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.data.remote.DtoTest"`
Expected: FAIL — `Dto.kt` does not exist yet.

- [ ] **Step 3: Implement the DTOs**

`app/src/main/java/com/archery/tracker/data/remote/Dto.kt`:

```kotlin
package com.archery.tracker.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ArrowDto(val value: Int, val isX: Boolean)

@Serializable
data class SessionDto(
    val id: String,
    val userId: String,
    val date: String,
    val type: String,
    val timeOfDay: String,
    val arrowSet: String,
    val poundage: Double,
    val notes: String?,
    val updatedAt: String,
)

@Serializable
data class RoundDto(
    val id: String,
    val sessionId: String,
    val index: Int,
    val targetPosition: String,
    val arrows: List<ArrowDto>,
    val notes: String?,
    val updatedAt: String,
)

@Serializable
data class SessionWithRoundsDto(
    val id: String,
    val userId: String,
    val date: String,
    val type: String,
    val timeOfDay: String,
    val arrowSet: String,
    val poundage: Double,
    val notes: String?,
    val updatedAt: String,
    val rounds: List<RoundDto>,
)

@Serializable
data class SeriesPointDto(val date: String, val value: Double)

@Serializable
data class RoundPointDto(
    val roundId: String,
    val sessionId: String,
    val roundIndex: Int,
    val date: String,
    val type: String,
    val arrowSet: String,
    val arrows: List<ArrowDto>,
    val total: Int,
    val xCount: Int,
    val tenCount: Int,
)

@Serializable
data class BestMarkersDto(val practice: RoundPointDto?, val competition: RoundPointDto?)

@Serializable
data class GapViewDto(
    val practiceAverage: Double?,
    val competitionAverage: Double?,
    val gap: Double?,
    val gapOverTime: List<SeriesPointDto>,
    val arrowSetMismatch: Boolean,
    val insufficient: String?,
)

@Serializable
data class TrendViewDto(
    val practice: List<RoundPointDto>,
    val competition: List<RoundPointDto>,
    val practiceRollingAverage: List<SeriesPointDto>,
    val competitionRollingAverage: List<SeriesPointDto>,
    val bestEver: BestMarkersDto,
    val bestLast12Months: BestMarkersDto,
    val insufficient: String?,
)

@Serializable
data class DistributionBucketDto(val value: Int, val count: Int)

@Serializable
data class ConsistencyViewDto(
    val distribution: List<DistributionBucketDto>,
    val xRate: Double,
    val tenPlusXRate: Double,
    val averageArrowValue: Double,
    val standardDeviationOverTime: List<SeriesPointDto>,
    val insufficient: String?,
)

@Serializable
data class PositionAverageDto(val position: Int, val average: Double)

@Serializable
data class PatternsViewDto(
    val byEndPosition: List<PositionAverageDto>,
    val byRoundPosition: List<PositionAverageDto>,
    val insufficient: String?,
)

@Serializable
data class StatsResponseDto(
    val roundCount: Int,
    val gap: GapViewDto,
    val trend: TrendViewDto,
    val consistency: ConsistencyViewDto,
    val patterns: PatternsViewDto,
)

@Serializable
data class SyncRequestDto(val sessions: List<SessionDto>, val rounds: List<RoundDto>)

@Serializable
data class SyncResponseDto(val sessions: Int, val rounds: Int)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.data.remote.DtoTest"`
Expected: PASS — 3 tests.

- [ ] **Step 5: Implement the Retrofit interface**

`app/src/main/java/com/archery/tracker/data/remote/ArcheryApi.kt`:

```kotlin
package com.archery.tracker.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.POST

interface ArcheryApi {

    @GET("sessions")
    suspend fun listSessions(
        @Query("type") type: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("time_of_day") timeOfDay: String? = null,
        @Query("target_position") targetPosition: String? = null,
        @Query("arrow_set") arrowSet: String? = null,
    ): List<SessionWithRoundsDto>

    @PUT("sessions/{id}")
    suspend fun putSession(@Path("id") id: String, @Body session: SessionDto): SessionDto

    @DELETE("sessions/{id}")
    suspend fun deleteSession(@Path("id") id: String): Response<Unit>

    @PUT("rounds/{id}")
    suspend fun putRound(@Path("id") id: String, @Body round: RoundDto): RoundDto

    @POST("sync")
    suspend fun sync(@Body request: SyncRequestDto): SyncResponseDto

    @GET("stats")
    suspend fun getStats(
        @Query("type") type: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("time_of_day") timeOfDay: String? = null,
        @Query("target_position") targetPosition: String? = null,
        @Query("arrow_set") arrowSet: String? = null,
    ): StatsResponseDto
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/archery/tracker/data/remote app/src/test/java/com/archery/tracker/data/remote
git commit -m "feat: add Retrofit API interface and DTOs"
```

---

### Task 7: Mappers and repository

**Deviation from the design doc, decided now rather than discovered later:** the design doc's edge-case table says deleting a session "queues a delete for the next sync," implying offline delete support. The backend's `POST /sync` (already deployed, phase 1) only batch-upserts — it has no delete support, and there is no other queueable delete mechanism server-side. Building a client-side "pending deletes" concept the backend can't actually consume would be waste. **Corrected behavior: deleting a session requires connectivity.** `deleteSession` calls `DELETE /sessions/{id}` directly; on success it also deletes locally; on failure, nothing local is touched and the caller surfaces an error. This is simpler and never risks silently discarding data the server was never told to delete.

**Files:**
- Create: `app/src/main/java/com/archery/tracker/data/repository/Mappers.kt`
- Create: `app/src/main/java/com/archery/tracker/data/repository/ArcheryRepository.kt`
- Test: `app/src/test/java/com/archery/tracker/data/repository/MappersTest.kt`
- Test: `app/src/test/java/com/archery/tracker/data/repository/ArcheryRepositoryTest.kt`
- Test: `app/src/test/java/com/archery/tracker/data/repository/FakeArcheryApi.kt`

**Interfaces:**
- Consumes: `Arrow`, `Round`, `Session`, `SessionWithRounds`, `SessionType`, `TimeOfDay`, `TargetPosition` from Task 1; `SessionEntity`, `RoundEntity`, `ArcheryDao` from Task 5; `ArrowDto`, `SessionDto`, `RoundDto`, `SessionWithRoundsDto`, `SyncRequestDto`, `ArcheryApi` from Task 6.
- Produces: mapper extension functions (`SessionEntity.toDomain()`, `Session.toEntity(dirty)`, `RoundEntity.toDomain()`, `Round.toEntity(dirty)`, `SessionDto.toEntity(dirty)`, `RoundDto.toEntity(dirty)`, `Session.toDto()`, `Round.toDto()`); `ArcheryRepository` with `sessions(): Flow<List<SessionWithRounds>>`, `createSessionWithFirstRound(session, firstRound)`, `saveRound(round)`, `deleteSession(id): Result<Unit>`, `syncDirty(): Result<Unit>`, `stats(filters): Result<StatsResponseDto>`.

- [ ] **Step 1: Write the failing mapper tests**

`app/src/test/java/com/archery/tracker/data/repository/MappersTest.kt`:

```kotlin
package com.archery.tracker.data.repository

import com.archery.tracker.core.Arrow
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.RoundEntity
import com.archery.tracker.data.local.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MappersTest {

    @Test
    fun `session entity to domain and back preserves every field`() {
        val entity = SessionEntity(
            id = "s1", date = "2026-08-01", type = "competition", timeOfDay = "evening",
            arrowSet = "Easton X10", poundage = 52.0, notes = "windy",
            updatedAt = "2026-08-01T00:00:00Z", dirty = true,
        )
        val domain = entity.toDomain()
        assertEquals(SessionType.COMPETITION, domain.type)
        assertEquals(TimeOfDay.EVENING, domain.timeOfDay)

        val backToEntity = domain.toEntity(dirty = true)
        assertEquals(entity, backToEntity)
    }

    @Test
    fun `round entity to domain and back preserves arrows and target position`() {
        val entity = RoundEntity(
            id = "r1", sessionId = "s1", index = 2, targetPosition = "C",
            arrows = listOf(Arrow(10, true), Arrow(0, false)),
            notes = null, updatedAt = "2026-08-01T00:00:00Z", dirty = false,
        )
        val domain = entity.toDomain()
        assertEquals(TargetPosition.C, domain.targetPosition)
        assertEquals(2, domain.arrows.size)

        assertEquals(entity, domain.toEntity(dirty = false))
    }

    @Test
    fun `session toDto omits nothing the backend needs and uses a placeholder userId`() {
        val session = Session(
            id = "s1", date = "2026-08-01", type = SessionType.PRACTICE,
            timeOfDay = TimeOfDay.MORNING, arrowSet = "ACC", poundage = 50.0,
            notes = null, updatedAt = "2026-08-01T00:00:00Z",
        )
        val dto = session.toDto()
        assertEquals("s1", dto.id)
        assertEquals("practice", dto.type)
        assertEquals("morning", dto.timeOfDay)
        assertEquals("", dto.userId) // the backend ignores/overwrites this; there is no client identity
    }

    @Test
    fun `sessionDto toEntity marks the row not dirty by default since it came from the server`() {
        val dto = com.archery.tracker.data.remote.SessionDto(
            id = "s1", userId = "archer", date = "2026-08-01", type = "practice",
            timeOfDay = "morning", arrowSet = "ACC", poundage = 50.0,
            notes = null, updatedAt = "2026-08-01T00:00:00Z",
        )
        assertFalse(dto.toEntity().dirty)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.data.repository.MappersTest"`
Expected: FAIL — `Mappers.kt` does not exist yet.

- [ ] **Step 3: Implement the mappers**

`app/src/main/java/com/archery/tracker/data/repository/Mappers.kt`:

```kotlin
package com.archery.tracker.data.repository

import com.archery.tracker.core.Arrow
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.RoundEntity
import com.archery.tracker.data.local.SessionEntity
import com.archery.tracker.data.remote.ArrowDto
import com.archery.tracker.data.remote.RoundDto
import com.archery.tracker.data.remote.SessionDto

private fun SessionType.wire(): String = when (this) {
    SessionType.PRACTICE -> "practice"
    SessionType.COMPETITION -> "competition"
}

private fun sessionTypeFromWire(value: String): SessionType = when (value) {
    "competition" -> SessionType.COMPETITION
    else -> SessionType.PRACTICE
}

private fun TimeOfDay.wire(): String = when (this) {
    TimeOfDay.MORNING -> "morning"
    TimeOfDay.EVENING -> "evening"
}

private fun timeOfDayFromWire(value: String): TimeOfDay = when (value) {
    "evening" -> TimeOfDay.EVENING
    else -> TimeOfDay.MORNING
}

private fun TargetPosition.wire(): String = name

private fun targetPositionFromWire(value: String): TargetPosition =
    TargetPosition.entries.firstOrNull { it.name == value } ?: TargetPosition.A

fun SessionEntity.toDomain(): Session = Session(
    id = id, date = date, type = sessionTypeFromWire(type), timeOfDay = timeOfDayFromWire(timeOfDay),
    arrowSet = arrowSet, poundage = poundage, notes = notes, updatedAt = updatedAt,
)

fun Session.toEntity(dirty: Boolean): SessionEntity = SessionEntity(
    id = id, date = date, type = type.wire(), timeOfDay = timeOfDay.wire(),
    arrowSet = arrowSet, poundage = poundage, notes = notes, updatedAt = updatedAt, dirty = dirty,
)

fun RoundEntity.toDomain(): Round = Round(
    id = id, sessionId = sessionId, index = index, targetPosition = targetPositionFromWire(targetPosition),
    arrows = arrows, notes = notes, updatedAt = updatedAt,
)

fun Round.toEntity(dirty: Boolean): RoundEntity = RoundEntity(
    id = id, sessionId = sessionId, index = index, targetPosition = targetPosition.wire(),
    arrows = arrows, notes = notes, updatedAt = updatedAt, dirty = dirty,
)

fun Session.toDto(): SessionDto = SessionDto(
    id = id, userId = "", date = date, type = type.wire(), timeOfDay = timeOfDay.wire(),
    arrowSet = arrowSet, poundage = poundage, notes = notes, updatedAt = updatedAt,
)

fun SessionDto.toEntity(dirty: Boolean = false): SessionEntity = SessionEntity(
    id = id, date = date, type = type, timeOfDay = timeOfDay,
    arrowSet = arrowSet, poundage = poundage, notes = notes, updatedAt = updatedAt, dirty = dirty,
)

fun Round.toDto(): RoundDto = RoundDto(
    id = id, sessionId = sessionId, index = index, targetPosition = targetPosition.wire(),
    arrows = arrows.map { ArrowDto(it.value, it.isX) }, notes = notes, updatedAt = updatedAt,
)

fun RoundDto.toEntity(dirty: Boolean = false): RoundEntity = RoundEntity(
    id = id, sessionId = sessionId, index = index, targetPosition = targetPosition,
    arrows = arrows.map { Arrow(it.value, it.isX) }, notes = notes, updatedAt = updatedAt, dirty = dirty,
)
```

- [ ] **Step 4: Run the mapper tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.data.repository.MappersTest"`
Expected: PASS — 4 tests.

- [ ] **Step 5: Write the fake API test double**

`app/src/test/java/com/archery/tracker/data/repository/FakeArcheryApi.kt`:

```kotlin
package com.archery.tracker.data.repository

import com.archery.tracker.data.remote.ArcheryApi
import com.archery.tracker.data.remote.RoundDto
import com.archery.tracker.data.remote.SessionDto
import com.archery.tracker.data.remote.SessionWithRoundsDto
import com.archery.tracker.data.remote.StatsResponseDto
import com.archery.tracker.data.remote.SyncRequestDto
import com.archery.tracker.data.remote.SyncResponseDto
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class FakeArcheryApi : ArcheryApi {
    var syncShouldFail = false
    var deleteShouldFail = false
    val syncCalls = mutableListOf<SyncRequestDto>()
    val deleteCalls = mutableListOf<String>()

    override suspend fun listSessions(
        type: String?, from: String?, to: String?,
        timeOfDay: String?, targetPosition: String?, arrowSet: String?,
    ): List<SessionWithRoundsDto> = emptyList()

    override suspend fun putSession(id: String, session: SessionDto): SessionDto = session

    override suspend fun deleteSession(id: String): Response<Unit> {
        deleteCalls.add(id)
        return if (deleteShouldFail) Response.error(500, "".toResponseBody(null))
        else Response.success(204, null)
    }

    override suspend fun putRound(id: String, round: RoundDto): RoundDto = round

    override suspend fun sync(request: SyncRequestDto): SyncResponseDto {
        syncCalls.add(request)
        if (syncShouldFail) throw java.io.IOException("network down")
        return SyncResponseDto(sessions = request.sessions.size, rounds = request.rounds.size)
    }

    override suspend fun getStats(
        type: String?, from: String?, to: String?,
        timeOfDay: String?, targetPosition: String?, arrowSet: String?,
    ): StatsResponseDto = throw NotImplementedError("not needed by repository tests")
}
```

- [ ] **Step 6: Write the failing repository tests**

`app/src/test/java/com/archery/tracker/data/repository/ArcheryRepositoryTest.kt`:

```kotlin
package com.archery.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.core.Arrow
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.ArcheryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArcheryRepositoryTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var api: FakeArcheryApi
    private lateinit var repository: ArcheryRepository

    private fun session(id: String = "s1") = Session(
        id = id, date = "2026-08-01", type = SessionType.PRACTICE, timeOfDay = TimeOfDay.MORNING,
        arrowSet = "ACC", poundage = 50.0, notes = null, updatedAt = "2026-08-01T00:00:00Z",
    )

    private fun round(id: String = "r1", sessionId: String = "s1") = Round(
        id = id, sessionId = sessionId, index = 1, targetPosition = TargetPosition.A,
        arrows = emptyList(), notes = null, updatedAt = "2026-08-01T00:00:00Z",
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries().build()
        api = FakeArcheryApi()
        repository = ArcheryRepository(db.archeryDao(), api)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `createSessionWithFirstRound persists both rows dirty`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())
        val sessions = repository.sessions().first()
        assertEquals(1, sessions.size)
        assertEquals(1, sessions[0].rounds.size)
    }

    @Test
    fun `syncDirty sends only dirty rows and clears their dirty flag on success`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())

        val result = repository.syncDirty()

        assertTrue(result.isSuccess)
        assertEquals(1, api.syncCalls.size)
        assertEquals(1, api.syncCalls[0].sessions.size)
        assertEquals(1, api.syncCalls[0].rounds.size)
        assertTrue(db.archeryDao().getDirtySessions().isEmpty())
        assertTrue(db.archeryDao().getDirtyRounds().isEmpty())
    }

    @Test
    fun `syncDirty leaves rows dirty when the network call fails`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())
        api.syncShouldFail = true

        val result = repository.syncDirty()

        assertTrue(result.isFailure)
        assertFalse(db.archeryDao().getDirtySessions().isEmpty())
        assertFalse(db.archeryDao().getDirtyRounds().isEmpty())
    }

    @Test
    fun `syncDirty is a no-op when nothing is dirty`() = runBlocking {
        val result = repository.syncDirty()
        assertTrue(result.isSuccess)
        assertTrue(api.syncCalls.isEmpty())
    }

    @Test
    fun `saveRound marks the round dirty for the next sync`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())
        repository.syncDirty()

        repository.saveRound(round().copy(arrows = listOf(Arrow(9, false))))

        assertEquals(1, db.archeryDao().getDirtyRounds().size)
    }

    @Test
    fun `deleteSession removes local rows only when the network call succeeds`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())

        val result = repository.deleteSession("s1")

        assertTrue(result.isSuccess)
        assertEquals(1, api.deleteCalls.size)
        assertTrue(repository.sessions().first().isEmpty())
    }

    @Test
    fun `deleteSession keeps local rows when the network call fails`() = runBlocking {
        repository.createSessionWithFirstRound(session(), round())
        api.deleteShouldFail = true

        val result = repository.deleteSession("s1")

        assertTrue(result.isFailure)
        assertEquals(1, repository.sessions().first().size)
    }
}
```

- [ ] **Step 7: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.data.repository.ArcheryRepositoryTest"`
Expected: FAIL — `ArcheryRepository.kt` does not exist yet.

- [ ] **Step 8: Implement the repository**

`app/src/main/java/com/archery/tracker/data/repository/ArcheryRepository.kt`:

```kotlin
package com.archery.tracker.data.repository

import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionWithRounds
import com.archery.tracker.data.local.ArcheryDao
import com.archery.tracker.data.remote.ArcheryApi
import com.archery.tracker.data.remote.StatsResponseDto
import com.archery.tracker.data.remote.SyncRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ArcheryRepository(
    private val dao: ArcheryDao,
    private val api: ArcheryApi,
) {

    fun sessions(): Flow<List<SessionWithRounds>> =
        dao.getAllSessions().map { sessionEntities ->
            sessionEntities.map { entity ->
                val rounds = dao.getRoundsForSession(entity.id).map { it.toDomain() }
                SessionWithRounds(entity.toDomain(), rounds)
            }
        }

    suspend fun createSessionWithFirstRound(session: Session, firstRound: Round) {
        dao.upsertSession(session.toEntity(dirty = true))
        dao.upsertRound(firstRound.toEntity(dirty = true))
    }

    suspend fun saveRound(round: Round) {
        dao.upsertRound(round.toEntity(dirty = true))
    }

    suspend fun deleteSession(id: String): Result<Unit> = runCatching {
        val response = api.deleteSession(id)
        if (!response.isSuccessful) {
            error("Delete failed with status ${response.code()}")
        }
        dao.deleteRoundsForSession(id)
        dao.deleteSession(id)
    }

    suspend fun syncDirty(): Result<Unit> = runCatching {
        val dirtySessions = dao.getDirtySessions()
        val dirtyRounds = dao.getDirtyRounds()
        if (dirtySessions.isEmpty() && dirtyRounds.isEmpty()) return@runCatching

        api.sync(
            SyncRequestDto(
                sessions = dirtySessions.map { it.toDomain().toDto() },
                rounds = dirtyRounds.map { it.toDomain().toDto() },
            ),
        )

        dirtySessions.forEach { dao.clearSessionDirty(it.id) }
        dirtyRounds.forEach { dao.clearRoundDirty(it.id) }
    }

    suspend fun stats(
        type: String? = null, from: String? = null, to: String? = null,
        timeOfDay: String? = null, targetPosition: String? = null, arrowSet: String? = null,
    ): Result<StatsResponseDto> = runCatching {
        api.getStats(type, from, to, timeOfDay, targetPosition, arrowSet)
    }
}
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.data.repository.ArcheryRepositoryTest"`
Expected: PASS — 7 tests.

- [ ] **Step 10: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS — all tests from Tasks 1–7.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/archery/tracker/data/repository app/src/test/java/com/archery/tracker/data/repository
git commit -m "feat: add mappers and repository with offline-first sync"
```

---

### Task 8: WorkManager sync worker

**Files:**
- Create: `app/src/main/java/com/archery/tracker/sync/SyncWorker.kt`
- Create: `app/src/main/java/com/archery/tracker/sync/SyncScheduler.kt`
- Test: `app/src/test/java/com/archery/tracker/sync/SyncWorkerTest.kt`

**Interfaces:**
- Consumes: `ArcheryRepository` from Task 7.
- Produces: `SyncWorker`, `SyncWorkerFactory`, `enqueueSync(context, repository)`.

- [ ] **Step 1: Write the failing worker test**

`app/src/test/java/com/archery/tracker/sync/SyncWorkerTest.kt`:

```kotlin
package com.archery.tracker.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.data.repository.FakeArcheryApi
import androidx.room.Room
import com.archery.tracker.data.local.ArcheryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var api: FakeArcheryApi
    private lateinit var repository: ArcheryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries().build()
        api = FakeArcheryApi()
        repository = ArcheryRepository(db.archeryDao(), api)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `doWork succeeds when syncDirty succeeds`() = runBlocking {
        val worker = TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(SyncWorkerFactory(repository))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork retries when syncDirty fails`() = runBlocking {
        api.syncShouldFail = true
        // Force something dirty so syncDirty actually attempts the network call.
        repository.createSessionWithFirstRound(
            com.archery.tracker.core.Session(
                id = "s1", date = "2026-08-01", type = com.archery.tracker.core.SessionType.PRACTICE,
                timeOfDay = com.archery.tracker.core.TimeOfDay.MORNING, arrowSet = "ACC",
                poundage = 50.0, notes = null, updatedAt = "2026-08-01T00:00:00Z",
            ),
            com.archery.tracker.core.Round(
                id = "r1", sessionId = "s1", index = 1,
                targetPosition = com.archery.tracker.core.TargetPosition.A,
                arrows = emptyList(), notes = null, updatedAt = "2026-08-01T00:00:00Z",
            ),
        )

        val worker = TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(SyncWorkerFactory(repository))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.sync.SyncWorkerTest"`
Expected: FAIL — `SyncWorker.kt` does not exist yet.

- [ ] **Step 3: Implement the worker and its factory**

`app/src/main/java/com/archery/tracker/sync/SyncWorker.kt`:

```kotlin
package com.archery.tracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.archery.tracker.data.repository.ArcheryRepository

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: ArcheryRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = repository.syncDirty()
        return if (result.isSuccess) Result.success() else Result.retry()
    }
}

class SyncWorkerFactory(private val repository: ArcheryRepository) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) {
            SyncWorker(appContext, workerParameters, repository)
        } else {
            null
        }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.sync.SyncWorkerTest"`
Expected: PASS — 2 tests.

- [ ] **Step 5: Implement the scheduler**

`app/src/main/java/com/archery/tracker/sync/SyncScheduler.kt`:

```kotlin
package com.archery.tracker.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

private const val SYNC_WORK_NAME = "archery-sync"

fun enqueueSync(context: Context) {
    val request = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/archery/tracker/sync app/src/test/java/com/archery/tracker/sync
git commit -m "feat: add WorkManager sync worker and scheduler"
```

---

### Task 9: DI container, application class, app shell, and navigation

No dedicated automated test for this task — it is pure wiring with no independent logic to assert on. Verified by a successful build (Step 5) and, structurally, by every later screen task actually resolving its ViewModel through this container.

**Files:**
- Create: `app/src/main/java/com/archery/tracker/di/AppContainer.kt`
- Create: `app/src/main/java/com/archery/tracker/ArcheryApplication.kt`
- Create: `app/src/main/java/com/archery/tracker/ui/MainActivity.kt`
- Create: `app/src/main/java/com/archery/tracker/ui/AppNav.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ArcheryDatabase` from Task 5, `ArcheryApi` from Task 6, `ArcheryRepository` from Task 7, `SyncWorkerFactory`/`enqueueSync` from Task 8.
- Produces: `AppContainer(context)` exposing `val repository: ArcheryRepository`; `ArcheryApplication` (also a `Configuration.Provider` wiring `SyncWorkerFactory`); `MainActivity`; `AppNav` composable; placeholder screen composables so the app compiles (`HistoryScreen`, `AnalysisScreen`, `NewSessionScreen`, `LiveScoringScreen`, `SessionDetailScreen` — each just a `Text("...")` for now; Tasks 10–13 replace them).

- [ ] **Step 1: Implement the DI container**

`app/src/main/java/com/archery/tracker/di/AppContainer.kt`:

```kotlin
package com.archery.tracker.di

import android.content.Context
import androidx.room.Room
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.remote.ArcheryApi
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val BASE_URL = "https://archeryapp-60081207448.development.catalystserverless.in/server/api/"

class AppContainer(context: Context) {

    private val database: ArcheryDatabase = Room.databaseBuilder(
        context.applicationContext, ArcheryDatabase::class.java, "archery.db",
    ).build()

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder().build()

    private val api: ArcheryApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ArcheryApi::class.java)

    val repository: ArcheryRepository = ArcheryRepository(database.archeryDao(), api)
}
```

- [ ] **Step 2: Implement the Application class**

`app/src/main/java/com/archery/tracker/ArcheryApplication.kt`:

```kotlin
package com.archery.tracker

import android.app.Application
import androidx.work.Configuration
import com.archery.tracker.di.AppContainer
import com.archery.tracker.sync.SyncWorkerFactory

class ArcheryApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(SyncWorkerFactory(container.repository))
            .build()
}
```

- [ ] **Step 3: Register the Application class and INTERNET permission already present**

Update `app/src/main/AndroidManifest.xml`'s `<application>` tag to reference it:

```xml
<application
    android:name=".ArcheryApplication"
    android:label="Archery Tracker"
    android:theme="@android:style/Theme.Material.Light.NoActionBar">
```

(The `<uses-permission>` lines from Task 1 are unchanged.)

- [ ] **Step 4: Implement navigation and placeholder screens, then MainActivity**

`app/src/main/java/com/archery/tracker/ui/AppNav.kt`:

```kotlin
package com.archery.tracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.analysis.AnalysisScreen
import com.archery.tracker.ui.history.HistoryScreen
import com.archery.tracker.ui.livescoring.LiveScoringScreen
import com.archery.tracker.ui.newsession.NewSessionScreen
import com.archery.tracker.ui.sessiondetail.SessionDetailScreen

private const val ROUTE_HISTORY = "history"
private const val ROUTE_ANALYSIS = "analysis"
private const val ROUTE_NEW_SESSION = "newSession"
private const val ROUTE_LIVE_SCORING = "liveScoring/{sessionId}/{roundId}"
private const val ROUTE_SESSION_DETAIL = "sessionDetail/{sessionId}"

fun liveScoringRoute(sessionId: String, roundId: String) = "liveScoring/$sessionId/$roundId"
fun sessionDetailRoute(sessionId: String) = "sessionDetail/$sessionId"

@Composable
fun AppNav(container: AppContainer) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == ROUTE_HISTORY,
                    onClick = { navController.navigate(ROUTE_HISTORY) },
                    icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                    label = { Text("History") },
                )
                NavigationBarItem(
                    selected = currentRoute == ROUTE_ANALYSIS,
                    onClick = { navController.navigate(ROUTE_ANALYSIS) },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = "Analysis") },
                    label = { Text("Analysis") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HISTORY,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_HISTORY) { HistoryScreen(container, navController) }
            composable(ROUTE_ANALYSIS) { AnalysisScreen(container) }
            composable(ROUTE_NEW_SESSION) { NewSessionScreen(container, navController) }
            composable(ROUTE_LIVE_SCORING) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
                val roundId = backStackEntry.arguments?.getString("roundId").orEmpty()
                LiveScoringScreen(container, sessionId, roundId, navController)
            }
            composable(ROUTE_SESSION_DETAIL) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
                SessionDetailScreen(container, sessionId, navController)
            }
        }
    }
}
```

Placeholder screens so the app compiles — each replaced by its own task:

`app/src/main/java/com/archery/tracker/ui/history/HistoryScreen.kt`:

```kotlin
package com.archery.tracker.ui.history

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.archery.tracker.di.AppContainer

@Composable
fun HistoryScreen(container: AppContainer, navController: NavController) {
    Text("History")
}
```

Repeat this exact one-line-body pattern for the other four placeholders, each in its own file, each with a `package` declaration matching its directory:
- `app/src/main/java/com/archery/tracker/ui/analysis/AnalysisScreen.kt` — `package com.archery.tracker.ui.analysis` — `fun AnalysisScreen(container: AppContainer)` → `Text("Analysis")`
- `app/src/main/java/com/archery/tracker/ui/newsession/NewSessionScreen.kt` — `package com.archery.tracker.ui.newsession` — `fun NewSessionScreen(container: AppContainer, navController: NavController)` → `Text("New session")`
- `app/src/main/java/com/archery/tracker/ui/livescoring/LiveScoringScreen.kt` — `package com.archery.tracker.ui.livescoring` — `fun LiveScoringScreen(container: AppContainer, sessionId: String, roundId: String, navController: NavController)` → `Text("Live scoring")`
- `app/src/main/java/com/archery/tracker/ui/sessiondetail/SessionDetailScreen.kt` — `package com.archery.tracker.ui.sessiondetail` — `fun SessionDetailScreen(container: AppContainer, sessionId: String, navController: NavController)` → `Text("Session detail")`

`app/src/main/java/com/archery/tracker/ui/MainActivity.kt`:

```kotlin
package com.archery.tracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.archery.tracker.ArcheryApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ArcheryApplication).container
        setContent {
            MaterialTheme {
                Surface {
                    AppNav(container)
                }
            }
        }
    }
}
```

- [ ] **Step 5: Verify the app builds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/archery/tracker/di app/src/main/java/com/archery/tracker/ArcheryApplication.kt app/src/main/java/com/archery/tracker/ui app/src/main/AndroidManifest.xml
git commit -m "feat: add DI container, application class, app shell, and navigation"
```

---

### Task 10: New session flow

Same invariant as phase 1 (spec §5.1): a session must never exist with zero rounds, enforced by creating the session and its first round together. This plan applies phase 1's orphaned-session fix **from the start**: the session's id is generated once (stable across a retry), not regenerated on every attempt — phase 1 shipped without this and had to patch it in after a review found a partial-failure could orphan a roundless session.

**Files:**
- Create: `app/src/main/java/com/archery/tracker/ui/newsession/NewSessionDefaults.kt`
- Create: `app/src/main/java/com/archery/tracker/ui/newsession/NewSessionViewModel.kt`
- Replace: `app/src/main/java/com/archery/tracker/ui/newsession/NewSessionScreen.kt`
- Test: `app/src/test/java/com/archery/tracker/ui/newsession/NewSessionDefaultsTest.kt`
- Test: `app/src/test/java/com/archery/tracker/ui/newsession/NewSessionViewModelTest.kt`

**Interfaces:**
- Consumes: `Session`, `Round`, `SessionType`, `TimeOfDay`, `TargetPosition`, `SessionWithRounds` from Task 1; `ArcheryRepository` from Task 7; `liveScoringRoute` from Task 9.
- Produces: `SessionDefaults(arrowSet: String, poundage: Double)`, `FALLBACK_DEFAULTS`, `deriveDefaults(sessions, type)`; `NewSessionViewModel` with a `StateFlow<NewSessionUiState>` and `fun start()`.

- [ ] **Step 1: Write the failing defaults test**

`app/src/test/java/com/archery/tracker/ui/newsession/NewSessionDefaultsTest.kt`:

```kotlin
package com.archery.tracker.ui.newsession

import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.SessionWithRounds
import com.archery.tracker.core.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Test

class NewSessionDefaultsTest {

    private fun sessionWithRounds(
        id: String, date: String, type: SessionType, arrowSet: String, poundage: Double,
    ) = SessionWithRounds(
        Session(id, date, type, TimeOfDay.MORNING, arrowSet, poundage, null, ""),
        emptyList(),
    )

    @Test
    fun `falls back when there is no history`() {
        assertEquals(FALLBACK_DEFAULTS, deriveDefaults(emptyList(), SessionType.PRACTICE))
    }

    @Test
    fun `takes the arrow set from the most recent session of the same type`() {
        val sessions = listOf(
            sessionWithRounds("s1", "2026-01-01", SessionType.PRACTICE, "ACC", 50.0),
            sessionWithRounds("s2", "2026-02-01", SessionType.COMPETITION, "Easton X10", 50.0),
        )
        assertEquals("Easton X10", deriveDefaults(sessions, SessionType.COMPETITION).arrowSet)
        assertEquals("ACC", deriveDefaults(sessions, SessionType.PRACTICE).arrowSet)
    }

    @Test
    fun `takes poundage from the most recent session of any type`() {
        val sessions = listOf(
            sessionWithRounds("s1", "2026-01-01", SessionType.PRACTICE, "ACC", 50.0),
            sessionWithRounds("s2", "2026-02-01", SessionType.COMPETITION, "Easton X10", 52.0),
        )
        assertEquals(52.0, deriveDefaults(sessions, SessionType.PRACTICE).poundage, 0.0)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.newsession.NewSessionDefaultsTest"`
Expected: FAIL — `NewSessionDefaults.kt` does not exist yet.

- [ ] **Step 3: Implement the defaults**

`app/src/main/java/com/archery/tracker/ui/newsession/NewSessionDefaults.kt`:

```kotlin
package com.archery.tracker.ui.newsession

import com.archery.tracker.core.SessionType
import com.archery.tracker.core.SessionWithRounds

data class SessionDefaults(val arrowSet: String, val poundage: Double)

val FALLBACK_DEFAULTS = SessionDefaults(arrowSet = "", poundage = 50.0)

private fun mostRecent(sessions: List<SessionWithRounds>): SessionWithRounds? =
    sessions.maxByOrNull { it.session.date }

fun deriveDefaults(sessions: List<SessionWithRounds>, type: SessionType): SessionDefaults {
    val latestOfType = mostRecent(sessions.filter { it.session.type == type })
    val latestOverall = mostRecent(sessions)
    return SessionDefaults(
        arrowSet = latestOfType?.session?.arrowSet ?: FALLBACK_DEFAULTS.arrowSet,
        poundage = latestOverall?.session?.poundage ?: FALLBACK_DEFAULTS.poundage,
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.newsession.NewSessionDefaultsTest"`
Expected: PASS — 3 tests.

- [ ] **Step 5: Write the failing ViewModel test**

`app/src/test/java/com/archery/tracker/ui/newsession/NewSessionViewModelTest.kt`:

```kotlin
package com.archery.tracker.ui.newsession

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.core.SessionType
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.data.repository.FakeArcheryApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NewSessionViewModelTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var repository: ArcheryRepository
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ArcheryRepository(db.archeryDao(), FakeArcheryApi())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `start creates a session and its first round together`() = runTest(dispatcher) {
        val viewModel = NewSessionViewModel(repository)
        viewModel.updateArrowSet("ACC")
        viewModel.start()
        dispatcher.scheduler.advanceUntilIdle()

        val sessions = db.archeryDao().getAllSessions()
        assertEquals(1, sessions.first().let { it.size })
        val rounds = db.archeryDao().getRoundsForSession(sessions.first()[0].id)
        assertEquals(1, rounds.size)
        assertEquals(0, rounds[0].arrows.size)
    }

    @Test
    fun `start reuses the same session id across a retry so a partial failure cannot orphan a session`() = runTest(dispatcher) {
        val viewModel = NewSessionViewModel(repository)
        val idBefore = viewModel.uiState.value.sessionId
        viewModel.start()
        dispatcher.scheduler.advanceUntilIdle()
        val idAfter = viewModel.uiState.value.sessionId

        assertEquals(idBefore, idAfter)
        assertNotNull(idBefore)
    }

    @Test
    fun `pre-fills poundage from history on type change`() = runTest(dispatcher) {
        // Seed one prior session with a distinctive poundage.
        val seedViewModel = NewSessionViewModel(repository)
        seedViewModel.updatePoundage(53.0)
        seedViewModel.start()
        dispatcher.scheduler.advanceUntilIdle()

        val viewModel = NewSessionViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(53.0, viewModel.uiState.value.poundage, 0.0)
    }
}
```

- [ ] **Step 6: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.newsession.NewSessionViewModelTest"`
Expected: FAIL — `NewSessionViewModel.kt` does not exist yet.

- [ ] **Step 7: Implement the ViewModel**

`app/src/main/java/com/archery/tracker/ui/newsession/NewSessionViewModel.kt`:

```kotlin
package com.archery.tracker.ui.newsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class NewSessionUiState(
    val sessionId: String = UUID.randomUUID().toString(),
    val roundId: String = UUID.randomUUID().toString(),
    val type: SessionType = SessionType.PRACTICE,
    val date: String = Instant.now().toString().substring(0, 10),
    val timeOfDay: TimeOfDay = TimeOfDay.MORNING,
    val targetPosition: TargetPosition = TargetPosition.A,
    val arrowSet: String = "",
    val poundage: Double = 50.0,
    val error: String? = null,
    val started: Boolean = false,
)

class NewSessionViewModel(private val repository: ArcheryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(NewSessionUiState())
    val uiState: StateFlow<NewSessionUiState> = _uiState.asStateFlow()

    init {
        loadDefaults()
    }

    private fun loadDefaults() {
        viewModelScope.launch {
            repository.sessions().collect { sessions ->
                val defaults = com.archery.tracker.ui.newsession.deriveDefaults(sessions, _uiState.value.type)
                _uiState.value = _uiState.value.copy(arrowSet = defaults.arrowSet, poundage = defaults.poundage)
                return@collect // one snapshot is enough; not a live subscription for this form
            }
        }
    }

    fun updateType(type: SessionType) { _uiState.value = _uiState.value.copy(type = type) }
    fun updateDate(date: String) { _uiState.value = _uiState.value.copy(date = date) }
    fun updateTimeOfDay(timeOfDay: TimeOfDay) { _uiState.value = _uiState.value.copy(timeOfDay = timeOfDay) }
    fun updateTargetPosition(position: TargetPosition) { _uiState.value = _uiState.value.copy(targetPosition = position) }
    fun updateArrowSet(arrowSet: String) { _uiState.value = _uiState.value.copy(arrowSet = arrowSet) }
    fun updatePoundage(poundage: Double) { _uiState.value = _uiState.value.copy(poundage = poundage) }

    fun start() {
        viewModelScope.launch {
            val state = _uiState.value
            val now = Instant.now().toString()
            val session = Session(
                id = state.sessionId, date = state.date, type = state.type, timeOfDay = state.timeOfDay,
                arrowSet = state.arrowSet, poundage = state.poundage, notes = null, updatedAt = now,
            )
            val round = Round(
                id = state.roundId, sessionId = state.sessionId, index = 1,
                targetPosition = state.targetPosition, arrows = emptyList(), notes = null, updatedAt = now,
            )
            try {
                repository.createSessionWithFirstRound(session, round)
                _uiState.value = _uiState.value.copy(error = null, started = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Could not start the session. Check your connection and try again.")
            }
        }
    }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.newsession.NewSessionViewModelTest"`
Expected: PASS — 3 tests.

- [ ] **Step 9: Replace the placeholder screen**

`app/src/main/java/com/archery/tracker/ui/newsession/NewSessionScreen.kt`:

```kotlin
package com.archery.tracker.ui.newsession

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.liveScoringRoute

@Composable
fun NewSessionScreen(container: AppContainer, navController: NavController) {
    val viewModel = viewModel<NewSessionViewModel> { NewSessionViewModel(container.repository) }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.started) {
        if (state.started) {
            navController.navigate(liveScoringRoute(state.sessionId, state.roundId))
        }
    }

    Column(Modifier.padding(16.dp)) {
        Text("New session")
        state.error?.let { Text(it) }

        var typeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
            OutlinedTextField(value = state.type.name, onValueChange = {}, readOnly = true, label = { Text("Type") })
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
            androidx.compose.material3.ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                SessionType.entries.forEach { type ->
                    DropdownMenuItem(text = { Text(type.name) }, onClick = { viewModel.updateType(type); typeExpanded = false })
                }
            }
        }

        OutlinedTextField(value = state.date, onValueChange = viewModel::updateDate, label = { Text("Date") })
        OutlinedTextField(value = state.arrowSet, onValueChange = viewModel::updateArrowSet, label = { Text("Arrow set") })
        OutlinedTextField(
            value = state.poundage.toString(),
            onValueChange = { it.toDoubleOrNull()?.let(viewModel::updatePoundage) },
            label = { Text("Poundage") },
        )

        Button(onClick = viewModel::start) { Text("Start round 1") }
    }
}
```

- [ ] **Step 10: Run the full unit test suite and verify the build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/archery/tracker/ui/newsession app/src/test/java/com/archery/tracker/ui/newsession
git commit -m "feat: add new session flow with history-derived defaults"
```

---

### Task 11: Live scoring screen

The one thing this whole client exists for: every tap writes to Room **before** anything else happens, so nothing is ever lost to an app kill, and a sync is triggered once a full end (6 arrows) is reached — matching phase 1's "PUT after each completed end" rule, adapted to "persist locally on every tap, enqueue a sync after each completed end."

**Files:**
- Create: `app/src/main/java/com/archery/tracker/ui/livescoring/LiveScoringViewModel.kt`
- Replace: `app/src/main/java/com/archery/tracker/ui/livescoring/LiveScoringScreen.kt`
- Test: `app/src/test/java/com/archery/tracker/ui/livescoring/LiveScoringViewModelTest.kt`
- Test: `app/src/androidTest/java/com/archery/tracker/ui/livescoring/LiveScoringScreenTest.kt`

**Interfaces:**
- Consumes: `Arrow`, `ArrowValue`, `Round`, `ARROWS_PER_END`, `ARROWS_PER_ROUND`, `endTotals`, `runningTotals`, `roundTotal` from Tasks 1–2; `ArcheryRepository` from Task 7; `enqueueSync` from Task 8.
- Produces: `LiveScoringViewModel` with `StateFlow<LiveScoringUiState>`, `fun add(value: ArrowValue, isX: Boolean)`, `fun undo()`.

The design doc's testing section specifically calls for a **Compose UI test** on this screen (not just a ViewModel unit test) — the ViewModel tests below prove the logic; the UI test below proves the screen is actually wired to it (the real "X" button tap really calls `add(10, true)`, the displayed totals really reflect state).

- [ ] **Step 1: Write the failing ViewModel test**

`app/src/test/java/com/archery/tracker/ui/livescoring/LiveScoringViewModelTest.kt`:

```kotlin
package com.archery.tracker.ui.livescoring

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.data.repository.FakeArcheryApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveScoringViewModelTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var api: FakeArcheryApi
    private lateinit var repository: ArcheryRepository
    private val dispatcher = StandardTestDispatcher()
    private val sessionId = "s1"
    private val roundId = "r1"

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries().build()
        api = FakeArcheryApi()
        repository = ArcheryRepository(db.archeryDao(), api)
        repository.createSessionWithFirstRound(
            Session(sessionId, "2026-08-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, "2026-08-01T00:00:00Z"),
            Round(roundId, sessionId, 1, TargetPosition.A, emptyList(), null, "2026-08-01T00:00:00Z"),
        )
        repository.syncDirty()
        dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `each tap is persisted to Room before anything else`() = runTest(dispatcher) {
        val viewModel = LiveScoringViewModel(repository, sessionId, roundId)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.add(9, isX = false)
        dispatcher.scheduler.advanceUntilIdle()

        val persisted = db.archeryDao().getRoundsForSession(sessionId)[0]
        assertEquals(1, persisted.arrows.size)
        assertEquals(9, persisted.arrows[0].value)
    }

    @Test
    fun `shows a running end total as arrows are entered`() = runTest(dispatcher) {
        val viewModel = LiveScoringViewModel(repository, sessionId, roundId)
        dispatcher.scheduler.advanceUntilIdle()

        repeat(3) { viewModel.add(9, isX = false) }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(27, viewModel.uiState.value.currentEndTotal)
    }

    @Test
    fun `triggers a sync only once a full end of six arrows is reached`() = runTest(dispatcher) {
        val viewModel = LiveScoringViewModel(repository, sessionId, roundId)
        dispatcher.scheduler.advanceUntilIdle()

        repeat(5) { viewModel.add(9, isX = false) }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(api.syncCalls.isEmpty())

        viewModel.add(9, isX = false)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, api.syncCalls.size)
    }

    @Test
    fun `undoes the last arrow`() = runTest(dispatcher) {
        val viewModel = LiveScoringViewModel(repository, sessionId, roundId)
        dispatcher.scheduler.advanceUntilIdle()

        repeat(2) { viewModel.add(9, isX = false) }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.undo()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(9, viewModel.uiState.value.currentEndTotal)
        assertEquals(1, viewModel.uiState.value.arrows.size)
    }

    @Test
    fun `stops accepting arrows at 36`() = runTest(dispatcher) {
        val viewModel = LiveScoringViewModel(repository, sessionId, roundId)
        dispatcher.scheduler.advanceUntilIdle()

        repeat(36) { viewModel.add(5, isX = false) }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(180, viewModel.uiState.value.roundTotal)

        viewModel.add(5, isX = false)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(180, viewModel.uiState.value.roundTotal)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.livescoring.LiveScoringViewModelTest"`
Expected: FAIL — `LiveScoringViewModel.kt` does not exist yet.

- [ ] **Step 3: Implement the ViewModel**

`app/src/main/java/com/archery/tracker/ui/livescoring/LiveScoringViewModel.kt`:

```kotlin
package com.archery.tracker.ui.livescoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archery.tracker.core.ARROWS_PER_END
import com.archery.tracker.core.ARROWS_PER_ROUND
import com.archery.tracker.core.Arrow
import com.archery.tracker.core.ArrowValue
import com.archery.tracker.core.Round
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.endTotals
import com.archery.tracker.core.roundTotal
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

data class LiveScoringUiState(
    val arrows: List<Arrow> = emptyList(),
    val currentEndTotal: Int = 0,
    val roundTotal: Int = 0,
    val roundIndex: Int = 1,
    val loaded: Boolean = false,
)

class LiveScoringViewModel(
    private val repository: ArcheryRepository,
    private val sessionId: String,
    private val roundId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveScoringUiState())
    val uiState: StateFlow<LiveScoringUiState> = _uiState.asStateFlow()

    private var sessionType = com.archery.tracker.core.SessionType.PRACTICE
    private var roundIndex = 1
    private var targetPosition = TargetPosition.A

    init {
        viewModelScope.launch {
            val sessionWithRounds = repository.sessions().first().first { it.session.id == sessionId }
            val round = sessionWithRounds.rounds.first { it.id == roundId }
            sessionType = sessionWithRounds.session.type
            roundIndex = round.index
            targetPosition = round.targetPosition
            updateState(round.arrows)
            _uiState.value = _uiState.value.copy(loaded = true, roundIndex = roundIndex)
        }
    }

    private fun updateState(arrows: List<Arrow>) {
        _uiState.value = _uiState.value.copy(
            arrows = arrows,
            currentEndTotal = endTotals(arrows).lastOrNull() ?: 0,
            roundTotal = roundTotal(arrows),
        )
    }

    fun add(value: ArrowValue, isX: Boolean) {
        val current = _uiState.value.arrows
        if (current.size >= ARROWS_PER_ROUND) return
        val next = current + Arrow(value, isX)
        persist(next)
    }

    fun undo() {
        val current = _uiState.value.arrows
        if (current.isEmpty()) return
        persist(current.dropLast(1))
    }

    private fun persist(next: List<Arrow>) {
        updateState(next)
        viewModelScope.launch {
            val round = Round(
                id = roundId, sessionId = sessionId, index = roundIndex, targetPosition = targetPosition,
                arrows = next, notes = null, updatedAt = Instant.now().toString(),
            )
            repository.saveRound(round)
            if (next.size % ARROWS_PER_END == 0 && next.isNotEmpty()) {
                repository.syncDirty()
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.livescoring.LiveScoringViewModelTest"`
Expected: PASS — 5 tests.

- [ ] **Step 5: Replace the placeholder screen**

The composable is split in two from the start: `LiveScoringScreenContent` takes a `LiveScoringViewModel` directly (so a test can hand it a hand-built ViewModel against an in-memory repository), and `LiveScoringScreen` is the thin route-facing wrapper `AppNav` actually calls, which resolves the ViewModel from `AppContainer`.

`app/src/main/java/com/archery/tracker/ui/livescoring/LiveScoringScreen.kt`:

```kotlin
package com.archery.tracker.ui.livescoring

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.di.AppContainer

private data class Key(val label: String, val value: Int, val isX: Boolean)

private val KEYS = listOf(
    Key("X", 10, true), Key("10", 10, false), Key("9", 9, false),
    Key("8", 8, false), Key("7", 7, false), Key("6", 6, false),
    Key("5", 5, false), Key("M", 0, false),
)

@Composable
fun LiveScoringScreen(container: AppContainer, sessionId: String, roundId: String, navController: NavController) {
    val viewModel = viewModel<LiveScoringViewModel>(key = "$sessionId-$roundId") {
        LiveScoringViewModel(container.repository, sessionId, roundId)
    }
    LiveScoringScreenContent(viewModel)
}

@Composable
fun LiveScoringScreenContent(viewModel: LiveScoringViewModel) {
    val state by viewModel.uiState.collectAsState()

    if (!state.loaded) {
        Text("Loading…")
        return
    }

    Column(Modifier.padding(16.dp)) {
        Text("Round ${state.roundIndex}")
        Text("End total: ${state.currentEndTotal}")
        Text("Round total: ${state.roundTotal}")

        LazyVerticalGrid(columns = GridCells.Fixed(3)) {
            items(KEYS) { key ->
                Button(onClick = { viewModel.add(key.value, key.isX) }) { Text(key.label) }
            }
        }
        Row {
            Button(onClick = viewModel::undo) { Text("Undo") }
        }
    }
}
```

`roundId` is always a real round `id` (the UUID assigned when the round was created — by `NewSessionScreen` for round 1, or by `SessionDetailScreen`'s "add round N" action for later rounds, Task 13), never its `index` — the two are unrelated identifiers and must not be conflated.

- [ ] **Step 6: Write the failing Compose UI test**

`app/src/androidTest/java/com/archery/tracker/ui/livescoring/LiveScoringScreenTest.kt`:

```kotlin
package com.archery.tracker.ui.livescoring

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.data.repository.FakeArcheryApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveScoringScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: ArcheryDatabase
    private lateinit var repository: ArcheryRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ArcheryRepository(db.archeryDao(), FakeArcheryApi())
        repository.createSessionWithFirstRound(
            Session("s1", "2026-08-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, "2026-08-01T00:00:00Z"),
            Round("r1", "s1", 1, TargetPosition.A, emptyList(), null, "2026-08-01T00:00:00Z"),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun tappingNineUpdatesTheDisplayedEndTotalAndPersistsToRoom() {
        val viewModel = LiveScoringViewModel(repository, "s1", "r1")
        composeRule.setContent { LiveScoringScreenContent(viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("9").performClick()
        composeRule.onNodeWithText("9").performClick()
        composeRule.onNodeWithText("9").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("End total: 27").assertExists()

        val persisted = runBlocking { db.archeryDao().getRoundsForSession("s1")[0] }
        assertEquals(3, persisted.arrows.size)
    }

    @Test
    fun undoRemovesTheLastArrow() {
        val viewModel = LiveScoringViewModel(repository, "s1", "r1")
        composeRule.setContent { LiveScoringScreenContent(viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("9").performClick()
        composeRule.onNodeWithText("9").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("End total: 9").assertExists()
    }
}
```

- [ ] **Step 7: Run the Compose UI test**

Run: `./gradlew connectedDebugAndroidTest --tests "com.archery.tracker.ui.livescoring.LiveScoringScreenTest"` (requires a running emulator or connected device)
Expected: PASS — 2 tests.

- [ ] **Step 8: Run the full unit test suite and verify the build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/archery/tracker/ui/livescoring app/src/test/java/com/archery/tracker/ui/livescoring app/src/androidTest/java/com/archery/tracker/ui/livescoring
git commit -m "feat: add live scoring with per-tap Room durability and per-end sync"
```

---

### Task 12: History screen with sync-status indicator

**Files:**
- Create: `app/src/main/java/com/archery/tracker/ui/history/HistorySummary.kt`
- Create: `app/src/main/java/com/archery/tracker/ui/history/HistoryViewModel.kt`
- Replace: `app/src/main/java/com/archery/tracker/ui/history/HistoryScreen.kt`
- Test: `app/src/test/java/com/archery/tracker/ui/history/HistorySummaryTest.kt`
- Test: `app/src/test/java/com/archery/tracker/ui/history/HistoryViewModelTest.kt`

**Interfaces:**
- Consumes: `SessionWithRounds`, `roundTotal`, `xCount`, `isRoundComplete` from Tasks 1–2; `ArcheryRepository` from Task 7; `sessionDetailRoute` from Task 9; needs the DAO's dirty rows too, so `ArcheryRepository` gains one new read-only method here: `hasUnsyncedData(sessionId): Boolean`.
- Produces: `SessionSummary(total, xCount, roundCount, hasIncompleteRound)`, `summarise(sessionWithRounds)`; `HistoryViewModel` with `StateFlow<List<HistoryRow>>` where `HistoryRow(session, summary, isDirty)`.

- [ ] **Step 1: Write the failing summary test**

`app/src/test/java/com/archery/tracker/ui/history/HistorySummaryTest.kt`:

```kotlin
package com.archery.tracker.ui.history

import com.archery.tracker.core.Arrow
import com.archery.tracker.core.ArrowValue
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.SessionWithRounds
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySummaryTest {

    private fun fill(value: ArrowValue, isX: Boolean, count: Int): List<Arrow> = List(count) { Arrow(value, isX) }

    private fun sessionWithRounds(roundsArrows: List<List<Arrow>>) = SessionWithRounds(
        Session("s1", "2026-01-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, ""),
        roundsArrows.mapIndexed { i, arrows ->
            Round("r$i", "s1", i + 1, TargetPosition.A, arrows, null, "")
        },
    )

    @Test
    fun `totals every round in the session`() {
        val summary = summarise(sessionWithRounds(listOf(fill(9, false, 36), fill(10, true, 36))))
        assertEquals(324 + 360, summary.total)
        assertEquals(2, summary.roundCount)
    }

    @Test
    fun `counts Xs across the session`() {
        val summary = summarise(sessionWithRounds(listOf(fill(10, true, 36))))
        assertEquals(36, summary.xCount)
    }

    @Test
    fun `flags a session containing an incomplete round`() {
        val summary = summarise(sessionWithRounds(listOf(fill(9, false, 36), fill(9, false, 12))))
        assertTrue(summary.hasIncompleteRound)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.history.HistorySummaryTest"`
Expected: FAIL — `HistorySummary.kt` does not exist yet.

- [ ] **Step 3: Implement the summary**

`app/src/main/java/com/archery/tracker/ui/history/HistorySummary.kt`:

```kotlin
package com.archery.tracker.ui.history

import com.archery.tracker.core.SessionWithRounds
import com.archery.tracker.core.isRoundComplete
import com.archery.tracker.core.roundTotal
import com.archery.tracker.core.xCount

data class SessionSummary(
    val total: Int,
    val xCount: Int,
    val roundCount: Int,
    val hasIncompleteRound: Boolean,
)

fun summarise(sessionWithRounds: SessionWithRounds): SessionSummary {
    val rounds = sessionWithRounds.rounds
    return SessionSummary(
        total = rounds.sumOf { roundTotal(it.arrows) },
        xCount = rounds.sumOf { xCount(it.arrows) },
        roundCount = rounds.size,
        hasIncompleteRound = rounds.any { !isRoundComplete(it.arrows) },
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.history.HistorySummaryTest"`
Expected: PASS — 3 tests.

- [ ] **Step 5: Add `hasUnsyncedData` to the repository**

Modify `app/src/main/java/com/archery/tracker/data/repository/ArcheryRepository.kt` (from Task 7) — add this method to the `ArcheryRepository` class body, alongside the existing ones:

```kotlin
    suspend fun hasUnsyncedData(sessionId: String): Boolean {
        val sessionDirty = dao.getDirtySessions().any { it.id == sessionId }
        val roundsDirty = dao.getDirtyRounds().any { it.sessionId == sessionId }
        return sessionDirty || roundsDirty
    }
```

- [ ] **Step 6: Write the failing ViewModel test**

`app/src/test/java/com/archery/tracker/ui/history/HistoryViewModelTest.kt`:

```kotlin
package com.archery.tracker.ui.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.data.repository.FakeArcheryApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var api: FakeArcheryApi
    private lateinit var repository: ArcheryRepository
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries().build()
        api = FakeArcheryApi()
        repository = ArcheryRepository(db.archeryDao(), api)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `lists sessions with a summary and marks unsynced ones dirty`() = runTest(dispatcher) {
        repository.createSessionWithFirstRound(
            Session("s1", "2026-01-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, "2026-01-01T00:00:00Z"),
            Round("r1", "s1", 1, TargetPosition.A, emptyList(), null, "2026-01-01T00:00:00Z"),
        )
        val viewModel = HistoryViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val rows = viewModel.rows.value
        assertEquals(1, rows.size)
        assertTrue(rows[0].isDirty)
    }

    @Test
    fun `clears the dirty indicator once synced`() = runTest(dispatcher) {
        repository.createSessionWithFirstRound(
            Session("s1", "2026-01-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, "2026-01-01T00:00:00Z"),
            Round("r1", "s1", 1, TargetPosition.A, emptyList(), null, "2026-01-01T00:00:00Z"),
        )
        repository.syncDirty()
        val viewModel = HistoryViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.rows.value.none { it.isDirty })
    }
}
```

- [ ] **Step 7: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.history.HistoryViewModelTest"`
Expected: FAIL — `HistoryViewModel.kt` does not exist yet.

- [ ] **Step 8: Implement the ViewModel**

`app/src/main/java/com/archery/tracker/ui/history/HistoryViewModel.kt`:

```kotlin
package com.archery.tracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archery.tracker.core.Session
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryRow(val session: Session, val summary: SessionSummary, val isDirty: Boolean)

class HistoryViewModel(private val repository: ArcheryRepository) : ViewModel() {

    private val _rows = MutableStateFlow<List<HistoryRow>>(emptyList())
    val rows: StateFlow<List<HistoryRow>> = _rows.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sessions().collect { sessions ->
                _rows.value = sessions
                    .sortedByDescending { it.session.date }
                    .map { swr ->
                        HistoryRow(
                            session = swr.session,
                            summary = summarise(swr),
                            isDirty = repository.hasUnsyncedData(swr.session.id),
                        )
                    }
            }
        }
    }
}
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.history.HistoryViewModelTest"`
Expected: PASS — 2 tests.

- [ ] **Step 10: Replace the placeholder screen**

`app/src/main/java/com/archery/tracker/ui/history/HistoryScreen.kt`:

```kotlin
package com.archery.tracker.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.sessionDetailRoute

@Composable
fun HistoryScreen(container: AppContainer, navController: NavController) {
    val viewModel = viewModel<HistoryViewModel> { HistoryViewModel(container.repository) }
    val rows by viewModel.rows.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("newSession") }) {
                Icon(Icons.Filled.Add, contentDescription = "New session")
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text("No sessions yet. Log your first round to get started.")
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(rows, key = { it.session.id }) { row ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        Text("${row.session.date} — ${row.session.type}")
                        Text("${row.summary.total} (${row.summary.xCount} X)")
                        if (row.summary.hasIncompleteRound) Text("incomplete")
                        if (row.isDirty) Text("not yet synced")
                    }
                }
            }
        }
    }
}
```

**Note:** tapping a row to navigate to `sessionDetailRoute(row.session.id)` is deliberately left for Task 13 to wire up together with the screen it navigates to, so the two aren't split across tasks in a way that leaves either half untestable on its own.

- [ ] **Step 11: Run the full unit test suite and verify the build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/archery/tracker/ui/history app/src/main/java/com/archery/tracker/data/repository/ArcheryRepository.kt app/src/test/java/com/archery/tracker/ui/history
git commit -m "feat: add history screen with sync-status indicator"
```

---

### Task 13: Session detail screen — scorecard, add round, delete, and wiring from History

Same descending-end display convention as phase 1 (spec §2.3): arrows are stored in entry order but a completed round's scorecard renders each end **descending** by value (X highest among tens), independent of the live-entry order.

**Files:**
- Create: `app/src/main/java/com/archery/tracker/ui/sessiondetail/Scorecard.kt`
- Create: `app/src/main/java/com/archery/tracker/ui/sessiondetail/SessionDetailViewModel.kt`
- Replace: `app/src/main/java/com/archery/tracker/ui/sessiondetail/SessionDetailScreen.kt`
- Modify: `app/src/main/java/com/archery/tracker/ui/history/HistoryScreen.kt` (wire up the row-tap navigation deferred from Task 12)
- Test: `app/src/test/java/com/archery/tracker/ui/sessiondetail/ScorecardTest.kt`
- Test: `app/src/test/java/com/archery/tracker/ui/sessiondetail/SessionDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `Arrow`, `Round`, `ARROWS_PER_END`, `ends`, `endTotals`, `runningTotals`, `roundTotal`, `ROUNDS_PER_SESSION` from Tasks 1–2; `ArcheryRepository` from Task 7; `liveScoringRoute` from Task 9.
- Produces: `arrowLabel(arrow)`, `descendingEnd(end)`, `SessionDetailViewModel` with `StateFlow<SessionDetailUiState>`, `fun addRound(): String` (returns the new round's id for immediate navigation), `fun deleteSession(onDeleted: () -> Unit)`.

- [ ] **Step 1: Write the failing scorecard math test**

`app/src/test/java/com/archery/tracker/ui/sessiondetail/ScorecardTest.kt`:

```kotlin
package com.archery.tracker.ui.sessiondetail

import com.archery.tracker.core.Arrow
import org.junit.Assert.assertEquals
import org.junit.Test

class ScorecardTest {

    @Test
    fun `arrowLabel marks X, miss, and plain values`() {
        assertEquals("X", arrowLabel(Arrow(10, true)))
        assertEquals("M", arrowLabel(Arrow(0, false)))
        assertEquals("9", arrowLabel(Arrow(9, false)))
    }

    @Test
    fun `descendingEnd sorts by value descending with X above a plain ten`() {
        val end = listOf(
            Arrow(8, false), Arrow(10, false), Arrow(9, false),
            Arrow(0, false), Arrow(10, true), Arrow(6, false),
        )
        val sorted = descendingEnd(end).map { arrowLabel(it) }
        assertEquals(listOf("X", "10", "9", "8", "6", "M"), sorted)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.sessiondetail.ScorecardTest"`
Expected: FAIL — `Scorecard.kt` does not exist yet.

- [ ] **Step 3: Implement the scorecard math**

`app/src/main/java/com/archery/tracker/ui/sessiondetail/Scorecard.kt`:

```kotlin
package com.archery.tracker.ui.sessiondetail

import com.archery.tracker.core.Arrow

fun arrowLabel(arrow: Arrow): String = when {
    arrow.isX -> "X"
    arrow.value == 0 -> "M"
    else -> arrow.value.toString()
}

fun descendingEnd(end: List<Arrow>): List<Arrow> =
    end.sortedWith(compareByDescending<Arrow> { it.value }.thenByDescending { it.isX })
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.sessiondetail.ScorecardTest"`
Expected: PASS — 2 tests.

- [ ] **Step 5: Write the failing ViewModel test**

`app/src/test/java/com/archery/tracker/ui/sessiondetail/SessionDetailViewModelTest.kt`:

```kotlin
package com.archery.tracker.ui.sessiondetail

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.data.repository.FakeArcheryApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionDetailViewModelTest {

    private lateinit var db: ArcheryDatabase
    private lateinit var api: FakeArcheryApi
    private lateinit var repository: ArcheryRepository
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries().build()
        api = FakeArcheryApi()
        repository = ArcheryRepository(db.archeryDao(), api)
        repository.createSessionWithFirstRound(
            Session("s1", "2026-01-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, "2026-01-01T00:00:00Z"),
            Round("r1", "s1", 1, TargetPosition.A, emptyList(), null, "2026-01-01T00:00:00Z"),
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the session and its rounds`() = runTest(dispatcher) {
        val viewModel = SessionDetailViewModel(repository, "s1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("s1", viewModel.uiState.value.session?.id)
        assertEquals(1, viewModel.uiState.value.rounds.size)
    }

    @Test
    fun `offers to add a round while under the practice limit and returns the new round id`() = runTest(dispatcher) {
        val viewModel = SessionDetailViewModel(repository, "s1")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canAddRound)

        val newRoundId = viewModel.addRound()
        dispatcher.scheduler.advanceUntilIdle()

        val rounds = db.archeryDao().getRoundsForSession("s1")
        assertEquals(2, rounds.size)
        assertTrue(rounds.any { it.id == newRoundId && it.index == 2 })
    }

    @Test
    fun `deleteSession removes the session and its rounds when the network call succeeds`() = runTest(dispatcher) {
        val viewModel = SessionDetailViewModel(repository, "s1")
        dispatcher.scheduler.advanceUntilIdle()

        var deleted = false
        viewModel.deleteSession { deleted = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(deleted)
        assertTrue(db.archeryDao().getAllSessions().first().isEmpty())
    }
}
```

- [ ] **Step 6: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.sessiondetail.SessionDetailViewModelTest"`
Expected: FAIL — `SessionDetailViewModel.kt` does not exist yet.

- [ ] **Step 7: Implement the ViewModel**

`app/src/main/java/com/archery/tracker/ui/sessiondetail/SessionDetailViewModel.kt`:

```kotlin
package com.archery.tracker.ui.sessiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archery.tracker.core.ROUNDS_PER_SESSION
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class SessionDetailUiState(
    val session: Session? = null,
    val rounds: List<Round> = emptyList(),
    val canAddRound: Boolean = false,
)

class SessionDetailViewModel(
    private val repository: ArcheryRepository,
    private val sessionId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sessions().collect { sessions ->
                val match = sessions.firstOrNull { it.session.id == sessionId } ?: return@collect
                val limit = ROUNDS_PER_SESSION.getValue(match.session.type)
                _uiState.value = SessionDetailUiState(
                    session = match.session,
                    rounds = match.rounds.sortedBy { it.index },
                    canAddRound = match.rounds.size < limit,
                )
            }
        }
    }

    suspend fun addRound(): String {
        val state = _uiState.value
        val session = requireNotNull(state.session)
        val nextIndex = state.rounds.size + 1
        val newRoundId = UUID.randomUUID().toString()
        val round = Round(
            id = newRoundId, sessionId = sessionId, index = nextIndex,
            targetPosition = state.rounds.firstOrNull()?.targetPosition ?: com.archery.tracker.core.TargetPosition.A,
            arrows = emptyList(), notes = null, updatedAt = Instant.now().toString(),
        )
        repository.saveRound(round)
        return newRoundId
    }

    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteSession(sessionId)
            if (result.isSuccess) onDeleted()
        }
    }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.sessiondetail.SessionDetailViewModelTest"`
Expected: PASS — 3 tests.

- [ ] **Step 9: Replace the placeholder screen**

`app/src/main/java/com/archery/tracker/ui/sessiondetail/SessionDetailScreen.kt`:

```kotlin
package com.archery.tracker.ui.sessiondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.core.roundTotal
import com.archery.tracker.core.ends
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.liveScoringRoute
import kotlinx.coroutines.launch

@Composable
fun SessionDetailScreen(container: AppContainer, sessionId: String, navController: NavController) {
    val viewModel = viewModel<SessionDetailViewModel>(key = sessionId) {
        SessionDetailViewModel(container.repository, sessionId)
    }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val session = state.session ?: run { Text("Loading…"); return }

    Column(Modifier.padding(16.dp)) {
        Text("${session.date} · ${session.type} · ${session.timeOfDay} · ${session.arrowSet} · ${session.poundage} lb")

        state.rounds.forEach { round ->
            Text("Round ${round.index} · position ${round.targetPosition} · ${roundTotal(round.arrows)}")
            ends(round.arrows).forEachIndexed { i, end ->
                val sorted = descendingEnd(end).joinToString(" ") { arrowLabel(it) }
                Text("End ${i + 1}: $sorted")
            }
            Button(onClick = { navController.navigate(liveScoringRoute(sessionId, round.id)) }) {
                Text("Edit round ${round.index}")
            }
        }

        if (state.canAddRound) {
            Button(onClick = {
                scope.launch {
                    val newRoundId = viewModel.addRound()
                    navController.navigate(liveScoringRoute(sessionId, newRoundId))
                }
            }) { Text("Add round ${state.rounds.size + 1}") }
        }

        Button(onClick = { showDeleteConfirm = true }) { Text("Delete session") }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete this session and all of its rounds?") },
                text = { Text("This cannot be undone.") },
                confirmButton = {
                    Button(onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSession { navController.popBackStack() }
                    }) { Text("Delete") }
                },
                dismissButton = {
                    Button(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                },
            )
        }
    }
}
```

- [ ] **Step 10: Wire up History's row tap (deferred from Task 12)**

Modify `app/src/main/java/com/archery/tracker/ui/history/HistoryScreen.kt` — wrap each row's `Column` in a clickable modifier navigating to the session's detail:

```kotlin
                items(rows, key = { it.session.id }) { row ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .clickable { navController.navigate(sessionDetailRoute(row.session.id)) },
                    ) {
```

Add the matching import to that file: `import androidx.compose.foundation.clickable`.

- [ ] **Step 11: Run the full unit test suite and verify the build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/archery/tracker/ui/sessiondetail app/src/main/java/com/archery/tracker/ui/history/HistoryScreen.kt app/src/test/java/com/archery/tracker/ui/sessiondetail
git commit -m "feat: add session detail with descending scorecard, add round, and delete"
```

---

### Task 14: Analysis screen

Renders the same `GET /stats` payload the web client's four analysis views consume — no on-device recomputation (Global Constraints, and design doc §2). **No charting library dependency** — bars are drawn with plain `Box` width proportions (a `Box` whose width is a fraction of its parent, matching the simplest possible reading of "distribution"/"pattern" data), consistent with this plan's "no unnecessary dependency" stance; a real chart library can be added later as a pure swap inside this screen if ever wanted, without touching the ViewModel or ther rest of the app.

**Files:**
- Create: `app/src/main/java/com/archery/tracker/ui/analysis/AnalysisViewModel.kt`
- Replace: `app/src/main/java/com/archery/tracker/ui/analysis/AnalysisScreen.kt`
- Test: `app/src/test/java/com/archery/tracker/ui/analysis/AnalysisViewModelTest.kt`

**Interfaces:**
- Consumes: `StatsResponseDto` and its nested DTOs from Task 6; `ArcheryRepository.stats(...)` from Task 7 (extended in Task 12).
- Produces: `AnalysisViewModel` with `StateFlow<AnalysisUiState>` where `AnalysisUiState(stats: StatsResponseDto?, error: String?)`.

- [ ] **Step 1: Write the failing ViewModel test**

`app/src/test/java/com/archery/tracker/ui/analysis/AnalysisViewModelTest.kt`:

```kotlin
package com.archery.tracker.ui.analysis

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.remote.ArcheryApi
import com.archery.tracker.data.remote.ConsistencyViewDto
import com.archery.tracker.data.remote.GapViewDto
import com.archery.tracker.data.remote.PatternsViewDto
import com.archery.tracker.data.remote.RoundDto
import com.archery.tracker.data.remote.SessionDto
import com.archery.tracker.data.remote.SessionWithRoundsDto
import com.archery.tracker.data.remote.StatsResponseDto
import com.archery.tracker.data.remote.SyncRequestDto
import com.archery.tracker.data.remote.SyncResponseDto
import com.archery.tracker.data.remote.TrendViewDto
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

private val emptyStats = StatsResponseDto(
    roundCount = 0,
    gap = GapViewDto(null, null, null, emptyList(), false, "Needs history"),
    trend = TrendViewDto(emptyList(), emptyList(), emptyList(), emptyList(),
        com.archery.tracker.data.remote.BestMarkersDto(null, null),
        com.archery.tracker.data.remote.BestMarkersDto(null, null), "Needs history"),
    consistency = ConsistencyViewDto(emptyList(), 0.0, 0.0, 0.0, emptyList(), "Needs history"),
    patterns = PatternsViewDto(emptyList(), emptyList(), "Needs history"),
)

private class StubApi(private val statsResult: () -> StatsResponseDto) : ArcheryApi {
    override suspend fun listSessions(type: String?, from: String?, to: String?, timeOfDay: String?, targetPosition: String?, arrowSet: String?): List<SessionWithRoundsDto> = emptyList()
    override suspend fun putSession(id: String, session: SessionDto): SessionDto = session
    override suspend fun deleteSession(id: String): Response<Unit> = Response.success(204, null)
    override suspend fun putRound(id: String, round: RoundDto): RoundDto = round
    override suspend fun sync(request: SyncRequestDto): SyncResponseDto = SyncResponseDto(0, 0)
    override suspend fun getStats(type: String?, from: String?, to: String?, timeOfDay: String?, targetPosition: String?, arrowSet: String?): StatsResponseDto = statsResult()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnalysisViewModelTest {

    private lateinit var db: ArcheryDatabase
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcheryDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `loads stats successfully`() = runTest(dispatcher) {
        val repository = ArcheryRepository(db.archeryDao(), StubApi { emptyStats })
        val viewModel = AnalysisViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.stats?.roundCount)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `surfaces an error when the network call fails`() = runTest(dispatcher) {
        val repository = ArcheryRepository(db.archeryDao(), StubApi { throw java.io.IOException("offline") })
        val viewModel = AnalysisViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.analysis.AnalysisViewModelTest"`
Expected: FAIL — `AnalysisViewModel.kt` does not exist yet.

- [ ] **Step 3: Implement the ViewModel**

`app/src/main/java/com/archery/tracker/ui/analysis/AnalysisViewModel.kt`:

```kotlin
package com.archery.tracker.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archery.tracker.data.remote.StatsResponseDto
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnalysisUiState(val stats: StatsResponseDto? = null, val error: String? = null)

class AnalysisViewModel(private val repository: ArcheryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = AnalysisUiState()
            repository.stats().fold(
                onSuccess = { stats -> _uiState.value = AnalysisUiState(stats = stats) },
                onFailure = { _uiState.value = AnalysisUiState(error = "Could not load your statistics.") },
            )
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.archery.tracker.ui.analysis.AnalysisViewModelTest"`
Expected: PASS — 2 tests.

- [ ] **Step 5: Replace the placeholder screen**

`app/src/main/java/com/archery/tracker/ui/analysis/AnalysisScreen.kt`:

```kotlin
package com.archery.tracker.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archery.tracker.data.remote.ConsistencyViewDto
import com.archery.tracker.data.remote.GapViewDto
import com.archery.tracker.data.remote.PatternsViewDto
import com.archery.tracker.data.remote.TrendViewDto
import com.archery.tracker.di.AppContainer

@Composable
private fun Bar(fraction: Float) {
    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(12.dp).background(Color(0xFFFFB020)))
}

@Composable
private fun GapCard(view: GapViewDto) {
    Text("Practice vs competition")
    if (view.insufficient != null) { Text(view.insufficient); return }
    Text("${view.gap}")
    Text("Practice ${view.practiceAverage} · Competition ${view.competitionAverage}")
    if (view.arrowSetMismatch) Text("You shoot different arrows in practice and competition.")
}

@Composable
private fun TrendCard(view: TrendViewDto) {
    Text("Score trend")
    if (view.insufficient != null) { Text(view.insufficient); return }
    Text("Best ever — practice ${view.bestEver.practice?.total ?: "—"}, competition ${view.bestEver.competition?.total ?: "—"}")
    Text("Best in the last 12 months — practice ${view.bestLast12Months.practice?.total ?: "—"}, competition ${view.bestLast12Months.competition?.total ?: "—"}")
}

@Composable
private fun ConsistencyCard(view: ConsistencyViewDto) {
    Text("Consistency")
    if (view.insufficient != null) { Text(view.insufficient); return }
    Text("X rate ${view.xRate}% · 10+X rate ${view.tenPlusXRate}% · average arrow ${view.averageArrowValue}")
    val maxCount = (view.distribution.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    view.distribution.forEach { bucket ->
        val label = if (bucket.value == 0) "M" else bucket.value.toString()
        Text("$label: ${bucket.count}")
        Bar(bucket.count.toFloat() / maxCount)
    }
}

@Composable
private fun PatternsCard(view: PatternsViewDto) {
    Text("Within-session patterns")
    if (view.insufficient != null) { Text(view.insufficient); return }
    Text("Average by end position")
    view.byEndPosition.forEach { entry ->
        Text("End ${entry.position}: ${entry.average}")
        Bar((entry.average / 60).toFloat())
    }
    Text("Average by round position")
    view.byRoundPosition.forEach { entry ->
        Text("Round ${entry.position}: ${entry.average}")
        Bar((entry.average / 360).toFloat())
    }
}

@Composable
fun AnalysisScreen(container: AppContainer) {
    val viewModel = viewModel<AnalysisViewModel> { AnalysisViewModel(container.repository) }
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.padding(16.dp)) {
        Text("Analysis")
        state.error?.let {
            Text(it)
            Button(onClick = viewModel::load) { Text("Try again") }
        }
        state.stats?.let { stats ->
            GapCard(stats.gap)
            TrendCard(stats.trend)
            ConsistencyCard(stats.consistency)
            PatternsCard(stats.patterns)
        }
    }
}
```

- [ ] **Step 6: Run the full unit test suite and verify the build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/archery/tracker/ui/analysis app/src/test/java/com/archery/tracker/ui/analysis
git commit -m "feat: add analysis screen rendering the four server-computed views"
```

---

## Self-Review

**1. Spec coverage** — every section of `docs/superpowers/specs/2026-08-02-archery-android-design.md` maps to a task:

| Spec section | Covered by |
|---|---|
| §1 purpose (live, offline, per-tap scoring) | Task 11 |
| §2 relationship to phase 1 (duplicate scoring, not statistics; no auth) | Tasks 1–4 (duplication + conformance fixture), Task 14 (stats rendered not recomputed), Global Constraints (no auth) |
| §3 architecture, no DI framework, no multi-module split | Task 9; every task's package placement |
| §3.1 networking (Retrofit + kotlinx.serialization) | Task 6 |
| §4 data model (Room entities, arrows as a JSON array not the codec, no outbox table) | Task 5 |
| §5 sync (trigger, payload, success/failure, conflict policy) | Task 7 (repository), Task 8 (WorkManager), Task 11 (per-tap + per-end trigger) |
| §6 screens (live scoring, sessions+sync, session detail, analysis) | Tasks 10–11, 12, 13, 14 respectively |
| §7 error handling (app killed mid-round, sync failure, analysis offline, delete confirm, incomplete round) | Task 11 (Room durability), Task 8 (retry), Task 14 (error+retry), Task 13 (confirm dialog), incomplete-round handling stays server-side by design (never duplicated) |
| §8 testing (conformance fixture, in-memory Room + fake API, Compose UI test) | Task 4, Task 7, Task 11 Steps 6–7 |
| §9 out of scope (iOS, on-device stats, multi-device conflict resolution, login, arrow-position/wind/equipment logging) | Nothing in this plan builds any of these |

**Gap found and closed during this self-review**: the design doc's testing section explicitly calls for a Compose UI test on the live-scoring screen; the original draft of Task 11 only had ViewModel unit tests. Closed by adding Task 11 Steps 6–7 (`LiveScoringScreenTest`) and the `LiveScoringScreenContent`/`LiveScoringScreen` split needed to make it renderable in isolation.

**2. Placeholder scan** — no `TBD`, `TODO`, or "similar to Task N" instructions in any task. One deliberate scope-boundary deviation from the design doc is called out explicitly rather than silently implemented: Task 7's delete-requires-connectivity correction (the design doc implied offline-delete queueing the backend cannot actually support).

**3. Type consistency — fixes applied during this self-review**:
- The live-scoring route originally carried a round's `index` where it needed the round's `id` (an Int where a String identity was needed) — found while drafting Task 11, corrected across Task 9's route definition, Task 10's `NewSessionViewModel`/`NewSessionScreen`, and Task 11's `LiveScoringViewModel`/`LiveScoringScreen` before any task was left inconsistent with its neighbors.
- Screen composables were inconsistently placed — some flat under `ui/`, some correctly nested in their feature package (`ui/newsession/`, `ui/livescoring/`) — with `package` declarations that didn't match either. Corrected everywhere to nest every screen alongside its ViewModel, matching the File Structure section at the top of this plan, with `package` declarations and `AppNav`'s imports fixed to match.
- `FakeArcheryApi`'s deleted-response stub used a deprecated OkHttp static factory (`ResponseBody.create`); corrected to the modern `toResponseBody()` extension.

