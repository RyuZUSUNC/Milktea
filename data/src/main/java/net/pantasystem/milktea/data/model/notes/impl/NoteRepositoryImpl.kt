package net.pantasystem.milktea.data.model.notes.impl

import net.pantasystem.milktea.data.api.misskey.MisskeyAPIProvider
import net.pantasystem.milktea.model.AddResult
import net.pantasystem.milktea.common.Encryption
import net.pantasystem.milktea.data.model.notes.*
import net.pantasystem.milktea.data.model.notes.draft.db.DraftNoteDao
import net.pantasystem.milktea.model.notes.reaction.CreateReaction
import net.pantasystem.milktea.data.model.settings.SettingStore
import kotlinx.coroutines.*
import net.pantasystem.milktea.api.misskey.notes.DeleteNote
import net.pantasystem.milktea.api.misskey.notes.NoteRequest
import net.pantasystem.milktea.common.Logger
import net.pantasystem.milktea.api.misskey.throwIfHasError
import net.pantasystem.milktea.data.model.drive.FileUploaderProvider
import net.pantasystem.milktea.model.notes.*
import javax.inject.Inject

@Suppress("UNREACHABLE_CODE", "IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION")
class NoteRepositoryImpl @Inject constructor(
    val loggerFactory: Logger.Factory,
    val userDataSource: net.pantasystem.milktea.model.user.UserDataSource,
    val noteDataSource: NoteDataSource,
    val filePropertyDataSource: net.pantasystem.milktea.model.drive.FilePropertyDataSource,
    val encryption: Encryption,
    val uploader: FileUploaderProvider,
    val misskeyAPIProvider: MisskeyAPIProvider,
    val draftNoteDao: DraftNoteDao,
    val settingStore: SettingStore,
    val accountRepository: net.pantasystem.milktea.model.account.AccountRepository,
    val noteCaptureAPIProvider: NoteCaptureAPIWithAccountProvider
) : NoteRepository {

    private val logger = loggerFactory.create("NoteRepositoryImpl")
    private val noteDataSourceAdder: NoteDataSourceAdder by lazy {
        NoteDataSourceAdder(userDataSource, noteDataSource, filePropertyDataSource)
    }

    override suspend fun create(createNote: CreateNote): Note {
        val task = PostNoteTask(
            encryption,
            createNote,
            createNote.author,
            loggerFactory,
            filePropertyDataSource
        )
        val result = runCatching {
            task.execute(
                uploader.get(createNote.author)
            ) ?: throw IllegalStateException("ファイルのアップロードに失敗しました")
        }.runCatching {
            this.getOrThrow().let {
                misskeyAPIProvider.get(createNote.author).create(it).body()?.createdNote
            }
        }

        if (result.isFailure) {
            val exDraft = createNote.draftNoteId?.let {
                draftNoteDao.getDraftNote(
                    createNote.author.accountId,
                    createNote.draftNoteId!!
                )
            }
            draftNoteDao.fullInsert(task.toDraftNote(exDraft))
        }
        val noteDTO = result.getOrThrow()
        require(noteDTO != null)
        createNote.draftNoteId?.let {
            draftNoteDao.deleteDraftNote(createNote.author.accountId, draftNoteId = it)
        }

        if (createNote.channelId == null) {
            settingStore.setNoteVisibility(createNote)
        }

        return noteDataSourceAdder.addNoteDtoToDataSource(createNote.author, noteDTO)

    }

    override suspend fun delete(noteId: Note.Id): Boolean {
        val account = accountRepository.get(noteId.accountId)
        return misskeyAPIProvider.get(account).delete(
            DeleteNote(i = account.getI(encryption), noteId = noteId.noteId)
        ).isSuccessful
    }

    override suspend fun find(noteId: Note.Id): Note {
        val account = accountRepository.get(noteId.accountId)

        var note = runCatching {
            noteDataSource.get(noteId)
        }.getOrNull()
        if (note != null) {
            return note
        }

        logger.debug("request notes/show=$noteId")
        note = runCatching {
            misskeyAPIProvider.get(account).showNote(
                NoteRequest(
                    i = account.getI(encryption),
                    noteId = noteId.noteId
                )
            )
        }.getOrNull()?.body()?.let {
            noteDataSourceAdder.addNoteDtoToDataSource(account, it)
        }
        note ?: throw NoteNotFoundException(noteId)
        return note
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun toggleReaction(createReaction: CreateReaction): Boolean {
        val account = accountRepository.get(createReaction.noteId.accountId)
        var note = find(createReaction.noteId)
        if (note.myReaction?.isNotBlank() == true) {
            if (!unreaction(createReaction.noteId)) {
                return false
            }

            if (note.myReaction == createReaction.reaction) {
                logger.debug("同一のリアクションが選択されています。")
                return true
            }
            note = noteDataSource.get(createReaction.noteId)
        }


        return runCatching {
            if (postReaction(createReaction) && !noteCaptureAPIProvider.get(account)
                    .isCaptured(createReaction.noteId.noteId)
            ) {
                noteDataSource.add(note.onIReacted(createReaction.reaction))
            }
            true
        }.getOrThrow()


    }

    override suspend fun unreaction(noteId: Note.Id): Boolean {
        val note = find(noteId)
        val account = accountRepository.get(noteId.accountId)
        return postUnReaction(noteId)
                && (noteCaptureAPIProvider.get(account).isCaptured(noteId.noteId)
                || (note.myReaction != null
                && noteDataSource.add(note.onIUnReacted()) != AddResult.CANCEL))
    }

    private suspend fun postReaction(createReaction: CreateReaction): Boolean {
        val account = accountRepository.get(createReaction.noteId.accountId)
        val res = misskeyAPIProvider.get(account).createReaction(
            net.pantasystem.milktea.api.misskey.notes.CreateReaction(
                i = account.getI(encryption),
                noteId = createReaction.noteId.noteId,
                reaction = createReaction.reaction
            )
        )
        res.throwIfHasError()
        return res.isSuccessful
    }

    private suspend fun postUnReaction(noteId: Note.Id): Boolean {
        val note = find(noteId)
        val account = accountRepository.get(noteId.accountId)
        val res = misskeyAPIProvider.get(account).deleteReaction(
            DeleteNote(
                noteId = note.id.noteId,
                i = account.getI(encryption)
            )
        )
        res.throwIfHasError()
        return res.isSuccessful

    }
}