package dev.mehuol.finsight.event;

/** A file was accepted for a user's chat and is waiting to be embedded. */
public record DocumentUploadStartedEvent(String conversationId, String fileName, long userId) {
}
