# OreoTunes UI Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the requested home, playback dock, glass surface, Now Playing gesture, and library performance refresh while preserving OreoTunes' current identity.

**Architecture:** Continue the existing Android Compose, Navigation, and ViewModel structure. Make surface-specific changes in current files; only introduce a shared helper when it is the smallest way to keep repeated glass styling consistent. Measure the library before changing list work, then fix the proven invalidation or loading bottleneck.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, existing Kyant backdrop dependency, Coil, Android Gradle project.

**Spec:** `docs/superpowers/specs/2026-11-21-oreotunes-ui-refresh-design.md` and acceptance checklist `docs/ui-refresh-brief.md`.

## Global Constraints

- Keep the app's existing visual identity and liquid glass language.
- Keep the home featured hero and merged playback dock as the primary visual moments; keep supporting surfaces quieter and readable.
- Follow Android edge-to-edge and inset behavior, accessible contrast, and minimum touch target guidance.
- Preserve existing information/actions and visible gesture alternatives.
- Do not remove liquid glass as a performance workaround.
- Preserve all worktree changes that predate this task; never reset or overwrite them.
- Do not add dependencies or change architecture unless the existing project cannot satisfy a requirement without it.

## Review Focus

- System navigation modes and edge-to-edge: the dock must not consume system navigation area or apply bottom insets twice. Verify gesture and three-button mode on the available target.
- Theme and contrast: glass text and controls must remain legible in light, dark, and AMOLED themes. Inspect the changed dialog/dock surfaces in each supported theme.
- Gesture arbitration: upward lyrics, downward minimize, and lyrics-to-player transitions must work when drags begin on artwork, lyrics, and the handle; retain Back and visible controls.
- Large libraries and playback ticks: representative scrolling with playback active must improve without moving per-frame/per-tick work into every row. Record comparable before/after frame timing or a stated repeatable proxy.
- Small/large windows and accessibility: preserve scroll access, keyboard reachability where present, minimum 48dp touch targets, and screen-reader labels after layout changes.

---

## File Structure

- `app/src/main/java/com/kurixutian/oreotunes/MainActivity.kt` owns composition-root state collection, the mini-player/navigation dock, and global modal entry points.
- `app/src/main/java/com/kurixutian/oreotunes/ui/components/MiniPlayer.kt` owns mini-player appearance and controls.
- `app/src/main/java/com/kurixutian/oreotunes/ui/screens/HomeScreen.kt` owns the home hero and AI Playlist entry point.
- `app/src/main/java/com/kurixutian/oreotunes/ui/screens/SettingsDialog.kt` and `ModernPlaybackStatsDialog.kt` own Settings and Listening Insights surfaces.
- `app/src/main/java/com/kurixutian/oreotunes/ui/screens/NowPlayingScreen.kt` owns player/lyrics transitions and lyrics surface; `ui/components/NowPlayingArtwork.kt` owns artwork overflow entry.
- `app/src/main/java/com/kurixutian/oreotunes/ui/screens/LibraryScreen.kt`, `ui/components/SongComponents.kt`, and `ui/components/ArtworkThumbnail.kt` own library rows/artwork; `ui/viewmodel/PlayerViewModel.kt` owns playback position updates.
- Other dialog/popup files under `app/src/main/java/com/kurixutian/oreotunes/ui/components/` own the remaining modal surfaces; inventory all actual dialog and popup entry points before applying the same treatment.

## Task 1: Restore the Home Hero and AI Playlist Entry

**Files:**
- Modify: `app/src/main/java/com/kurixutian/oreotunes/MainActivity.kt` (pass the existing root backdrop into Home)
- Modify: `app/src/main/java/com/kurixutian/oreotunes/ui/screens/HomeScreen.kt`
- Create: `app/src/main/java/com/kurixutian/oreotunes/ui/utils/HomeGreeting.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/kurixutian/oreotunes/ui/utils/HomeGreetingTest.kt`
- Reference: committed `HomeScreen.kt` at `HEAD` and `docs/screenshots/home_default.PNG`

**Interfaces:**
- Keep the existing `HomeScreen` callbacks and use `featuredHeroAlbums` and current hero click/play callbacks. Pass the existing root `navigationBackdrop` into Home so its pinned header can use the same captured visual layer; do not add a second full-list backdrop.

- [ ] Compare the current worktree version with the committed hero and record the exact original carousel behavior/content and the separate recommendation blocks to remove.
- [ ] Write a unit test for `homeGreetingResource(hourOfDay: Int): Int` covering 0–11 morning, 12–16 afternoon, and 17–23 evening, then run it and confirm it fails before implementation.
- [ ] Implement `homeGreetingResource` in `HomeGreeting.kt`, add the three greeting resources, and render the greeting from the device's local hour.
- [ ] Keep the Home header pinned. At the top it blends into the background; when content scrolls beneath it, reveal a liquid-glass background. Keep all existing buttons and callbacks unchanged.
- [ ] Restore the featured hero using existing callbacks and assets; preserve surrounding home content and current theme handling.
- [ ] Keep one entry labeled exactly “AI Playlist”; remove separate mood/trip recommendation cards/sections without removing the requested AI feature.
- [ ] Check empty hero data and small-width behavior, then build the app module.

## Task 2: Make the Mini-Player and Navigation a Continuous Dock

**Files:**
- Modify: `app/src/main/java/com/kurixutian/oreotunes/MainActivity.kt`
- Modify: `app/src/main/java/com/kurixutian/oreotunes/ui/components/MiniPlayer.kt`

**Interfaces:**
- Keep existing dock callbacks and navigation routes; preserve distinct player and navigation hit targets.

- [ ] Measure the current nav/system inset relationship in the root `Box`, `MiniPlayer` padding, and `UniversalBottomNavigation` before altering the layout.
- [ ] Apply bottom system insets exactly once so the app bar ends within its app area and system navigation space remains system-owned.
- [ ] Give the mini-player the same liquid glass treatment as navigation and remove the playback-blue fill. Match the user-provided two-state reference: wide player above wide navigation at rest, then one compact horizontal glass dock during downward scrolling.
- [ ] Animate layout, shape, and spacing smoothly between states while preserving each navigation route and playback action as a separate 48dp target; adapt to narrow widths without clipping or hiding controls.
- [ ] Check player controls and nav items retain separate 48dp touch targets, then build and inspect gesture and three-button navigation modes on an available target.

## Task 3: Refine Dialog and Modal Glass Surfaces

**Files:**
- Modify: `app/src/main/java/com/kurixutian/oreotunes/ui/screens/SettingsDialog.kt`
- Modify: `app/src/main/java/com/kurixutian/oreotunes/ui/screens/ModernPlaybackStatsDialog.kt`
- Modify: `app/src/main/java/com/kurixutian/oreotunes/ui/components/SongActionSheet.kt` (also used by Now Playing overflow)
- Modify as inventory requires: dialog/popup files in `app/src/main/java/com/kurixutian/oreotunes/ui/components/`
- Reuse: existing glass/backdrop components and current dialog window blur behavior where applicable.

**Interfaces:**
- Preserve dialog parameters, dismiss behavior, all existing setting controls, all Insights data/actions, and each modal's callbacks.

- [ ] Inventory every actual app `Dialog`, `AlertDialog`, `Popup`, and `DropdownMenu` surface; identify the root container and theme colors at each call site.
- [ ] Improve Settings grouping and scanability while preserving each control and its current callback.
- [ ] Improve Listening Insights hierarchy, spacing, and scanability while preserving every metric and action.
- [ ] Apply a readable liquid-glass surface treatment to Settings, Insights, and all remaining app modal/popup surfaces; centralize only repeated surface code where it reduces total changes.
- [ ] Check light/dark contrast, dim/blur fallback behavior, dismissal, keyboard reachability, and scroll bounds; build and inspect the surfaces on an available Android target.

## Task 4: Unify Now Playing Gestures and Glass Playback Surfaces

**Files:**
- Modify: `app/src/main/java/com/kurixutian/oreotunes/ui/screens/NowPlayingScreen.kt`

**Interfaces:**
- Keep current player callbacks and lyrics data flow; retain Back, lyrics, and minimize controls as gesture alternatives.
- The shared Now Playing overflow is covered by Task 3's `SongActionSheet.kt` surface work.

- [ ] Trace the current drag handlers, shared animation state, thresholds, and overflow callback before changing gesture ownership.
- [ ] Make upward drag open lyrics and downward drag minimize from both player and lyrics content, with one consistent transition direction/state outcome.
- [ ] Apply the existing liquid-glass treatment to the lyrics background and Now Playing overflow surface without reducing text/control contrast.
- [ ] Add or update focused Compose behavior tests for gesture direction/state transitions where the current test setup supports them; run those tests and build the app module.
- [ ] Inspect the full player and lyrics states on an available Android target, including gesture and visible-control paths.

## Task 5: Measure and Fix Library Scrolling Jank

**Files:**
- Inspect/modify as evidence requires: `app/src/main/java/com/kurixutian/oreotunes/MainActivity.kt`
- Inspect/modify as evidence requires: `app/src/main/java/com/kurixutian/oreotunes/ui/viewmodel/PlayerViewModel.kt`
- Inspect/modify as evidence requires: `app/src/main/java/com/kurixutian/oreotunes/ui/screens/LibraryScreen.kt`
- Inspect/modify as evidence requires: `app/src/main/java/com/kurixutian/oreotunes/ui/components/SongComponents.kt` and `ArtworkThumbnail.kt`

**Interfaces:**
- Preserve playback progress for MiniPlayer and Now Playing; keep library song ordering, row identity, artwork, and click behavior.

- [ ] Establish a repeatable baseline on a representative library with playback active and record missed frames/frame-time percentiles or a documented proxy.
- [ ] Compare recomposition behavior with playback position ticks isolated versus included in broad `uiState`; separately compare warm and cold artwork scrolling if root state invalidation does not explain the slowdown.
- [ ] Add a focused regression test for any extracted state/formatting boundary or list behavior the fix changes.
- [ ] Make the smallest change supported by the measurement; keep high-frequency reads in the smallest rendering scope and preserve stable keys/content types.
- [ ] Repeat the same measurement and report the before/after evidence; verify glass remains enabled and build/run the app module.

## Final Verification

- [ ] Review the final diff against the pre-task working tree so no unrelated pre-existing edits were reverted or included accidentally.
- [ ] Run applicable unit/UI tests and the Android app build.
- [ ] Run the app on an available target and inspect the home, dock, Settings, Insights, Now Playing, and lyrics surfaces in available light/dark modes and navigation modes.
- [ ] Record any unavailable device modes, large-library profiling, or visual checks as coverage limits rather than claiming they passed.
