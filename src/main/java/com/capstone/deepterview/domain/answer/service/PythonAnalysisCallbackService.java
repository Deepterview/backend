package com.capstone.deepterview.domain.answer.service;

import com.capstone.deepterview.domain.answer.domain.NonverbalAnalysis;
import com.capstone.deepterview.domain.answer.domain.SpeechAnalysis;
import com.capstone.deepterview.domain.answer.dto.request.PythonAnalysisCallbackRequest;
import com.capstone.deepterview.domain.answer.dto.request.PythonAnalysisResult;
import com.capstone.deepterview.domain.answer.dto.request.PythonTranscriptionResult;
import com.capstone.deepterview.domain.answer.repository.AnswerRepository;
import com.capstone.deepterview.domain.answer.repository.NonverbalAnalysisRepository;
import com.capstone.deepterview.domain.answer.repository.SpeechAnalysisRepository;
import com.capstone.deepterview.global.exception.CustomException;
import com.capstone.deepterview.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PythonAnalysisCallbackService {

    private final AnswerRepository answerRepository;
    private final SpeechAnalysisRepository speechAnalysisRepository;
    private final NonverbalAnalysisRepository nonverbalAnalysisRepository;

    @Transactional
    public void process(PythonAnalysisCallbackRequest request) {
        if ("error".equals(request.status())) {
            log.error("Python 분석 실패 answerId={} error={}", request.interviewId(), request.error());
            return;
        }

        Long answerId = Long.parseLong(request.interviewId());
        var answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "답변을 찾을 수 없습니다."));

        PythonAnalysisResult result = request.result();
        log.info("Python 콜백 수신 answerId={} audio={} transcription={}",
                answerId,
                result.audio(),
                result.transcription() != null ? result.transcription().text() : null);

        if (result.transcription() != null && result.transcription().text() != null) {
            answer.updateTranscript(result.transcription().text());
        }

        if (speechAnalysisRepository.findByAnswer_Id(answerId).isEmpty()) {
            Float wpm = calculateWpm(result.transcription());
            Float paceScore = normalizeTempo(result.audio() != null ? result.audio().tempoBpm() : null);
            Float clarityScore = normalizePitch(result.audio() != null ? result.audio().pitchMeanHz() : null);

            speechAnalysisRepository.save(SpeechAnalysis.create(
                    answer, wpm, null, null, null, paceScore, clarityScore, null
            ));
        }

        List<Map<String, Object>> gazeFrames = result.gazeFrames();
        int frameCount = result.frameCount() != null ? result.frameCount() : 0;
        if (gazeFrames != null && !gazeFrames.isEmpty() && frameCount > 0) {
            if (nonverbalAnalysisRepository.findByAnswer_Id(answerId).isEmpty()) {
                Float eyeContactScore = (float) gazeFrames.size() / frameCount * 100f;
                nonverbalAnalysisRepository.save(NonverbalAnalysis.create(
                        answer, eyeContactScore, null, null, null, null, null, null, null
                ));
            }
        }
    }

    private Float calculateWpm(PythonTranscriptionResult transcription) {
        if (transcription == null || transcription.segments() == null || transcription.segments().isEmpty()) {
            return null;
        }
        var segments = transcription.segments();
        double durationMin = (segments.get(segments.size() - 1).end() - segments.get(0).start()) / 60.0;
        if (durationMin <= 0) return null;
        long wordCount = segments.stream()
                .mapToLong(s -> s.text().trim().split("\\s+").length)
                .sum();
        return (float) (wordCount / durationMin);
    }

    private Float normalizeTempo(Double bpm) {
        if (bpm == null) return null;
        // 60~180 BPM 범위를 0~100점으로 정규화
        return (float) Math.max(0, Math.min(100, (bpm - 60) / 120.0 * 100));
    }

    private Float normalizePitch(Double hz) {
        if (hz == null) return null;
        // 80~300 Hz 범위를 0~100점으로 정규화
        return (float) Math.max(0, Math.min(100, (hz - 80) / 220.0 * 100));
    }
}
