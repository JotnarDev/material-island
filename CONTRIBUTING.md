# Contributing to Material Island

Thanks for helping! Bug reports, device reports, ideas and pull requests are all welcome.

## Reporting a bug

Open a [bug report](https://github.com/JotnarDev/material-island/issues/new?template=bug_report.yml). The most useful details are:

- Phone model, Android version and skin (Pixel, HyperOS, One UI…).
- Material Island version (shown in **About**).
- What you did, what you expected and what happened. A screen recording helps a lot.
- For layout problems: a screenshot with **Size & position → Calibration** turned on.

Please blur anything personal (names, messages, locations) in screenshots.

## Suggesting a feature

Open a [feature request](https://github.com/JotnarDev/material-island/issues/new?template=feature_request.yml) and say what you'd like the island to do and why. Mock-ups and links to references are great.

## Your first pull request

1. Pick an issue labelled [good first issue](https://github.com/JotnarDev/material-island/labels/good%20first%20issue) and leave a comment so others know you're on it.
2. Fork the repo and create a branch from `main`.
3. Build with JDK 17 and the Android SDK (`android-37.2` platform):
   ```bash
   ./gradlew assembleFossDebug
   adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
   ```
4. Turn on notification access and the accessibility service (see the README). The **Try the island** buttons in the app show demo activities, so you can test without real calls or music.
5. Open a pull request that explains what changed and how you tested it. Screenshots or GIFs for visual changes, please.

The [project map](README.md#project-map) and its quick recipes show where each part lives.

## Code style

- Kotlin and Jetpack Compose with Material 3 Expressive. Follow the style of the file you're editing.
- Keep the island fast. Nothing should animate or poll while it's at rest.
- **Privacy rules:**
  - Don't add analytics, trackers or ad SDKs.
  - Don't send anything about the user to a server.
  - New network features must be opt-in.
- Keep the README (English and Spanish) in sync when you change user-facing behaviour.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
