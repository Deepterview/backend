package com.capstone.deepterview.domain.answer.controller;

import com.capstone.deepterview.domain.answer.dto.request.PythonAnalysisCallbackRequest;
import com.capstone.deepterview.domain.answer.service.PythonAnalysisCallbackService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Python 분석 콜백 컨트롤러")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal")
public class InternalAnalysisCallbackController {

    private final PythonAnalysisCallbackService pythonAnalysisCallbackService;

    @PostMapping("/analysis/callback")
    public ResponseEntity<Void> receiveAnalysisResult(@RequestBody PythonAnalysisCallbackRequest request) {
        pythonAnalysisCallbackService.process(request);
        return ResponseEntity.ok().build();
    }
}
