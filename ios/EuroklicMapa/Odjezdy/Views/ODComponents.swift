import SwiftUI

/// Štítek linky (Barlow Condensed na webu; tady systémové písmo se zúžením).
struct ODLineBadge: View {
    @Environment(\.odColors) private var c
    let line: String
    let cls: ODLineClass

    var body: some View {
        Text(line.isEmpty ? "–" : line)
            .font(.headline.weight(.bold))
            .fontWidth(.condensed)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .foregroundStyle(Color.white)
            .padding(.horizontal, 8)
            .frame(minWidth: 44, minHeight: 30)
            .background(c.lineColor(for: cls), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
            .dynamicTypeSize(...DynamicTypeSize.accessibility1)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(line.isEmpty ? "Bez linky" : "Linka \(line)")
    }
}

/// "včas" / "+3 min" – bez barvy by se nedalo rozlišit, proto vždy i text.
struct ODDelayLabel: View {
    @Environment(\.odColors) private var c
    let minutes: Int?
    var showOnTime = true

    var body: some View {
        if let minutes {
            if minutes > 0 {
                Text("+\(minutes) min")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(c.bad)
                    .accessibilityLabel("zpoždění \(minutes) minut")
            } else if showOnTime {
                Text("včas")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(c.good)
                    .accessibilityLabel("jede včas")
            }
        }
    }
}

enum ODBannerTone {
    case info, warning
}

struct ODBanner: View {
    @Environment(\.odColors) private var c
    let symbol: String
    let text: String
    var tone: ODBannerTone = .info

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: symbol)
                .foregroundStyle(tone == .warning ? c.bad : c.muted)
                .accessibilityHidden(true)
            Text(text)
                .font(.footnote.weight(.medium))
                .foregroundStyle(c.text)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(c.surface, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(c.line, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}

struct ODEmptyState: View {
    @Environment(\.odColors) private var c
    let symbol: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: symbol)
                .font(.largeTitle)
                .foregroundStyle(c.muted)
                .accessibilityHidden(true)
            Text(title)
                .font(.headline)
                .foregroundStyle(c.text)
                .multilineTextAlignment(.center)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(c.muted)
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }
}

/// Kulaté tlačítko filtru (44 pt), zapnuté = plné, vypnuté = obrys.
struct ODRoundFilterButton: View {
    @Environment(\.odColors) private var c
    let filter: ODMapFilter
    let isOn: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: filter.symbol)
                .font(.body.weight(.semibold))
                .foregroundStyle(isOn ? c.inkText : c.text)
                .frame(width: 44, height: 44)
                .background(
                    Circle().fill(isOn ? c.ink : c.surface)
                )
                .overlay(Circle().stroke(isOn ? c.ink : c.line, lineWidth: 1.5))
                .shadow(color: Color.black.opacity(0.18), radius: 4, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(filter.title)
        .accessibilityValue(isOn ? "zapnuto" : "vypnuto")
        .accessibilityHint("Klepnutím zobrazíte nebo skryjete")
        .accessibilityAddTraits(.isToggle)
    }
}

/// Karta v duchu webu: plocha, 1 pt okraj, poloměr 8.
struct ODCardBackground: ViewModifier {
    @Environment(\.odColors) private var c

    func body(content: Content) -> some View {
        content
            .background(c.surface, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(c.line, lineWidth: 1))
    }
}

extension View {
    func odCard() -> some View { modifier(ODCardBackground()) }
}

/// Hlavička odpovídající "označníku" (cedule) zastávky.
struct ODSignHeader<Trailing: View>: View {
    @Environment(\.odColors) private var c
    let title: String
    let subtitle: String?
    @ViewBuilder let trailing: () -> Trailing

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(c.signText)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(c.signText.opacity(0.9))
                }
            }
            Spacer(minLength: 0)
            trailing()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(c.sign)
    }
}

/// Žlutý podtržení jako `--mark` (aktivní záložka / chip na webu).
struct ODChip: View {
    @Environment(\.odColors) private var c
    let title: String
    let isOn: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(isOn ? c.text : c.muted)
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .overlay(alignment: .bottom) {
                    Rectangle()
                        .fill(isOn ? c.mark : Color.clear)
                        .frame(height: 3)
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? [.isSelected, .isButton] : .isButton)
    }
}

extension ODDeparture {
    /// "5 min" / "teď" / "07:32" pro velké číslo vpravo.
    func whenText(now: Date) -> (big: String, small: String?) {
        let t = realTime
        let mins = ODTime.minutesUntil(t, now: now)
        if mins <= 0 { return ("teď", nil) }
        if mins < 60 { return ("\(mins)", "min") }
        return (ODTime.hm(t), nil)
    }
}
