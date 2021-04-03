package jp.panta.misskeyandroidclient.view.notes.reaction.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.panta.misskeyandroidclient.R
import jp.panta.misskeyandroidclient.databinding.DialogReactionHistoryPagerBinding
import jp.panta.misskeyandroidclient.model.notes.Note
import jp.panta.misskeyandroidclient.viewmodel.MiCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReactionHistoryPagerDialog : BottomSheetDialogFragment(){

    companion object {
        private const val EXTRA_NOTE_ID = "EXTRA_NOTE_ID"
        private const val EXTRA_ACCOUNT_ID = "EXTRA_ACCOUNT_ID"

        fun newInstance(noteId: Note.Id): ReactionHistoryPagerDialog {
            return ReactionHistoryPagerDialog().also {
                it.arguments = Bundle().also { bundle ->
                    bundle.putString(EXTRA_NOTE_ID, noteId.noteId)
                    bundle.putLong(EXTRA_ACCOUNT_ID, noteId.accountId)
                }
            }
        }
    }

    lateinit var binding: DialogReactionHistoryPagerBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.dialog_reaction_history_pager, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val aId = requireArguments().getLong(EXTRA_ACCOUNT_ID, - 1)
        val nId = requireArguments().getString(EXTRA_NOTE_ID)
        require(aId != -1L)
        requireNotNull(nId)

        val noteId = Note.Id(aId, nId)

        val miCore = requireContext().applicationContext as MiCore
        val noteRepository = miCore.getNoteRepository()
        binding.reactionHistoryTab.setupWithViewPager(binding.reactionHistoryPager)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val types: MutableList<ViewType> = noteRepository.find(noteId).reactionCounts.map {
                    it.reaction.replace(":", "")
                }.map {
                    ViewType.Type(it)
                }.toMutableList()
                types.add(0, ViewType.All)
                types
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    showPager(noteId, it)
                }
            }
        }

    }

    private fun showPager(noteId: Note.Id, types: List<ViewType>) {
        val adapter = ReactionHistoryPagerAdapter(childFragmentManager, types, noteId)
        binding.reactionHistoryPager.adapter = adapter
    }
}