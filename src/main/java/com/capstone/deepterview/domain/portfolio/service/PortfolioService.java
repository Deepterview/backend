package com.capstone.deepterview.domain.portfolio.service;

import com.capstone.deepterview.domain.member.domain.User;
import com.capstone.deepterview.domain.member.repository.UserRepository;
import com.capstone.deepterview.domain.portfolio.domain.Portfolio;
import com.capstone.deepterview.domain.portfolio.dto.response.PortfolioExtractResponse;
import com.capstone.deepterview.domain.portfolio.dto.response.PortfolioQuestionsResponse;
import com.capstone.deepterview.domain.portfolio.dto.response.PortfolioUploadResponse;
import com.capstone.deepterview.domain.portfolio.repository.PortfolioRepository;
import com.capstone.deepterview.global.ai.LlmFeedbackService;
import com.capstone.deepterview.global.exception.CustomException;
import com.capstone.deepterview.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final LlmFeedbackService llmFeedbackService;

    @Value("${app.file.portfolio-storage-dir}")
    private String portfolioStorageDir;

    @Transactional
    public PortfolioUploadResponse uploadPortfolio(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "포트폴리오 파일은 필수입니다.");
        }

        String storedPath = storePortfolioFile(file);
        Portfolio portfolio = portfolioRepository.save(Portfolio.create(user, storedPath));

        return new PortfolioUploadResponse(portfolio.getId(), portfolio.getFilePath());
    }

    @Transactional
    public PortfolioExtractResponse extractPortfolio(Long userId, Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "포트폴리오를 찾을 수 없습니다."));

        if (!portfolio.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "해당 포트폴리오에 접근할 권한이 없습니다.");
        }

        throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "포트폴리오 텍스트 추출 기능은 현재 준비 중입니다.");
    }

    @Transactional(readOnly = true)
    public PortfolioQuestionsResponse generateQuestions(Long userId, Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "포트폴리오를 찾을 수 없습니다."));

        if (!portfolio.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "해당 포트폴리오에 접근할 권한이 없습니다.");
        }

        if (portfolio.getExtractedText() == null || portfolio.getExtractedText().isBlank()) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "텍스트가 추출되지 않은 포트폴리오입니다. 먼저 텍스트를 추출해주세요.");
        }

        List<String> questions = llmFeedbackService.generatePortfolioQuestions(portfolio.getExtractedText());

        if (questions.isEmpty()) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "면접 질문 생성에 실패했습니다.");
        }

        return new PortfolioQuestionsResponse(portfolio.getId(), questions);
    }

    private String storePortfolioFile(MultipartFile file) {
        try {
            Path projectRoot = Paths.get(System.getProperty("user.dir")).normalize();
            Path dir = Paths.get(portfolioStorageDir);
            if (!dir.isAbsolute()) {
                dir = projectRoot.resolve(dir);
            }
            dir = dir.normalize();
            Files.createDirectories(dir);

            String original = Optional.ofNullable(file.getOriginalFilename()).orElse("portfolio");
            String ext = extractExtension(original);
            String filename = UUID.randomUUID() + ext;

            Path target = dir.resolve(filename);
            file.transferTo(target);

            Path absoluteFile = target.toAbsolutePath().normalize();
            try {
                return projectRoot.relativize(absoluteFile).toString().replace('\\', '/');
            } catch (IllegalArgumentException ex) {
                return absoluteFile.toString().replace('\\', '/');
            }
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "포트폴리오 파일 저장에 실패했습니다.");
        }
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot);
    }
}
