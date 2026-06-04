package com.capstone.deepterview.domain.interview.repository;

import com.capstone.deepterview.domain.interview.domain.JobCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobCategoryRepository extends JpaRepository<JobCategory, Long> {
	List<JobCategory> findByActiveTrueOrderByIdAsc();
	Optional<JobCategory> findByName(String name);
}

