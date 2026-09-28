package dev.mehuol.finsight.event;

/** A document is fully embedded and searchable in its chat. */
public record DocumentReadyEvent(String conversationId, String fileName, int chunks) {
}
