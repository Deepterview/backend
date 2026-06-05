package com.capstone.deepterview.domain.interview.domain;

import com.capstone.deepterview.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "question_pool")
public class QuestionPool extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "job_category_id")
	private JobCategory jobCategory;

	@Lob
	@Column(nullable = false)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(name = "question_type", nullable = false, length = 20)
	private QuestionType questionType;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	protected QuestionPool() {
	}

	public static QuestionPool of(JobCategory jobCategory, String content, QuestionType questionType) {
		QuestionPool pool = new QuestionPool();
		pool.jobCategory = jobCategory;
		pool.content = content;
		pool.questionType = questionType;
		pool.active = true;
		return pool;
	}
}
