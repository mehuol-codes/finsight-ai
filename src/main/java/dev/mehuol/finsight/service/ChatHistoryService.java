package dev.mehuol.finsight.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mehuol.finsight.dto.ChatMessageDto;
import dev.mehuol.finsight.dto.ChatSummary;
import dev.mehuol.finsight.event.DocumentReadyEvent;
import dev.mehuol.finsight.event.DocumentUploadStartedEvent;
import dev.mehuol.finsight.exception.ChatNotFoundException;
import dev.mehuol.finsight.repository.ChatRepository;
import dev.mehuol.finsight.repository.ChatRepository.Ownership;
import dev.mehuol.finsight.util.AiErrors;
import dev.mehuol.finsight.util.ChatTitles;
import dev.mehuol.finsight.util.ConversationIds;
import reactor.core.publisher.Flux;

/**
 * The chat list and the messages shown in the UI. A chat is created lazily on its first message
 * or upload and belongs to the user who created it; every method that takes a chat id checks that
 * the chat is the caller's. Other users' chats are reported as not found.
 */
@Service
public class ChatHistoryService {

    private final ChatRepository chats;
    private final DocumentService documents;
    private final ChatMemory chatMemory;

    public ChatHistoryService(ChatRepository chats, DocumentService documents, ChatMemory chatMemory) {
        this.chats = chats;
        this.documents = documents;
        this.chatMemory = chatMemory;
    }

    /** Allows the user's own chats and ids not used yet (a new chat); anything else is "not found". */
    public void requireAccess(String chatId, long userId) {
        ConversationIds.requireValid(chatId);
        if (chats.ownership(chatId, userId) == Ownership.NOT_MINE) {
            throw new ChatNotFoundException();
        }
    }

    /** Like {@link #requireAccess} but the chat must already exist. */
    public void requireOwnChat(String chatId, long userId) {
        ConversationIds.requireValid(chatId);
        if (chats.ownership(chatId, userId) != Ownership.MINE) {
            throw new ChatNotFoundException();
        }
    }

    /** Creates the chat for this user if it doesn't exist yet; the first title wins. */
    public void ensureChat(String chatId, String titleCandidate, long userId) {
        requireAccess(chatId, userId);
        chats.insertIfAbsent(chatId, ChatTitles.from(titleCandidate), userId);
    }

    @Transactional
    public void addMessage(String chatId, String role, String label, String content, boolean hasImage) {
        chats.insertMessage(chatId, role, label, content, hasImage);
        chats.touch(chatId);
    }

    /**
     * Turns a token stream into the NDJSON events the UI reads ({"t":..} / {"error":..}) and
     * saves the assistant's text once the stream ends, even if the user stopped it midway.
     */
    public Flux<Map<String, String>> streamAndSave(String chatId, String label, Flux<String> tokens) {
        StringBuilder answer = new StringBuilder();
        return tokens
                .doOnNext(answer::append)
                .map(token -> Map.of("t", token))
                .onErrorResume(e -> Flux.just(Map.of("error", AiErrors.userMessage(e))))
                .doFinally(signal -> {
                    if (!answer.isEmpty()) {
                        addMessage(chatId, ChatMessageDto.ASSISTANT, label, answer.toString(), false);
                    }
                });
    }

    public List<ChatSummary> list(long userId) {
        return chats.findRecent(userId);
    }

    public List<ChatMessageDto> messages(String chatId, long userId) {
        requireOwnChat(chatId, userId);
        return chats.findMessages(chatId);
    }

    public void rename(String chatId, String title, long userId) {
        requireOwnChat(chatId, userId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is empty");
        }
        chats.updateTitle(chatId, ChatTitles.from(title));
    }

    /** Deletes the chat, its UI messages, its documents and the LLM's memory of it. */
    public void delete(String chatId, long userId) {
        requireOwnChat(chatId, userId);
        documents.deleteAll(chatId);
        chatMemory.clear(chatId);
        chats.delete(chatId);
    }

    /** A chat that starts with an upload gets the file name as its title. */
    @EventListener
    public void onUploadStarted(DocumentUploadStartedEvent event) {
        ensureChat(event.conversationId(), "📄 " + event.fileName(), event.userId());
    }

    /** The label carries the file name so the UI can show that document's Summarize chip. */
    @EventListener
    public void onDocumentReady(DocumentReadyEvent event) {
        addMessage(event.conversationId(), ChatMessageDto.EVENT, event.fileName(),
                "📄 " + event.fileName() + " is ready (" + event.chunks() + " chunks)", false);
    }
}
