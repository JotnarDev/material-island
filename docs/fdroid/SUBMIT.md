# Getting Material Island into F-Droid

F-Droid builds the app from source with the `fdroid` flavor: no ads, no Google libraries and no self-updater, because F-Droid delivers the updates. The app's listing (descriptions, icon, screenshots, changelogs) comes from `fastlane/metadata/android/`. F-Droid reads it from the tagged commit it builds.

## Before submitting

1. Release **v1.2.1** (versionCode 6) so that the tag the recipe points to exists. `fastlane/.../changelogs/6.txt` is already written.
2. Check that it builds locally with `./gradlew assembleFdroidRelease`. The output is `app-fdroid-release-unsigned.apk`, which F-Droid signs.

## Submitting (needs a GitLab account)

1. Fork https://gitlab.com/fdroid/fdroiddata.
2. Add `metadata/dev.jotnar.materialisland.yml`, copied from `docs/fdroid/dev.jotnar.materialisland.yml`.
3. Open a merge request with the **App inclusion** template. The CI pipeline runs `fdroid lint` and a test build.
4. Answer the reviewers. A review usually takes a few weeks.

## Notes for reviewers

- **Accessibility service:** it only draws the island over the status bar and tracks which app is in the foreground. It doesn't read window content. The app shows a prominent disclosure before sending the user to the accessibility settings.
- **Network:** only *At a glance* uses the internet, and it is off by default. It uses Open-Meteo, ESPN, Yahoo Finance and Google's favicon service, hence the `NonFreeNet` anti-feature.
- **AI disclosure:** large parts of the code were written with an AI coding assistant (Claude). F-Droid's inclusion policy has no rule against this, but stating it up front is honest and avoids surprises.

## Signatures

F-Droid signs with its own key. That means the F-Droid build and the GitHub build can't update each other: switching between them requires uninstalling, after a settings backup in **Backup**. Later this can change to reproducible builds, where F-Droid ships the developer-signed APK.
