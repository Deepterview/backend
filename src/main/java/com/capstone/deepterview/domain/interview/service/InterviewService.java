package com.capstone.deepterview.domain.interview.service;

import com.capstone.deepterview.domain.answer.domain.Answer;
import com.capstone.deepterview.domain.answer.repository.AnswerRepository;
import com.capstone.deepterview.domain.answer.service.AnswerAsyncAnalysisRunner;
import com.capstone.deepterview.domain.interview.domain.*;
import com.capstone.deepterview.domain.interview.dto.request.CreateSessionRequest;
import com.capstone.deepterview.domain.interview.dto.response.CreateSessionResponse;
import com.capstone.deepterview.domain.interview.dto.response.JobCategoryResponse;
import com.capstone.deepterview.domain.interview.dto.response.QuestionResponse;
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
import com.capstone.deepterview.global.exception.CustomException;
import com.capstone.deepterview.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
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
	private final AnswerAsyncAnalysisRunner answerAsyncAnalysisRunner;

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

		Question firstQuestion = pickFirstQuestion(session, jobCategory.getId());
		questionRepository.save(firstQuestion);

		return CreateSessionResponse.of(session, List.of(QuestionResponse.from(firstQuestion)));
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
		return SessionStatusResponse.ended(session.getId(), session.getStatus(), session.getEndedAt());
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

	public void generateReport(Long userId, Long sessionId) {
		InterviewSession session = getOwnedSession(userId, sessionId);
		if (session.getStatus() != SessionStatus.COMPLETED) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR, "완료된 세션만 리포트를 생성할 수 있습니다.");
		}

		List<Answer> answersWithVideo = answerRepository.findBySessionIdWithVideoPath(sessionId);

		if (answersWithVideo.isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR, "업로드된 영상이 없어 정밀 분석을 시작할 수 없습니다.");
		}

		answersWithVideo.forEach(answer -> {
			String absolutePath = Paths.get(System.getProperty("user.dir"))
					.resolve(answer.getAudioFilePath())
					.toAbsolutePath().normalize().toString().replace('\\', '/');
			answerAsyncAnalysisRunner.runVideoAnalysis(answer.getId(), absolutePath);
		});
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
		return Question.create(session, picked.getContent(), picked.getQuestionType(), 1, 120);
	}
}

