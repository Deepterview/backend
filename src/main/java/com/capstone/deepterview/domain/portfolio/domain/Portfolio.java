package com.capstone.deepterview.domain.portfolio.domain;

import com.capstone.deepterview.domain.member.domain.User;
import com.capstone.deepterview.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "portfolio")
public class Portfolio extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Lob
    private String extractedText;

    @Column(name = "is_scanned")
    private Boolean isScanned;

    protected Portfolio() {
    }

    public static Portfolio create(User user, String filePath) {
        Portfolio portfolio = new Portfolio();
        portfolio.user = user;
        portfolio.filePath = filePath;
        return portfolio;
    }

    public void updateExtractedText(String extractedText, boolean isScanned) {
        this.extractedText = extractedText;
        this.isScanned = isScanned;
    }
}
