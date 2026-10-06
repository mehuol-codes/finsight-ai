package dev.mehuol.finsight.service;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import dev.mehuol.finsight.dto.ChatMessageDto;
import dev.mehuol.finsight.dto.PortfolioAnalysis;
import dev.mehuol.finsight.dto.PortfolioExtraction;
import dev.mehuol.finsight.util.AiErrors;
import dev.mehuol.finsight.util.PortfolioCalculator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

/**
 * Analyses a portfolio from an uploaded file in three steps: the model extracts the holdings
 * as structured data, Java calculates the metrics, and the model comments on those metrics.
 */
@Service
public class PortfolioService {

    // Holdings exports are small; this only guards against sending a huge file to the model.
    private static final int MAX_CHARS = 150_000;

    private final ChatClient analysisClient;
    private final DocumentService documents;
    private final ChatHistoryService history;
    private final JsonMapper json;
    private final Resource extractionPrompt;
    private final Resource insightsPrompt;

    public PortfolioService(@Qualifier("analysisClient") ChatClient analysisClient, DocumentService documents,
            ChatHistoryService history, JsonMapper json,
            @Value("classpath:prompts/portfolio-extraction.st") Resource extractionPrompt,
            @Value("classpath:prompts/portfolio-insights.st") Resource insightsPrompt) {
        this.analysisClient = analysisClient;
        this.documents = documents;
        this.history = history;
        this.json = json;
        this.extractionPrompt = extractionPrompt;
        this.insightsPrompt = insightsPrompt;
    }

    /**
     * Streams NDJSON events: first {"portfolio": "<analysis JSON>"} for the charts, then the
     * commentary as {"t": token} events, or {"error": ...} if the file holds no usable holdings.
     */
    public Flux<Map<String, String>> analyse(String conversationId, String name, long userId) {
        history.requireOwnChat(conversationId, userId); // the chat exists: it holds the uploaded file

        Mono<String> analysisJson = Mono.fromCallable(() -> {
                    PortfolioAnalysis analysis = PortfolioCalculator.analyse(name, extract(conversationId, name));
                    String body = json.writeValueAsString(analysis);
                    history.addMessage(conversationId, ChatMessageDto.PORTFOLIO, "📈 Portfolio · " + name, body, false);
                    return body;
                })
                .subscribeOn(Schedulers.boundedElastic()); // blocking model call and JDBC

        return analysisJson
                .flatMapMany(body -> Flux.just(Map.of("portfolio", body))
                        .concatWith(history.streamAndSave(conversationId, "💡 Portfolio insights · " + name,
                                insights(body))))
                .onErrorResume(e -> Flux.just(Map.of("error", AiErrors.userMessage(e))));
    }

    private PortfolioExtraction extract(String conversationId, String name) {
        String text = documents.fullText(conversationId, name);
        if (text.isBlank()) {
            throw new IllegalArgumentException("Document not found: " + name);
        }
        String truncated = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        PortfolioExtraction extraction = analysisClient.prompt()
                .system(extractionPrompt)
                .user("Document \"" + name + "\":\n\n" + truncated)
                .call()
                .entity(PortfolioExtraction.class);
        if (extraction == null || extraction.holdings() == null || extraction.holdings().isEmpty()) {
            throw new IllegalArgumentException("No investment holdings were found in " + name
                    + ". Upload a holdings export or portfolio sheet.");
        }
        return extraction;
    }

    private Flux<String> insights(String analysisJson) {
        return analysisClient.prompt()
                .system(insightsPrompt)
                .user(analysisJson)
                .stream()
                .content();
    }
}
