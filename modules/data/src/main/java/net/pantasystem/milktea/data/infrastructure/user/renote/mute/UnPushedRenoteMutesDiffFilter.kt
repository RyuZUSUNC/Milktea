package net.pantasystem.milktea.data.infrastructure.user.renote.mute

import net.pantasystem.milktea.api.misskey.users.renote.mute.RenoteMuteDTO
import net.pantasystem.milktea.model.user.renote.mute.RenoteMute

/**
 * リモートのミュートの追加状態とローカルの未同期状態のRenoteMuteを比較して
 * ローカルでも未同期状態かつリモートにも存在しないリソースを洗い出す
 */
class UnPushedRenoteMutesDiffFilter {

    operator fun invoke(mutes: List<RenoteMuteDTO>, locals: List<RenoteMute>): List<RenoteMute> {
        return locals.filterNot { mute ->
            mutes.any { dto ->
                mute.userId.id == dto.muteeId
            }
        }
    }

}