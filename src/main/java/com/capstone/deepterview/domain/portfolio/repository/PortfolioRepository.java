package com.capstone.deepterview.domain.portfolio.repository;

import com.capstone.deepterview.domain.portfolio.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
}
