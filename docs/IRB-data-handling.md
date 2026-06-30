# ScreenTrack — Data Collection, Storage & Privacy (IRB Supplement)

**App:** ScreenTrack (Android, package `com.chiragdodia.screentrack`)
**Prepared for:** IRB / ethics review
**Date:** 2026-06-29
**Platform:** Android only (minimum Android 8.0 / API 26)

> **Important scope note:** This document describes the **on-device** version of
> the app, in which all analysis happens locally on the participant's phone and
> no screen images leave the device. The study **must be conducted with this
> on-device build.** An earlier version that transmitted screenshots to a remote
> server must not be used for the study.

---

## 1. Purpose

ScreenTrack passively observes what is on the participant's phone screen and
derives high-level indicators of (a) the emotional tone of on-screen content and
(b) the category of activity (e.g., social media, messaging, work). These derived
indicators are used to provide the participant with wellness insights and to
support the research study on screen use and mood.

## 2. What data is collected

The app **does not retain screen images.** For each sampled moment it stores only
the following derived fields in a local database:

| Field | Description | Example |
|-------|-------------|---------|
| `timestamp` | When the sample was taken | 2026-06-29 14:32 |
| `emotion` | Dominant emotion label inferred from on-screen text | "joy", "sadness", "neutral" |
| `emotionScore` | Confidence of the emotion label (0–1) | 0.74 |
| `sentiment` | Sentiment value derived from emotion (−1 to +1) | −0.5 |
| `contentType` | Activity category | "social_media", "work" |
| `contentConfidence` | Confidence of the category (0–1) | 0.6 |
| `textSnippet` | Up to **200 characters** of text recognized on screen | (see §6 — sensitive) |

## 3. How data is collected (processing pipeline)

1. With the participant's explicit permission (Android's standard screen-capture
   consent dialog), the app captures the screen at a fixed interval
   (default: one frame every 5 seconds; paused when the screen is off).
2. Each captured frame is held **only in memory**, downscaled, and passed to an
   **on-device** analyzer.
3. The analyzer uses **Google ML Kit on-device text recognition (OCR)** to read
   visible text, then applies local keyword-based rules to classify emotion and
   content category. **No cloud service is contacted for this analysis.**
4. The image is **immediately discarded from memory** (`recycle()`) after
   analysis. It is never written to storage and never transmitted.
5. Only the derived fields in §2 are written to the local database.

## 4. What is explicitly NOT collected or stored

- ❌ **No screenshots or images** are ever saved to disk or sent anywhere.
- ❌ **No full screen text** is stored — only a truncated snippet (≤200 chars).
- ❌ **No keystrokes, passwords, contacts, location, or files** are accessed.
- ❌ **No audio or camera** access.
- ❌ **No raw data is transmitted to any study/third-party server.**

## 5. Where data is stored

- All derived records are stored in a **local database on the participant's own
  device**, inside the app's private storage area. This area is sandboxed by
  Android and is not accessible to other apps, and is protected by the device's
  built-in storage encryption.
- Data is **automatically deleted after 30 days** (rolling retention).
- The participant can stop collection at any time and uninstalling the app
  removes all stored data.

## 6. Sensitive-data consideration: text snippet

The 200-character `textSnippet` can, in principle, contain fragments of personal
text visible on screen (e.g., part of a message). It is stored **only on the
participant's device** and never transmitted. Mitigations:

- It is truncated to 200 characters.
- It is covered by the 30-day deletion window.
- **Recommended for the study:** disable or further shorten the snippet if the
  research questions do not require it, to minimize stored sensitive content.

## 7. Data that leaves the device

The only off-device transmission is a **Firebase Analytics** event named
`analysis_received` containing two category labels: `emotion` and `content_type`
(e.g., "joy" / "social_media"). This contains **no screen images, no recognized
text, and no direct identifiers** — only aggregate category labels tied to
Firebase's anonymous installation identifier.

- **Recommended for the study:** consider **disabling analytics entirely** for
  study builds so that *no* participant-derived data leaves the device. This
  makes the privacy guarantee absolute and simplifies IRB review.

## 8. Distribution & consent

- The app is **not published to any public app store.** It is distributed
  privately via **Firebase App Distribution** to a named list of enrolled
  participants only.
- Participants are **invited individually by email** and can install the app
  only after enrolling and accepting their invite.
- On first capture, Android shows its own system consent dialog; capture cannot
  begin without the participant explicitly granting it.
- This is in addition to the study's own informed-consent process.

## 9. Participant controls

- Start/stop capture at any time from within the app.
- Capture automatically pauses when the screen is off.
- Configurable sampling interval and retention period.
- Uninstalling removes all locally stored data.

## 10. Summary of privacy posture

| Question | Answer |
|----------|--------|
| Are screenshots stored? | No — discarded immediately after on-device analysis |
| Is screen content sent to a server? | No |
| Does analysis use the cloud? | No — fully on-device (ML Kit OCR + local rules) |
| What leaves the device? | Only two aggregate category labels via analytics (recommend disabling) |
| Where is data kept? | Locally on the participant's device only |
| How long? | 30 days, then auto-deleted |
| Platform | Android only |

---

### Open items for the research team

1. **Confirm the study uses the on-device build** (not the earlier server-upload version).
2. **Decision:** keep or disable the `textSnippet` field (§6).
3. **Decision:** keep or disable Firebase Analytics for study builds (§7).
