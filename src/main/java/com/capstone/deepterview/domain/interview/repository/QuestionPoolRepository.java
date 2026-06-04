package com.capstone.deepterview.domain.interview.repository;

import com.capstone.deepterview.domain.interview.domain.QuestionPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionPoolRepository extends JpaRepository<QuestionPool, Long> {
	List<QuestionPool> findByJobCategoryIdAndActiveTrue(Long jobCategoryId);
}
