import SwiftUI

/// Krátký úvod při prvním spuštění (jedna přeskočitelná obrazovka, bez oprávnění).
/// Znovu se dá otevřít ve Více → Jak aplikace funguje.
struct OnboardingView: View {
    /// Znovu otevřený úvod (z Více): tlačítka „Zavřít“ / „Hotovo“ místo „Přeskočit“ / „Začít“.
    var isRevisit = false
    let onFinish: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Text("Vítejte v Euroklíč Mapě")
                        .font(.largeTitle.bold())
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                    point("figure.roll",
                          "Najděte vhodnou toaletu",
                          "Euroklíč je univerzální klíč k vybraným bezbariérovým toaletám. Appka vám pomůže najít místa a informace o přístupu.")
                    point("location",
                          "Polohu povolíte, až ji potřebujete",
                          "Zeptáme se při použití funkce s polohou. Appka funguje i bez ní.")
                    point("hand.raised",
                          "Anonymní měření je ve výchozím stavu vypnuté",
                          "Dobrovolně ho můžete zapnout ve Více.")
                    point("arrow.left.arrow.right",
                          "Dva režimy, jedna appka",
                          "Mezi Euroklíčem a Odjezdy přepnete tlačítkem na mapě nebo ve Více.")
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 24)
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onFinish) {
                        Text(LocalizedStringKey(isRevisit ? "Zavřít" : "Přeskočit"))
                    }
                    .frame(minWidth: 44, minHeight: 44)
                }
            }
            .safeAreaInset(edge: .bottom) {
                Button(action: onFinish) {
                    Text(LocalizedStringKey(isRevisit ? "Hotovo" : "Začít"))
                        .font(.headline)
                        .frame(maxWidth: .infinity, minHeight: 52)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.roundedRectangle(radius: 16))
                .tint(Theme.brand)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(.bar)
            }
        }
        .interactiveDismissDisabled()
    }

    private func point(_ symbol: String, _ title: LocalizedStringKey, _ text: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: symbol)
                .font(.title2)
                .foregroundStyle(Theme.tint)
                .frame(width: 32)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline).accessibilityAddTraits(.isHeader)
                Text(text).font(.body).foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// Vstup do appky: při prvním spuštění úvod, jinak záložky. Stávající uživatelé (už mají uloženou
/// verzi pro „Co je nového“) úvod neuvidí.
struct AppEntryView: View {
    @AppStorage("onboarding_seen_v1") private var seen = false

    init() {
        let d = UserDefaults.standard
        if d.object(forKey: "onboarding_seen_v1") == nil,
           !(d.string(forKey: "last_seen_version") ?? "").isEmpty {
            d.set(true, forKey: "onboarding_seen_v1")
        }
    }

    var body: some View {
        if seen {
            RootTabView()
        } else {
            OnboardingView { seen = true }
        }
    }
}
