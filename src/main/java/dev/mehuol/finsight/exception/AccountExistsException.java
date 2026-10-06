package dev.mehuol.finsight.exception;

/** Signing up with an email that already has an account (HTTP 409). */
public class AccountExistsException extends RuntimeException {

    public AccountExistsException() {
        super("An account with this email already exists. Sign in instead.");
    }
}
