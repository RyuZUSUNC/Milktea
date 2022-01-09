package jp.panta.misskeyandroidclient.model.core

enum class ConnectionStatus(val flag: Int){
    ACCOUNT_ERROR(1),
    NETWORK_ERROR(2),
    SUCCESS(8)
}