package org.thoughtcrime.securesms.conversation.ui.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationBottomSheetCallback
import org.thoughtcrime.securesms.conversation.ConversationItemDisplayMode
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.colors.RecyclerViewColorizer
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.quotes.OriginalMessageSeparatorDecoration
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.databinding.MessageEditHistoryBottomSheetBinding
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ItemDecoration
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackController
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicy
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionPlayerHolder
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionRecycler
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.ViewModelFactory
import org.thoughtcrime.securesms.util.fragments.requireListener
import java.util.Locale

/**
 * Show history of edits for a specific message.
 */
class EditMessageHistoryDialog : FixedRoundedCornerBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.4f

  private val binding: MessageEditHistoryBottomSheetBinding by ViewBinderDelegate(MessageEditHistoryBottomSheetBinding::bind)
  private val messageId: Long by lazy { requireArguments().getLong(ARGUMENT_MESSAGE_ID) }
  private val conversationRecipient: Recipient by lazy { Recipient.resolved(requireArguments().getParcelable(ARGUMENT_CONVERSATION_RECIPIENT_ID)!!) }
  private val viewModel: EditMessageHistoryViewModel by viewModels(factoryProducer = ViewModelFactory.factoryProducer { EditMessageHistoryViewModel(messageId, conversationRecipient) })

  private val disposables: LifecycleDisposable = LifecycleDisposable()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    val view = MessageEditHistoryBottomSheetBinding.inflate(inflater, container, false).root
    view.minimumHeight = (resources.displayMetrics.heightPixels * peekHeightPercentage).toInt()
    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    disposables.bindTo(viewLifecycleOwner)

    val colorizer = Colorizer()

    val messageAdapter = ConversationAdapter(
      requireContext(),
      viewLifecycleOwner,
      GlideApp.with(this),
      Locale.getDefault(),
      ConversationAdapterListener(),
      conversationRecipient.hasWallpaper(),
      colorizer
    ).apply {
      setCondensedMode(ConversationItemDisplayMode.EXTRA_CONDENSED)
    }

    binding.editHistoryList.apply {
      layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, true)
      adapter = messageAdapter
      itemAnimator = null
      addItemDecoration(OriginalMessageSeparatorDecoration(context, R.string.EditMessageHistoryDialog_title))
    }

    val recyclerViewColorizer = RecyclerViewColorizer(binding.editHistoryList)

    disposables += viewModel
      .getEditHistory()
      .subscribeBy { messages ->
        if (messages.isEmpty()) {
          dismiss()
        }

        messageAdapter.submitList(messages)
        recyclerViewColorizer.setChatColors(conversationRecipient.chatColors)
      }

    disposables += viewModel.getNameColorsMap().subscribe { map ->
      colorizer.onNameColorsChanged(map)
      messageAdapter.notifyItemRangeChanged(0, messageAdapter.itemCount, ConversationAdapter.PAYLOAD_NAME_COLORS)
    }

    initializeGiphyMp4()
  }

  private fun initializeGiphyMp4(): GiphyMp4ProjectionRecycler {
    val maxPlayback = GiphyMp4PlaybackPolicy.maxSimultaneousPlaybackInConversation()
    val holders = GiphyMp4ProjectionPlayerHolder.injectVideoViews(
      requireContext(),
      viewLifecycleOwner.lifecycle,
      binding.videoContainer,
      maxPlayback
    )
    val callback = GiphyMp4ProjectionRecycler(holders)

    GiphyMp4PlaybackController.attach(binding.editHistoryList, callback, maxPlayback)
    binding.editHistoryList.addItemDecoration(GiphyMp4ItemDecoration(callback) {}, 0)

    return callback
  }

  private inner class ConversationAdapterListener : ConversationAdapter.ItemClickListener by requireListener<ConversationBottomSheetCallback>().getConversationAdapterListener() {
    override fun onQuoteClicked(messageRecord: MmsMessageRecord) = Unit
    override fun onScheduledIndicatorClicked(view: View, conversationMessage: ConversationMessage) = Unit
    override fun onGroupMemberClicked(recipientId: RecipientId, groupId: GroupId) = Unit
    override fun onItemClick(item: MultiselectPart) = Unit
    override fun onItemLongClick(itemView: View, item: MultiselectPart) = Unit
    override fun onQuotedIndicatorClicked(messageRecord: MessageRecord) = Unit
    override fun onReactionClicked(multiselectPart: MultiselectPart, messageId: Long, isMms: Boolean) = Unit
    override fun onMessageWithRecaptchaNeededClicked(messageRecord: MessageRecord) = Unit
    override fun onGroupMigrationLearnMoreClicked(membershipChange: GroupMigrationMembershipChange) = Unit
    override fun onChatSessionRefreshLearnMoreClicked() = Unit
    override fun onBadDecryptLearnMoreClicked(author: RecipientId) = Unit
    override fun onSafetyNumberLearnMoreClicked(recipient: Recipient) = Unit
    override fun onJoinGroupCallClicked() = Unit
    override fun onInviteFriendsToGroupClicked(groupId: GroupId.V2) = Unit
    override fun onEnableCallNotificationsClicked() = Unit
    override fun onCallToAction(action: String) = Unit
    override fun onDonateClicked() = Unit
    override fun onRecipientNameClicked(target: RecipientId) = Unit
    override fun onActivatePaymentsClicked() = Unit
    override fun onSendPaymentClicked(recipientId: RecipientId) = Unit
    override fun onEditedIndicatorClicked(messageRecord: MessageRecord) = Unit
  }

  companion object {
    private const val ARGUMENT_MESSAGE_ID = "message_id"
    private const val ARGUMENT_CONVERSATION_RECIPIENT_ID = "recipient_id"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, threadRecipient: RecipientId, messageId: Long) {
      EditMessageHistoryDialog()
        .apply {
          arguments = bundleOf(
            ARGUMENT_MESSAGE_ID to messageId,
            ARGUMENT_CONVERSATION_RECIPIENT_ID to threadRecipient
          )
        }
        .show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
