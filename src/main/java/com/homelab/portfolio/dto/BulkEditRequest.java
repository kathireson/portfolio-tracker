package com.homelab.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for bulk edit TSV upload
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkEditRequest {
    private Long portfolioId;
    private String tsvData;
}
