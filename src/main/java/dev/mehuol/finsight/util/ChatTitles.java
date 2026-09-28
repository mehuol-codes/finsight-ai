package dev.mehuol.finsight.util;

/** Builds the short, single-line titles shown in the chat list. */
public final class ChatTitles {

    private static final int MAX_LENGTH = 60;

    private ChatTitles() {
    }

    public static String from(String text) {
        String oneLine = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (oneLine.isEmpty()) {
            return "New chat";
        }
        return oneLine.length() > MAX_LENGTH ? oneLine.substring(0, MAX_LENGTH - 1) + "…" : oneLine;
    }
}
