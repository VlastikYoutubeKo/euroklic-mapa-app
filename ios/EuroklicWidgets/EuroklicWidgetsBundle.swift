import SwiftUI
import WidgetKit

@main
struct EuroklicWidgetsBundle: WidgetBundle {
    var body: some Widget {
        NearestToiletWidget()
        DeparturesWidget()
    }
}
