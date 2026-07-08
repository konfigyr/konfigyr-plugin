package com.konfigyr;

import org.jspecify.annotations.NullMarked;

import java.io.Serializable;

/**
 * Credentials used to authenticate with the Konfigyr Identity Provider and obtain an OAuth2 access
 * token for the Konfigyr Artifactory API.
 * <p>
 * This is a discriminated union of every OAuth2 grant type the Konfigyr Identity Provider accepts:
 * <ul>
 *     <li>{@link ClientCredentials}: the OAuth2 {@code client_credentials} grant, authenticating
 *     directly as a client using a long-lived {@code client_id}/{@code client_secret} pair.</li>
 *     <li>{@link TokenExchange}: the OAuth2 Token Exchange grant, exchanging an existing subject
 *     token (for example, an OIDC identity token issued by a CI/CD provider) for a Konfigyr-scoped
 *     access token, without requiring a long-lived client secret.</li>
 * </ul>
 * <p>
 * Every grant type is authenticated under a {@link #clientId()}, identifying which OAuth2 client is
 * requesting the access token, regardless of which grant is actually used to obtain it.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see ClientCredentials
 * @see TokenExchange
 */
@NullMarked
public sealed interface Credentials extends Serializable permits ClientCredentials, TokenExchange {

    /**
     * The OAuth2 {@code client_id} identifying the client requesting the access token.
     *
     * @return the OAuth2 client identifier, never {@literal null}.
     */
    String clientId();

}
