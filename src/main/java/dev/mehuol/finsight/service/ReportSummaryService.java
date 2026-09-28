package dev.mehuol.finsight.service;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

/** Summarizes a whole uploaded document (not just the top RAG matches) and saves it to the chat. */
@Service
public class ReportSummaryService {

    // Gemini Flash has a very large context window; this cap just guards against huge files.
    private static final int MAX_CHARS = 400_000;

    private final ChatClient summaryClient;
    private final DocumentService documents;
    private final ChatHistoryService history;

    public ReportSummaryService(@Qualifier("summaryClient") ChatClient summaryClient, DocumentService documents,
            ChatHistoryService history) {
        this.summaryClient = summaryClient;
        this.documents = documents;
        this.history = history;
    }

    /** Streams the summary as NDJSON events, like the chat endpoint. */
    public Flux<Map<String, String>> summarize(String conversationId, String name) {
        history.ensureChat(conversationId, "📄 " + name);
        return history.streamAndSave(conversationId, "📊 Summary · " + name,
                Flux.defer(() -> summaryTokens(conversationId, name)));
    }

    private Flux<String> summaryTokens(String conversationId, String name) {
        String text = documents.fullText(conversationId, name);
        if (text.isBlank()) {
            return Flux.error(new IllegalArgumentException("Document not found: " + name));
        }
        String truncated = text.length() > MAX_CHARS
                ? text.substring(0, MAX_CHARS) + "\n\n[Document truncated]"
                : text;
        return summaryClient.prompt()
                .user("Document \"" + name + "\":\n\n" + truncated)
                .stream()
                .content();
    }
}
