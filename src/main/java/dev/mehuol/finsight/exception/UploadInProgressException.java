package dev.mehuol.finsight.exception;

/** The same file is already being uploaded in this chat (HTTP 409). */
public class UploadInProgressException extends RuntimeException {

    public UploadInProgressException(String fileName) {
        super(fileName + " is already being uploaded in this chat");
    }
}
