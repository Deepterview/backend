package com.capstone.deepterview.domain.answer.service;

import com.capstone.deepterview.domain.answer.domain.*;
import com.capstone.deepterview.domain.answer.dto.response.*;
import com.capstone.deepterview.domain.answer.repository.*;
import com.capstone.deepterview.domain.interview.repository.QuestionRepository;
import com.capstone.deepterview.global.ai.LlmFeedbackService;
import com.capstone.deepterview.global.exception.BusinessException;
import com.capstone.deepterview.global.exception.CustomException;
import com.capstone.deepterview.global.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AnswerService {

	private final QuestionRepository questionRepository;
	private final AnswerRepository answerRepository;
	private final SpeechAnalysisRepository speechAnalysisRepository;
	private final StarAnalysisRepository starAnalysisRepository;
	private final NonverbalAnalysisRepository nonverbalAnalysisRepository;
	private final LlmFeedbackRepository llmFeedbackRepository;
	private final AnswerAsyncAnalysisRunner answerAsyncAnalysisRunner;
	private final ObjectMapper objectMapper;
	private final LlmFeedbackService llmFeedbackService;

	@Value("${app.file.answer-storage-dir}")
	private String answerStorageDir;

	@Transactional
	public SubmitAnswerResponse submitAnswer(
			Long userId,
			Long questionId,
			MultipartFile audio,
			int durationSec,
			CompletionStatus completionStatus
	) {
		var question = questionRepository.findByIdWithSessionUser(questionId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "질문을 찾을 수 없습니다."));

		if (!question.getSession().getUser().getId().equals(userId)) {
			throw new CustomException(ErrorCode.FORBIDDEN, "해당 질문에 답변할 권한이 없습니다.");
		}

		if (answerRepository.existsByQuestion_Id(questionId)) {
			throw new BusinessException(ErrorCode.CONFLICT, "이미 해당 질문에 대한 답변이 존재합니다.");
		}

		if (audio == null || audio.isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR, "음성 파일은 필수입니다.");
		}

		String storedPath = storeAudioFile(audio);
		Answer answer = Answer.create(question, storedPath, null, durationSec, completionStatus);
		answer.updateTranscript("목업 STT 결과입니다.");
		answerRepository.save(answer);

		String absolutePath = Paths.get(System.getProperty("user.dir")).resolve(storedPath)
				.toAbsolutePath().normalize().toString().replace('\\', '/');
		answerAsyncAnalysisRunner.runAnalyses(answer.getId(), absolutePath);

		return SubmitAnswerResponse.of(answer);
	}

	@Transactional(readOnly = true)
	public AnswerAnalysisResponse getAnalysis(Long userId, Long answerId) {
		Answer answer = answerRepository.findByIdWithQuestionSessionUser(answerId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "답변을 찾을 수 없습니다."));

		if (!answer.getQuestion().getSession().getUser().getId().equals(userId)) {
			throw new CustomException(ErrorCode.FORBIDDEN, "해당 답변을 조회할 권한이 없습니다.");
		}

		SpeechAnalysisView speech = speechAnalysisRepository.findByAnswer_Id(answerId)
				.map(this::toSpeechView)
				.orElse(null);

		StarAnalysisView star = starAnalysisRepository.findByAnswer_Id(answerId)
				.map(this::toStarView)
				.orElse(null);

		NonverbalAnalysisView nonverbal = nonverbalAnalysisRepository.findByAnswer_Id(answerId)
				.map(this::toNonverbalView)
				.orElse(null);

		LlmFeedbackView llm = llmFeedbackRepository.findByAnswer_Id(answerId)
				.map(this::toLlmView)
				.orElseGet(() ->
						llmFeedbackService.generateFeedback(
								answer.getTranscript(),
								answer.getQuestion().getContent()
						)
				);

		return new AnswerAnalysisResponse(
				answer.getId(),
				answer.getTranscript(),
				answer.getDurationSec(),
				speech,
				star,
				nonverbal,
				llm
		);
	}

	private String storeAudioFile(MultipartFile audio) {
		try {
			Path projectRoot = Paths.get(System.getProperty("user.dir")).normalize();
			Path dir = Paths.get(answerStorageDir);
			if (!dir.isAbsolute()) {
				dir = projectRoot.resolve(dir);
			}
			dir = dir.normalize();
			Files.createDirectories(dir);

			String original = Optional.of(audio.getOriginalFilename()).orElse("audio");
			String ext = extractExtension(original);
			String filename = UUID.randomUUID() + ext;

			Path target = dir.resolve(filename);
			audio.transferTo(target);

			Path absoluteFile = target.toAbsolutePath().normalize();
			try {
				return projectRoot.relativize(absoluteFile).toString().replace('\\', '/');
			} catch (IllegalArgumentException ex) {
				return absoluteFile.toString().replace('\\', '/');
			}
		} catch (IOException e) {
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "음성 파일 저장에 실패했습니다.");
		}
	}

	private static String extractExtension(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0 || dot == filename.length() - 1) {
			return "";
		}
		return filename.substring(dot);
	}

	private SpeechAnalysisView toSpeechView(SpeechAnalysis entity) {
		return new SpeechAnalysisView(
				entity.getWpm() == null ? null : entity.getWpm().doubleValue(),
				entity.getFillerCount(),
				parseFillerWords(entity.getFillerWordsJson()),
				entity.getSilenceRatio() == null ? null : entity.getSilenceRatio().doubleValue(),
				entity.getPaceScore() == null ? null : entity.getPaceScore().doubleValue(),
				entity.getClarityScore() == null ? null : entity.getClarityScore().doubleValue(),
				entity.getFeedback()
		);
	}

	private StarAnalysisView toStarView(StarAnalysis entity) {
		return new StarAnalysisView(
				toDouble(entity.getSituationScore()),
				toDouble(entity.getTaskScore()),
				toDouble(entity.getActionScore()),
				toDouble(entity.getResultScore()),
				toDouble(entity.getTotalScore()),
				entity.getSituationFeedback(),
				entity.getTaskFeedback(),
				entity.getActionFeedback(),
				entity.getResultFeedback()
		);
	}

	private NonverbalAnalysisView toNonverbalView(NonverbalAnalysis entity) {
		return new NonverbalAnalysisView(
				toDouble(entity.getEyeContactScore()),
				toDouble(entity.getConfidenceScore()),
				toDouble(entity.getAnxietyScore()),
				toDouble(entity.getSmileRatio()),
				toDouble(entity.getHeadStabilityScore()),
				entity.getDominantEmotion(),
				parseEmotionDistribution(entity.getEmotionDistributionJson()),
				entity.getFeedback()
		);
	}

	private LlmFeedbackView toLlmView(LlmFeedback entity) {
		List<String> followups = Stream.of(
						entity.getFollowupQuestion1(),
						entity.getFollowupQuestion2(),
						entity.getFollowupQuestion3()
				)
				.filter(Objects::nonNull)
				.filter(s -> !s.isBlank())
				.toList();

		return new LlmFeedbackView(
				entity.getStrength(),
				entity.getWeakness(),
				entity.getImprovement(),
				followups.isEmpty() ? List.of() : followups
		);
	}

	private static Double toDouble(Float value) {
		return value == null ? null : value.doubleValue();
	}

	private Map<String, Integer> parseFillerWords(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<>() {
			});
		} catch (Exception e) {
			return Map.of();
		}
	}

	private Map<String, Double> parseEmotionDistribution(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<>() {
			});
		} catch (Exception e) {
			return Map.of();
		}
	}
}
