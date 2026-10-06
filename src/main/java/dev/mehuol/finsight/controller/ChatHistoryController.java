package dev.mehuol.finsight.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import dev.mehuol.finsight.security.AppUserDetails;
import dev.mehuol.finsight.service.ChatHistoryService;

/** The signed-in user's chats. */
@RestController
@RequestMapping("/api/chats")
public class ChatHistoryController {

    private final ChatHistoryService history;

    public ChatHistoryController(ChatHistoryService history) {
        this.history = history;
    }

    @GetMapping
    public List<ChatSummary> list(@AuthenticationPrincipal AppUserDetails user) {
        return history.list(user.id());
    }

    @GetMapping("/{id}/messages")
    public List<ChatMessageDto> messages(@PathVariable String id, @AuthenticationPrincipal AppUserDetails user) {
        return history.messages(id, user.id());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> rename(@PathVariable String id, @RequestBody RenameChatRequest request,
            @AuthenticationPrincipal AppUserDetails user) {
        history.rename(id, request.title(), user.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal AppUserDetails user) {
        history.delete(id, user.id());
        return ResponseEntity.noContent().build();
    }
}
