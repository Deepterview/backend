package com.capstone.deepterview.domain.answer.service;

import com.capstone.deepterview.domain.answer.domain.*;
import com.capstone.deepterview.domain.answer.repository.*;
import com.capstone.deepterview.global.exception.CustomException;
import com.capstone.deepterview.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerAnalysisProcessingService {

	private final AnswerRepository answerRepository;
	private final SpeechAnalysisRepository speechAnalysisRepository;
	private final StarAnalysisRepository starAnalysisRepository;
	private final LlmFeedbackRepository llmFeedbackRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public void saveMockAnalyses(Long answerId) {
		Answer answer = answerRepository.findById(answerId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "답변을 찾을 수 없습니다."));

		if (speechAnalysisRepository.findByAnswer_Id(answerId).isEmpty()) {
			String fillerJson = toJson(Map.of("음", 2, "어", 1));
			speechAnalysisRepository.save(SpeechAnalysis.create(
					answer,
					142.3f,
					3,
					fillerJson,
					0.12f,
					78.0f,
					85.0f,
					"말하기 속도는 적절하나 필러워드를 줄이면 더 좋습니다."
			));
		}

		if (starAnalysisRepository.findByAnswer_Id(answerId).isEmpty()) {
			starAnalysisRepository.save(StarAnalysis.create(
					answer,
					80.0f,
					70.0f,
					90.0f,
					60.0f,
					75.0f,
					"상황 설명이 명확합니다.",
					"해결해야 할 과제를 더 구체적으로 설명해주세요.",
					"행동 단계가 잘 서술되어 있습니다.",
					"결과와 수치를 포함하면 더 좋습니다."
			));
		}

		if (llmFeedbackRepository.findByAnswer_Id(answerId).isEmpty()) {
			llmFeedbackRepository.save(LlmFeedback.create(
					answer,
					"행동 단계를 구체적으로 설명했습니다.",
					"결과에 수치가 없어 임팩트가 약합니다.",
					"Result 부분에 성과 지표를 추가하세요.",
					"그 과정에서 가장 어려웠던 점은 무엇이었나요?",
					"같은 상황이 다시 생긴다면 어떻게 다르게 접근하겠나요?",
					"팀원과의 협업은 어떻게 이루어졌나요?",
					"mock-llm",
					100,
					200,
					null,
					null
			));
		}

		if (answer.getTranscript() == null || answer.getTranscript().isBlank()) {
			answer.updateTranscript("목업 STT 결과입니다.");
		}
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			log.warn("JSON 직렬화 실패", e);
			return "{}";
		}
	}
}
