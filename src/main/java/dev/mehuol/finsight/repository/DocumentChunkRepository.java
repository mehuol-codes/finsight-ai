package dev.mehuol.finsight.repository;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read queries over the pgvector {@code vector_store} table, grouped by the chunk metadata
 * ({@code conversation_id}, {@code source}). Chunks are written and deleted through Spring AI's
 * {@code VectorStore}, which also creates the embeddings.
 */
@Repository
public class DocumentChunkRepository {

    private final JdbcTemplate jdbc;

    public DocumentChunkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** File name → number of stored chunks, for one chat. */
    public Map<String, Integer> countChunksBySource(String conversationId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbc.query("""
                SELECT metadata->>'source' AS name, COUNT(*) AS chunks
                FROM vector_store
                WHERE metadata->>'conversation_id' = ?
                GROUP BY 1 ORDER BY 1
                """, rs -> {
            counts.put(rs.getString("name"), rs.getInt("chunks"));
        }, conversationId);
        return counts;
    }

    public Set<String> findSourceNames(String conversationId) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT metadata->>'source' FROM vector_store WHERE metadata->>'conversation_id' = ?",
                String.class, conversationId));
    }

    public boolean existsForConversation(String conversationId) {
        Boolean any = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM vector_store WHERE metadata->>'conversation_id' = ?)",
                Boolean.class, conversationId);
        return Boolean.TRUE.equals(any);
    }

    public List<String> findIds(String conversationId, String source) {
        return jdbc.queryForList("""
                SELECT id::text FROM vector_store
                WHERE metadata->>'conversation_id' = ? AND metadata->>'source' = ?
                """, String.class, conversationId, source);
    }

    public List<String> findIds(String conversationId) {
        return jdbc.queryForList(
                "SELECT id::text FROM vector_store WHERE metadata->>'conversation_id' = ?", String.class,
                conversationId);
    }

    /** Chunk texts of one document in reading order. */
    public List<String> findContentInOrder(String conversationId, String source) {
        return jdbc.queryForList("""
                SELECT content FROM vector_store
                WHERE metadata->>'conversation_id' = ? AND metadata->>'source' = ?
                ORDER BY (metadata->>'chunk_index')::int NULLS LAST,
                         (metadata->>'page_number')::int NULLS LAST
                """, String.class, conversationId, source);
    }
}
