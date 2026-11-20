# Changelog

Changes to **Euroklíč Mapa for iOS**, newest version first. Czech version: `CHANGELOG.cs.md`.

When a version is released (git tag `v*`), the matching section of both files is used as the
GitHub Release description, and both files are bundled in the app (More → What's new; it also
shows by itself after an update). The app shows the one matching the phone's language.
A section heading must look like `## <version> — <date>`, items are `- ` bullets. Keep both
files in sync.

## 1.2.1 — 2026-11-20
- Android: the app is now available as a signed APK on GitHub Releases (EuroklicMapa.apk). It is not on Google Play or the App Store, so install it from the file.

## 1.2.0 — 2026-11-19
- The app is now in English and Czech. It follows the phone's language: Czech phones get Czech, everything else gets English (including this changelog, widgets and permission prompts).
- Distances use a decimal point in English and a comma in Czech.

## 1.1.0 — 2026-11-19
- New Odjezdy mode (public transport): live vehicle positions on the map with the line number, transport type filter, a vehicle card with destination, delay and route, departures from stops, stop and vehicle search, favorite stops and service alerts. Switch it in More → App mode.
- Widgets: Nearest toilet and Departures nearby (on the Home Screen and the Lock Screen).
- Profile picture and name of the logged-in user in More.
- Add a comment or photo to a place, report a problem and (for admins) the approval queue.
- Address search on the Euroklíč map.

## 1.0.1 — 2026-11-19
- A place's photo can be opened full screen (pinch with two fingers or double-tap to zoom).
- Odjezdy: fixed loading of DÚK services (the operator sent times in a different format, so they were dropped) and added an operator switch.
- Logging in with Discord and Google now returns to the app when finished (it used to stay on the web and not log you in).
- The app has its own icon.
- After an update the app shows what's new; the full list is in More → What's new.

## 1.0.0 — 2026-11-19
- Place detail as a card over the map: a big Navigate button, live departures from the nearest stops (odjezdy.online), details, voting and comments.
- Full-screen map centered on your location, with a panel of the nearest places.
- Choice of base map: Mapy.com or Apple Maps (More → Map).
- Dark mode and more readable links on a dark background.
- Sources for SideStore, AltStore, Feather and LiveContainer are at euroklic.odjezdy.online.
