import SwiftUI

/// One-tap switch between Euroklíč (toilets) and Odjezdy (public transport), shown on both maps
/// so nobody has to dig through „Více“. `target` is the mode the pill switches **to**.
struct ModeSwitchPill: View {
    let target: AppMode
    @AppStorage(AppMode.storageKey) private var mode: AppMode = .euroklic

    var body: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { mode = target }
        } label: {
            Label(target == .odjezdy ? L("Odjezdy") : L("Toalety"),
                  systemImage: target == .odjezdy ? "bus.fill" : "figure.roll")
                .font(.footnote.weight(.semibold))
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .foregroundStyle(.white)
                .background(Capsule().fill(Theme.brand))
                .shadow(radius: 3, y: 1)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(target == .odjezdy ? L("Přepnout na režim Odjezdy") : L("Zpět na Euroklíč Mapu"))
    }
}
