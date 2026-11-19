import Foundation

/// Surový výsledek zápisového volání: HTTP stav (0 = síťová chyba) + tolerantně dekódované tělo.
struct WriteOutcome {
    let status: Int
    let reply: WriteReply?
}

/// Obsah admin fronty (4 samostatné seznamy se samostatnými id).
struct AdminQueue {
    var places: [AdminPlace]
    var photos: [PhotoSuggestion]
    var comments: [CommentSuggestion]
    var reports: [LocationReport]

    var isEmpty: Bool { places.isEmpty && photos.isEmpty && comments.isEmpty && reports.isEmpty }
    /// Záhlaví sekcí jen když je neprázdných víc než jeden seznam (jako Android).
    var showsSectionHeaders: Bool {
        [places.isEmpty, photos.isEmpty, comments.isEmpty, reports.isEmpty].filter { !$0 }.count > 1
    }
}

enum AdminListResult {
    case loaded(AdminQueue)
    case failed(String)
}

/// Zápisové endpointy s Bearerem (komentáře, hlášení, fotky, admin). Tokeny se nikdy nelogují.
extension EuroklicAPI {
    static let offlineMessage = L("Bez připojení. Zkuste to znovu, až budete online.")
    static let maxPhotoBytes = 8 * 1024 * 1024

    // MARK: - Komentář

    func postComment(locationId: Int, text: String) async -> WriteResult {
        let payload: [String: Any] = ["location_id": locationId, "text": text.trimmingCharacters(in: .whitespacesAndNewlines)]
        guard let body = try? JSONSerialization.data(withJSONObject: payload) else {
            return .failed(L("Komentář se nepodařilo odeslat."))
        }
        var request = URLRequest(url: Self.baseURL.appendingPathComponent("api_comments.php"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        authorizeWrite(&request)

        let outcome = await perform(request)
        return translate(
            outcome,
            okMessage: L("Díky! Komentář čeká na schválení moderátorem a pak se objeví u místa."),
            fallback: L("Komentář se nepodařilo odeslat."),
            byStatus: [
                401: L("Přihlášení vypršelo. Přihlaste se prosím znovu."),
                403: L("Přihlášení vypršelo. Přihlaste se prosím znovu."),
                400: L("Komentář se nepodařilo odeslat (zkontrolujte délku 3–2000 znaků, max 1 odkaz)."),
                404: L("Tohle místo se nepodařilo najít."),
                409: L("Tenhle komentář jste už přidali."),
                429: L("Za poslední dobu jste přidali hodně příspěvků. Zkuste to později."),
            ],
            serverTextFor: [400],
        )
    }

    // MARK: - Nahlášení problému

    func reportProblem(locationId: Int, reason: ReportReason, note: String?) async -> WriteResult {
        var payload: [String: Any] = ["location_id": locationId, "reason": reason.rawValue]
        if let note = note?.trimmingCharacters(in: .whitespacesAndNewlines), !note.isEmpty {
            payload["note"] = note
        }
        guard let body = try? JSONSerialization.data(withJSONObject: payload) else {
            return .failed(L("Hlášení se nepodařilo odeslat."))
        }
        var request = URLRequest(url: Self.baseURL.appendingPathComponent("api_report.php"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        authorizeWrite(&request)

        let outcome = await perform(request)
        return translate(
            outcome,
            okMessage: L("Díky, hlášení je odesláno. Prověří ho moderátor."),
            fallback: L("Hlášení se nepodařilo odeslat."),
            byStatus: [
                401: L("Přihlášení vypršelo. Přihlaste se prosím znovu."),
                403: L("Přihlášení vypršelo. Přihlaste se prosím znovu."),
                400: L("Hlášení se nepodařilo odeslat (zkontrolujte vyplněná pole)."),
                404: L("Tohle místo se nepodařilo najít."),
                409: L("Tohle už jste nahlásili — čeká to na vyřízení."),
                429: L("Za poslední hodinu jste toho nahlásili hodně. Zkuste to později."),
            ],
            serverTextFor: [400],
        )
    }

    // MARK: - Fotka k existujícímu místu

    func addPhoto(placeId: Int, jpeg: Data) async -> WriteResult {
        guard !jpeg.isEmpty else { return .failed(L("Fotku se nepodařilo zpracovat.")) }
        guard jpeg.count <= Self.maxPhotoBytes else { return .failed(L("Fotka je příliš velká (max 8 MB).")) }

        let boundary = "Boundary-\(UUID().uuidString)"
        var body = Data()
        body.append(Data("--\(boundary)\r\n".utf8))
        body.append(Data("Content-Disposition: form-data; name=\"id\"\r\n\r\n".utf8))
        body.append(Data("\(placeId)\r\n".utf8))
        body.append(Data("--\(boundary)\r\n".utf8))
        body.append(Data("Content-Disposition: form-data; name=\"photo_file\"; filename=\"photo.jpg\"\r\n".utf8))
        body.append(Data("Content-Type: image/jpeg\r\n\r\n".utf8))
        body.append(jpeg)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))

        var request = URLRequest(url: Self.baseURL.appendingPathComponent("api_add_photo.php"))
        request.httpMethod = "POST"
        request.timeoutInterval = 90
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        authorizeWrite(&request)

        let outcome = await perform(request)
        return translate(
            outcome,
            okMessage: L("Díky! Fotka čeká na schválení moderátorem."),
            fallback: L("Odeslání se nezdařilo."),
            byStatus: [
                401: L("Přihlášení vypršelo. Přihlaste se prosím znovu."),
                403: L("Přihlášení vypršelo. Přihlaste se prosím znovu."),
                404: L("Tohle místo se nepodařilo najít."),
                413: L("Fotka je příliš velká (max 8 MB)."),
                429: L("Za poslední hodinu jste přidali hodně fotek. Zkuste to za chvíli."),
            ],
        )
    }

    // MARK: - Admin fronta

    func adminList() async -> AdminListResult {
        var request = URLRequest(url: Self.baseURL.appendingPathComponent("api_admin_list.php"))
        authorizeWrite(&request)
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            if status == 401 || status == 403 { return .failed(L("Nemáte oprávnění.")) }
            guard status == 200 else { return .failed("Chyba serveru (\(status)).") }
            guard let list = try? JSONDecoder().decode(AdminListResponse.self, from: data) else {
                return .failed(L("Odpověď serveru se nepodařilo zpracovat."))
            }
            if let error = list.error, !error.isEmpty { return .failed(error) }
            return .loaded(AdminQueue(
                places: list.locations ?? [],
                photos: list.photo_suggestions ?? [],
                comments: list.comment_suggestions ?? [],
                reports: list.location_reports ?? [],
            ))
        } catch {
            return .failed(L("Bez připojení."))
        }
    }

    /// `action` ∈ approve|reject|approve_photo|reject_photo|approve_comment|reject_comment|
    /// resolve_report|dismiss_report; `idField` ∈ id|photo_id|comment_id|report_id.
    func adminReview(action: String, idField: String, id: Int, successMessage: String) async -> WriteResult {
        var request = URLRequest(url: Self.baseURL.appendingPathComponent("api_admin.php"))
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data("action=\(action)&\(idField)=\(id)".utf8)
        authorizeWrite(&request)

        let outcome = await perform(request)
        switch outcome.status {
        case 0:
            return .failed(Self.offlineMessage)
        case 200:
            guard let reply = outcome.reply else { return .failed(L("Odpověď serveru se nepodařilo zpracovat.")) }
            if reply.success == true {
                return .ok(nonBlank(reply.message) ?? successMessage)
            }
            return .failed(reply.text ?? L("Akce se nezdařila."))
        case 404:
            // Frontu sdílí web i Discord: někdo jiný to už vyřídil → řádek zmizí.
            return .gone(outcome.reply?.text ?? L("Položku už vyřídil někdo jiný."))
        case 401, 403:
            return .failed(outcome.reply?.text ?? L("Nemáte oprávnění."))
        default:
            return .failed(outcome.reply?.text ?? "Chyba serveru (\(outcome.status)).")
        }
    }

    // MARK: - Plumbing

    private func authorizeWrite(_ request: inout URLRequest) {
        if let token = tokenProvider() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
    }

    private func perform(_ request: URLRequest) async -> WriteOutcome {
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            let reply = try? JSONDecoder().decode(WriteReply.self, from: data)
            return WriteOutcome(status: status, reply: reply)
        } catch {
            return WriteOutcome(status: 0, reply: nil)
        }
    }

    private func nonBlank(_ s: String?) -> String? {
        guard let s, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return s
    }

    private func translate(
        _ outcome: WriteOutcome,
        okMessage: String,
        fallback: String,
        byStatus: [Int: String],
        serverTextFor: Set<Int> = [],
    ) -> WriteResult {
        switch outcome.status {
        case 0:
            return .failed(Self.offlineMessage)
        case 200:
            // Chybějící cesta vrací HTML s 200 → reply == nil.
            guard let reply = outcome.reply else { return .failed(L("Odpověď serveru se nepodařilo zpracovat.")) }
            return reply.success == true ? .ok(okMessage) : .failed(reply.text ?? fallback)
        default:
            if serverTextFor.contains(outcome.status), let text = outcome.reply?.text { return .failed(text) }
            return .failed(byStatus[outcome.status] ?? "Chyba serveru (\(outcome.status)).")
        }
    }
}
