# BlueCard

[![CI](https://github.com/bryancassell/bluecard/actions/workflows/ci.yml/badge.svg)](https://github.com/bryancassell/bluecard/actions/workflows/ci.yml)

An Android app for Scouting America scouts to track their progress on merit badges and ranks.

With BlueCard, a scout can:

- Browse and search the merit badges, each with a short summary and a link to
  its official requirements.
- Record their counselor's contact details, and a completion date and notes for
  each requirement.
- Fill in the logs some requirements need, such as Personal Fitness's 12-week
  exercise log.
- Mark a badge completed on an earlier date, without entering each requirement.
- Save or share a PDF report of everything recorded for a completed badge.
- Track their ranks, Scout through Eagle, the same way, with the merit badges
  that Star, Life and Eagle need counted from their badge progress.
- Export their name, unit number and progress to a file, and import it later,
  for example on a new phone.

There's no account or server, and the app itself doesn't connect to the
internet: links to official pages open in the browser. Progress is stored on the
phone. Android's own backup also copies it to the scout's Google Drive, unless
they turn backup off in the phone's settings.

<p>
  <img src="docs/images/home.png" width="250"
    alt="Home screen: a card with the Tenderfoot rank, a trail of the ranks from Scout to Eagle Scout, and Second Class next, then one badge completed and two in progress, Eagle-required progress, and the Camping and Cooking badges in progress">
  <img src="docs/images/badge-detail.png" width="250"
    alt="Camping badge page: progress bar, Eagle-required label, a card saying the badge is in progress with a button to mark it completed, the counselor's contact details, and the first requirements, two of them complete">
  <img src="docs/images/tracker.png" width="250"
    alt="Camping requirement 9a: a log of three campouts, with a button to add another">
</p>

## Status

BlueCard is in development and hasn't been released yet. The catalog doesn't
include every merit badge yet.

## Supported devices

BlueCard runs on Android 8.0 (API 26) and newer.

## Building

You need Android Studio and the Android SDK it installs. You don't need to
install Gradle, Kotlin or a separate JDK, but command-line builds need
`JAVA_HOME` pointed at Android Studio's bundled JDK. The steps for macOS are in
[One-time machine setup](docs/toolchain.md#one-time-machine-setup-macos).

Then, from the repository root:

- `./gradlew build` builds the app and runs every check CI runs.
- `./gradlew installDebug` installs the debug app on a running emulator or
  phone.

You can also open the project in Android Studio and run the `app` configuration.
The other commands are listed in
[Everyday commands](docs/toolchain.md#everyday-commands).

## Documentation

| Document | What's in it |
|---|---|
| [`PRD.md`](PRD.md) | What the app does, and the decisions about how it looks and behaves |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | How the app is built, and the conventions new code follows |
| [`docs/toolchain.md`](docs/toolchain.md) | Machine setup, build commands, the testing rules the build checks, CI, the backup and release-build checks done by hand, and publishing test releases |
| [`docs/catalog.md`](docs/catalog.md) | How to write the catalog's merit badges, ranks and requirements |

## Disclaimer

BlueCard is an independent project. It is not affiliated with or endorsed by
Scouting America.

## License

BlueCard is released under the [MIT License](LICENSE).
