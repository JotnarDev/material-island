# Releasing

GitHub releases contain only the **foss** APK. GitHub Actions builds, signs and publishes them.

## Steps

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Write the notes in `release-notes/vX.Y.Z.md`, in English. Leave out the SHA-256 line: the workflow adds it.
3. Commit, push, then tag:
   ```bash
   git tag vX.Y.Z && git push origin vX.Y.Z
   ```
4. The **Release** workflow checks that the tag matches `versionName`, builds `assembleFossRelease`, signs it and publishes the release with the `**SHA-256:**` line that the in-app updater checks.

To test a build without publishing it, run the workflow by hand (**Actions → Release → Run workflow**). It leaves the signed APK as an artifact.

## Signing

- Releases from 1.2.1 on are signed with the release key, together with `signing/lineage.bin`, an APK Signature Scheme v3 rotation lineage. In the lineage the old debug key (used up to 1.2.0) vouches for the new one, so existing installs update without uninstalling.
- The key never goes in the repo. It lives in `../material-island-keys/` (`release.jks` and `keystore.properties`), with a backup in Bitwarden, and in the repo's Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Upload them with `scripts/upload-signing-secrets.sh`.
- To sign by hand: `scripts/sign-apk.sh in.apk out.apk`.
- **If the release key is lost, no existing install can be updated.** Keep the backup.
