package dev.mehuol.finsight.util;

import java.util.regex.Pattern;

/** Chat ids come from the browser, so they are validated before being used in queries or filters. */
public final class ConversationIds {

    // 36 = UUID length, which is also the size of the chat memory table's id column.
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{1,36}");

    private ConversationIds() {
    }

    public static boolean isValid(String conversationId) {
        return conversationId != null && VALID.matcher(conversationId).matches();
    }

    public static void requireValid(String conversationId) {
        if (!isValid(conversationId)) {
            throw new IllegalArgumentException("Invalid conversation id");
        }
    }
}
