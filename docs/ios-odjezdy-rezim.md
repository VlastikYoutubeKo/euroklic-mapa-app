# iOS: režim Odjezdy (veřejná doprava)

Kód: `ios/EuroklicMapa/Odjezdy/**`. Jediný vstupní bod je `OdjezdyRootView` (TabView: Mapa / Hledání / Oblíbené / Více).
API a doporučení: `docs/odjezdy-api-for-ios.md`.

- `Services/ODAPIClient.swift`: actor, krátká cache (vozidla 8 s, odjezdy 20 s, trasa 15 s), jedno volání v letu na URL,
  při 429 čeká do `X-RateLimit-Reset` (strop 2 min), `/api/v2/*` s fallbackem na starší endpointy.
- `ViewModels/ODMapViewModel.swift`: polling `/api/vehicles?fields=map` každých ~16 s jen když je mapa vidět a appka aktivní;
  viewport se filtruje v appce, max 350 štítků, od rozpětí 1,5° se vozidla neukazují.
- `Views/ODVehicleMapView.swift`: MKMapView (podklad Mapy.com/Apple přes `applyBasemap`), štítky linek, plynulý pohyb z `motion` (jen vizuálně, vypnuto při Omezení pohybu).
- Barvy: `Theme/ODTheme.swift` (tokeny z `v2.css` a `palettes.css`, 8 palet, volba v Více, klíč `od_palette`).
- Oblíbené zastávky: `Documents/od_favorite_stops.json`, max 50.
