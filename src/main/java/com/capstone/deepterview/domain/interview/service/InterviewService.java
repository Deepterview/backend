package com.capstone.deepterview.domain.interview.service;

import com.capstone.deepterview.domain.answer.repository.AnswerRepository;
import com.capstone.deepterview.domain.interview.domain.*;
import com.capstone.deepterview.domain.interview.dto.request.CreateSessionRequest;
import com.capstone.deepterview.domain.interview.dto.request.NextQuestionRequest;
import com.capstone.deepterview.domain.interview.dto.response.CreateSessionResponse;
import com.capstone.deepterview.domain.interview.dto.response.JobCategoryResponse;
import com.capstone.deepterview.domain.interview.dto.response.QuestionResponse;
import com.capstone.deepterview.global.ai.LlmFeedbackService;
import com.capstone.deepterview.domain.interview.dto.response.SessionDetailResponse;
import com.capstone.deepterview.domain.interview.dto.response.SessionListResponse;
import com.capstone.deepterview.domain.interview.dto.response.SessionListItemResponse;
import com.capstone.deepterview.domain.interview.dto.response.SessionStatusResponse;
import com.capstone.deepterview.domain.interview.repository.InterviewSessionRepository;
import com.capstone.deepterview.domain.interview.repository.JobCategoryRepository;
import com.capstone.deepterview.domain.interview.repository.QuestionPoolRepository;
import com.capstone.deepterview.domain.interview.repository.QuestionRepository;
import com.capstone.deepterview.domain.member.domain.User;
import com.capstone.deepterview.domain.member.repository.UserRepository;
import com.capstone.deepterview.domain.portfolio.domain.Portfolio;
import com.capstone.deepterview.domain.portfolio.repository.PortfolioRepository;
import com.capstone.deepterview.global.exception.CustomException;
import com.capstone.deepterview.global.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class InterviewService {

	private final InterviewSessionRepository interviewSessionRepository;
	private final QuestionRepository questionRepository;
	private final QuestionPoolRepository questionPoolRepository;
	private final JobCategoryRepository jobCategoryRepository;
	private final UserRepository userRepository;
	private final AnswerRepository answerRepository;
	private final PortfolioRepository portfolioRepository;
	private final LlmFeedbackService llmFeedbackService;
	private final ObjectMapper objectMapper;

	private static final int DEFAULT_TIME_LIMIT_SEC = 120;
	private static final Random RANDOM = new Random();

	@Transactional
	public CreateSessionResponse createSession(Long userId, CreateSessionRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

		JobCategory jobCategory = jobCategoryRepository.findById(request.jobCategoryId())
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "유효하지 않은 직군 카테고리입니다."));

		// careerYears가 0이면 신입으로 취급합니다.
		int normalizedCareerYears = Math.max(request.careerYears(), 0);

		if (request.sessionType() != SessionType.TECHNICAL && request.sessionType() != null) {
			throw new CustomException(ErrorCode.NOT_FOUND, "잘못된 세션 타입입니다.");
		}

		SessionType normalizedSessionType = SessionType.TECHNICAL;

		InterviewSession session = InterviewSession.create(
				user,
				jobCategory,
				request.jobTitle(),
				normalizedCareerYears,
				normalizedSessionType,
				request.totalQuestions()
		);
		interviewSessionRepository.save(session);

		Question firstQuestion = request.portfolioId() != null
				? createPortfolioQueue(session, userId, request.portfolioId())
				: pickFirstQuestion(session, jobCategory.getId());
		questionRepository.save(firstQuestion);

		return CreateSessionResponse.of(session, List.of(QuestionResponse.from(firstQuestion)));
	}

	private Question createPortfolioQueue(InterviewSession session, Long userId, Long portfolioId) {
		Portfolio portfolio = portfolioRepository.findById(portfolioId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "포트폴리오를 찾을 수 없습니다."));

		if (!portfolio.getUser().getId().equals(userId)) {
			throw new CustomException(ErrorCode.FORBIDDEN, "해당 포트폴리오에 접근할 권한이 없습니다.");
		}

		String questionsJson = portfolio.getGeneratedQuestionsJson();
		if (questionsJson == null || questionsJson.isBlank()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR,
					"포트폴리오 맞춤 질문이 아직 생성되지 않았습니다. 먼저 질문 생성 API를 호출해주세요.");
		}

		List<String> portfolioQuestions;
		try {
			portfolioQuestions = objectMapper.readValue(questionsJson, new TypeReference<List<String>>() {});
		} catch (Exception e) {
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "포트폴리오 맞춤 질문을 읽을 수 없습니다.");
		}

		if (portfolioQuestions.isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR,
					"포트폴리오 맞춤 질문이 아직 생성되지 않았습니다. 먼저 질문 생성 API를 호출해주세요.");
		}

		int queueSize = Math.min(portfolioQuestions.size(), session.getTotalQuestions());
		for (int i = 1; i < queueSize; i++) {
			questionRepository.save(Question.create(
					session, portfolioQuestions.get(i), QuestionType.PORTFOLIO, i + 1, DEFAULT_TIME_LIMIT_SEC));
		}

		return Question.create(session, portfolioQuestions.get(0), QuestionType.PORTFOLIO, 1, DEFAULT_TIME_LIMIT_SEC);
	}

	@Transactional(readOnly = true)
	public SessionListResponse getSessions(Long userId, SessionStatus status, Pageable pageable) {
		Page<InterviewSession> page = status == null
				? interviewSessionRepository.findByUserId(userId, pageable)
				: interviewSessionRepository.findByUserIdAndStatus(userId, status, pageable);

		Page<SessionListItemResponse> mappedPage = page.map(SessionListItemResponse::from);
		return SessionListResponse.from(mappedPage);
	}

	@Transactional(readOnly = true)
	public SessionDetailResponse getSessionDetail(Long userId, Long sessionId) {
		InterviewSession session = getOwnedSession(userId, sessionId);
		List<QuestionResponse> questions = questionRepository.findBySessionIdWithAnswerOrderByOrderNumAsc(sessionId)
				.stream()
				.map(QuestionResponse::from)
				.toList();
		return SessionDetailResponse.of(session, questions);
	}

	@Transactional
	public SessionStatusResponse startSession(Long userId, Long sessionId) {
		InterviewSession session = getOwnedSession(userId, sessionId);
		if (session.getStatus() != SessionStatus.READY) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR, "READY 상태의 세션만 시작할 수 있습니다.");
		}
		session.start(LocalDateTime.now());
		return SessionStatusResponse.started(session.getId(), session.getStatus(), session.getStartedAt());
	}

	@Transactional
	public SessionStatusResponse endSession(Long userId, Long sessionId) {
		InterviewSession session = getOwnedSession(userId, sessionId);
		if (session.getStatus() != SessionStatus.IN_PROGRESS) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR, "IN_PROGRESS 상태의 세션만 종료할 수 있습니다.");
		}
		session.complete(LocalDateTime.now());
		return SessionStatusResponse.ended(session.getId(), session.getStatus(), session.getStartedAt(), session.getEndedAt());
	}

	@Transactional
	public void deleteSession(Long userId, Long sessionId) {
		InterviewSession session = getOwnedSession(userId, sessionId);
		LocalDateTime now = LocalDateTime.now();
		session.softDelete(now);
		questionRepository.findBySessionIdOrderByOrderNumAsc(sessionId).forEach(question -> question.softDelete(now));
	}

	@Transactional(readOnly = true)
	public List<JobCategoryResponse> getJobCategories() {
		return jobCategoryRepository.findByActiveTrueOrderByIdAsc()
				.stream()
				.map(JobCategoryResponse::from)
				.toList();
	}

	public QuestionResponse getNextQuestion(Long userId, Long sessionId, NextQuestionRequest request) {
		InterviewSession session = getOwnedSession(userId, sessionId);

		if (session.getStatus() != SessionStatus.IN_PROGRESS) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR, "진행 중인 세션에서만 다음 질문을 요청할 수 있습니다.");
		}

		var answer = answerRepository.findByIdWithQuestionSessionUser(request.answerId())
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "답변을 찾을 수 없습니다."));

		if (!answer.getQuestion().getSession().getId().equals(sessionId)) {
			throw new CustomException(ErrorCode.FORBIDDEN, "해당 세션의 답변이 아닙니다.");
		}

		int nextOrderNum = answer.getQuestion().getOrderNum() + 1;

		if (nextOrderNum > session.getTotalQuestions()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR, "모든 질문이 완료되었습니다. 세션을 종료해주세요.");
		}

		Optional<Question> queued = questionRepository.findBySessionIdAndOrderNum(sessionId, nextOrderNum);
		if (queued.isPresent()) {
			return QuestionResponse.from(queued.get());
		}

		// DB 커넥션을 점유하지 않은 상태로 Claude 호출 (네트워크 왕복)
		MDC.put("sessionId", String.valueOf(sessionId));
		String followup;
		try {
			followup = llmFeedbackService.generateFollowupQuestion(
					answer.getTranscript(),
					answer.getQuestion().getContent()
			);
		} finally {
			MDC.remove("sessionId");
		}

		if (followup == null || followup.isBlank()) {
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "꼬리 질문 생성에 실패했습니다.");
		}

		Question nextQuestion = questionRepository.save(
				Question.create(session, followup, QuestionType.FOLLOWUP, nextOrderNum, DEFAULT_TIME_LIMIT_SEC));

		return QuestionResponse.from(nextQuestion);
	}

	private InterviewSession getOwnedSession(Long userId, Long sessionId) {
		return interviewSessionRepository.findByIdAndUserId(sessionId, userId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "세션을 찾을 수 없습니다."));
	}

	private Question pickFirstQuestion(InterviewSession session, Long jobCategoryId) {
		List<QuestionPool> pool = questionPoolRepository.findByJobCategoryIdAndActiveTrue(jobCategoryId);
		if (pool.isEmpty()) {
			throw new CustomException(ErrorCode.NOT_FOUND, "해당 직군의 질문 풀이 비어 있습니다.");
		}
		QuestionPool picked = pool.get(RANDOM.nextInt(pool.size()));
		return Question.create(session, picked.getContent(), picked.getQuestionType(), 1, DEFAULT_TIME_LIMIT_SEC);
	}
}

