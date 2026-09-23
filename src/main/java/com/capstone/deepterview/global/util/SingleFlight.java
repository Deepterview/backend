package com.capstone.deepterview.global.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SingleFlight<K, V> {

	private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

	public V execute(K key, Supplier<V> task) {
		CompletableFuture<V> mine = new CompletableFuture<>();
		CompletableFuture<V> existing = inFlight.putIfAbsent(key, mine);
		if (existing != null) {
			return join(existing);
		}

		try {
			V result = task.get();
			mine.complete(result);
			return result;
		} catch (RuntimeException | Error e) {
			mine.completeExceptionally(e);
			throw e;
		} finally {
			inFlight.remove(key, mine);
		}
	}

	private V join(CompletableFuture<V> future) {
		try {
			return future.join();
		} catch (CompletionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException re) {
				throw re;
			}
			if (cause instanceof Error err) {
				throw err;
			}
			throw e;
		}
	}
}
