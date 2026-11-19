import SwiftUI

/// Spodní karta vybraného vozidla na mapě (směr, zpoždění, další zastávka).
struct ODVehicleCard: View {
    @Environment(\.odColors) private var c
    @ObservedObject var vm: ODMapViewModel
    let vehicle: ODVehicle

    private var subtitle: String {
        var parts: [String] = [vehicle.type.label]
        if let carrier = vm.info?.carrier, !carrier.isEmpty {
            parts.append(carrier)
        } else {
            parts.append(ODProvider.label(vehicle.provider))
        }
        if let next = vehicle.nextStop, !next.isEmpty { parts.append(L("další: \(next)")) }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 12) {
                ODLineBadge(line: vehicle.isInService ? vehicle.line : vehicle.badgeText, cls: vehicle.lineClass)
                VStack(alignment: .leading, spacing: 2) {
                    Text(destinationText)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(c.text)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(c.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    if vehicle.offline {
                        Text("Poloha vozidla není aktuální")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(c.bad)
                    } else {
                        ODDelayLabel(minutes: vehicle.isInService ? vehicle.delayMinutes : nil)
                    }
                }
                Spacer(minLength: 4)
                Button {
                    vm.clearSelection()
                } label: {
                    Image(systemName: "xmark")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(c.text)
                        .frame(width: 44, height: 44)
                        .background(c.surface2, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Zavřít kartu vozidla")
            }
            Button {
                vm.showVehicleDetail = true
            } label: {
                HStack {
                    Label("Zastávky a podrobnosti", systemImage: "list.bullet")
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    Image(systemName: "chevron.up")
                        .font(.footnote.weight(.semibold))
                        .accessibilityHidden(true)
                }
                .foregroundStyle(c.text)
                .padding(.horizontal, 12)
                .frame(minHeight: 44)
                .background(c.surface2, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityHint("Zobrazí časovou osu zastávek a údaje o vozidle")
        }
        .padding(12)
        .odCard()
        .shadow(color: Color.black.opacity(0.2), radius: 10, y: 3)
        .accessibilityElement(children: .contain)
    }

    private var destinationText: String {
        if let d = vehicle.destination, !d.isEmpty {
            return vehicle.isInService ? "→ \(d)" : d
        }
        return vehicle.isInService ? L("Směr neuveden") : L("Vozidlo mimo provoz")
    }
}

/// Podrobnosti vozidla: zpoždění, vizitka a časová osa zastávek.
struct ODVehicleDetailSheet: View {
    @Environment(\.odColors) private var c
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var vm: ODMapViewModel

    var body: some View {
        VStack(spacing: 0) {
            if let v = vm.selectedVehicle {
                header(v)
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        delaySection(v)
                        infoSection(v)
                        timelineSection(v)
                    }
                    .padding(16)
                }
            } else {
                ODEmptyState(symbol: "bus", title: L("Vozidlo není vybráno"), message: L("Vyberte vozidlo na mapě."))
                Spacer(minLength: 0)
            }
        }
        .background(c.surface)
    }

    private func header(_ v: ODVehicle) -> some View {
        HStack(spacing: 12) {
            ODLineBadge(line: v.isInService ? v.line : v.badgeText, cls: v.lineClass)
            VStack(alignment: .leading, spacing: 2) {
                Text(v.destination.map { $0.isEmpty ? L("Směr neuveden") : "→ \($0)" } ?? L("Směr neuveden"))
                    .font(.title3.weight(.bold))
                    .foregroundStyle(c.text)
                    .accessibilityAddTraits(.isHeader)
                Text(v.type.label)
                    .font(.footnote)
                    .foregroundStyle(c.muted)
            }
            Spacer(minLength: 0)
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(c.text)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Zavřít")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .overlay(alignment: .bottom) { Rectangle().fill(c.line).frame(height: 1) }
    }

    // MARK: Zpoždění

    @ViewBuilder
    private func delaySection(_ v: ODVehicle) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            sectionTitle(L("Zpoždění"))
            if v.isInService, let m = v.delayMinutes {
                HStack(spacing: 8) {
                    Text(m > 0 ? "+\(m) min" : L("včas"))
                        .font(.title2.weight(.bold))
                        .monospacedDigit()
                        .fontWidth(.condensed)
                        .foregroundStyle(m > 0 ? c.bad : c.good)
                    Text("aktuálně")
                        .font(.footnote)
                        .foregroundStyle(c.muted)
                }
            } else {
                Text("Aktuální zpoždění není známé.")
                    .font(.subheadline)
                    .foregroundStyle(c.muted)
            }
            if let avg = vm.avgDelay, let a = avg.avgDelay, avg.tripCount > 0 {
                let rounded = Int(a.rounded())
                Text("Tento spoj měl za posledních 14 dní průměrné zpoždění \(rounded > 0 ? "+\(rounded) min" : L("do minuty")) (\(avg.tripCount) jízd).")
                    .font(.subheadline)
                    .foregroundStyle(c.muted)
            }
        }
    }

    // MARK: Vizitka

    @ViewBuilder
    private func infoSection(_ v: ODVehicle) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionTitle(L("Vozidlo"))
            let facts = factList(v)
            if facts.isEmpty && !(vm.info?.pending ?? false) {
                Text("Údaje o vozidle nejsou k dispozici.")
                    .font(.subheadline)
                    .foregroundStyle(c.muted)
            }
            ForEach(facts, id: \.0) { fact in
                HStack(alignment: .firstTextBaseline) {
                    Text(fact.0)
                        .font(.subheadline)
                        .foregroundStyle(c.muted)
                    Spacer(minLength: 12)
                    Text(fact.1)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(c.text)
                        .multilineTextAlignment(.trailing)
                }
                .accessibilityElement(children: .combine)
            }
            if vm.info?.pending == true {
                Text("Údaje o vozidle se právě dohledávají…")
                    .font(.footnote)
                    .foregroundStyle(c.muted)
            }
            if let note = vm.info?.note, !note.isEmpty {
                Text(note)
                    .font(.footnote)
                    .foregroundStyle(c.muted)
            }
        }
    }

    private func factList(_ v: ODVehicle) -> [(String, String)] {
        var out: [(String, String)] = []
        out.append((L("Systém"), ODProvider.label(v.provider)))
        if let i = vm.info {
            if let s = i.carrier, !s.isEmpty { out.append((L("Dopravce"), s)) }
            let model = [i.manufacturer, i.model].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " ")
            if !model.isEmpty { out.append(("Model", model)) }
            if let s = i.year, !s.isEmpty { out.append((L("Rok výroby"), s)) }
            if let s = i.depot, !s.isEmpty { out.append((L("Vozovna"), s)) }
            if let s = i.fleetNo, !s.isEmpty { out.append((L("Evidenční číslo"), s)) }
            if let s = i.plate, !s.isEmpty { out.append(("SPZ", s)) }
            if let s = i.equipment, !s.isEmpty { out.append((L("Vybavení"), s)) }
        }
        if vm.info?.fleetNo == nil, let e = v.evc, !e.isEmpty { out.append((L("Evidenční číslo"), e)) }
        if vm.info?.plate == nil, let s = v.spz, !s.isEmpty { out.append(("SPZ", s)) }
        if v.wheelchair == true { out.append((L("Bezbariérové"), L("ano"))) }
        // Duplicitní popisky vyřadit (id v ForEach).
        var seen = Set<String>()
        return out.filter { seen.insert($0.0).inserted }
    }

    // MARK: Časová osa

    @ViewBuilder
    private func timelineSection(_ v: ODVehicle) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionTitle(L("Zastávky"))
                .padding(.bottom, 8)
            if let route = vm.route {
                if route.stops.isEmpty {
                    Text(route.available ? L("Pro tento spoj nejsou k dispozici zastávky.") : L("Trasa vozidla není k dispozici."))
                        .font(.subheadline)
                        .foregroundStyle(c.muted)
                } else {
                    let passedCount = passed(route.stops, vehicle: v)
                    ForEach(Array(route.stops.enumerated()), id: \.offset) { index, stop in
                        timelineRow(stop, index: index, count: route.stops.count, passed: index < passedCount, vehicle: v)
                    }
                }
            } else {
                ProgressView("Načítám trasu…")
                    .foregroundStyle(c.muted)
            }
        }
    }

    /// Kolik zastávek už vozidlo projelo (podle jména další zastávky, jinak podle času).
    private func passed(_ stops: [ODRouteStop], vehicle v: ODVehicle) -> Int {
        if let next = v.nextStop?.lowercased(), !next.isEmpty,
           let idx = stops.firstIndex(where: { $0.name.lowercased() == next }) {
            return idx
        }
        let delay = Double(max(0, v.delayMinutes ?? 0)) * 60
        let now = Date()
        var count = 0
        for s in stops {
            if let t = s.time, t.addingTimeInterval(delay) < now { count += 1 } else { break }
        }
        return count
    }

    private func timelineRow(_ stop: ODRouteStop, index: Int, count: Int, passed: Bool, vehicle v: ODVehicle) -> some View {
        let delayMin = max(0, v.delayMinutes ?? 0)
        let expected: Date? = {
            guard let t = stop.time else { return nil }
            return t.addingTimeInterval(Double(delayMin) * 60)
        }()
        return HStack(alignment: .top, spacing: 12) {
            VStack(spacing: 0) {
                Rectangle()
                    .fill(index == 0 ? Color.clear : (passed ? c.muted : c.accent))
                    .frame(width: 3, height: 10)
                Circle()
                    .fill(passed ? c.muted : c.accent)
                    .frame(width: 12, height: 12)
                Rectangle()
                    .fill(index == count - 1 ? Color.clear : (passed ? c.muted : c.accent))
                    .frame(width: 3)
                    .frame(maxHeight: .infinity)
            }
            .frame(width: 14)
            .accessibilityHidden(true)

            Text(stop.name)
                .font(.body.weight(passed ? .regular : .semibold))
                .foregroundStyle(passed ? c.muted : c.text)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.vertical, 8)
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 0) {
                if let t = stop.time {
                    Text(ODTime.hm(t))
                        .font(.body.weight(.semibold))
                        .monospacedDigit()
                        .fontWidth(.condensed)
                        .foregroundStyle(passed ? c.muted : c.text)
                } else if let raw = stop.rawTime, !raw.isEmpty {
                    Text(raw)
                        .font(.body)
                        .foregroundStyle(c.muted)
                }
                if !passed, delayMin > 0, let e = expected {
                    Text(ODTime.hm(e))
                        .font(.footnote.weight(.semibold))
                        .monospacedDigit()
                        .foregroundStyle(c.bad)
                }
            }
            .padding(.vertical, 8)
        }
        .frame(minHeight: 44)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(timelineLabel(stop, passed: passed, expected: expected, delayMin: delayMin))
    }

    private func timelineLabel(_ stop: ODRouteStop, passed: Bool, expected: Date?, delayMin: Int) -> String {
        var s = stop.name
        if let t = stop.time { s += ", \(ODTime.hm(t))" }
        if passed {
            s += L(", již projeto")
        } else if delayMin > 0, let e = expected {
            s += L(", s předpokládaným zpožděním \(delayMin) minut v \(ODTime.hm(e))")
        }
        return s
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.footnote.weight(.bold))
            .foregroundStyle(c.muted)
            .accessibilityAddTraits(.isHeader)
    }
}
