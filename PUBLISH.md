# ScreenTrack — Google Play Store Publishing Guide

**App:** ScreenTrack — AI-Powered Digital Wellbeing
**Package:** `com.screentrack.mobile`
**Version:** 1.0.0 (versionCode 1)
**Last Updated:** 2026-03-31

---

## Table of Contents

1. [Pre-Publishing Checklist](#1-pre-publishing-checklist)
2. [Step 1 — Generate Release Keystore](#2-step-1--generate-release-keystore)
3. [Step 2 — Configure Signing](#3-step-2--configure-signing)
4. [Step 3 — Build the Release AAB](#4-step-3--build-the-release-aab)
5. [Step 4 — Create the Privacy Policy](#5-step-4--create-the-privacy-policy)
6. [Step 5 — Prepare Play Store Assets](#6-step-5--prepare-play-store-assets)
7. [Step 6 — Create Google Play Console Account](#7-step-6--create-google-play-console-account)
8. [Step 7 — Create the App in Play Console](#8-step-7--create-the-app-in-play-console)
9. [Step 8 — Fill in Store Listing](#9-step-8--fill-in-store-listing)
10. [Step 9 — Content Rating](#10-step-9--content-rating)
11. [Step 10 — App Content Declaration (Screen Capture)](#11-step-10--app-content-declaration-screen-capture)
12. [Step 11 — Upload the AAB](#12-step-11--upload-the-aab)
13. [Step 12 — Submit for Review](#13-step-12--submit-for-review)
14. [Post-Publish Tasks](#14-post-publish-tasks)
15. [Updating the App in Future](#15-updating-the-app-in-future)
16. [Troubleshooting](#16-troubleshooting)

---

## 1. Pre-Publishing Checklist

Run through this before starting. Every item must be checked before submitting.

### Technical
- [ ] Release keystore generated and backed up
- [ ] `keystore.properties` created and filled in
- [ ] `./gradlew bundleRelease` builds without errors
- [ ] Release AAB installs and runs on a physical device or emulator
- [ ] Backend at `https://cpdodia-screentrack-api.hf.space` is reachable
- [ ] Test connection in Settings screen works on release build
- [ ] All 5 screens load correctly (Home, Trends, History, Insights, Settings)
- [ ] Screen capture starts and stops without crashes
- [ ] Firebase Crashlytics is connected (`google-services.json` present)

### Store Listing
- [ ] App icon 512×512 PNG ready
- [ ] Feature graphic 1024×500 PNG ready
- [ ] Minimum 2 phone screenshots ready (recommended: 5, one per screen)
- [ ] Short description written (max 80 characters)
- [ ] Full description written (max 4000 characters)
- [ ] Privacy policy hosted at a public URL

### Legal / Policy
- [ ] Privacy policy covers screen capture and data handling
- [ ] App complies with Google Play's [Sensitive App Permissions policy](https://support.google.com/googleplay/android-developer/answer/9888170)
- [ ] `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission is justified in declaration

---

## 2. Step 1 — Generate Release Keystore

> **Critical:** This keystore is tied to your app forever. If you lose it, you cannot publish updates. Back it up immediately.

### Generate the keystore

Run this command from the `mobile-android/` directory:

```bash
keytool -genkey -v \
  -keystore screentrack-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias screentrack
```

You will be prompted to enter:
- **Keystore password** — choose a strong password, save it somewhere safe
- **First and last name** — your name or organization name
- **Organizational unit** — can leave blank or enter "Mobile"
- **Organization** — your name or company
- **City, State, Country** — your location info
- **Key password** — can be same as keystore password

### Verify it was created

```bash
keytool -list -v -keystore screentrack-release.jks
```

You should see your alias `screentrack` listed with certificate details.

### Back it up immediately

Copy `screentrack-release.jks` to:
- A USB drive / external disk
- Google Drive / iCloud (in a private folder)
- A password manager that supports file attachments

**Do NOT commit the `.jks` file to Git.** It is already in `.gitignore`.

---

## 3. Step 2 — Configure Signing

Create the file `mobile-android/keystore.properties` (this file is gitignored):

```properties
SCREENTRACK_KEYSTORE_PATH=../mobile-android/screentrack-release.jks
SCREENTRACK_KEYSTORE_PASSWORD=your_keystore_password_here
SCREENTRACK_KEY_ALIAS=screentrack
SCREENTRACK_KEY_PASSWORD=your_key_password_here
```

> **Note:** The path is relative to the project root (`ScreenTrack-og/`), not the `mobile-android/` folder.
> If your `.jks` file is inside `mobile-android/`, the path above is correct.

### Verify signing config loads

Open `app/build.gradle.kts` and confirm the `signingConfigs.release` block reads these values.
The build system falls back to environment variables if the file is missing — useful for CI.

---

## 4. Step 3 — Build the Release AAB

Google Play requires an **Android App Bundle (.aab)**, not a plain APK.

```bash
cd mobile-android
./gradlew bundleRelease
```

The output will be at:
```
app/build/outputs/bundle/release/app-release.aab
```

### Optional: Also build a signed APK for local testing

```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

Install it on a physical device:
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

### What to verify on the release build
- App icon appears correctly on launcher
- Splash screen shows correctly
- Settings screen shows backend URL as pre-filled (hardcoded)
- "Test Connection" in Settings successfully reaches the backend
- Starting screen capture works and the notification appears
- App does NOT crash on any of the 5 screens

---

## 5. Step 4 — Create the Privacy Policy

Google Play **requires** a privacy policy for any app that uses sensitive permissions.
ScreenTrack uses `FOREGROUND_SERVICE_MEDIA_PROJECTION` — this is a high-sensitivity permission.

### What the privacy policy must cover

1. **What data is collected**
   - Screen content (images captured at configurable intervals)
   - Emotion and sentiment analysis results (derived from screen content)
   - Content type classifications

2. **How data is processed**
   - Images are sent to the ScreenTrack backend (hosted on Hugging Face Spaces)
   - AI analysis is performed server-side (OCR, emotion model, content classifier)
   - Analysis results are returned and stored locally on the device

3. **Where data is stored**
   - Analysis results stored locally on device using Room database
   - Configurable retention period (1–90 days, default 30 days)
   - Raw screenshots are NOT permanently stored — only analysis results

4. **Third-party services used**
   - Groq API (LLM text and emotion analysis)
   - Firebase Crashlytics (crash reporting — no personal data)
   - Firebase Analytics (anonymous usage stats)

5. **User control**
   - User can delete all data from Settings > Clear Data
   - User can stop capture at any time
   - Retention period is configurable

6. **Contact information**
   - Provide an email address for data requests

### Hosting the privacy policy (free options)

**Option A — GitHub Pages (recommended)**
1. Create a file `privacy-policy.md` in your GitHub repo
2. Enable GitHub Pages on the repo (Settings > Pages > Deploy from branch)
3. Your policy URL will be: `https://username.github.io/repo-name/privacy-policy`

**Option B — Notion**
1. Create a Notion page with your privacy policy
2. Click Share > Publish to web
3. Copy the public URL

**Option C — Google Sites**
1. Go to sites.google.com
2. Create a simple one-page site
3. Paste your privacy policy text

---

## 6. Step 5 — Prepare Play Store Assets

### App Icon (512×512 PNG)
- Export the adaptive icon foreground from `res/mipmap/` at 512×512
- Background should be solid dark color (`#0F1118`) to match the app theme
- No transparency — Play Store requires a solid icon
- Tool: Android Studio > Image Asset wizard, or export from your design tool

### Feature Graphic (1024×500 PNG)
- This is the banner shown at the top of your Play Store listing
- Suggested content: app name, tagline like "AI-powered screen wellbeing", and a screenshot
- Tools: Canva (free, has Play Store templates), Figma, or Photoshop
- Do NOT include device frames or Play Store badges in this graphic

### Screenshots (minimum 2, recommended 5)

Take one screenshot per main screen. Minimum size: 1080×1920px (portrait).

**Recommended screenshots:**
1. **Home screen** — showing live capture status and mood ring
2. **Trends screen** — showing the sentiment line chart
3. **History screen** — showing capture history cards
4. **Insights screen** — showing the wellness score arc
5. **Settings screen** — showing configuration options

**How to take screenshots in Android Studio:**
1. Open the emulator (Pixel 6 or similar, API 33+)
2. Run the app in release mode or debug mode
3. Navigate to each screen
4. Use the emulator toolbar camera icon, or press `Ctrl+S` (Windows) / `Cmd+S` (Mac)
5. Screenshots save to your Desktop by default

**Optional:** Use a phone mockup frame around your screenshots using [mockuphone.com](https://mockuphone.com) (free).

---

## 7. Step 6 — Create Google Play Console Account

1. Go to [play.google.com/console](https://play.google.com/console)
2. Sign in with your Google account
3. Click **Get started**
4. Accept the Developer Distribution Agreement
5. Pay the **one-time $25 USD registration fee**
6. Fill in your developer name (shown publicly on Play Store)
7. Provide your email address and phone number
8. Account activation takes up to 48 hours (usually faster)

---

## 8. Step 7 — Create the App in Play Console

Once your account is active:

1. In Play Console, click **Create app**
2. Fill in:
   - **App name:** ScreenTrack
   - **Default language:** English (United States)
   - **App or game:** App
   - **Free or paid:** Free
3. Accept the developer policies
4. Click **Create app**

---

## 9. Step 8 — Fill in Store Listing

Navigate to **Store presence > Main store listing** in the left sidebar.

### App details

| Field | Value |
|-------|-------|
| App name | ScreenTrack |
| Short description | AI-powered screen wellness tracker with emotion analysis |
| Full description | See template below |

### Full description template

```
ScreenTrack is an AI-powered digital wellbeing app that helps you understand
how your screen time affects your emotions and mental wellness.

HOW IT WORKS
ScreenTrack periodically captures your screen at an interval you choose (every
1 to 60 seconds). Each capture is analyzed by AI to detect:

• Emotion — joy, sadness, anger, fear, disgust, surprise, or neutral
• Sentiment — a score from very negative to very positive
• Content type — social media, news, work, gaming, and 18 other categories

All analysis results are stored locally on your device. No raw screenshots
are saved permanently.

KEY FEATURES
★ Real-time mood ring — see your current emotional state at a glance
★ Trends dashboard — interactive charts with time-range filters
★ History browser — browse all past captures with filters
★ Wellness insights — daily score, peak usage hours, and period comparisons
★ Smart suggestions — get nudges when negative patterns are detected
★ PDF export — export your wellbeing report

PRIVACY FIRST
• Screen captures are analyzed and immediately discarded
• Only the analysis result (emotion, sentiment, content type) is stored
• All data stays on your device
• Configurable data retention (1–90 days)
• Clear all data at any time from Settings

REQUIREMENTS
• Android 8.0 or higher
• Internet connection (for AI analysis)
• Backend server must be configured in Settings
```

### Graphics

Upload:
- App icon (512×512 PNG)
- Feature graphic (1024×500 PNG)
- At least 2 phone screenshots

---

## 10. Step 9 — Content Rating

Navigate to **Policy > App content > Content ratings** in Play Console.

1. Click **Start questionnaire**
2. Select category: **Utility**
3. Answer the questions honestly:
   - Violence: No
   - Sexual content: No
   - Profanity: No
   - Controlled substances: No
   - User-generated content: No (the user's own screen is analyzed, not shared)
4. Submit and get your rating (expected: **Everyone** or **Everyone 10+**)

---

## 11. Step 10 — App Content Declaration (Screen Capture)

This is the most important step for ScreenTrack due to its use of `MediaProjection`.

Navigate to **Policy > App content** in Play Console.

### Sensitive permissions

You must declare the use of `FOREGROUND_SERVICE_MEDIA_PROJECTION`:

1. Go to **App content > Sensitive app permissions**
2. Select **Media Projection / Screen capture**
3. Provide justification: *"ScreenTrack captures the user's screen at regular intervals to perform AI-based emotion, sentiment, and content analysis for personal digital wellbeing tracking. The user explicitly grants permission via Android's MediaProjection consent dialog before any capture begins. No raw screenshots are stored permanently — only AI analysis results."*

### Privacy policy

1. Go to **App content > Privacy policy**
2. Paste your hosted privacy policy URL (from Step 4)

### Data safety section

Navigate to **Policy > App content > Data safety**. Fill in:

| Question | Answer |
|----------|--------|
| Does the app collect or share user data? | Yes |
| Data type: Photos and videos | Yes — screen captures (not shared with users, used for analysis only) |
| Data type: App activity | Yes — capture timestamps and analysis results |
| Is data encrypted in transit? | Yes (HTTPS) |
| Can users request data deletion? | Yes (via Settings > Clear Data) |

---

## 12. Step 11 — Upload the AAB

Navigate to **Release > Production** (or start with **Internal testing** recommended).

### Recommended release track order
1. **Internal testing** first — share with yourself and test thoroughly (instant rollout, no review)
2. **Closed testing (alpha)** — share with a small group
3. **Open testing (beta)** — optional wider test
4. **Production** — full public release

### Upload steps

1. Click **Create new release**
2. Click **Upload** and select `app/build/outputs/bundle/release/app-release.aab`
3. Add release notes:
   ```
   Initial release of ScreenTrack.
   - AI-powered screen emotion and sentiment analysis
   - 5-screen dashboard with trends, history, and insights
   - Smart wellness suggestions
   - PDF export
   ```
4. Click **Save** then **Review release**
5. Fix any warnings shown (usually about target API level or permissions)
6. Click **Start rollout**

---

## 13. Step 12 — Submit for Review

1. After uploading to a release track, click **Submit for review**
2. Google will review the app — typically **3–7 business days** for first submission
3. You will receive an email when approved or if there are issues

### If Google rejects the app

Common rejection reasons for screen capture apps:

- **Policy violation — sensitive permissions:** Add more detail to your permission declaration justification
- **Privacy policy insufficient:** Make sure the policy explicitly mentions screen capture
- **Missing data safety form:** Complete the Data Safety section fully
- **App crashes during review:** Make sure the release build is stable on multiple API levels

---

## 14. Post-Publish Tasks

Once the app is live:

- [ ] Test the Play Store listing looks correct
- [ ] Download from Play Store on a clean device and verify it works
- [ ] Monitor Firebase Crashlytics for any crashes from real users
- [ ] Monitor Firebase Analytics for usage patterns
- [ ] Respond to any Play Store reviews
- [ ] Set up a support email address visible in the listing
- [ ] Verify the backend continues to run reliably (HF Spaces 24/7 solution)

---

## 15. Updating the App in Future

For every update:

1. Increment `versionCode` (must increase by at least 1):
   ```kotlin
   // app/build.gradle.kts
   versionCode = 2        // must be higher than previous
   versionName = "1.1.0"  // human-readable
   ```
2. Build a new AAB: `./gradlew bundleRelease`
3. Go to Play Console > Release > Production > Create new release
4. Upload the new AAB
5. Add release notes describing what changed
6. Submit for review (updates review faster than initial, usually 1–3 days)

**Never reuse a versionCode.** Play Store will reject the upload.

---

## 16. Troubleshooting

### Build errors

| Error | Fix |
|-------|-----|
| `Keystore file not found` | Check the path in `keystore.properties` is correct relative to project root |
| `Wrong password for keystore` | Double-check `SCREENTRACK_KEYSTORE_PASSWORD` in `keystore.properties` |
| `R8 compilation error` | Check `proguard-rules.pro` for missing keep rules |
| `google-services.json not found` | Download from Firebase Console and place in `app/` |

### Play Console errors

| Error | Fix |
|-------|-----|
| `versionCode already used` | Increment `versionCode` in `build.gradle.kts` |
| `Target API level too low` | Update `targetSdk` to 34 (already done) |
| `Sensitive permission not declared` | Complete the App Content > Sensitive permissions section |
| `Privacy policy URL unreachable` | Check the URL is publicly accessible without login |
| `AAB not signed` | Verify `signingConfig = signingConfigs.getByName("release")` is set in `buildTypes.release` |

### Runtime issues on release build

| Issue | Fix |
|-------|-----|
| Network request fails | Release builds block cleartext HTTP — backend must use HTTPS (already the case with HF Spaces) |
| App crashes on launch | Enable `isDebuggable = true` temporarily or check Crashlytics logs |
| Missing class at runtime | Add missing ProGuard keep rule in `proguard-rules.pro` |
| Firebase not initializing | Verify `google-services.json` is present in `app/` and not excluded by `.gitignore` |

---

## Key Files Reference

| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | versionCode, versionName, signingConfig, backend URL/API key |
| `keystore.properties` | Signing credentials (gitignored, create locally) |
| `screentrack-release.jks` | Release keystore (gitignored, back up externally) |
| `app/src/main/AndroidManifest.xml` | Permissions and service declarations |
| `app/proguard-rules.pro` | R8 keep rules for release minification |
| `app/src/main/res/xml/network_security_config.xml` | HTTP/HTTPS network policy |
| `app/google-services.json` | Firebase config (gitignored, download from Firebase Console) |
