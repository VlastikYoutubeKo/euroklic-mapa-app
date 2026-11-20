import MapKit
import CoreLocation
import PhotosUI
import SwiftUI

/// Add-place sheet: name, description, map-picked position, optional photo
/// (downscaled to 1800 px JPEG q82 — the server caps uploads at 8 MB).
/// Server sets `source='user'`, `approved=0` → moderation queue.
struct AddPlaceView: View {
    @EnvironmentObject private var auth: AuthSession
    @EnvironmentObject private var location: LocationService
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var desc = ""
    @State private var coordinate: CLLocationCoordinate2D?
    @State private var photoItem: PhotosPickerItem?
    @State private var showLibrary = false
    @State private var showCamera = false
    @State private var showPhotoSource = false
    @State private var showFiles = false
    @State private var photoJPEG: Data?
    @State private var submitting = false
    @State private var resultMessage: String?

    private var canSubmit: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && coordinate != nil && auth.isLoggedIn && !submitting
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Název místa *", text: $name)
                    TextField("Podrobný popis (kde přesně dveře jsou…)", text: $desc, axis: .vertical)
                        .lineLimit(3...6)
                } header: {
                    Text("Nové místo")
                } footer: {
                    Text("Než se místo objeví v mapě, schválí ho moderátor.")
                }

                Section("Poloha *") {
                    NavigationLink {
                        MapPickerView(coordinate: $coordinate)
                    } label: {
                        if let coordinate {
                            Label {
                                LabeledContent("Vybráno", value: String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude))
                            } icon: {
                                Image(systemName: "mappin.circle.fill").foregroundStyle(Theme.brand)
                            }
                        } else {
                            Label("Vybrat na mapě", systemImage: "mappin.and.ellipse")
                        }
                    }
                }

                Section("Fotka (nepovinné)") {
                    if let data = photoJPEG, let image = UIImage(data: data) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                            .frame(maxWidth: .infinity)
                            .frame(height: 180)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
                            .accessibilityLabel("Vybraná fotka")
                        Button("Odebrat fotku", role: .destructive) {
                            photoJPEG = nil
                            photoItem = nil
                        }
                    } else {
                        // One entry point; the menu offers camera / photo library / Files.
                        Button { showPhotoSource = true } label: {
                            Label("Přidat fotku", systemImage: "camera")
                        }
                    }
                }

                if !auth.isLoggedIn {
                    Section {
                        Label("Nejdřív se přihlaste. Fotku i jméno vidí moderátoři.", systemImage: "lock")
                            .font(.footnote)
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
            }
            .safeAreaInset(edge: .bottom) {
                Button {
                    submit()
                } label: {
                    Group {
                        if submitting {
                            ProgressView().tint(.white)
                        } else {
                            Text("Odeslat ke schválení")
                        }
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 28)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .tint(Theme.brand)
                .disabled(!canSubmit)
                .padding(.horizontal)
                .padding(.vertical, 8)
                .background(.bar)
            }
            .confirmationDialog("Přidat fotku", isPresented: $showPhotoSource, titleVisibility: .visible) {
                if CameraPicker.isAvailable {
                    Button("Vyfotit") { showCamera = true }
                }
                Button("Vybrat z fotek") { showLibrary = true }
                Button("Vybrat ze souborů") { showFiles = true }
                Button("Zrušit", role: .cancel) {}
            }
            .fileImporter(isPresented: $showFiles, allowedContentTypes: [.image]) { result in
                guard case .success(let url) = result else { return }
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                if let data = try? Data(contentsOf: url), let image = UIImage(data: data) {
                    photoJPEG = Self.downscaleToJPEG(image, longestEdge: 1800, quality: 0.82)
                }
            }
            .photosPicker(isPresented: $showLibrary, selection: $photoItem, matching: .images)
            .fullScreenCover(isPresented: $showCamera) {
                CameraPicker(isPresented: $showCamera) { image in
                    photoJPEG = Self.downscaleToJPEG(image, longestEdge: 1800, quality: 0.82)
                }
                .ignoresSafeArea()
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
            .task { location.requestAndRefresh() }
        }
    }

    private func submit() {
        guard let picked = coordinate, !submitting else { return }
        submitting = true
        resultMessage = nil
        Task {
            defer { submitting = false }
            do {
                let result = try await EuroklicAPI().addPlace(
                    name: name.trimmingCharacters(in: .whitespaces),
                    desc: desc.trimmingCharacters(in: .whitespaces),
                    lat: picked.latitude,
                    lon: picked.longitude,
                    photoJPEG: photoJPEG,
                )
                if result.success {
                    resultMessage = L("Místo odesláno ke schválení. Po schválení se objeví na mapě všem.")
                    name = ""; desc = ""; coordinate = nil; photoJPEG = nil; photoItem = nil
                } else {
                    resultMessage = result.message ?? result.error ?? L("Server odeslání odmítl.")
                }
            } catch {
                resultMessage = (error as? LocalizedError)?.errorDescription ?? L("Odeslání se nepodařilo.")
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

/// Tap-to-place pin picker; opens on the user's position (when known) with a "my location" button.
struct MapPickerView: View {
    @Binding var coordinate: CLLocationCoordinate2D?
    @EnvironmentObject private var location: LocationService
    @Environment(\.dismiss) private var dismiss
    @State private var recenter: CLLocationCoordinate2D?
    @State private var recenterToken = 0

    var body: some View {
        MapPickerRepresentable(
            coordinate: $coordinate,
            start: coordinate ?? location.lastLocation?.coordinate,
            recenter: recenter,
            recenterToken: recenterToken,
        )
        .ignoresSafeArea(edges: .bottom)
        .navigationTitle("Vyberte polohu")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Hotovo") { dismiss() }
                    .disabled(coordinate == nil)
            }
        }
        .safeAreaInset(edge: .top) {
            Text("Klepněte na mapu, kam patří značka.")
                .font(.footnote)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Capsule().fill(.regularMaterial))
        }
        .overlay(alignment: .bottomTrailing) {
            Button {
                if let c = location.lastLocation?.coordinate {
                    coordinate = c
                    recenter = c
                    recenterToken += 1
                } else {
                    location.requestAndRefresh()
                }
            } label: {
                Image(systemName: "location.fill")
                    .font(.title3)
                    .frame(width: 52, height: 52)
                    .background(Circle().fill(.regularMaterial))
            }
            .padding(16)
            .accessibilityLabel("Moje poloha")
        }
        .onChange(of: location.lastLocation) { newValue in
            // The fix can arrive after the picker opened; centre once if nothing is chosen yet.
            if coordinate == nil, let c = newValue?.coordinate {
                recenter = c
                recenterToken += 1
            }
        }
    }
}

struct MapPickerRepresentable: UIViewRepresentable {
    @Binding var coordinate: CLLocationCoordinate2D?
    var start: CLLocationCoordinate2D?
    var recenter: CLLocationCoordinate2D?
    var recenterToken: Int

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.applyBasemap(MapProvider.stored)
        if let start {
            map.setRegion(MKCoordinateRegion(center: start, latitudinalMeters: 400, longitudinalMeters: 400), animated: false)
        } else {
            map.setRegion(MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 49.8, longitude: 15.5),
                span: MKCoordinateSpan(latitudeDelta: 4, longitudeDelta: 4),
            ), animated: false)
        }
        if let c = coordinate { context.coordinator.place(c, on: map) }
        map.addGestureRecognizer(UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tap(_:))))
        context.coordinator.map = map
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.parent = self
        if context.coordinator.lastToken != recenterToken {
            context.coordinator.lastToken = recenterToken
            if let c = recenter {
                map.setRegion(MKCoordinateRegion(center: c, latitudinalMeters: 300, longitudinalMeters: 300), animated: true)
            }
        }
        // Keep the pin in sync with the binding (set by the "my location" button or a tap).
        if let c = coordinate {
            let shown = map.annotations.first?.coordinate
            if shown == nil || abs(shown!.latitude - c.latitude) > 1e-7 || abs(shown!.longitude - c.longitude) > 1e-7 {
                context.coordinator.place(c, on: map)
            }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, MKMapViewDelegate, UIGestureRecognizerDelegate {
        var parent: MapPickerRepresentable
        weak var map: MKMapView?
        var lastToken = 0

        init(parent: MapPickerRepresentable) { self.parent = parent }

        func place(_ c: CLLocationCoordinate2D, on map: MKMapView) {
            map.removeAnnotations(map.annotations)
            let pin = MKPointAnnotation()
            pin.coordinate = c
            map.addAnnotation(pin)
        }

        @objc func tap(_ recognizer: UITapGestureRecognizer) {
            guard let map else { return }
            let c = map.convert(recognizer.location(in: map), toCoordinateFrom: map)
            parent.coordinate = c
            place(c, on: map)
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
