package com.capstone.deepterview.domain.portfolio.service;

import com.capstone.deepterview.domain.member.domain.User;
import com.capstone.deepterview.domain.member.repository.UserRepository;
import com.capstone.deepterview.domain.portfolio.domain.Portfolio;
import com.capstone.deepterview.domain.portfolio.dto.response.PortfolioExtractResponse;
import com.capstone.deepterview.domain.portfolio.repository.PortfolioRepository;
import com.capstone.deepterview.global.ai.LlmFeedbackService;
import com.capstone.deepterview.global.exception.CustomException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private LlmFeedbackService llmFeedbackService;

    @InjectMocks
    private PortfolioService portfolioService;

    @TempDir
    Path tempDir;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.of("portfolio-test@example.com", "Tester", null);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    @DisplayName("텍스트 레이어가 있는 PDF는 텍스트를 정상 추출한다")
    void extractPortfolio_withTextPdf_returnsExtractedText() throws IOException {
        Path pdfPath = tempDir.resolve("resume.pdf");
        writeTextPdf(pdfPath, "Backend developer Hong Gildong.");

        Portfolio portfolio = Portfolio.create(user, pdfPath.toAbsolutePath().toString());
        ReflectionTestUtils.setField(portfolio, "id", 10L);
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));

        PortfolioExtractResponse response = portfolioService.extractPortfolio(1L, 10L);

        assertThat(response.isScanned()).isFalse();
        assertThat(response.extractedText()).contains("Hong Gildong");
        assertThat(portfolio.getExtractedText()).contains("Hong Gildong");
    }

    @Test
    @DisplayName("텍스트 레이어가 없는 스캔본 PDF는 거부한다")
    void extractPortfolio_withBlankPdf_throwsValidationError() throws IOException {
        Path pdfPath = tempDir.resolve("scanned.pdf");
        writeBlankPdf(pdfPath);

        Portfolio portfolio = Portfolio.create(user, pdfPath.toAbsolutePath().toString());
        ReflectionTestUtils.setField(portfolio, "id", 11L);
        when(portfolioRepository.findById(11L)).thenReturn(Optional.of(portfolio));

        assertThatThrownBy(() -> portfolioService.extractPortfolio(1L, 11L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("스캔본");
    }

    private void writeTextPdf(Path path, String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(path.toFile());
        }
    }

    private void writeBlankPdf(Path path) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(path.toFile());
        }
    }
}
