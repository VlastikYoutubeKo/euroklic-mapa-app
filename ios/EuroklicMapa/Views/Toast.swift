import SwiftUI

/// Short, non-blocking confirmation ("sent", "thanks, recorded"). Follows the system look:
/// a material capsule with an SF Symbol near the bottom edge, a matching haptic, and a
/// VoiceOver announcement (the banner itself is not focusable, so it must be announced).
struct ToastItem: Equatable, Identifiable {
    enum Kind { case success, info, failure }

    let id = UUID()
    var text: String
    var kind: Kind = .success

    var symbol: String {
        switch kind {
        case .success: "checkmark.circle.fill"
        case .info: "info.circle.fill"
        case .failure: "exclamationmark.triangle.fill"
        }
    }

    var tint: Color {
        switch kind {
        case .success: .green
        case .info: .secondary
        case .failure: .orange
        }
    }
}

private struct ToastModifier: ViewModifier {
    @Binding var item: ToastItem?

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let item {
                    HStack(spacing: 10) {
                        Image(systemName: item.symbol)
                            .foregroundStyle(item.tint)
                            .imageScale(.large)
                        Text(item.text)
                            .font(.subheadline)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(.regularMaterial, in: Capsule())
                    .shadow(color: .black.opacity(0.15), radius: 8, y: 2)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .accessibilityHidden(true)
                    .onTapGesture { self.item = nil }  // dismiss shortcut only; VoiceOver gets the announcement instead
                }
            }
            .animation(.spring(duration: 0.35), value: item)
            .onChange(of: item) { _, new in
                guard let new else { return }
                UIAccessibility.post(notification: .announcement, argument: new.text)
                switch new.kind {
                case .success: UINotificationFeedbackGenerator().notificationOccurred(.success)
                case .failure: UINotificationFeedbackGenerator().notificationOccurred(.warning)
                case .info: break
                }
            }
            .task(id: item?.id) {
                guard item != nil else { return }
                try? await Task.sleep(for: .seconds(3))
                if !Task.isCancelled { item = nil }
            }
    }
}

extension View {
    func toast(_ item: Binding<ToastItem?>) -> some View {
        modifier(ToastModifier(item: item))
    }
}
