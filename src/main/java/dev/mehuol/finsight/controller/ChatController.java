package dev.mehuol.finsight.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.mehuol.finsight.service.ChatService;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** Streams the answer as newline-delimited JSON. Accepts an optional chart screenshot. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Map<String, String>> chat(@RequestParam(defaultValue = "") String message,
            @RequestParam String conversationId,
            @RequestParam(required = false) MultipartFile image) throws IOException {
        boolean hasImage = image != null && !image.isEmpty();
        return chatService.chat(conversationId, message,
                hasImage ? image.getBytes() : null, hasImage ? image.getContentType() : null);
    }
}
