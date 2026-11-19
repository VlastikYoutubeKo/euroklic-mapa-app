import SwiftUI

/// Full-screen photo: pinch to zoom, drag when zoomed, double-tap toggles zoom, ✕ or swipe-free close button.
struct PhotoViewer: View {
    let url: URL
    @Environment(\.dismiss) private var dismiss

    @State private var scale: CGFloat = 1
    @State private var baseScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var baseOffset: CGSize = .zero

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()

            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFit()
                        .scaleEffect(scale)
                        .offset(offset)
                        .gesture(zoomAndPan)
                        .onTapGesture(count: 2) { toggleZoom() }
                        .accessibilityLabel("Fotografie místa")
                        .accessibilityHint("Dvojitým klepnutím přiblížíte")
                case .failure:
                    VStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle")
                        Text("Fotku se nepodařilo načíst")
                    }
                    .foregroundStyle(.white)
                default:
                    ProgressView().tint(.white)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(.black.opacity(0.55)))
            }
            .padding(16)
            .accessibilityLabel("Zavřít fotku")
        }
    }

    private var zoomAndPan: some Gesture {
        MagnifyGesture()
            .onChanged { value in
                scale = min(max(baseScale * value.magnification, 1), 6)
            }
            .onEnded { _ in
                baseScale = scale
                if scale <= 1.01 { reset() }
            }
            .simultaneously(with:
                DragGesture()
                    .onChanged { value in
                        guard scale > 1 else { return }
                        offset = CGSize(
                            width: baseOffset.width + value.translation.width,
                            height: baseOffset.height + value.translation.height,
                        )
                    }
                    .onEnded { _ in baseOffset = offset }
            )
    }

    private func toggleZoom() {
        withAnimation(.easeOut(duration: 0.2)) {
            if scale > 1 { reset() } else { scale = 2.5; baseScale = 2.5 }
        }
    }

    private func reset() {
        scale = 1
        baseScale = 1
        offset = .zero
        baseOffset = .zero
    }
}
