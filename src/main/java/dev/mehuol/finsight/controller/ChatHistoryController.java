package dev.mehuol.finsight.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mehuol.finsight.dto.ChatMessageDto;
import dev.mehuol.finsight.dto.ChatSummary;
import dev.mehuol.finsight.dto.RenameChatRequest;
import dev.mehuol.finsight.service.ChatHistoryService;

@RestController
@RequestMapping("/api/chats")
public class ChatHistoryController {

    private final ChatHistoryService history;

    public ChatHistoryController(ChatHistoryService history) {
        this.history = history;
    }

    @GetMapping
    public List<ChatSummary> list() {
        return history.list();
    }

    @GetMapping("/{id}/messages")
    public List<ChatMessageDto> messages(@PathVariable String id) {
        return history.messages(id);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> rename(@PathVariable String id, @RequestBody RenameChatRequest request) {
        return history.rename(id, request.title()) ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        history.delete(id);
        return ResponseEntity.noContent().build();
    }
}
