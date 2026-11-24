import SwiftUI

/// Jeden řádek odjezdu (jako `.dep` na webu): štítek linky, směr, velký čas vpravo.
struct ODDepartureRow: View {
    @Environment(\.odColors) private var c
    let departure: ODDeparture
    let now: Date
    let isExpanded: Bool
    /// `false` = řádek jen ukazuje odjezd (panel V okolí), VoiceOver pak nehlásí tlačítko ani rozbalení.
    var isInteractive: Bool = true

    private var subtitle: String {
        var parts: [String] = []
        if departure.terminatesHere { parts.append(L("konečná")) }
        if let p = departure.platform, !p.isEmpty { parts.append(L("nást. \(p)")) }
        parts.append(departure.systemLabel)
        if let op = departure.operatorName, !op.isEmpty, op != departure.provider { parts.append(op) }
        if abs(departure.realTime.timeIntervalSince(departure.planned)) >= 60 {
            parts.append(L("dle JŘ \(ODTime.hm(departure.planned))"))
        }
        return parts.joined(separator: " · ")
    }

    private var spokenLabel: String {
        let d = departure
        var s = L("Linka \(d.line.isEmpty ? L("bez čísla") : d.line)")
        if !d.destination.isEmpty { s += L(", směr \(d.destination)") }
        if d.cancelled {
            s += L(", zrušeno")
        } else {
            let mins = ODTime.minutesUntil(d.realTime, now: now)
            if mins <= 0 {
                s += L(", odjíždí právě teď")
            } else if mins < 60 {
                s += ", odjede za \(mins) minut v \(ODTime.hm(d.realTime))"
            } else {
                s += ", odjede v \(ODTime.hm(d.realTime))"
            }
            if let m = d.delayMinutes {
                s += m > 0 ? L(", zpoždění \(m) minut") : L(", včas")
            }
        }
        if let p = d.platform, !p.isEmpty { s += L(", nástupiště \(p)") }
        return s
    }

    var body: some View {
        let d = departure
        let when = d.whenText(now: now)
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                ODLineBadge(line: d.line, cls: d.lineClass)
                VStack(alignment: .leading, spacing: 2) {
                    Text(d.destination.isEmpty ? L("Směr neuveden") : d.destination)
                        .font(.body.weight(.semibold))
                        .strikethrough(d.cancelled)
                        .foregroundStyle(c.text)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(c.muted)
                    if d.cancelled {
                        Text("Zrušeno")
                            .font(.footnote.weight(.bold))
                            .foregroundStyle(c.bad)
                    }
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 0) {
                    HStack(alignment: .firstTextBaseline, spacing: 3) {
                        Text(when.big)
                            .font(.title2.weight(.bold))
                            .monospacedDigit()
                            .fontWidth(.condensed)
                            .foregroundStyle(d.cancelled ? c.muted : c.text)
                        if let small = when.small {
                            Text(small)
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(c.muted)
                        }
                    }
                    if !d.cancelled {
                        ODDelayLabel(minutes: d.delayMinutes)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .frame(minHeight: 44)

            if isExpanded {
                details
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
            }
        }
        .background(isExpanded ? c.surface2 : Color.clear)
        .overlay(alignment: .bottom) {
            Rectangle().fill(c.line).frame(height: 1)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spokenLabel)
        .accessibilityHint(isInteractive ? (isExpanded ? L("Skryje podrobnosti") : L("Zobrazí podrobnosti")) : "")
        .accessibilityAddTraits(isInteractive ? .isButton : [])
    }

    @ViewBuilder
    private var details: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let origin = departure.origin, !origin.isEmpty {
                Text("Z: \(origin)")
            }
            if !departure.viaStops.isEmpty {
                Text("Přes: \(departure.viaStops.joined(separator: ", "))")
            }
            if departure.lowFloor == true {
                Label("Nízkopodlažní vozidlo", systemImage: "figure.roll")
            }
            Text("\(departure.type.label), \(departure.systemLabel)")
        }
        .font(.subheadline)
        .foregroundStyle(c.muted)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Karta zastávky: cedule s názvem, chips dopravců, odjezdy (obnova po ~20 s).
struct ODStopSheet: View {
    @Environment(\.odColors) private var c
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @EnvironmentObject private var favorites: ODFavoritesStore
    @StateObject private var vm: ODStopBoardViewModel
    @State private var expandedId: String?
    @State private var showLimitAlert = false

    private let onShowOnMap: ((ODStop) -> Void)?

    init(stop: ODStop, onShowOnMap: ((ODStop) -> Void)? = nil) {
        _vm = StateObject(wrappedValue: ODStopBoardViewModel(stop: stop))
        self.onShowOnMap = onShowOnMap
    }

    var body: some View {
        VStack(spacing: 0) {
            ODSignHeader(title: vm.stop.name, subtitle: ODProvider.label(vm.stop.provider)) {
                HStack(spacing: 0) {
                    favoriteButton
                    closeButton
                }
            }
            actionBar
            if vm.systems.count >= 2 { chips }
            content
        }
        .background(c.surface)
        .task { await vm.run() }
        .alert("Limit oblíbených", isPresented: $showLimitAlert) {
            Button("Rozumím", role: .cancel) {}
        } message: {
            Text("Můžete mít nejvýš \(ODFavoritesStore.maxCount) oblíbených zastávek. Některou nejdřív odeberte.")
        }
    }

    private var isFavorite: Bool { favorites.contains(vm.stop) }

    private var favoriteButton: some View {
        Button {
            if !favorites.toggle(vm.stop) { showLimitAlert = true }
        } label: {
            Image(systemName: isFavorite ? "star.fill" : "star")
                .font(.title3)
                .foregroundStyle(c.signText)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isFavorite ? L("Odebrat z oblíbených") : L("Přidat do oblíbených"))
    }

    private var closeButton: some View {
        Button {
            dismiss()
        } label: {
            Image(systemName: "xmark")
                .font(.body.weight(.semibold))
                .foregroundStyle(c.signText)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Zavřít")
    }

    private var actionBar: some View {
        HStack(spacing: 8) {
            Button {
                let s = vm.stop
                if let url = URL(string: "http://maps.apple.com/?daddr=\(s.lat),\(s.lng)&dirflg=w") {
                    openURL(url)
                }
            } label: {
                Label("Navigovat", systemImage: "figure.walk")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(c.inkText)
                    .padding(.horizontal, 14)
                    .frame(minHeight: 44)
                    .background(c.ink, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityHint("Otevře pěší navigaci v Mapách")

            if let onShowOnMap {
                Button {
                    onShowOnMap(vm.stop)
                } label: {
                    Label("Na mapě", systemImage: "map")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(c.text)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 44)
                        .background(c.surface, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 6, style: .continuous).stroke(c.line, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .overlay(alignment: .bottom) { Rectangle().fill(c.line).frame(height: 1) }
    }

    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 2) {
                ODChip(title: L("Vše"), isOn: vm.systemFilter == nil) { vm.systemFilter = nil }
                ForEach(vm.systems, id: \.self) { s in
                    ODChip(title: s, isOn: vm.systemFilter == s) { vm.systemFilter = s }
                }
            }
            .padding(.horizontal, 8)
        }
        .overlay(alignment: .bottom) { Rectangle().fill(c.line).frame(height: 1) }
        .accessibilityLabel("Filtr dopravců")
    }

    private var content: some View {
        ScrollView {
            VStack(spacing: 0) {
                if let err = vm.errorText {
                    ODBanner(
                        symbol: "wifi.exclamationmark",
                        text: vm.departures.isEmpty ? err : err + L(" Zobrazeny jsou poslední načtené odjezdy."),
                        tone: .warning
                    )
                    .padding(12)
                }
                if vm.isLoading && vm.departures.isEmpty {
                    ProgressView("Načítám odjezdy…")
                        .foregroundStyle(c.muted)
                        .padding(32)
                        .frame(maxWidth: .infinity)
                } else if vm.visibleDepartures.isEmpty {
                    if vm.errorText == nil {
                        ODEmptyState(
                            symbol: "clock.badge.questionmark",
                            title: L("Žádné odjezdy"),
                            message: L("V nejbližších hodinách odsud nic nejede, nebo o spojích nemáme data.")
                        )
                    }
                } else {
                    TimelineView(.periodic(from: Date(), by: 30)) { context in
                        VStack(spacing: 0) {
                            ForEach(vm.visibleDepartures) { d in
                                Button {
                                    withAnimation(.easeInOut(duration: 0.15)) {
                                        expandedId = (expandedId == d.id) ? nil : d.id
                                    }
                                } label: {
                                    ODDepartureRow(departure: d, now: context.date, isExpanded: expandedId == d.id)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    if let t = vm.lastLoad {
                        Text("Aktualizováno \(ODTime.hm(t)) · data odjezdy.online")
                            .font(.footnote)
                            .foregroundStyle(c.muted)
                            .padding(16)
                    }
                }
            }
        }
        .refreshable { await vm.load() }
    }
}
