package dev.mehuol.finsight.dto;

import java.time.OffsetDateTime;

/** One entry in the sidebar chat list. */
public record ChatSummary(String id, String title, OffsetDateTime updatedAt) {
}
