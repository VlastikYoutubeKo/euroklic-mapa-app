import CoreLocation
import PhotosUI
import SwiftUI

/// Add-place sheet: name, description, map-picked position, optional photo
/// (downscaled to 1800 px JPEG q82 — the server caps uploads at 8 MB).
/// Server sets `source='user'`, `approved=0` → moderation queue.
struct AddPlaceView: View {
    @EnvironmentObject private var auth: AuthSession
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var desc = ""
    @State private var coordinate: CLLocationCoordinate2D?
    @State private var photoItem: PhotosPickerItem?
    @State private var photoJPEG: Data?
    @State private var submitting = false
    @State private var resultMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Nové místo") {
                    TextField("Název místa *", text: $name)
                    TextField("Podrobný popis (kde přesně dveře jsou…)", text: $desc, axis: .vertical)
                        .lineLimit(3...6)
                }

                Section("Poloha *") {
                    NavigationLink {
                        MapPickerView(coordinate: $coordinate)
                    } label: {
                        if let coordinate {
                            LabeledContent("Vybráno", value: String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude))
                        } else {
                            Text("Vybrat na mapě")
                        }
                    }
                }

                Section("Fotka (nepovinné)") {
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        Label(photoJPEG == nil ? "Vybrat fotku" : "Fotka vybrána ✓", systemImage: "camera")
                    }
                    if photoJPEG != nil {
                        Button("Odebrat fotku", role: .destructive) {
                            photoJPEG = nil
                            photoItem = nil
                        }
                        .font(.footnote)
                    }
                }

                if !auth.isLoggedIn {
                    Section {
                        HStack(spacing: 10) {
                            Image(systemName: "lock")
                            Text("Nejdřív se přihlaste (Více → Přihlásit se). Fotka i jméno vidí moderátoři.")
                                .font(.footnote)
                        }
                        Button("Přihlásit se") { auth.login() }
                    }
                }

                if let resultMessage {
                    Section {
                        Text(resultMessage).font(.footnote)
                    }
                }
            }
            .navigationTitle("Přidat místo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Zrušit") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if submitting {
                        ProgressView()
                    } else {
                        Button("Odeslat ke schválení") { submit() }
                            .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || coordinate == nil || !auth.isLoggedIn)
                    }
                }
            }
            .onChange(of: photoItem) { newItem in
                guard let item = newItem else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let image = UIImage(data: data) {
                        photoJPEG = Self.downscaleToJPEG(image, longestEdge: 1800, quality: 0.82)
                    }
                }
            }
        }
    }

    private func submit() {
        guard let coordinate, !submitting else { return }
        submitting = true
        resultMessage = nil
        Task {
            defer { submitting = false }
            do {
                let result = try await EuroklicAPI().addPlace(
                    name: name.trimmingCharacters(in: .whitespaces),
                    desc: desc.trimmingCharacters(in: .whitespaces),
                    lat: coordinate.latitude,
                    lon: coordinate.longitude,
                    photoJPEG: photoJPEG,
                )
                if result.success {
                    resultMessage = "Místo odesláno ke schválení. Po schválení se objeví na mapě všem."
                    name = ""; desc = ""; coordinate = nil; photoJPEG = nil; photoItem = nil
                } else {
                    resultMessage = result.message ?? result.error ?? "Server odeslání odmítl."
                }
            } catch {
                resultMessage = (error as? LocalizedError)?.errorDescription ?? "Odeslání se nepodařilo."
            }
        }
    }

    /// Port of Android `ImageUtils.downscaleToJpeg` (longest edge 1800, q82).
    static func downscaleToJPEG(_ image: UIImage, longestEdge: CGFloat, quality: CGFloat) -> Data? {
        let maxSide = max(image.size.width, image.size.height)
        let scale = min(1, longestEdge / maxSide)
        let target = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let rendered = UIGraphicsImageRenderer(size: target, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
        return rendered.jpegData(compressionQuality: quality)
    }
}

/// Tap-to-place pin picker — "Klikni kamkoliv na mapu pro umístění bodu!"
struct MapPickerView: View {
    @Binding var coordinate: CLLocationCoordinate2D?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        MapPickerRepresentable(coordinate: $coordinate)
            .ignoresSafeArea()
            .navigationTitle("Vyberte polohu")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Hotovo") { dismiss() }
                        .disabled(coordinate == nil)
                }
            }
            .safeAreaInset(edge: .top) {
                Text("Klikni kamkoliv na mapu pro umístění bodu!")
                    .font(.footnote)
                    .padding(8)
                    .background(Capsule().fill(.regularMaterial))
            }
    }
}

struct MapPickerRepresentable: UIViewRepresentable {
    @Binding var coordinate: CLLocationCoordinate2D?

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        let tiles = MKTileOverlay(urlTemplate: MapViewTemplate.url)
        tiles.canReplaceMapContent = true
        map.addOverlay(tiles)
        map.setRegion(MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 49.8, longitude: 15.5),
            span: MKCoordinateSpan(latitudeDelta: 4, longitudeDelta: 4),
        ), animated: false)
        map.addGestureRecognizer(UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tap(_:))))
        context.coordinator.map = map
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.parent = self
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, MKMapViewDelegate, UIGestureRecognizerDelegate {
        var parent: MapPickerRepresentable
        weak var map: MKMapView?

        init(parent: MapPickerRepresentable) { self.parent = parent }

        @objc func tap(_ recognizer: UITapGestureRecognizer) {
            guard let map else { return }
            let point = recognizer.location(in: map)
            parent.coordinate = map.convert(point, toCoordinateFrom: map)
            map.removeAnnotations(map.annotations)
            let pin = MKPointAnnotation()
            pin.coordinate = parent.coordinate!
            map.addAnnotation(pin)
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            true
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tiles = overlay as? MKTileOverlay {
                return MKTileOverlayRenderer(tileOverlay: tiles)
            }
            return MKOverlayRenderer(overlay: overlay)
        }
    }
}
