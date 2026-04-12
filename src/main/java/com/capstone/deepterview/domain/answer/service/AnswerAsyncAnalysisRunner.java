package com.capstone.deepterview.domain.answer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerAsyncAnalysisRunner {

	private final AnswerAnalysisProcessingService answerAnalysisProcessingService;

	@Async
	public void runMockAnalyses(Long answerId) {
		try {
			answerAnalysisProcessingService.saveMockAnalyses(answerId);
		} catch (Exception e) {
			log.error("비동기 분석 처리 실패 answerId={}", answerId, e);
		}
	}
}
