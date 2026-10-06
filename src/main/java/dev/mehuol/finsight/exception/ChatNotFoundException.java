package dev.mehuol.finsight.exception;

/**
 * The chat doesn't exist for this user (HTTP 404). Also used for another user's chat, so the
 * response doesn't reveal that it exists.
 */
public class ChatNotFoundException extends RuntimeException {

    public ChatNotFoundException() {
        super("Chat not found");
    }
}
