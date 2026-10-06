package dev.mehuol.finsight.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.mehuol.finsight.dto.DocumentInfo;
import dev.mehuol.finsight.security.AppUserDetails;
import dev.mehuol.finsight.service.ChatHistoryService;
import dev.mehuol.finsight.service.DocumentService;
import dev.mehuol.finsight.service.PortfolioService;
import dev.mehuol.finsight.service.ReportSummaryService;
import reactor.core.publisher.Flux;

/** Documents of one of the user's chats; every endpoint takes the chat's conversationId. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documents;
    private final ReportSummaryService summaries;
    private final PortfolioService portfolios;
    private final ChatHistoryService history;

    public DocumentController(DocumentService documents, ReportSummaryService summaries,
            PortfolioService portfolios, ChatHistoryService history) {
        this.documents = documents;
        this.summaries = summaries;
        this.portfolios = portfolios;
        this.history = history;
    }

    @GetMapping
    public List<DocumentInfo> list(@RequestParam String conversationId, @AuthenticationPrincipal AppUserDetails user) {
        history.requireAccess(conversationId, user.id());
        return documents.list(conversationId);
    }

    /**
     * Accepts the file and returns 202 right away; embedding continues in the background and
     * its progress shows up in {@link #list}. The UI polls that list until the file is ready.
     */
    @PostMapping
    public ResponseEntity<DocumentInfo> upload(@RequestParam String conversationId,
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal AppUserDetails user)
            throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        history.requireAccess(conversationId, user.id());
        DocumentInfo info = documents.upload(conversationId, user.id(), file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(info);
    }

    /** Streams a structured summary of the whole document as NDJSON, like the chat endpoint. */
    @PostMapping(path = "/{name}/summary", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Map<String, String>> summary(@RequestParam String conversationId, @PathVariable String name,
            @AuthenticationPrincipal AppUserDetails user) {
        return summaries.summarize(conversationId, name, user.id());
    }

    /**
     * Analyses a holdings file: streams {"portfolio": "<JSON>"} for the charts, then the
     * commentary tokens, as NDJSON.
     */
    @PostMapping(path = "/{name}/portfolio", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Map<String, String>> portfolio(@RequestParam String conversationId, @PathVariable String name,
            @AuthenticationPrincipal AppUserDetails user) {
        return portfolios.analyse(conversationId, name, user.id());
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@RequestParam String conversationId, @PathVariable String name,
            @AuthenticationPrincipal AppUserDetails user) {
        history.requireAccess(conversationId, user.id());
        return documents.delete(conversationId, name) ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
