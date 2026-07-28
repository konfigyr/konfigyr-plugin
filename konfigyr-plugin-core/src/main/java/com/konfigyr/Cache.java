package com.konfigyr;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple thread-safe, in-process cache where every entry shares the same time-to-live, measured
 * from the moment it was put into the cache.
 * <p>
 * There is no eviction beyond expiry, entries are only ever removed lazily, the next time they're
 * looked up after expiring. This is intentional: instances of this cache are meant to live for the
 * lifetime of a single JVM (e.g., a Gradle daemon), holding a handful of small, infrequently changing
 * entries, not a general-purpose bounded cache.
 *
 * @param <K> the key type
 * @param <V> the value type
 * @author Vladimir Spasic
 * @since 1.2.0
 */
@NullMarked
public final class Cache<K, V> {

    private final ConcurrentHashMap<K, CacheEntry<V>> map = new ConcurrentHashMap<>();
    private final Function<V, @Nullable Duration> expiryResolver;

    /**
     * Creates a new {@link Cache} whose entries expire after the given duration.
     *
     * @param expiry how long an entry stays valid after being put into the cache, in milliseconds.
     */
    public Cache(long expiry) {
        this(Duration.ofMillis(expiry));
    }

    /**
     * Creates a new {@link Cache} whose entries expire after the given duration.
     *
     * @param expiry how long an entry stays valid after being put into the cache, cannot be {@literal null}.
     */
    public Cache(Duration expiry) {
        this(value -> expiry);
    }

    /**
     * Creates a new {@link Cache} whose entries expire after the extracted duration
     * from the given value.
     *
     * @param expiryResolver the function to extract the duration from the value, cannot be {@literal null}.
     */
    public Cache(Function<V, @Nullable Duration> expiryResolver) {
        this.expiryResolver = expiryResolver;
    }

    /**
     * Puts the given value into the cache under the given key, replacing any value already present,
     * and resetting its expiry based on the configured {@link #expiryResolver}.
     *
     * @param key the key to associate the value with, cannot be {@literal null}.
     * @param value the value to cache, cannot be {@literal null}.
     */
    public void put(K key, V value) {
        final Duration expiry = expiryResolver.apply(value);
        map.put(key, new CacheEntry<>(value, expiry));
    }

    /**
     * Looks up the value cached under the given key.
     *
     * @param key the key to look up, cannot be {@literal null}.
     * @return the cached value, or {@literal null} if absent or expired.
     */
    @Nullable
    public V get(K key) {
        final CacheEntry<V> entry = map.get(key);

        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            map.remove(key);
            return null;
        }

        return entry.value();
    }

    /**
     * Look up the value cached under the given key, computing and caching it via the given
     * {@code loader} if it's absent or expired.
     * <p>
     * The lookup-and-load is atomic per key: concurrent calls for the same key never both run the
     * loader, unlike concurrent calls for different keys, which proceed independently. Since this
     * runs inside {@link ConcurrentHashMap}'s per-key locking, the loader should be reasonably quick
     * and must not itself call back into this same cache.
     *
     * @param key the key to look up, cannot be {@literal null}.
     * @param loader supplies the value to cache when none is present, cannot be {@literal null}.
     * @return the cached or freshly loaded value, never {@literal null}.
     */
    public V get(K key, Supplier<V> loader) {
        return map.compute(key, (k, existing) -> {
            if (existing != null && !existing.isExpired()) {
                return existing;
            }

            final V value = loader.get();

            return new CacheEntry<>(value, expiryResolver.apply(value));
        }).value();
    }

    private record CacheEntry<V>(V value, Instant expiration) {

        private CacheEntry(V value, @Nullable Duration expiry) {
            this(value, Instant.now().plus(Objects.requireNonNull(expiry,
                    () -> "Failed to resolve expiration duration for cache entry: " + value)));
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiration);
        }

    }

}
