package com.capstone.deepterview.domain.report.service;

import com.capstone.deepterview.domain.answer.domain.Answer;
import com.capstone.deepterview.domain.answer.domain.NonverbalAnalysis;
import com.capstone.deepterview.domain.answer.domain.SpeechAnalysis;
import com.capstone.deepterview.domain.answer.domain.StarAnalysis;
import com.capstone.deepterview.domain.answer.repository.AnswerRepository;
import com.capstone.deepterview.domain.interview.domain.InterviewSession;
import com.capstone.deepterview.domain.interview.domain.SessionStatus;
import com.capstone.deepterview.domain.interview.dto.response.SessionReportResponse;
import com.capstone.deepterview.domain.interview.repository.InterviewSessionRepository;
import com.capstone.deepterview.domain.report.domain.FeedbackReport;
import com.capstone.deepterview.domain.report.domain.Grade;
import com.capstone.deepterview.domain.report.repository.FeedbackReportRepository;
import com.capstone.deepterview.global.ai.LlmFeedbackService;
import com.capstone.deepterview.global.ai.LlmReportSummary;
import com.capstone.deepterview.global.exception.CustomException;
import com.capstone.deepterview.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final FeedbackReportRepository feedbackReportRepository;
    private final AnswerRepository answerRepository;
    private final LlmFeedbackService llmFeedbackService;

    @Transactional
    public SessionReportResponse generateOrGetReport(Long userId, Long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);

        Optional<FeedbackReport> existing = feedbackReportRepository.findBySession_Id(sessionId);
        if (existing.isPresent()) {
            return SessionReportResponse.of(existing.get());
        }

        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "완료된 세션만 리포트를 생성할 수 있습니다.");
        }

        List<Answer> answers = answerRepository.findBySessionIdWithAnalyses(sessionId);

        Float speechScore = computeSpeechScore(answers);
        Float nonverbalScore = computeNonverbalScore(answers);
        Float contentScore = computeContentScore(answers);
        Float overallScore = computeOverallScore(speechScore, nonverbalScore, contentScore);
        Grade grade = computeGrade(overallScore);

        LlmReportSummary summary = llmFeedbackService.generateReportSummary(session, answers);

        FeedbackReport report = feedbackReportRepository.save(FeedbackReport.create(
                session,
                speechScore,
                nonverbalScore,
                contentScore,
                overallScore,
                grade,
                summary.strengthSummary(),
                summary.weaknessSummary(),
                summary.improvementPriority(),
                summary.aiSummary()
        ));

        return SessionReportResponse.of(report);
    }

    private Float computeSpeechScore(List<Answer> answers) {
        List<Float> scores = answers.stream()
                .map(Answer::getSpeechAnalysis)
                .filter(Objects::nonNull)
                .map(s -> {
                    float sum = 0;
                    int count = 0;
                    if (s.getPaceScore() != null) { sum += s.getPaceScore(); count++; }
                    if (s.getClarityScore() != null) { sum += s.getClarityScore(); count++; }
                    return count > 0 ? sum / count : null;
                })
                .filter(Objects::nonNull)
                .toList();
        return scores.isEmpty() ? null
                : (float) scores.stream().mapToDouble(Float::doubleValue).average().orElse(0);
    }

    private Float computeNonverbalScore(List<Answer> answers) {
        List<Float> scores = answers.stream()
                .map(Answer::getNonverbalAnalysis)
                .filter(Objects::nonNull)
                .map(NonverbalAnalysis::getEyeContactScore)
                .filter(Objects::nonNull)
                .toList();
        return scores.isEmpty() ? null
                : (float) scores.stream().mapToDouble(Float::doubleValue).average().orElse(0);
    }

    private Float computeContentScore(List<Answer> answers) {
        List<Float> scores = answers.stream()
                .map(Answer::getStarAnalysis)
                .filter(Objects::nonNull)
                .map(StarAnalysis::getTotalScore)
                .filter(Objects::nonNull)
                .map(s -> s * 10f)
                .toList();
        return scores.isEmpty() ? null
                : (float) scores.stream().mapToDouble(Float::doubleValue).average().orElse(0);
    }

    private Float computeOverallScore(Float speech, Float nonverbal, Float content) {
        float weightedSum = 0;
        float totalWeight = 0;
        if (speech != null)   { weightedSum += speech   * 0.3f; totalWeight += 0.3f; }
        if (nonverbal != null) { weightedSum += nonverbal * 0.3f; totalWeight += 0.3f; }
        if (content != null)  { weightedSum += content  * 0.4f; totalWeight += 0.4f; }
        return totalWeight == 0 ? null : weightedSum / totalWeight;
    }

    private Grade computeGrade(Float score) {
        if (score == null) return Grade.D;
        if (score >= 95) return Grade.S;
        if (score >= 85) return Grade.A;
        if (score >= 75) return Grade.B;
        if (score >= 65) return Grade.C;
        return Grade.D;
    }

    private InterviewSession getOwnedSession(Long userId, Long sessionId) {
        return interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "세션을 찾을 수 없습니다."));
    }
}
