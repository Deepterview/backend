package com.capstone.deepterview.domain.portfolio.service;

import com.capstone.deepterview.domain.portfolio.dto.request.PythonExtractResumeRequest;
import com.capstone.deepterview.domain.portfolio.dto.response.PythonExtractResumeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class PortfolioPythonClient {

    private final RestTemplate restTemplate;

    @Value("${app.python.base-url}")
    private String pythonBaseUrl;

    public PythonExtractResumeResponse extractResume(String pdfPath) {
        return restTemplate.postForObject(
                pythonBaseUrl + "/api/v1/extract-resume",
                new PythonExtractResumeRequest(pdfPath),
                PythonExtractResumeResponse.class
        );
    }
}
