package cz.euroklicmapa.data.model

import cz.euroklicmapa.util.t
import kotlinx.serialization.Serializable

/** The 6 reasons `POST /api_report.php` accepts — exact wire values, do not localize the enum
 *  itself; [ReportReason.label] carries the Czech text shown in the UI. */
enum class ReportReason(val wireValue: String, val label: String) {
    NOT_FOUND("not_found", t("Místo už neexistuje")),
    KEY_BROKEN("key_broken", t("Euroklíč nefunguje")),
    WRONG_LOCATION("wrong_location", t("Špatná poloha")),
    WRONG_INFO("wrong_info", t("Špatné informace")),
    BAD_PHOTO("bad_photo", t("Nevhodná fotografie")),
    OTHER("other", t("Jiné")),
}

@Serializable
data class PostReportRequest(
    val location_id: Int,
    val reason: String,
    val note: String? = null,
)
