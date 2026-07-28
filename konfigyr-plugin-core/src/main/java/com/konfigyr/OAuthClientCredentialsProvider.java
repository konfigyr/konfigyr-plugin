package com.konfigyr;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

/**
 * Obtains OAuth2 access tokens for authenticating with the Konfigyr Artifactory API.
 * <p>
 * A single implementation instance is meant to be shared across every registry configured for a
 * build: {@link Registry} is supplied per call rather than fixed at construction time, so one
 * provider serves every registry's {@link DefaultArtifactoryClient} while still caching and
 * refreshing each registry's token independently.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see DefaultOAuthClientCredentialsProvider
 */
@NullMarked
public interface OAuthClientCredentialsProvider {

    /**
     * Returns a valid OAuth2 access token for the given registry, refreshing it if necessary.
     *
     * @param registry the registry to authenticate against, cannot be {@literal null}.
     * @return Bearer token string, never {@literal null}.
     */
    @NonNull
    String getAccessToken(Registry registry);

}
