package jp.panta.misskeyandroidclient.api.reaction

data class RequestReactionHistoryDTO (
    val i: String,
    val noteId: String,
    val type: String?,
    val limit: Int = 20,
    val offset: Int? = null,
)