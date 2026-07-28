package com.konfigyr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class CacheTest {

    @Test
    @DisplayName("should return null for an absent key")
    void absentKeyReturnsNull() {
        final var cache = new Cache<String, String>(Duration.ofMinutes(1));

        assertThat(cache.get("missing")).isNull();
    }

    @Test
    @DisplayName("should return a previously put value while it has not expired")
    void putThenGetReturnsValue() {
        final var cache = new Cache<String, String>(Duration.ofMinutes(1));
        cache.put("key", "value");

        assertThat(cache.get("key")).isEqualTo("value");
    }

    @Test
    @DisplayName("should return null once an entry has expired")
    void expiredEntryReturnsNull() throws InterruptedException {
        final var cache = new Cache<String, String>(Duration.ofMillis(50));
        cache.put("key", "value");
        Thread.sleep(100);

        assertThat(cache.get("key")).isNull();
    }

    @Test
    @DisplayName("should resolve each value's own time-to-live when constructed with an expiry resolver")
    void perValueExpiryResolver() throws InterruptedException {
        final var cache = new Cache<String, Duration>(value -> value);
        cache.put("short", Duration.ofMillis(50));
        cache.put("long", Duration.ofMinutes(1));

        Thread.sleep(100);

        assertThat(cache.get("short")).isNull();
        assertThat(cache.get("long")).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("should fail fast when the expiry resolver returns no duration for a value")
    void nullExpiryResolverResultFailsFast() {
        final var cache = new Cache<String, String>(value -> null);

        assertThatNullPointerException()
                .isThrownBy(() -> cache.put("key", "value"))
                .withMessageContaining("value");
    }

    @Test
    @DisplayName("should compute and cache a value via the loader when absent")
    void getWithLoaderComputesWhenAbsent() {
        final var cache = new Cache<String, String>(Duration.ofMinutes(1));
        final var invocations = new AtomicInteger();

        final String value = cache.get("key", () -> {
            invocations.incrementAndGet();
            return "loaded";
        });

        assertThat(value).isEqualTo("loaded");
        assertThat(invocations).hasValue(1);
    }

    @Test
    @DisplayName("should not invoke the loader again while the cached value has not expired")
    void getWithLoaderReusesFreshValue() {
        final var cache = new Cache<String, String>(Duration.ofMinutes(1));
        final var invocations = new AtomicInteger();

        cache.get("key", () -> {
            invocations.incrementAndGet();
            return "loaded";
        });
        cache.get("key", () -> {
            invocations.incrementAndGet();
            return "loaded-again";
        });

        assertThat(invocations).hasValue(1);
    }

    @Test
    @DisplayName("should invoke the loader again once the cached value has expired")
    void getWithLoaderRecomputesAfterExpiry() throws InterruptedException {
        final var cache = new Cache<String, String>(Duration.ofMillis(50));
        final var invocations = new AtomicInteger();

        cache.get("key", () -> {
            invocations.incrementAndGet();
            return "loaded";
        });

        Thread.sleep(100);

        cache.get("key", () -> {
            invocations.incrementAndGet();
            return "loaded-again";
        });

        assertThat(invocations).hasValue(2);
    }

    @Test
    @DisplayName("should only invoke the loader once for the same key under concurrent access")
    void getWithLoaderIsAtomicPerKey() throws InterruptedException {
        final var cache = new Cache<String, String>(Duration.ofMinutes(1));
        final var invocations = new AtomicInteger();
        final int threads = 20;
        final var ready = new CountDownLatch(threads);
        final var start = new CountDownLatch(1);
        final var done = new CountDownLatch(threads);

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        cache.get("key", () -> {
                            invocations.incrementAndGet();
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return "loaded";
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(invocations).hasValue(1);
    }

}
