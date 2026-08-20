% ScreenTrack — iOS Handover
% Feasibility, plan, and what can (and cannot) be built
% Prepared 2026-08-20

# 0. Read this first — there is no iOS app yet

**No iOS source code exists.** This document is the iOS **build plan and feasibility
analysis**, not a codebase. The reason is fundamental, not a matter of effort:
**iOS cannot reproduce the Android screen-capture engine.** This handover explains
exactly what iOS can and cannot do and specifies the realistic iOS app to build.

# 1. Why the Android engine does not port to iOS

The Android app works by capturing the screen every few seconds in the background
and analyzing it on-device. On iOS this is impossible:

- **No passive background screen capture.** iOS's only screen API, **ReplayKit**,
  is *user-initiated*, shows a **persistent red recording indicator**, and cannot
  run silently in the background sampling frames. There is no equivalent to
  Android's MediaProjection foreground service.
- **Background suspension.** iOS aggressively suspends apps; a continuous
  capture-and-analyze loop cannot stay alive.
- **App Store policy.** An app that reads the user's screen across other apps for
  wellness monitoring is an automatic rejection.

This is the same wall as a web version — a different mechanism, the same outcome.

# 2. What iOS *can* do — the Screen Time API

Apple's sanctioned tool is **Screen Time** (frameworks: **FamilyControls**,
**DeviceActivity**, **ManagedSettings**):

- ✅ App / category **usage duration**, pickups, notification counts.
- ❌ **No screen content** — no OCR, no on-screen emotion/sentiment.
- ⚠️ **Export is restricted by design.** Usage is rendered inside a sandboxed
  `DeviceActivityReport` SwiftUI extension that has **no network access**,
  specifically to stop apps from harvesting Screen Time data. You can react to
  **threshold events** and store flags, but you cannot export a clean minute-by-minute log.

# 3. Portability map (Android feature → iOS)

| Android feature | iOS equivalent | Portable? |
|-----------------|----------------|-----------|
| Continuous screen capture (MediaProjection) | ReplayKit (user-initiated, indicator, no background) | ❌ No |
| On-screen OCR + content category | — | ❌ No |
| Facial-expression / text emotion from screen | — | ❌ No |
| App / social-media **usage time** | Screen Time API (DeviceActivity) | ⚠️ Display yes; export restricted |
| Daily mental-health survey | Standard app + notifications | ✅ Yes |
| On-device, private processing | Screen Time is on-device by design | ✅ Yes |
| Private beta distribution (Firebase) | **TestFlight** | ✅ Yes |

**Conclusion:** the iOS app is a **different product with the same research goal** —
it measures *usage + self-reported mood*, not *screen-content emotion*.

# 4. The buildable iOS app — specification

**Product:** an iOS study app that measures **social-media usage** (Screen Time API)
and **mental health** (daily in-app surveys / EMA). It does **not** capture or
analyze screen content.

**Stack:** iOS 16+, Swift + SwiftUI. Frameworks: FamilyControls (authorization),
DeviceActivity (monitoring + report), ManagedSettings (optional), UserNotifications
(daily prompts). Distribution: **TestFlight**.

**MVP features:**
1. Onboarding + consent, then Screen Time authorization
   (`AuthorizationCenter.requestAuthorization(for: .individual)`).
2. Usage monitoring — `DeviceActivitySchedule` + a `DeviceActivityMonitor`
   extension tracking social-media category usage; threshold events stored as flags.
3. Usage display — a `DeviceActivityReport` view (Apple renders it; the app cannot
   read the raw numbers directly).
4. Daily mental-health survey (EMA) — a short validated scale + mood rating,
   prompted by a daily local notification.
5. Survey-data delivery — responses to a researcher-controlled store or a Google
   Form (same options as the Android survey).
6. Participant controls — pause / withdraw / view / delete.

# 5. Prerequisites (gating — start early)

1. **Apple Developer Program** membership ($99/yr).
2. A **Mac + Xcode**.
3. The **Family Controls distribution entitlement** — Apple must approve this by
   request for non-parental-control use. **This is the critical-path dependency and
   can be slow.**
4. IRB coverage for the iOS data collection.

# 6. The data-delivery problem (decide before building)

- **Survey data:** easy — a normal iOS app has network; responses can go to a
  database or a Google Form.
- **Usage data:** Apple **blocks** clean export of Screen Time numbers. Options,
  all imperfect: (a) threshold-event flags (low fidelity); (b) ask participants to
  submit their iOS Screen Time weekly summary manually; (c) accept that iOS shows
  usage to the participant but not researcher-grade raw export. This is a
  research-design decision.

# 7. Rough phasing

1. **Phase 0 (unblock):** Apple Developer account + request Family Controls
   entitlement (long lead) + IRB check for iOS.
2. **Phase 1:** SwiftUI shell, onboarding/consent, Screen Time authorization.
3. **Phase 2:** Daily survey + notifications + survey-data delivery.
4. **Phase 3:** DeviceActivity usage monitoring + report view + threshold events.
5. **Phase 4:** TestFlight distribution + pilot.

# 8. What is in the iOS zip

The iOS zip contains this handover plus the full **PRD** (`docs/PRD-android-and-ios.md`)
which includes the complete Android reference and the iOS specification. **There is
no Xcode project** — it must be created new following §4–§7 above.
