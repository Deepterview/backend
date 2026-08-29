package com.capstone.deepterview.domain.interview.service;

import com.capstone.deepterview.domain.answer.domain.Answer;
import com.capstone.deepterview.domain.answer.domain.CompletionStatus;
import com.capstone.deepterview.domain.answer.repository.AnswerRepository;
import com.capstone.deepterview.domain.interview.domain.InterviewSession;
import com.capstone.deepterview.domain.interview.domain.JobCategory;
import com.capstone.deepterview.domain.interview.domain.Question;
import com.capstone.deepterview.domain.interview.domain.QuestionType;
import com.capstone.deepterview.domain.interview.domain.SessionStatus;
import com.capstone.deepterview.domain.interview.domain.SessionType;
import com.capstone.deepterview.domain.interview.dto.request.CreateSessionRequest;
import com.capstone.deepterview.domain.interview.dto.request.NextQuestionRequest;
import com.capstone.deepterview.domain.interview.dto.response.CreateSessionResponse;
import com.capstone.deepterview.domain.interview.dto.response.QuestionResponse;
import com.capstone.deepterview.domain.interview.repository.InterviewSessionRepository;
import com.capstone.deepterview.domain.interview.repository.JobCategoryRepository;
import com.capstone.deepterview.domain.interview.repository.QuestionPoolRepository;
import com.capstone.deepterview.domain.interview.repository.QuestionRepository;
import com.capstone.deepterview.domain.member.domain.User;
import com.capstone.deepterview.domain.member.repository.UserRepository;
import com.capstone.deepterview.domain.portfolio.domain.Portfolio;
import com.capstone.deepterview.domain.portfolio.repository.PortfolioRepository;
import com.capstone.deepterview.global.ai.LlmFeedbackService;
import com.capstone.deepterview.global.exception.CustomException;
import com.capstone.deepterview.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

	@Mock
	private InterviewSessionRepository interviewSessionRepository;

	@Mock
	private QuestionRepository questionRepository;

	@Mock
	private QuestionPoolRepository questionPoolRepository;

	@Mock
	private JobCategoryRepository jobCategoryRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private AnswerRepository answerRepository;

	@Mock
	private PortfolioRepository portfolioRepository;

	@Mock
	private LlmFeedbackService llmFeedbackService;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper();

	@InjectMocks
	private InterviewService interviewService;

	private User user;
	private JobCategory jobCategory;

	@BeforeEach
	void setUp() {
		user = User.of("interview-test@example.com", "Tester", null);
		ReflectionTestUtils.setField(user, "id", 1L);

		jobCategory = JobCategory.of("백엔드 개발", "설명");
		ReflectionTestUtils.setField(jobCategory, "id", 10L);

		lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		lenient().when(jobCategoryRepository.findById(10L)).thenReturn(Optional.of(jobCategory));
		lenient().when(interviewSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(questionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	@DisplayName("portfolioId가 주어지면 캐시된 맞춤 질문을 큐로 저장하고 1번 질문을 반환한다")
	void createSession_withPortfolioId_persistsPortfolioQueue() throws Exception {
		Portfolio portfolio = Portfolio.create(user, "resume.pdf");
		ReflectionTestUtils.setField(portfolio, "id", 20L);
		portfolio.updateGeneratedQuestions(objectMapper.writeValueAsString(
				List.of("맞춤질문1", "맞춤질문2", "맞춤질문3", "맞춤질문4", "맞춤질문5")));
		when(portfolioRepository.findById(20L)).thenReturn(Optional.of(portfolio));

		CreateSessionRequest request = new CreateSessionRequest(10L, "백엔드 개발자", 0, SessionType.TECHNICAL, 3, 20L);

		CreateSessionResponse response = interviewService.createSession(1L, request);

		assertThat(response.questions()).hasSize(1);
		QuestionResponse firstQuestion = response.questions().get(0);
		assertThat(firstQuestion.content()).isEqualTo("맞춤질문1");
		assertThat(firstQuestion.questionType()).isEqualTo(QuestionType.PORTFOLIO);

		ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
		verify(questionRepository, times(3)).save(captor.capture());
		List<Question> saved = captor.getAllValues();

		assertThat(saved).extracting(Question::getQuestionType).containsOnly(QuestionType.PORTFOLIO);
		assertThat(saved).extracting(Question::getOrderNum).containsExactlyInAnyOrder(1, 2, 3);
		assertThat(saved.stream().filter(q -> q.getOrderNum() == 1).findFirst().orElseThrow().getContent())
				.isEqualTo("맞춤질문1");
		assertThat(saved.stream().filter(q -> q.getOrderNum() == 3).findFirst().orElseThrow().getContent())
				.isEqualTo("맞춤질문3");

		verify(questionPoolRepository, never()).findByJobCategoryIdAndActiveTrue(anyLong());
	}

	@Test
	@DisplayName("portfolioId는 주어졌지만 맞춤 질문이 아직 생성되지 않았으면 검증 에러를 던진다")
	void createSession_withPortfolioId_withoutCachedQuestions_throwsValidationError() {
		Portfolio portfolio = Portfolio.create(user, "resume.pdf");
		ReflectionTestUtils.setField(portfolio, "id", 21L);
		when(portfolioRepository.findById(21L)).thenReturn(Optional.of(portfolio));

		CreateSessionRequest request = new CreateSessionRequest(10L, "백엔드 개발자", 0, SessionType.TECHNICAL, 3, 21L);

		assertThatThrownBy(() -> interviewService.createSession(1L, request))
				.isInstanceOf(CustomException.class)
				.satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	@DisplayName("다음 순서에 이미 저장된 큐 질문이 있으면 LLM 호출 없이 그대로 반환한다")
	void getNextQuestion_returnsQueuedQuestionWithoutCallingLlm() {
		InterviewSession session = InterviewSession.create(user, jobCategory, "백엔드 개발자", 0, SessionType.TECHNICAL, 3);
		ReflectionTestUtils.setField(session, "id", 100L);
		session.start(java.time.LocalDateTime.now());
		when(interviewSessionRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(session));

		Question firstQuestion = Question.create(session, "맞춤질문1", QuestionType.PORTFOLIO, 1, 120);
		ReflectionTestUtils.setField(firstQuestion, "id", 1000L);
		Answer answer = Answer.create(firstQuestion, null, "제 답변입니다.", 30, CompletionStatus.COMPLETED);
		ReflectionTestUtils.setField(answer, "id", 5000L);
		when(answerRepository.findByIdWithQuestionSessionUser(5000L)).thenReturn(Optional.of(answer));

		Question queuedSecondQuestion = Question.create(session, "맞춤질문2", QuestionType.PORTFOLIO, 2, 120);
		ReflectionTestUtils.setField(queuedSecondQuestion, "id", 1001L);
		when(questionRepository.findBySessionIdAndOrderNum(100L, 2)).thenReturn(Optional.of(queuedSecondQuestion));

		QuestionResponse response = interviewService.getNextQuestion(1L, 100L, new NextQuestionRequest(5000L));

		assertThat(response.content()).isEqualTo("맞춤질문2");
		assertThat(response.questionType()).isEqualTo(QuestionType.PORTFOLIO);
		verify(llmFeedbackService, never()).generateFollowupQuestion(anyString(), anyString());
		verify(questionRepository, never()).save(any());
	}

	@Test
	@DisplayName("큐가 소진되면 LLM으로 꼬리질문을 생성해 저장한다")
	void getNextQuestion_whenQueueExhausted_generatesFollowup() {
		InterviewSession session = InterviewSession.create(user, jobCategory, "백엔드 개발자", 0, SessionType.TECHNICAL, 3);
		ReflectionTestUtils.setField(session, "id", 200L);
		session.start(java.time.LocalDateTime.now());
		when(interviewSessionRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

		Question lastQueuedQuestion = Question.create(session, "맞춤질문2", QuestionType.PORTFOLIO, 2, 120);
		ReflectionTestUtils.setField(lastQueuedQuestion, "id", 2000L);
		Answer answer = Answer.create(lastQueuedQuestion, null, "제 답변입니다.", 30, CompletionStatus.COMPLETED);
		ReflectionTestUtils.setField(answer, "id", 6000L);
		when(answerRepository.findByIdWithQuestionSessionUser(6000L)).thenReturn(Optional.of(answer));

		when(questionRepository.findBySessionIdAndOrderNum(200L, 3)).thenReturn(Optional.empty());
		when(llmFeedbackService.generateFollowupQuestion("제 답변입니다.", "맞춤질문2")).thenReturn("추가로 설명해주실 수 있나요?");

		QuestionResponse response = interviewService.getNextQuestion(1L, 200L, new NextQuestionRequest(6000L));

		assertThat(response.content()).isEqualTo("추가로 설명해주실 수 있나요?");
		assertThat(response.questionType()).isEqualTo(QuestionType.FOLLOWUP);
		verify(questionRepository, times(1)).save(any());
	}
}
