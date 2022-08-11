package net.pantasystem.milktea.data.infrastructure.messaging


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import net.pantasystem.milktea.model.AddResult
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.messaging.Message
import net.pantasystem.milktea.model.messaging.MessagingId
import net.pantasystem.milktea.model.messaging.UnReadMessages

interface MessageDataSource {
    suspend fun add(message: Message): AddResult

    suspend fun addAll(messages: List<Message>): List<AddResult>

    suspend fun delete(messageId: Message.Id): Boolean

    suspend fun find(messageId: Message.Id): Message?

    suspend fun readAllMessages(accountId: Long)
}

class InMemoryMessageDataSource(
    private val accountRepository: AccountRepository
) : MessageDataSource, UnReadMessages {

    private val messageIdAndMessage = mutableMapOf<Message.Id, Message>()
    private val messagesState = MutableStateFlow(
        synchronized(messageIdAndMessage){
            messageIdAndMessage.values.toList()
        }
    )

    override suspend fun add(message: Message): AddResult {
        return createOrUpdate(message).also {
            updateState()
        }
    }

    override suspend fun addAll(messages: List<Message>): List<AddResult> {
        return messages.map {
            add(it)
        }
    }

    override suspend fun delete(messageId: Message.Id): Boolean {
        return remove(messageId).also {
            updateState()
        }
    }

    override suspend fun find(messageId: Message.Id): Message? {
        return get(messageId)
    }

    override fun findByMessagingId(messagingId: MessagingId): Flow<List<Message>> {
        return messagesState.map {
            val ac = accountRepository.get(messagingId.accountId).getOrThrow()
            it.filterNot { msg ->
                msg.isRead || msg.userId.id == ac.remoteId
            }.filter { msg ->
                messagingId == msg.messagingId(ac)
            }
        }
    }

    override fun findByAccountId(accountId: Long): Flow<List<Message>> {
        return messagesState.map {
            val ac = accountRepository.get(accountId).getOrThrow()
            it.filterNot { msg ->
                msg.isRead || msg.userId.id == ac.remoteId
            }.filter { msg ->
                msg.id.accountId == accountId
            }
        }
    }

    override fun findAll(): Flow<List<Message>> {
        return messagesState.map {
            it.filterNot { msg ->
                val ac = accountRepository.get(msg.id.accountId).getOrThrow()
                msg.isRead || msg.userId.id == ac.remoteId
            }
        }
    }

    override suspend fun readAllMessages(accountId: Long) {
        readAllMessagesByAccountId(accountId)
        updateState()
    }

    private fun readAllMessagesByAccountId(accountId: Long) {
        synchronized(messageIdAndMessage) {
            messageIdAndMessage.keys.filter {
                it.accountId == accountId
            }.mapNotNull {
                messageIdAndMessage[it]
            }.filterNot {
                it.isRead
            }.map {
                it.read()
            }.forEach {
                messageIdAndMessage[it.id] = it
            }
        }
    }

    private fun createOrUpdate(message: Message): AddResult {
        synchronized(messageIdAndMessage) {
            messageIdAndMessage[message.id]?.let{
                messageIdAndMessage[message.id] = message
                return AddResult.Updated
            }
            messageIdAndMessage[message.id] = message
            return AddResult.Created
        }
    }

    private fun get(messageId: Message.Id): Message? {
        synchronized(messageIdAndMessage) {
            return messageIdAndMessage[messageId]
        }
    }

    private fun remove(messageId: Message.Id): Boolean {
        synchronized(messageIdAndMessage) {
            return messageIdAndMessage.remove(messageId) != null
        }
    }

    private fun updateState() {
        synchronized(messageIdAndMessage) {
            this.messagesState.value = messageIdAndMessage.values.toList()
        }
    }

}