package com.seventhray.contracts.dto;

import com.seventhray.contracts.model.Contract;
import com.seventhray.contracts.model.ContractStatus;

import java.time.Instant;
import java.util.List;

public record ContractResponse(
        Long id,
        String title,
        String originalFilename,
        String contentType,
        long sizeBytes,
        ContractStatus status,
        ContractStatus nextStatus,
        int wordCount,
        String textPreview,
        Instant createdAt,
        Instant updatedAt,
        List<HistoryEntry> history) {

    public record HistoryEntry(ContractStatus from, ContractStatus to, Instant changedAt) {}

    public static ContractResponse from(Contract c, boolean withHistory) {
        String text = c.getExtractedText() == null ? "" : c.getExtractedText();
        String preview = text.length() > 300 ? text.substring(0, 300) + "..." : text;
        int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
        List<HistoryEntry> hist = withHistory
                ? c.getHistory().stream()
                    .map(h -> new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getChangedAt()))
                    .toList()
                : null;
        return new ContractResponse(c.getId(), c.getTitle(), c.getOriginalFilename(),
                c.getContentType(), c.getSizeBytes(), c.getStatus(), c.getStatus().next(),
                words, preview, c.getCreatedAt(), c.getUpdatedAt(), hist);
    }
}
