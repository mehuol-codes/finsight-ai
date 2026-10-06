package dev.mehuol.finsight.dto;

/**
 * A message shown in a chat. {@code role} is user, assistant, event (e.g. "document ready";
 * {@code label} holds the file name) or portfolio ({@code content} is the analysis as JSON,
 * drawn as charts by the UI).
 */
public record ChatMessageDto(String role, String label, String content, boolean hasImage) {

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";
    public static final String EVENT = "event";
    public static final String PORTFOLIO = "portfolio";
}
