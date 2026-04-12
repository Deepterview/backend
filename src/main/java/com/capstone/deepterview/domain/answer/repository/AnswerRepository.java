package com.capstone.deepterview.domain.answer.repository;

import com.capstone.deepterview.domain.answer.domain.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

	boolean existsByQuestion_Id(Long questionId);

	@Query("SELECT a FROM Answer a JOIN FETCH a.question q JOIN FETCH q.session s JOIN FETCH s.user WHERE a.id = :id")
	Optional<Answer> findByIdWithQuestionSessionUser(@Param("id") Long id);
}
