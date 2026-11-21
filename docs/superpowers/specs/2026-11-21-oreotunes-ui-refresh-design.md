# OreoTunes UI Refresh Design

## Intent

Refine OreoTunes playback and library use while retaining its current liquid-glass identity. Restore the original featured home hero, make the bottom playback dock feel continuous, improve modal and lyrics surfaces, restore Now Playing gestures, and fix library scroll jank at its measured cause.

## Goals

- Keep the app dock inside its intended bounds and leave Android system navigation insets usable across navigation modes and edge-to-edge layouts.
- Give the mini-player the existing glass treatment and visually connect it to the bottom navigation while preserving distinct, accessible controls.
- Restore the featured carousel from repository history, label its AI entry point “AI Playlist,” and remove the separate mood/trip recommendation sections.
- Replace the Home “Discover” heading with a time-appropriate greeting, preserving the current action buttons and their behavior.
- Keep the Home header pinned while scrolling: blend it into the background at scroll position zero, then reveal a liquid-glass background as content passes beneath it.
- Apply readable glass surfaces to Settings, Listening Insights, app dialogs and modal surfaces, lyrics, and the Now Playing overflow.
- Improve Settings and Insights hierarchy without removing their existing information or actions.
- Make upward and downward Now Playing gestures consistent across the main content and lyrics, with visible controls as alternatives.
- Diagnose library scrolling with evidence and a repeatable representative before/after measure; retain glass effects.
- Check light/dark themes, accessibility, touch targets, system navigation modes, and small/large layouts.

## Non-goals

- Replacing OreoTunes' visual identity, adding unrelated features, or rewriting its navigation or screen architecture.
- Inventing hero content or changing factual copy beyond the requested AI Playlist label.
- Removing glass as a performance workaround or tuning artwork/prefetch behavior without evidence.

## Constraints and evidence

- Continue the existing Android Compose implementation and preserve the working-tree changes that were present before this task.
- `docs/screenshots/home_default.PNG` and the committed `HomeScreen.kt` provide the original hero reference.
- No Orpheus Beta screenshot or implementation exists in repository history; implement the brief's stated continuous dock behavior without claiming an exact clone.
- Library tracks already use stable song IDs and remembered sorting. Playback position is updated every 200 ms in `PlayerViewModel`, while `MainActivity` collects the broad UI state at the composition root. Measure whether that invalidation drives scroll jank before changing list or image behavior.
- The user supplied a two-state dock reference in chat: at rest, a wide mini-player floats above a wide glass navigation dock; during downward scrolling, both compact into a single horizontal glass dock. Use the screenshots as visual guidance, while keeping navigation and playback targets distinct and reachable.
- The user supplied a Home header reference in chat: use a greeting such as “Good afternoon,” chosen from local time; keep the existing Home action buttons exactly as they are.
- The Home header remains pinned; it is visually transparent at the top and gains the liquid-glass treatment while the list scrolls beneath it.

## Approaches

1. **Focused edits in the current Compose structure (chosen).** Restore the historical hero, adjust the existing dock and affected surfaces/gestures, then make the smallest evidence-backed library fix. This follows the existing architecture and limits regression risk.
2. **Refactor glass modal surfaces into a shared system first.** This could centralize styling but expands the change across many dialogs and is not needed to meet the brief unless inspection finds repeated code that makes reuse clearly smaller.
3. **Limit work to the library performance issue.** This would leave most of the requested refresh incomplete, so it does not fit the brief.

## Implementation calls

- Keep the current glass language, theme behavior, information, actions, and navigation. Use existing backdrop and glass components where they fit; add no dependency unless the current project cannot meet a requirement with what is installed.
- Use the historical hero layout and behavior, removing only the requested separate mood/trip recommendation sections.
- Use a three-part local-time greeting (morning, afternoon, evening); retain the existing Home action button set and callbacks without alteration.
- Keep the header's current buttons/callbacks and reveal its liquid-glass background only after the Home list begins scrolling.
- Animate between a stacked at-rest layout and a compact shared dock during downward scrolling, following the user-provided screenshots. Preserve all navigation routes and playback actions as separate, reachable touch targets, apply system insets exactly once, and adapt the compact layout to available width without clipping controls.
- Keep gesture state and transitions consistent between Now Playing and lyrics, retaining Back and visible lyrics/minimize controls.
- Profile or otherwise measure a representative library before and after the targeted fix. Do not attribute jank to glass without evidence.
- Run the app on an available Android target and inspect the changed surfaces before calling the work complete; report unavailable device or profiling coverage plainly.

## Scope for the builder

Inspect each named surface and its current caller before editing. Keep changes local to the existing screen/component structure, update every affected call site, and preserve all unrelated pre-existing worktree changes. The user brief `docs/ui-refresh-brief.md` remains the acceptance checklist.
