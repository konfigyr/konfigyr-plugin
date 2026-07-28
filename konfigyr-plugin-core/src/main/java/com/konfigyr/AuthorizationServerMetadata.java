package com.konfigyr;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

import java.net.URI;
import java.util.Objects;

/**
 * The subset of an OAuth2 Authorization Server's metadata that the Konfigyr plugin needs, resolved
 * by {@link AuthorizationServerMetadataResolver} from a registry's {@code url}.
 *
 * @param issuer        The Authorization Server's issuer identifier, as defined by
 *                       <a href="https://datatracker.ietf.org/doc/html/rfc8414">RFC 8414</a>, never {@literal null}.
 * @param tokenEndpoint The OAuth2 token endpoint URI, never {@literal null}.
 * @author Vladimir Spasic
 * @since 1.2.0
 * @see AuthorizationServerMetadataResolver
 */
@NullMarked
record AuthorizationServerMetadata(URI issuer, URI tokenEndpoint) {

    AuthorizationServerMetadata {
        Objects.requireNonNull(issuer, "issuer must not be null");
        Objects.requireNonNull(tokenEndpoint, "tokenEndpoint must not be null");
    }

}
