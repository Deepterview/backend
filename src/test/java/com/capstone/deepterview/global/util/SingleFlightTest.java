package com.capstone.deepterview.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SingleFlightTest {

	private static final int CALLERS = 5;

	@Test
	@DisplayName("같은 키로 동시에 호출하면 작업은 1번만 실행되고 모두 같은 결과를 받는다")
	void concurrentCallsShareSingleExecution() throws Exception {
		SingleFlight<Long, String> flight = new SingleFlight<>();
		AtomicInteger executions = new AtomicInteger();
		CountDownLatch ownerStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		ExecutorService pool = Executors.newFixedThreadPool(CALLERS);
		try {
			List<Future<String>> futures = new ArrayList<>();
			futures.add(pool.submit(() -> flight.execute(1L, () -> {
				executions.incrementAndGet();
				ownerStarted.countDown();
				await(release);
				return "result";
			})));
			ownerStarted.await(5, TimeUnit.SECONDS);

			for (int i = 1; i < CALLERS; i++) {
				futures.add(pool.submit(() -> flight.execute(1L, () -> {
					executions.incrementAndGet();
					return "other";
				})));
			}
			Thread.sleep(200);
			release.countDown();

			for (Future<String> f : futures) {
				assertThat(f.get(5, TimeUnit.SECONDS)).isEqualTo("result");
			}
			assertThat(executions.get()).isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	@DisplayName("작업이 예외를 던지면 대기 중인 호출도 같은 예외를 받는다")
	void waitersReceiveOwnerException() throws Exception {
		SingleFlight<Long, String> flight = new SingleFlight<>();
		CountDownLatch ownerStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<String> owner = pool.submit(() -> flight.execute(1L, () -> {
				ownerStarted.countDown();
				await(release);
				throw new IllegalStateException("boom");
			}));
			ownerStarted.await(5, TimeUnit.SECONDS);
			Future<String> waiter = pool.submit(() -> flight.execute(1L, () -> "other"));
			Thread.sleep(200);
			release.countDown();

			assertThatThrownBy(() -> owner.get(5, TimeUnit.SECONDS))
					.hasCauseInstanceOf(IllegalStateException.class);
			assertThatThrownBy(() -> waiter.get(5, TimeUnit.SECONDS))
					.hasCauseInstanceOf(IllegalStateException.class);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	@DisplayName("작업이 끝난 뒤 같은 키로 다시 호출하면 새로 실행한다")
	void runsAgainAfterCompletion() {
		SingleFlight<Long, Integer> flight = new SingleFlight<>();
		AtomicInteger executions = new AtomicInteger();

		flight.execute(1L, executions::incrementAndGet);
		flight.execute(1L, executions::incrementAndGet);

		assertThat(executions.get()).isEqualTo(2);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
