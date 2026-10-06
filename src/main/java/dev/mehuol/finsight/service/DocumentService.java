package dev.mehuol.finsight.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import dev.mehuol.finsight.dto.DocumentInfo;
import dev.mehuol.finsight.event.DocumentReadyEvent;
import dev.mehuol.finsight.event.DocumentUploadStartedEvent;
import dev.mehuol.finsight.exception.UploadInProgressException;
import dev.mehuol.finsight.repository.DocumentChunkRepository;
import dev.mehuol.finsight.util.AiErrors;
import dev.mehuol.finsight.util.ConversationIds;
import jakarta.annotation.PreDestroy;

/**
 * Reads uploaded documents, splits them into chunks and stores them in pgvector.
 * Documents belong to one chat: every chunk carries {@code conversation_id} and
 * {@code source} (the file name) metadata, so a chat only ever sees its own uploads.
 *
 * <p>Embedding runs in the background because the Gemini free tier rate-limits it and a
 * large PDF can take minutes; progress is exposed through {@link #list(String)}.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final List<String> TEXT_TYPES = List.of(".txt", ".md", ".csv");
    private static final List<String> OFFICE_TYPES = List.of(".docx", ".doc", ".xlsx", ".xls");
    private static final List<String> SUPPORTED = List.of(".pdf", ".txt", ".md", ".csv", ".docx", ".doc", ".xlsx",
            ".xls");
    private static final int BATCH_SIZE = 10;
    private static final int MAX_RATE_LIMIT_RETRIES = 8;
    public static final int MAX_DOCUMENTS_PER_CHAT = 5;

    private final VectorStore vectorStore;
    private final DocumentChunkRepository chunks;
    private final ApplicationEventPublisher events;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().build();
    /** Uploads that are embedding or have failed, keyed by chat + file name. */
    private final Map<String, IngestJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService ingestQueue =
            Executors.newSingleThreadExecutor(Thread.ofVirtual().name("ingest").factory());

    public DocumentService(VectorStore vectorStore, DocumentChunkRepository chunks, ApplicationEventPublisher events) {
        this.vectorStore = vectorStore;
        this.chunks = chunks;
        this.events = events;
    }

    private static final class IngestJob {

        final String conversationId;
        final String name;
        final int total;
        volatile int embedded;
        volatile String status = DocumentInfo.PROCESSING;
        volatile String message = "Waiting in queue…";
        volatile boolean cancelled;

        IngestJob(String conversationId, String name, int total) {
            this.conversationId = conversationId;
            this.name = name;
            this.total = total;
        }

        boolean processing() {
            return DocumentInfo.PROCESSING.equals(status);
        }

        DocumentInfo info() {
            return new DocumentInfo(name, total, status, embedded, message);
        }
    }

    /**
     * Parses and splits the file right away (so bad files fail fast), then embeds it in the
     * background. A {@link DocumentReadyEvent} is published once the document is searchable.
     */
    public DocumentInfo upload(String conversationId, long userId, String fileName, byte[] content) {
        ConversationIds.requireValid(conversationId);
        String name = fileName == null ? "" : fileName.strip();
        String lower = name.toLowerCase(Locale.ROOT);
        if (SUPPORTED.stream().noneMatch(lower::endsWith)) {
            throw new IllegalArgumentException("Supported files: PDF, Word (DOCX/DOC), Excel (XLSX/XLS), CSV, TXT, MD");
        }

        List<Document> documentChunks = split(conversationId, name, content);
        IngestJob job = new IngestJob(conversationId, name, documentChunks.size());
        synchronized (jobs) { // check-then-register atomically, so parallel uploads can't exceed the limit
            IngestJob existing = jobs.get(key(conversationId, name));
            if (existing != null && existing.processing()) {
                throw new UploadInProgressException(name);
            }
            Set<String> names = documentNames(conversationId);
            names.remove(name); // re-uploading a file replaces it, so it doesn't count twice
            if (names.size() >= MAX_DOCUMENTS_PER_CHAT) {
                throw new IllegalArgumentException("A chat can have at most " + MAX_DOCUMENTS_PER_CHAT
                        + " documents. Delete one or start a new chat.");
            }
            jobs.put(key(conversationId, name), job); // also replaces an old failed attempt
        }

        events.publishEvent(new DocumentUploadStartedEvent(conversationId, name, userId));
        // One file at a time: parallel embedding would only hit the free-tier rate limit sooner.
        ingestQueue.submit(() -> runIngest(job, documentChunks));
        return job.info();
    }

    @PreDestroy
    void stopIngestQueue() {
        ingestQueue.shutdownNow(); // interrupts a rate-limit wait so shutdown isn't blocked
    }

    /** Ready documents from the database plus uploads still embedding (or failed). */
    public List<DocumentInfo> list(String conversationId) {
        ConversationIds.requireValid(conversationId);
        Map<String, DocumentInfo> byName = new TreeMap<>();
        chunks.countChunksBySource(conversationId)
                .forEach((name, count) -> byName.put(name, DocumentInfo.ready(name, count)));
        // An in-flight or failed upload replaces the row (its chunks may be partially stored).
        jobs.values().stream()
                .filter(job -> job.conversationId.equals(conversationId))
                .forEach(job -> byName.put(job.name, job.info()));
        return new ArrayList<>(byName.values());
    }

    public boolean hasDocuments(String conversationId) {
        return ConversationIds.isValid(conversationId) && chunks.existsForConversation(conversationId);
    }

    /** The whole document's text in reading order, used for summaries. */
    public String fullText(String conversationId, String name) {
        ConversationIds.requireValid(conversationId);
        return String.join("\n\n", chunks.findContentInOrder(conversationId, name));
    }

    /** Deletes a document; an upload still in progress is cancelled. Returns false if nothing matched. */
    public boolean delete(String conversationId, String name) {
        ConversationIds.requireValid(conversationId);
        boolean found = false;
        IngestJob job = jobs.get(key(conversationId, name));
        if (job != null) {
            found = true;
            if (job.processing()) {
                job.cancelled = true; // the worker removes its own chunks and the job
            }
            else {
                jobs.remove(key(conversationId, name), job);
            }
        }
        List<String> ids = chunks.findIds(conversationId, name);
        deleteQuietly(ids);
        return found || !ids.isEmpty();
    }

    /** Removes every document of a chat (used when the chat itself is deleted). */
    public void deleteAll(String conversationId) {
        ConversationIds.requireValid(conversationId);
        jobs.values().stream()
                .filter(job -> job.conversationId.equals(conversationId))
                .forEach(job -> delete(conversationId, job.name));
        deleteQuietly(chunks.findIds(conversationId));
    }

    /** Vector-search filter that restricts retrieval to one chat's documents. */
    public static Filter.Expression conversationFilter(String conversationId) {
        return new FilterExpressionBuilder().eq("conversation_id", conversationId).build();
    }

    /** Names of this chat's documents, ready or still uploading (failed uploads don't count). */
    private Set<String> documentNames(String conversationId) {
        Set<String> names = chunks.findSourceNames(conversationId);
        jobs.values().stream()
                .filter(job -> job.conversationId.equals(conversationId) && job.processing())
                .forEach(job -> names.add(job.name));
        return names;
    }

    private List<Document> split(String conversationId, String name, byte[] content) {
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        };
        List<Document> pages;
        try {
            pages = read(name.toLowerCase(Locale.ROOT), resource);
        }
        catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not read " + name + ": " + e.getMessage(), e);
        }
        List<Document> split = splitter.apply(pages).stream()
                .filter(doc -> doc.getText() != null && !doc.getText().isBlank())
                .toList();
        if (split.isEmpty()) {
            throw new IllegalArgumentException("No readable text found in " + name
                    + " (scanned PDFs without a text layer are not supported)");
        }
        return IntStream.range(0, split.size())
                .mapToObj(i -> withSource(split.get(i), conversationId, name, i))
                .toList();
    }

    /** PDFs are read page by page (for page citations); Word and Excel files go through Apache Tika. */
    static List<Document> read(String lowerName, Resource resource) {
        if (lowerName.endsWith(".pdf")) {
            return new PagePdfDocumentReader(resource).get();
        }
        if (OFFICE_TYPES.stream().anyMatch(lowerName::endsWith)) {
            return new TikaDocumentReader(resource).get();
        }
        if (TEXT_TYPES.stream().anyMatch(lowerName::endsWith)) {
            return new TextReader(resource).get();
        }
        throw new IllegalArgumentException("Unsupported file type");
    }

    /** Whether a file looks like tabular or office data that may hold a portfolio. */
    public static boolean isSpreadsheetOrWord(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".csv") || OFFICE_TYPES.stream().anyMatch(lower::endsWith);
    }

    /**
     * Adds the new chunks first and only then removes the previous version of the file, so a
     * failed re-upload never destroys the copy that was already there.
     */
    private void runIngest(IngestJob job, List<Document> documentChunks) {
        job.message = "Embedding…";
        List<String> previousIds = chunks.findIds(job.conversationId, job.name);
        try {
            addInBatches(job, documentChunks);
            if (!previousIds.isEmpty()) {
                vectorStore.delete(previousIds);
            }
            jobs.remove(key(job.conversationId, job.name), job);
            log.info("Ingested {} ({} chunks) into chat {}", job.name, documentChunks.size(), job.conversationId);
            events.publishEvent(new DocumentReadyEvent(job.conversationId, job.name, documentChunks.size()));
        }
        catch (RuntimeException e) {
            deleteQuietly(documentChunks.stream().map(Document::getId).toList()); // only this attempt's chunks
            if (job.cancelled) {
                jobs.remove(key(job.conversationId, job.name), job);
                log.info("Upload of {} cancelled", job.name);
                return;
            }
            job.status = DocumentInfo.FAILED;
            job.message = AiErrors.userMessage(e);
            log.warn("Ingesting {} failed: {}", job.name, job.message);
        }
    }

    /**
     * The Gemini free tier limits embedding tokens per minute, so large PDFs hit 429. Send small
     * batches and, when limited, wait for the per-minute window to reset instead of failing.
     */
    private void addInBatches(IngestJob job, List<Document> documentChunks) {
        for (int start = 0; start < documentChunks.size(); start += BATCH_SIZE) {
            List<Document> batch = documentChunks.subList(start, Math.min(start + BATCH_SIZE, documentChunks.size()));
            for (int attempt = 1; ; attempt++) {
                checkCancelled(job);
                try {
                    vectorStore.add(batch);
                    break;
                }
                catch (RuntimeException e) {
                    if (!AiErrors.isRateLimited(e) || attempt == MAX_RATE_LIMIT_RETRIES) {
                        throw e;
                    }
                    int waitSeconds = attempt == 1 ? 30 : 60;
                    job.message = "Gemini free-tier limit reached, waiting " + waitSeconds + "s (retry " + attempt
                            + "/" + (MAX_RATE_LIMIT_RETRIES - 1) + ")";
                    log.warn("Rate limited while embedding {} (chunks {}-{}), retrying in {}s", job.name, start,
                            start + batch.size(), waitSeconds);
                    sleepUnlessCancelled(job, waitSeconds);
                }
            }
            job.embedded = start + batch.size();
            job.message = "Embedding…";
            log.info("Embedded {} / {} chunks of {}", job.embedded, documentChunks.size(), job.name);
        }
    }

    private static void checkCancelled(IngestJob job) {
        if (job.cancelled) {
            throw new IllegalStateException("Upload cancelled");
        }
    }

    private static void sleepUnlessCancelled(IngestJob job, int seconds) {
        try {
            for (int i = 0; i < seconds && !job.cancelled; i++) {
                Thread.sleep(1000);
            }
        }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for rate limit", ie);
        }
    }

    private void deleteQuietly(List<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(ids);
        }
        catch (RuntimeException e) {
            log.warn("Could not delete {} chunks: {}", ids.size(), e.getMessage());
        }
    }

    private static String key(String conversationId, String name) {
        return conversationId + '\u0000' + name;
    }

    // Prefix the chunk with its origin so the model can cite it in answers.
    private static Document withSource(Document doc, String conversationId, String name, int index) {
        Object page = doc.getMetadata().get("page_number");
        String header = "[Source: " + name + (page != null ? ", page " + page : "") + "]\n";
        Document chunk = new Document(header + doc.getText(), doc.getMetadata());
        chunk.getMetadata().put("conversation_id", conversationId);
        chunk.getMetadata().put("source", name);
        chunk.getMetadata().put("chunk_index", index);
        return chunk;
    }
}
