# Beta changelog

Changes in **beta** builds of **Euroklíč Mapa** (Android and iOS), newest first. Czech version: `CHANGELOG.beta.cs.md`.
Stable releases are in `CHANGELOG.md`.

A beta is released with a pre-release tag such as `v1.4.0-beta.1` (add its section here and in the Czech file first).
It is published as a GitHub *pre-release* (never "latest", so stable users are not affected), its notes come from
these files, and the iOS beta source lists it. Heading format: `## <version> — <date>`, items are `- ` bullets;
`[Android]` / `[iOS]` prefixes work as in `CHANGELOG.md`. Keep both files in sync.

## 1.4.0-beta.1 — 2026-11-24
- First beta. Betas come out before a stable release so new features can be tried early; they may contain bugs.
- Optional anonymous usage measurement (More → off by default). It only counts which features are used, never location or personal data. Details are in PRIVACY.md.
- [iOS] The beta is a separate app ("Euroklíč β", icon with a BETA ribbon), so it installs next to the stable version. In SideStore and other sideload stores it is the second app in the same source.
- [iOS] Data transfer between the stable version and the beta (More → Data transfer): export favorite places, favorite stops, votes and settings to a file in one app and import it in the other. Nothing is deleted, favorites are only added. Login is not transferred. Export is available in the stable version from the next stable release.
- [iOS] Odjezdy: a panel at the bottom of the map with stop search and nearby stops with their next departures. Drag the handle up or tap it to expand.
- [Android] The beta installs over the stable version and keeps your data.