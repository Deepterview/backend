package com.capstone.deepterview.domain.interview.dto.request;

import jakarta.validation.constraints.NotNull;

public record NextQuestionRequest(
        @NotNull Long answerId
) {
}
