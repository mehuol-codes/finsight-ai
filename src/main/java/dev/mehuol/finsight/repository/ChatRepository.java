package dev.mehuol.finsight.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.mehuol.finsight.dto.ChatMessageDto;
import dev.mehuol.finsight.dto.ChatSummary;

/** The chat list and the messages shown in the UI (tables in schema.sql). */
@Repository
public class ChatRepository {

    private static final int LIST_LIMIT = 200;

    private final JdbcTemplate jdbc;

    public ChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates the chat if it doesn't exist yet; an existing chat keeps its title. */
    public void insertIfAbsent(String chatId, String title) {
        jdbc.update("INSERT INTO chat (id, title) VALUES (?, ?) ON CONFLICT (id) DO NOTHING", chatId, title);
    }

    public void insertMessage(String chatId, String role, String label, String content, boolean hasImage) {
        jdbc.update("INSERT INTO chat_message (chat_id, role, label, content, has_image) VALUES (?, ?, ?, ?, ?)",
                chatId, role, label, content, hasImage);
    }

    public void touch(String chatId) {
        jdbc.update("UPDATE chat SET updated_at = now() WHERE id = ?", chatId);
    }

    public List<ChatSummary> findRecent() {
        return jdbc.query("SELECT id, title, updated_at FROM chat ORDER BY updated_at DESC LIMIT ?",
                (rs, i) -> new ChatSummary(rs.getString("id"), rs.getString("title"),
                        rs.getObject("updated_at", OffsetDateTime.class)),
                LIST_LIMIT);
    }

    public List<ChatMessageDto> findMessages(String chatId) {
        return jdbc.query("SELECT role, label, content, has_image FROM chat_message WHERE chat_id = ? ORDER BY id",
                (rs, i) -> new ChatMessageDto(rs.getString("role"), rs.getString("label"), rs.getString("content"),
                        rs.getBoolean("has_image")),
                chatId);
    }

    /** Returns false if the chat doesn't exist. */
    public boolean updateTitle(String chatId, String title) {
        return jdbc.update("UPDATE chat SET title = ? WHERE id = ?", title, chatId) > 0;
    }

    /** Messages are removed by the ON DELETE CASCADE foreign key. */
    public void delete(String chatId) {
        jdbc.update("DELETE FROM chat WHERE id = ?", chatId);
    }
}
