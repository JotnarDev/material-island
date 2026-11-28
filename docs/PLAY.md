# Publishing the `play` version

## Already done

- **Upload key:** `../material-island-keys/upload.jks`, with its passwords in `upload.properties`. Keep it outside the repo and back it up in Bitwarden. Gradle uses it automatically for the `play` flavor; if the file is missing, it falls back to the debug key.
- **Bundle:** `./gradlew bundlePlayRelease` produces `app/build/outputs/bundle/playRelease/app-play-release.aab`, signed with the upload key.
- **Store listing texts:** `docs/play/en-US/` and `docs/play/es-ES/` contain the title (30 chars max), short description (80) and full description (4000). They're plain text without HTML, as Play wants. The graphics are the same as F-Droid's: `fastlane/metadata/android/en-US/images/` has the 512×512 icon, the 1024×500 feature graphic and the phone screenshots.
- **In-app disclosure** for the Accessibility API, **UMP consent** and **Ad privacy choices** are implemented.

## Needs your accounts

1. **Google Play developer account** (25 USD, one time). New personal accounts must run a **closed test with at least 12 testers for 14 days** before production access.
2. **AdMob:**
   1. Create the app and a *Banner* ad unit.
   2. Replace the test IDs in `app/build.gradle.kts` → `productFlavors.play` (`admobAppId` and `ADMOB_BANNER_ID`).
   3. Link AdMob to the Play app.
   4. In *Privacy & messaging*, publish a GDPR message (and a US states message).
   5. Never click your own live ads.
3. **Play App Signing:** accept it when you create the app. Google keeps the signing key; you upload with `upload.jks`.

## Play Console → App content

| Section | Answer |
|---|---|
| Privacy policy | A public URL is required, e.g. a `PRIVACY.md` on GitHub |
| Ads | Contains ads: **Yes** |
| App access | All functionality available without special access |
| Content rating | Utility, no user-generated content |
| Target audience | 18+ (avoids the Families policy, since the app has ads) |
| Accessibility API | **Not** an accessibility tool. Purpose: draw the island over the status bar and detect the foreground app. Attach a video of the in-app disclosure (*Accessibility service → "Material Island uses the Accessibility API" → Agree and continue*) and of turning the service on |
| Data safety | See below |

### Data safety

- **Collected, shared with Google (AdMob):** device or other IDs (advertising ID), app interactions, diagnostics. Purposes: advertising and analytics; fraud prevention. Collection is optional only where consent applies (EEA/UK): answer "required" unless you choose otherwise.
- **Location (approximate):** only if the user enables At a glance and taps *Use my location*. It is sent to Open-Meteo to get the weather. Not stored, not used for ads.
- **Notifications and accessibility data:** processed only on the device, never collected. Declare nothing for them.
- **Encryption in transit:** yes (HTTPS).
- **Deletion:** no account and no server-side data, so there is nothing to delete.
