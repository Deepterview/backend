package com.capstone.deepterview.domain.interview.repository;

import com.capstone.deepterview.domain.interview.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
	List<Question> findBySessionIdOrderByOrderNumAsc(Long sessionId);
}

