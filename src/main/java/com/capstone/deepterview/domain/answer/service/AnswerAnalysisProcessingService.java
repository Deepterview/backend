package com.capstone.deepterview.domain.answer.service;

import com.capstone.deepterview.domain.answer.domain.Answer;
import com.capstone.deepterview.domain.answer.domain.StarAnalysis;
import com.capstone.deepterview.domain.answer.repository.AnswerRepository;
import com.capstone.deepterview.domain.answer.repository.StarAnalysisRepository;
import com.capstone.deepterview.global.exception.CustomException;
import com.capstone.deepterview.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnswerAnalysisProcessingService {

	private final AnswerRepository answerRepository;
	private final StarAnalysisRepository starAnalysisRepository;

	@Transactional
	public void saveMockStarAnalysis(Long answerId) {
		Answer answer = answerRepository.findById(answerId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "답변을 찾을 수 없습니다."));

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
	}

}
