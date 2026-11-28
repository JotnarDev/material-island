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
- **Network:** only *At a glance* uses the internet, and it is off by default. It uses Open-Meteo and ESPN, hence the `NonFreeNet` anti-feature.
- **AI disclosure:** large parts of the code were written with an AI coding assistant (Claude). F-Droid's inclusion policy has no rule against this, but stating it up front is honest and avoids surprises.

## Signatures: reproducible builds

F-Droid builds the `fdroid` flavor from the tagged commit and compares it with `material-island-fdroid-X.Y.Z.apk` from the GitHub release. If they match, F-Droid ships the developer-signed APK. Users can then move between the GitHub and F-Droid versions without uninstalling, since both carry the same signature.

- The release workflow builds and signs that APK on every tag.
- Verified on 2026-11-27: the build from a local clean checkout and the GitHub Actions build of the same commit are byte-for-byte identical (`vcsInfo.include = false` removes the only difference).
- `AllowedAPKSigningKeys` is the SHA-256 of the release certificate. The APK is signed with APK Signature Scheme v3 plus a rotation lineage from the old debug key (see `docs/RELEASING.md`).
