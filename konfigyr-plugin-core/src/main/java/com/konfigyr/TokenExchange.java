package com.konfigyr;

import org.jspecify.annotations.NullMarked;

import java.io.Serial;
import java.util.Objects;

/**
 * {@link Credentials} for the OAuth2 Token Exchange grant, as defined by
 * <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693</a>.
 * <p>
 * This grant exchanges an existing {@code subjectToken}, for example, an OIDC identity token issued
 * to a CI/CD job by its provider — for a Konfigyr-scoped access token, identifying the client as
 * {@code clientId}. Unlike {@link ClientCredentials}, it does not require a long-lived client secret
 * to be stored and distributed wherever the client runs, since the subject token is typically short-lived
 * and issued fresh for every build.
 *
 * @param clientId         the OAuth2 {@code client_id} identifying the client, never {@literal null}.
 * @param subjectToken     the token being exchanged for an access token, never {@literal null}.
 * @param subjectTokenType an identifier, as defined by RFC 8693, for the type of the
 *                         {@code subjectToken} (for example {@code urn:ietf:params:oauth:token-type:jwt}
 *                         or {@code urn:ietf:params:oauth:token-type:id_token}), never {@literal null}.
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see Credentials
 */
@NullMarked
public record TokenExchange(String clientId, String subjectToken, String subjectTokenType) implements Credentials {

    @Serial
    private static final long serialVersionUID = 3399240925753860590L;

    public TokenExchange {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(subjectToken, "subjectToken must not be null");
        Objects.requireNonNull(subjectTokenType, "subjectTokenType must not be null");
    }

    /**
     * Creates a new instance of the {@link TokenExchange} with a {@code urn:ietf:params:oauth:token-type:jwt}
     * subject token type.
     *
     * @param clientId     the OAuth2 {@code client_id} identifying the client, never {@literal null}.
     * @param subjectToken the token being exchanged for an access token, never {@literal null}.
     */
    public TokenExchange(String clientId, String subjectToken) {
        this(clientId, subjectToken, "urn:ietf:params:oauth:token-type:jwt");
    }
}
