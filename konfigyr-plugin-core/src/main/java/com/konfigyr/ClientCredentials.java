package com.konfigyr;

import org.jspecify.annotations.NullMarked;

import java.io.Serial;
import java.util.Objects;

/**
 * {@link Credentials} for the OAuth2 {@code client_credentials} grant, as defined by
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4">RFC 6749, Section 4.4</a>.
 * <p>
 * This grant authenticates directly as the client identified by {@code clientId}, using a long-lived
 * {@code clientSecret} known only to the client and the Konfigyr Identity Provider. It is the
 * simplest way to authenticate machine-to-machine, but requires the client secret to be stored and
 * distributed securely wherever the client runs.
 *
 * @param clientId     the OAuth2 {@code client_id} identifying the client, never {@literal null}.
 * @param clientSecret the OAuth2 {@code client_secret} known only to the client and the identity
 *                     provider, never {@literal null}.
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see Credentials
 */
@NullMarked
public record ClientCredentials(String clientId, String clientSecret) implements Credentials {

    @Serial
    private static final long serialVersionUID = 3319420792259400921L;

    public ClientCredentials {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(clientSecret, "clientSecret must not be null");
    }

}
