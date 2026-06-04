package com.capstone.deepterview.global.config;

import com.capstone.deepterview.domain.interview.domain.JobCategory;
import com.capstone.deepterview.domain.interview.domain.QuestionPool;
import com.capstone.deepterview.domain.interview.domain.QuestionType;
import com.capstone.deepterview.domain.interview.repository.JobCategoryRepository;
import com.capstone.deepterview.domain.interview.repository.QuestionPoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

	private final JobCategoryRepository jobCategoryRepository;
	private final QuestionPoolRepository questionPoolRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (questionPoolRepository.count() > 0) {
			return;
		}
		seedQuestions();
	}

	private JobCategory findOrCreateCategory(String name, String description) {
		return jobCategoryRepository.findByName(name)
				.orElseGet(() -> jobCategoryRepository.save(JobCategory.of(name, description)));
	}

	private void seedQuestions() {
		JobCategory backend = findOrCreateCategory("백엔드 개발", "서버, API, 데이터베이스 중심의 백엔드 개발");
		JobCategory frontend = findOrCreateCategory("프론트엔드 개발", "UI/UX, 웹 클라이언트 중심의 프론트엔드 개발");
		JobCategory fullstack = findOrCreateCategory("풀스택 개발", "프론트엔드와 백엔드를 아우르는 풀스택 개발");
		JobCategory dataEng = findOrCreateCategory("데이터 엔지니어링", "데이터 파이프라인, ETL, 데이터 플랫폼 구축");
		JobCategory aiMl = findOrCreateCategory("AI/ML 엔지니어링", "머신러닝 모델 개발 및 MLOps");
		JobCategory devops = findOrCreateCategory("DevOps/인프라", "CI/CD, 클라우드 인프라, 컨테이너 오케스트레이션");

		questionPoolRepository.saveAll(List.of(
				QuestionPool.of(backend, "RESTful API 설계 시 고려해야 할 핵심 원칙은 무엇인가요? 실제 프로젝트에서 어떻게 적용하셨는지 설명해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(backend, "데이터베이스 트랜잭션의 ACID 속성에 대해 설명하고, 실무에서 트랜잭션을 잘못 관리해 문제가 생겼던 경험이 있다면 말씀해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(backend, "캐싱 전략(로컬 캐시, 분산 캐시 등)을 설명하고 본인이 적용해본 사례를 이야기해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(backend, "MSA와 모놀리식 아키텍처의 장단점을 비교하고, 어떤 상황에서 각각을 선택하시겠습니까?", QuestionType.TECHNICAL),
				QuestionPool.of(backend, "동시성 문제를 경험하거나 해결한 사례가 있다면 설명해주세요. 어떤 방식으로 해결하셨나요?", QuestionType.TECHNICAL),

				QuestionPool.of(frontend, "React의 Virtual DOM이 실제 DOM과 다른 점을 설명하고, 렌더링 최적화를 위해 어떤 기법을 사용하셨나요?", QuestionType.TECHNICAL),
				QuestionPool.of(frontend, "브라우저의 렌더링 과정(Critical Rendering Path)을 설명하고, 성능 개선을 위해 어떤 부분을 최적화하셨는지 말씀해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(frontend, "JavaScript의 이벤트 루프 동작 방식을 설명하고, 비동기 처리에서 겪었던 어려움과 해결 방법을 말씀해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(frontend, "CSR, SSR, SSG의 차이점을 설명하고, 프로젝트에서 어떤 방식을 선택하셨는지 그 이유와 함께 말씀해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(frontend, "상태 관리 라이브러리를 사용해본 경험이 있으신가요? 어떤 기준으로 선택하셨고 어떻게 활용하셨는지 설명해주세요.", QuestionType.TECHNICAL),

				QuestionPool.of(fullstack, "백엔드와 프론트엔드 간 API 인터페이스를 설계할 때 어떤 사항을 중점적으로 고려하시나요?", QuestionType.TECHNICAL),
				QuestionPool.of(fullstack, "JWT 기반 인증/인가를 구현해보셨나요? 토큰 갱신 전략과 보안 고려사항을 설명해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(fullstack, "CI/CD 파이프라인을 구축하거나 개선한 경험을 설명해주세요. 어떤 도구를 사용하셨나요?", QuestionType.TECHNICAL),
				QuestionPool.of(fullstack, "TypeScript를 도입하거나 활용한 경험이 있으신가요? 도입 후 달라진 점을 설명해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(fullstack, "풀스택 개발 시 가장 어려웠던 부분은 무엇이었고, 어떻게 해결하셨나요?", QuestionType.TECHNICAL),

				QuestionPool.of(dataEng, "ETL 파이프라인을 설계하거나 운영해본 경험을 설명해주세요. 어떤 문제를 해결하셨나요?", QuestionType.TECHNICAL),
				QuestionPool.of(dataEng, "Apache Kafka 또는 다른 메시지 큐를 활용한 실시간 데이터 처리 경험이 있으신가요?", QuestionType.TECHNICAL),
				QuestionPool.of(dataEng, "데이터 웨어하우스와 데이터 레이크의 차이점을 설명하고, 각각을 활용한 사례를 말씀해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(dataEng, "대용량 데이터 처리 시 발생한 성능 문제를 어떻게 진단하고 해결하셨나요?", QuestionType.TECHNICAL),
				QuestionPool.of(dataEng, "데이터 품질 관리를 위해 파이프라인에서 어떤 모니터링이나 검증 로직을 적용하셨나요?", QuestionType.TECHNICAL),

				QuestionPool.of(aiMl, "모델 성능 평가 지표를 선택하는 기준을 설명해주세요. 비즈니스 맥락에 따라 다르게 선택한 경험이 있나요?", QuestionType.TECHNICAL),
				QuestionPool.of(aiMl, "과적합 문제를 경험하셨나요? 어떤 방법으로 해결하셨는지 설명해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(aiMl, "모델을 프로덕션 환경에 배포하고 운영한 경험이 있으신가요? MLOps 관점에서 어떤 점을 고려하셨나요?", QuestionType.TECHNICAL),
				QuestionPool.of(aiMl, "Transformer 아키텍처의 Self-Attention 메커니즘을 설명해주세요. 실제로 활용하신 경험도 함께 말씀해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(aiMl, "불균형 데이터셋을 다루는 전략을 설명하고, 실제 프로젝트에서 어떻게 적용하셨나요?", QuestionType.TECHNICAL),

				QuestionPool.of(devops, "컨테이너와 가상 머신의 차이점을 설명하고, Docker를 실무에서 어떻게 활용하셨나요?", QuestionType.TECHNICAL),
				QuestionPool.of(devops, "Kubernetes를 사용해보셨나요? 핵심 컴포넌트와 실제 운영 경험을 설명해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(devops, "무중단 배포를 구현해본 경험이 있으신가요? Blue-Green, Canary 등 어떤 전략을 사용하셨나요?", QuestionType.TECHNICAL),
				QuestionPool.of(devops, "IaC(Infrastructure as Code) 도구(Terraform, Ansible 등)를 사용해본 경험을 설명해주세요.", QuestionType.TECHNICAL),
				QuestionPool.of(devops, "프로덕션 장애를 경험하셨나요? 어떻게 대응하셨고 사후에 어떤 개선을 하셨는지 말씀해주세요.", QuestionType.TECHNICAL)
		));
	}
}
