package com.homelab.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for bulk edit operation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkEditResult {
    private int updated;
    private int skipped;
    private List<String> skippedTickers;
    private String message;
    private boolean success;
}
