package dev.mehuol.finsight.dto;

/**
 * A message shown in a chat. {@code role} is user, assistant or event (e.g. "document ready");
 * for upload events {@code label} holds the file name.
 */
public record ChatMessageDto(String role, String label, String content, boolean hasImage) {

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";
    public static final String EVENT = "event";
}
