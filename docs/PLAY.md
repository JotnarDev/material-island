# Publishing the `play` version

Checklist before the first upload to Google Play.

1. **AdMob IDs.** `app/build.gradle.kts` → `productFlavors.play` uses Google's *test* IDs. Create an app and a banner ad unit in AdMob, then replace `admobAppId` and `ADMOB_BANNER_ID`. Never click your own live ads.
2. **Privacy messages.** In AdMob → *Privacy & messaging*, create a GDPR message (and a US states message if needed). The app already shows it through Google's User Messaging Platform before loading ads, and **About → Ad privacy choices** lets people change their choice.
3. **Signing.** Create an upload key (`keytool -genkeypair …`) and a `signingConfigs.release` for the `play` flavor. Keep using Play App Signing. Don't publish the play APK on GitHub.
4. **Accessibility declaration.** In Play Console → *App content → Accessibility API*, declare that the app is **not** an accessibility tool (`isAccessibilityTool="false"`). Explain that the API draws the island over the status bar and detects the foreground app. Record a short video that shows the in-app disclosure (**Accessibility service → "Material Island uses the Accessibility API" → Agree and continue**) and turning the service on.
5. **Data safety form.** Declare the advertising ID and the data AdMob collects (device or other IDs, app interactions, diagnostics) for advertising and analytics, as shared with Google. At a glance sends the city or coordinates to Open-Meteo only when the user enables it.
6. **Ads declaration:** *Contains ads → Yes*.
7. **Build:** `./gradlew bundlePlayRelease` → `app/build/outputs/bundle/playRelease/app-play-release.aab`.
