package com.konfigyr;

import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.json.JSONWriter;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 02.10.22, Sun
 **/
@RequiredArgsConstructor
final class HttpClientConfigurationMetadataUploader implements ConfigurationMetadataUploader {

    private final Logger logger;
    private final HttpHost host;
    private final HttpClient client;
    private final List<Supplier<ConfigurationMetadata>> sources;

    @Nonnull
    @Override
    public List<ConfigurationMetadata> upload() {
        return sources.stream()
                .map(this::upload)
                .collect(Collectors.toList());
    }

    private ConfigurationMetadata upload(@Nonnull Supplier<ConfigurationMetadata> factory) {
        final ConfigurationMetadata metadata;

        try {
            metadata = factory.get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate configuration metadata", e);
        }

        final Artifact artifact = metadata.getArtifact();

        if (logger.isDebugEnabled()) {
            logger.debug("Successfully parsed {} configuration properties for {}", metadata.size(), artifact);
        }

        final URI uri;

        try {
            uri = new URIBuilder()
                    .setHttpHost(host)
                    .appendPath("upload")
                    .appendPath(artifact.getGroup())
                    .appendPath(artifact.getName())
                    .appendPath(artifact.getVersion())
                    .build();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to generate configuration metadata upload URI for: " + artifact, e);
        }

        final byte[] payload;

        try {
            payload = JSONWriter.valueToString(metadata.getMetadata())
                    .getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate configuration metadata JSON payload for: " + artifact, e);
        }

        final HttpEntity entity = EntityBuilder.create()
                .setContentType(ContentType.APPLICATION_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setBinary(payload)
                .build();

        final HttpPost request = new HttpPost(uri);
        request.setAbsoluteRequestUri(true);
        request.setEntity(entity);

        if (logger.isDebugEnabled()) {
            logger.debug("Executing upload configuration request against URI: {}", request.getRequestUri());
        }

        final HttpResponse response;

        try {
            response = client.execute(host, request);
        } catch (IOException e) {
            throw new IllegalStateException("Fail to execute Konfigyr Upload REST API request against " + host, e);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Konfigyr Host {} responded with status code {} and body {}",
                    host, response.getCode(), extractResponse(response));
        }

        if (response.getCode() == HttpStatus.SC_UNAUTHORIZED) {
            throw new HttpResponseException("Invalid Konfigyr Access Token provided. Please check your access token " +
                    "and try again.", response);
        }

        if (response.getCode() == HttpStatus.SC_FORBIDDEN) {
            throw new HttpResponseException("Your Konfigyr Access Token does not have a permission to " +
                    "upload Spring configuration metadata. Please check your access token and try again.", response);
        }

        if (response.getCode() >= 500) {
            throw new HttpResponseException("Konfigyr Upload REST API return a 5xx HTTP Status code with a following " +
                    "error response: " + extractResponse(response), response);
        }

        if (response.getCode() >= 400) {
            throw new HttpResponseException("Konfigyr Upload REST API return a 4xx HTTP Status code with a following " +
                    "error response: " + extractResponse(response), response);
        }

        return metadata;
    }

    private static String extractResponse(HttpResponse response) {
        if (response instanceof HttpEntityContainer) {
            final HttpEntity entity = ((HttpEntityContainer) response).getEntity();

            try {
                return EntityUtils.toString(entity);
            } catch (IOException | ParseException e) {
                return "Failed to extract HTTP Response: " + e;
            }
        }

        return null;
    }
}
