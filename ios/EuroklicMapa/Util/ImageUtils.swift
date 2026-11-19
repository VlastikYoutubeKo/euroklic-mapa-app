import UIKit

/// Port of Android `ImageUtils.downscaleToJpeg`: respektuje EXIF orientaci, zmenší nejdelší
/// stranu na `maxEdge` a zakóduje JPEG. Server má strop 8 MB, takže se případně
/// ještě sníží kvalita.
enum ImageUtils {
    static let maxUploadBytes = 8 * 1024 * 1024

    static func downscaleToJPEG(data: Data, maxEdge: CGFloat = 1800, quality: CGFloat = 0.82) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        return downscaleToJPEG(image: image, maxEdge: maxEdge, quality: quality)
    }

    static func downscaleToJPEG(image: UIImage, maxEdge: CGFloat = 1800, quality: CGFloat = 0.82) -> Data? {
        // `size` je už po aplikaci orientace; `draw(in:)` orientaci „vypálí“ do pixelů,
        // takže výsledné JPEG nemá EXIF rotaci a je správně otočené všude.
        let longest = max(image.size.width, image.size.height)
        guard longest > 0 else { return nil }
        let scale = min(1, maxEdge / longest)
        let target = CGSize(
            width: max(1, (image.size.width * scale).rounded()),
            height: max(1, (image.size.height * scale).rounded()),
        )

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        let rendered = UIGraphicsImageRenderer(size: target, format: format).image { _ in
            UIColor.white.setFill()
            UIRectFill(CGRect(origin: .zero, size: target))
            image.draw(in: CGRect(origin: .zero, size: target))
        }

        var q = quality
        var jpeg = rendered.jpegData(compressionQuality: q)
        while let current = jpeg, current.count > maxUploadBytes, q > 0.3 {
            q -= 0.1
            jpeg = rendered.jpegData(compressionQuality: q)
        }
        return jpeg
    }
}
