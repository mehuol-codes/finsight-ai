package dev.mehuol.finsight.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.event.EventListener;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mehuol.finsight.dto.ChatMessageDto;
import dev.mehuol.finsight.dto.ChatSummary;
import dev.mehuol.finsight.event.DocumentReadyEvent;
import dev.mehuol.finsight.event.DocumentUploadStartedEvent;
import dev.mehuol.finsight.repository.ChatRepository;
import dev.mehuol.finsight.util.ChatTitles;
import dev.mehuol.finsight.util.ConversationIds;
import reactor.core.publisher.Flux;

/** The chat list and the messages shown in the UI. A chat is created lazily on its first message or upload. */
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

    /** Creates the chat if it doesn't exist yet; the first title wins. */
    public void ensureChat(String chatId, String titleCandidate) {
        ConversationIds.requireValid(chatId);
        chats.insertIfAbsent(chatId, ChatTitles.from(titleCandidate));
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
                .onErrorResume(e -> Flux.just(Map.of("error",
                        String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage()))))
                .doFinally(signal -> {
                    if (!answer.isEmpty()) {
                        addMessage(chatId, ChatMessageDto.ASSISTANT, label, answer.toString(), false);
                    }
                });
    }

    public List<ChatSummary> list() {
        return chats.findRecent();
    }

    public List<ChatMessageDto> messages(String chatId) {
        ConversationIds.requireValid(chatId);
        return chats.findMessages(chatId);
    }

    public boolean rename(String chatId, String title) {
        ConversationIds.requireValid(chatId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is empty");
        }
        return chats.updateTitle(chatId, ChatTitles.from(title));
    }

    /** Deletes the chat, its UI messages, its documents and the LLM's memory of it. */
    public void delete(String chatId) {
        ConversationIds.requireValid(chatId);
        documents.deleteAll(chatId);
        chatMemory.clear(chatId);
        chats.delete(chatId);
    }

    /** A chat that starts with an upload gets the file name as its title. */
    @EventListener
    public void onUploadStarted(DocumentUploadStartedEvent event) {
        ensureChat(event.conversationId(), "📄 " + event.fileName());
    }

    /** The label carries the file name so the UI can show that document's Summarize chip. */
    @EventListener
    public void onDocumentReady(DocumentReadyEvent event) {
        addMessage(event.conversationId(), ChatMessageDto.EVENT, event.fileName(),
                "📄 " + event.fileName() + " is ready (" + event.chunks() + " chunks)", false);
    }
}
