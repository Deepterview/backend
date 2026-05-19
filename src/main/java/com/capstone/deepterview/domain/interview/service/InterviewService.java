package com.capstone.deepterview.domain.interview.service;

import com.capstone.deepterview.domain.answer.domain.Answer;
import com.capstone.deepterview.domain.answer.repository.AnswerRepository;
import com.capstone.deepterview.domain.answer.service.AnswerAsyncAnalysisRunner;
import com.capstone.deepterview.domain.interview.domain.*;
import com.capstone.deepterview.domain.interview.dto.request.CreateSessionRequest;
import com.capstone.deepterview.domain.interview.dto.response.*;
import com.capstone.deepterview.domain.interview.repository.InterviewSessionRepository;
import com.capstone.deepterview.domain.interview.repository.JobCategoryRepository;
import com.capstone.deepterview.domain.interview.repository.QuestionRepository;
import com.capstone.deepterview.domain.member.domain.User;
import com.capstone.deepterview.domain.member.repository.UserRepository;
import com.capstone.deepterview.domain.report.repository.FeedbackReportRepository;
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

@Service
@RequiredArgsConstructor
public class InterviewService {

	private final InterviewSessionRepository interviewSessionRepository;
	private final QuestionRepository questionRepository;
	private final JobCategoryRepository jobCategoryRepository;
	private final UserRepository userRepository;
	private final FeedbackReportRepository feedbackReportRepository;
	private final AnswerRepository answerRepository;
	private final AnswerAsyncAnalysisRunner answerAsyncAnalysisRunner;

	@Transactional
	public CreateSessionResponse createSession(Long userId, CreateSessionRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

		JobCategory jobCategory = jobCategoryRepository.findById(request.jobCategoryId())
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "유효하지 않은 직군 카테고리입니다."));

		// careerYears가 0이면 신입으로 취급합니다.
		int normalizedCareerYears = Math.max(request.careerYears(), 0);

		InterviewSession session = InterviewSession.create(
				user,
				jobCategory,
				request.jobTitle(),
				normalizedCareerYears,
				request.sessionType(),
				request.totalQuestions()
		);
		interviewSessionRepository.save(session);

		List<Question> questions = createMockQuestions(session, request.totalQuestions(), request.sessionType(), normalizedCareerYears);
		questionRepository.saveAll(questions);

		List<QuestionResponse> questionResponses = questions.stream()
				.map(QuestionResponse::from)
				.toList();

		return CreateSessionResponse.of(session, questionResponses);
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
		List<QuestionResponse> questions = questionRepository.findBySessionIdOrderByOrderNumAsc(sessionId)
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
	public SessionReportResponse getSessionReport(Long userId, Long sessionId) {
		InterviewSession session = getOwnedSession(userId, sessionId);
		if (session.getStatus() != SessionStatus.COMPLETED) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR, "세션이 완료되지 않아 리포트를 조회할 수 없습니다.");
		}
		return feedbackReportRepository.findBySession_Id(sessionId)
				.map(SessionReportResponse::of)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "종합 리포트가 아직 생성되지 않았습니다."));
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

	private List<Question> createMockQuestions(InterviewSession session, int totalQuestions, SessionType sessionType, int careerYears) {
		return java.util.stream.IntStream.rangeClosed(1, totalQuestions)
				.mapToObj(order -> {
					QuestionType questionType = switch (sessionType) {
						case TECHNICAL -> QuestionType.TECHNICAL;
						case PERSONALITY -> QuestionType.BEHAVIORAL;
						case COMBINED -> order % 2 == 0 ? QuestionType.BEHAVIORAL : QuestionType.TECHNICAL;
					};
					String careerLabel = careerYears == 0 ? "신입" : careerYears + "년차";
					String content = String.format("[%s %s] 모의 질문 %d번입니다. 본인의 경험을 기반으로 답변해주세요.", sessionType, careerLabel, order);
					// TODO: LLM 연동 후 직무/경력 기반 질문 생성으로 대체
					return Question.create(session, content, questionType, order, 120);
				})
				.toList();
	}
}

