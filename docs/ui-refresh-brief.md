# OreoTunes UI Refresh Brief

## Purpose

Refine OreoTunes' playback and library experience while keeping its liquid glass visual language. Restore the featured hero from the original OreoTunes home experience, improve the relationship between the mini-player and bottom navigation, restore Now Playing gestures, and address the clunky dialogs and library scrolling slowdown.

This document records the requested outcomes. It does not prescribe a rewrite or authorize removing liquid glass.

## Requested changes

### Bottom navigation and mini-player

- Keep the bottom navigation buttons within the app's navigation bar. The bar must not expand as a solid app surface into the phone's system gesture/navigation area; handle system insets so the gesture area remains system space and visually appropriate.
- Restyle the mini-player with the same liquid glass background treatment as the bottom navigation. Remove the current playback-color blue background treatment.
- When the user scrolls down, have the mini-player and bottom navigation visually merge into one continuous dock, as in Orpheus Beta. Preserve usable controls and clear separation of tap targets.
- Keep the dock readable and usable with gesture navigation, other system navigation modes, and edge-to-edge layouts.

### Home and AI playlist entry point

- Restore the featured hero shown in the original OreoTunes home experience. Use the original design/assets/behavior as the reference where available in the repository or history.
- Rename the “Gemini Mood” and “Trip Mix” entry point to **AI Playlist**.
- Remove the separate mood and trip recommendation cards/sections. Keep the requested AI Playlist entry point.

### Liquid glass surfaces

Apply the app's liquid glass treatment to the following surfaces, using the existing visual language and components where practical:

- Settings dialog
- Listening Insights dialog/screen surface
- Other app dialogs and modal surfaces
- Lyrics background
- Now Playing overflow/menu dialog or popup
- Mini-player, matching the bottom navigation dock

Keep text, icons, controls, and modal boundaries legible over translucent surfaces. Do not remove liquid glass as a performance workaround.

### Settings and Listening Insights usability

- Redesign the Settings dialog because its current layout feels clunky.
- Refine Listening Insights for the same reason. Improve its hierarchy, spacing, and scanability while keeping its existing information and actions unless a change is necessary to meet this brief.

### Now Playing gestures

- Restore the upward swipe gesture in Now Playing to open lyrics.
- Preserve/add the downward swipe gesture to minimize Now Playing and lyrics.
- Make the gesture direction, transition, and resulting state consistent whether the user starts from the main Now Playing content or lyrics. Keep visible controls available as an accessible alternative to gestures.

### Library scrolling performance

- Diagnose and fix the lag/performance drop when scrolling the library. It occurs even without liquid glass, so investigate list composition, recomposition, artwork loading, and other scrolling work before attributing it to glass effects.
- Preserve liquid glass while fixing the underlying source of jank.
- Confirm improvement on a representative library with enough items to expose the slowdown, using profiling or another repeatable before/after measurement where practical.

## Design direction

- Keep OreoTunes' existing visual identity and liquid glass language.
- Treat the home featured hero and the merged playback dock as the primary visual moments; keep supporting surfaces quieter and readable.
- Match the original OreoTunes hero and Orpheus Beta dock behavior by inspecting available project references rather than inventing new content or interactions.
- Follow Android edge-to-edge and inset behavior, accessible contrast, and minimum touch target guidance.

## Acceptance criteria

- [ ] App bottom navigation occupies only its intended app area; system gesture/navigation insets do not become an oversized solid app bar.
- [ ] Mini-player uses liquid glass rather than the blue playback background.
- [ ] On downward library scrolling, mini-player and bottom navigation merge into a continuous dock with working, distinct controls.
- [ ] Home includes the original OreoTunes featured hero.
- [ ] The AI entry point is labeled “AI Playlist”; separate mood and trip recommendation sections are gone.
- [ ] Settings, Listening Insights, other dialogs, lyrics, and Now Playing menu surfaces use the liquid glass treatment.
- [ ] Settings and Listening Insights hierarchy/layout are easier to scan and use.
- [ ] Upward swipe opens lyrics; downward swipe minimizes Now Playing/lyrics; visible alternatives remain available.
- [ ] Library scrolling no longer exhibits the current performance drop on a representative collection, with liquid glass retained.
- [ ] Light/dark themes, system navigation modes, accessibility, and small/large screen layouts remain usable.

## Suggested implementation workflow and skills

Apply only the relevant parts of these skills; the list is a handoff for implementation, not a request to redesign unrelated areas.

1. **`using-superpowers`** — check skill and repository guidance before work and follow required workflows.
2. **`brainstorming`** — settle material design choices and document the chosen approach before implementation where the brief leaves a real product decision open.
3. **`compose`** — identify the task path and the existing Compose project conventions first.
4. **`mobile-design-android`** — guide Android-native edge-to-edge, system insets, navigation, touch targets, contrast, and motion.
5. **`compose-ui`** — use for composables, glass surfaces, bottom dock, modal layouts, list performance, gestures, accessibility, and visual behavior.
6. **`impeccable`** — use for the visual/UX audit and refinement of the home, settings, insights, playback, and modal surfaces.
7. **`frontend-design`** — use only if a web-based visual prototype is chosen; this work is primarily native Android UI.
8. **`compose-feature`** — use if the changes require adding/changing a destination or a full screen/dialog feature slice.
9. **`compose-architecture`** — use if work touches state ownership, navigation, ViewModels, or app architecture; otherwise avoid unnecessary architecture changes.
10. **`investigate-first`** and **`systematic-debugging`** — diagnose the library jank and gesture regressions from evidence before changing behavior or optimizing.
11. **`ponytail`** — keep the implementation to the smallest complete changes in the existing app structure.
12. **`test-driven-development`** — use for behavior fixes such as gestures, navigation state, and scroll-related logic where tests can capture the behavior.
13. **`verification-before-completion`** — verify the final changes before claiming the UI or performance issue is resolved; run the UI and inspect it on an available Android target.
14. **`requesting-code-review`** — request review after the multi-surface change is complete.

### Useful skill references for implementation

- `compose-ui/references/edge-to-edge-and-insets.md`
- `compose-ui/references/motion.md`
- `compose-ui/references/lists.md`
- `compose-ui/references/performance-diagnostics.md`
- `compose-ui/references/accessibility.md`
- `compose-ui/references/design-system.md`
- `compose-feature/references/ui-testing.md` for changed UI behavior
- `compose-feature/references/run-and-see.md` before calling visual work complete

## Project references to inspect

- Existing screenshots: `docs/screenshots/home_default.PNG`, `home_dark.PNG`, `home_light.PNG`, `now_playing.PNG`, `lyrics.PNG`, `settings.PNG`, and `insights.PNG`.
- Existing implementation entry points include `HomeScreen.kt`, `LibraryScreen.kt`, `NowPlayingScreen.kt`, `SettingsDialog.kt`, `ModernPlaybackStatsDialog.kt`, `MiniPlayer.kt`, and `GlassComponents.kt`.
- Search repository history or available original assets for the original OreoTunes featured hero and compare the intended dock behavior against the user's Orpheus Beta reference.

