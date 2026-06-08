package com.capstone.deepterview.domain.portfolio.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PythonExtractResumeResponse(
        String text,
        @JsonProperty("is_scanned") boolean isScanned
) {
}
