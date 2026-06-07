package com.capstone.deepterview.domain.portfolio.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PythonExtractResumeRequest(
        @JsonProperty("pdf_path") String pdfPath
) {
}
