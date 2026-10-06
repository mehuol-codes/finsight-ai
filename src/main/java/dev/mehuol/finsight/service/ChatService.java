package dev.mehuol.finsight.service;

import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import dev.mehuol.finsight.dto.ChatMessageDto;
import dev.mehuol.finsight.util.ConversationIds;
import reactor.core.publisher.Flux;

/** Answers chat messages, optionally with a chart image, grounded in this chat's documents when it has any. */
@Service
public class ChatService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final int DOCUMENT_EXCERPTS = 6;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final PromptTemplate documentContextPrompt;
    private final DocumentService documents;
    private final ChatHistoryService history;

    public ChatService(@Qualifier("chatClient") ChatClient chatClient, VectorStore vectorStore,
            PromptTemplate documentContextPrompt, DocumentService documents, ChatHistoryService history) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.documentContextPrompt = documentContextPrompt;
        this.documents = documents;
        this.history = history;
    }

    /**
     * Streams the answer as NDJSON events: {"t":"token"} ... or {"error":"..."}. Validation
     * problems are also sent as an error event, because the UI reads this endpoint as a stream.
     */
    public Flux<Map<String, String>> chat(String conversationId, String message, byte[] image, String imageType,
            long userId) {
        boolean hasImage = image != null && image.length > 0;
        if (message.isBlank() && !hasImage) {
            return error("Message is empty");
        }
        if (hasImage && !IMAGE_TYPES.contains(imageType)) {
            return error("Only PNG, JPEG and WEBP images are supported");
        }
        if (!ConversationIds.isValid(conversationId)) {
            return error("Invalid conversation id");
        }

        String text = message.isBlank() ? "Analyse this chart." : message;
        history.ensureChat(conversationId, text, userId);
        history.addMessage(conversationId, ChatMessageDto.USER, null, text, hasImage);

        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                .user(u -> {
                    u.text(text);
                    if (hasImage) {
                        u.media(MimeType.valueOf(imageType), new ByteArrayResource(image));
                    }
                })
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
        // Document search only runs when this chat has uploads, and only over those uploads.
        if (documents.hasDocuments(conversationId)) {
            request = request.advisors(documentSearch(conversationId));
        }
        return history.streamAndSave(conversationId, null, request.stream().content());
    }

    private QuestionAnswerAdvisor documentSearch(String conversationId) {
        return QuestionAnswerAdvisor.builder(vectorStore)
                .promptTemplate(documentContextPrompt)
                .searchRequest(SearchRequest.builder()
                        .topK(DOCUMENT_EXCERPTS)
                        .filterExpression(DocumentService.conversationFilter(conversationId))
                        .build())
                .build();
    }

    private static Flux<Map<String, String>> error(String message) {
        return Flux.just(Map.of("error", message));
    }
}
