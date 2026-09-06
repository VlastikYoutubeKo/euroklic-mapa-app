import Foundation

/// Mapy.com raster tile config. The key is injected at BUILD TIME via
/// `Secrets.xcconfig` → build setting `MAPY_APIKEY` → Info.plist `MapyApiKey`
/// (see project.yml). Never committed to VCS — copy
/// `Secrets.xcconfig.example` to `Secrets.xcconfig` before building.
enum MapyTiles {
    static let apiKey = (Bundle.main.object(forInfoDictionaryKey: "MapyApiKey") as? String) ?? ""

    /// Empty when the key is missing — callers skip the tile overlay and the
    /// app falls back to Apple's basemap instead of spamming 401s.
    static var urlTemplate: String {
        guard !apiKey.isEmpty, apiKey != "REPLACE_WITH_YOUR_MAPY_KEY" else { return "" }
        return "https://api.mapy.com/v1/maptiles/basic/256/{z}/{x}/{y}?apikey=\(apiKey)"
    }
}
