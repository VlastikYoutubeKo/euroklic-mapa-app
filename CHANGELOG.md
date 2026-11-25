# Changelog

Changes to **Euroklíč Mapa** (Android and iOS), newest version first. Czech version: `CHANGELOG.cs.md`.

When a version is released (git tag `v*`), the matching section of both files is used as the
GitHub Release description, and both files are bundled in the app (More → What's new; it also
shows by itself after an update). The app shows the one matching the phone's language.
A section heading must look like `## <version> — <date>`, items are `- ` bullets. A bullet starting
with `[Android]` or `[iOS]` is shown only in that app (the tag is hidden); untagged bullets are shown
in both. Keep both files in sync.

## 1.4.0 — 2026-11-25
- Optional anonymous usage measurement (More → Privacy). It is off by default, only counts which features are used and never collects location or personal data. Details are in PRIVACY.md.
- [iOS] A short welcome screen on first launch. You can open it again in More → How the app works.
- [iOS] Redesigned place detail: the note about the place (for example a long-term closure) is right under the title, the Navigate button is more compact, station accessibility comes before the departures, departures can be collapsed, and saving a place uses a bookmark.
- [iOS] A calmer map: one clear "Nearest toilet" button, the other actions (Add a place, Download map) are in a menu, and the Odjezdy switch is a secondary button. The map controls and the list of nearby places also hold up at the largest text sizes.
- [iOS] Odjezdy: a panel at the bottom of the map with stops nearby and their next departures (stops show up immediately, departures fill in as they load), transport filters with names and a checkmark, and search for a stop or vehicle.
- [iOS] More → Data transfer: export favorite places, favorite stops, votes and settings to a file and import them in another copy of the app, for example the beta. Statistics moved one level down.
- [iOS] Favorites have a clearer empty state.

## 1.3.0 — 2026-11-20
- Offline mode: without a connection the app runs from the downloaded places and the saved map, and says so at the top of the map. The map of an area can be downloaded in advance (the download button on the map). On iPhone this works with the Mapy.com map only.
- One-tap switch between Euroklíč and Odjezdy right on the map (no more digging through More). On Android there is also a home-screen shortcut: long-press the app icon → Departures.
- Adding a photo now offers three sources: take a photo, choose from the gallery, or choose from files.
- [Android] When adding a place, the map opens on your location, and the "Use my location" button moves the pin there.
- [iOS] The "Add a place" form is redesigned: the map opens on your location with a "My location" button, clearer photo and submit buttons.

## 1.2.2 — 2026-11-20
- [Android] The app is now in English and Czech. It follows the phone's language: Czech phones get Czech, everything else gets English.
- [Android] "What's new" is shown once after an update; the full history is in More → What's new.
- [Android] New Odjezdy mode (public transport), switch it in More → Odjezdy mode: live vehicle map with line numbers and a type filter, a vehicle card with delay, route, fleet number and vehicle details, stops with departures, stop search and favorite stops.
- [Android] Home-screen widget "Nearest toilet" (More → Home screen widget).
- [iOS] A short confirmation after voting or sending a comment, photo or report, and your vote on a place is remembered after a restart.
- [iOS] Clearer voting buttons ("Works" / "Doesn't work"), "No votes yet" is no longer green, warnings are easier to read, counts in the statistics are plain numbers.

## 1.2.1 — 2026-11-20
- [Android] The app is now available as a signed APK on GitHub Releases (EuroklicMapa.apk). It is not on Google Play or the App Store, so install it from the file.

## 1.2.0 — 2026-11-19
- [iOS] The app is now in English and Czech. It follows the phone's language: Czech phones get Czech, everything else gets English (including this changelog, widgets and permission prompts).
- [iOS] Distances use a decimal point in English and a comma in Czech.

## 1.1.0 — 2026-11-19
- [iOS] New Odjezdy mode (public transport): live vehicle positions on the map with the line number, transport type filter, a vehicle card with destination, delay and route, departures from stops, stop and vehicle search, favorite stops and service alerts. Switch it in More → App mode.
- [iOS] Widgets: Nearest toilet and Departures nearby (on the Home Screen and the Lock Screen).
- [iOS] Profile picture and name of the logged-in user in More.
- [iOS] Add a comment or photo to a place, report a problem and (for admins) the approval queue.
- [iOS] Address search on the Euroklíč map.

## 1.0.1 — 2026-11-19
- [iOS] A place's photo can be opened full screen (pinch with two fingers or double-tap to zoom).
- [iOS] Odjezdy: fixed loading of DÚK services (the operator sent times in a different format, so they were dropped) and added an operator switch.
- [iOS] Logging in with Discord and Google now returns to the app when finished (it used to stay on the web and not log you in).
- [iOS] The app has its own icon.
- [iOS] After an update the app shows what's new; the full list is in More → What's new.

## 1.0.0 — 2026-11-19
- [iOS] Place detail as a card over the map: a big Navigate button, live departures from the nearest stops (odjezdy.online), details, voting and comments.
- [iOS] Full-screen map centered on your location, with a panel of the nearest places.
- [iOS] Choice of base map: Mapy.com or Apple Maps (More → Map).
- [iOS] Dark mode and more readable links on a dark background.
- [iOS] Sources for SideStore, AltStore, Feather and LiveContainer are at euroklic.odjezdy.online.
