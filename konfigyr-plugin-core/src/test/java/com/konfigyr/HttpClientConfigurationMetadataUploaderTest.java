package com.konfigyr;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.konfigyr.test.TestFactories;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 02.10.22, Sun
 **/
@WireMockTest
class HttpClientConfigurationMetadataUploaderTest {

    @Test
    void shouldUploadArtifactMetadata(WireMockRuntimeInfo wireMock) throws Exception {
        registerMappingFor(wireMock, "/upload/com.acme/acme/1.0.0");
        registerMappingFor(wireMock, "/upload/com.acme/acme/1.0.1");
        registerMappingFor(wireMock, "/upload/com.acme/core/1.0.2");
        registerMappingFor(wireMock, "/upload/com.acme/spring/1.0.2");

        final ConfigurationMetadataUploader uploader = create(wireMock, "test-access-token");
        final List<ConfigurationMetadata> metadata = uploader.upload();

        assertThat(metadata)
                .isNotNull()
                .hasSize(4);

        assertThat(metadata)
                .flatExtracting(ConfigurationMetadata::getArtifact)
                .containsExactlyInAnyOrder(
                        TestFactories.artifact(),
                        TestFactories.artifact("1.0.1"),
                        TestFactories.artifact("core", "1.0.2"),
                        TestFactories.artifact("spring", "1.0.2")
                );

        wireMock.getWireMock().verifyThat(4, RequestPatternBuilder.allRequests());
        verifyMappingFor("/upload/com.acme/acme/1.0.0");
        verifyMappingFor("/upload/com.acme/acme/1.0.1");
        verifyMappingFor("/upload/com.acme/core/1.0.2");
        verifyMappingFor("/upload/com.acme/spring/1.0.2");
    }

    @Test
    void shouldFailDueToAuthenticationException(WireMockRuntimeInfo wireMock) throws Exception {
        wireMock.getWireMock().register(
                post(anyUrl())
                        .withHeader(HttpHeaders.AUTHORIZATION, equalToIgnoreCase("Bearer invalid-access-token"))
                        .withHost(equalToIgnoreCase("localhost"))
                        .withPort(wireMock.getHttpPort())
                        .withScheme("http")
                        .willReturn(
                                jsonResponse("{}", HttpStatus.SC_UNAUTHORIZED)
                        )
        );

        final ConfigurationMetadataUploader uploader = create(wireMock, "invalid-access-token");

        assertThatThrownBy(uploader::upload)
                .isInstanceOf(HttpResponseException.class)
                .extracting(HttpResponseException.class::cast)
                .returns(HttpStatus.SC_UNAUTHORIZED, HttpResponseException::getStatus);

        wireMock.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
    }

    @Test
    void shouldFailDueToPermissionException(WireMockRuntimeInfo wireMock) throws Exception {
        wireMock.getWireMock().register(
                post(anyUrl())
                        .withHeader(HttpHeaders.AUTHORIZATION, equalToIgnoreCase("Bearer forbidden-access-token"))
                        .withHost(equalToIgnoreCase("localhost"))
                        .withPort(wireMock.getHttpPort())
                        .withScheme("http")
                        .willReturn(
                                jsonResponse("{}", HttpStatus.SC_FORBIDDEN)
                        )
        );

        final ConfigurationMetadataUploader uploader = create(wireMock, "forbidden-access-token");

        assertThatThrownBy(uploader::upload)
                .isInstanceOf(HttpResponseException.class)
                .extracting(HttpResponseException.class::cast)
                .returns(HttpStatus.SC_FORBIDDEN, HttpResponseException::getStatus);

        wireMock.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
    }

    private static ConfigurationMetadataUploader create(WireMockRuntimeInfo info, String token) throws IOException  {
        return ConfigurationMetadataUploaderBuilder.create()
                .artifact(TestFactories.artifact(), TestFactories.loadResources(
                        "spring-configuration-metadata.json",
                        "additional-spring-configuration-metadata.json"
                ))
                .artifact(TestFactories.artifact("1.0.1"), TestFactories.loadResources(
                        "spring-configuration-metadata.json",
                        "additional-spring-configuration-metadata.json"
                ))
                .artifact(TestFactories.artifact("core", "1.0.2"), TestFactories.loadResources(
                        "spring-configuration-metadata.json",
                        "additional-spring-configuration-metadata.json"
                ))
                .artifact(TestFactories.artifact("spring", "1.0.2"), TestFactories.loadResources(
                        "spring-configuration-metadata.json",
                        "additional-spring-configuration-metadata.json"
                ))
                .host(info.getHttpBaseUrl())
                .token(token)
                .build();
    }



    private static void registerMappingFor(WireMockRuntimeInfo info, String path) {
        info.getWireMock().register(
                post(urlPathEqualTo(path))
                        .withHeader(HttpHeaders.AUTHORIZATION, equalToIgnoreCase("Bearer test-access-token"))
                        .withHost(equalToIgnoreCase("localhost"))
                        .withPort(info.getHttpPort())
                        .withScheme("http")
                        .willReturn(
                                okJson("{}").withUniformRandomDelay(200, 600)
                        )
        );
    }

    private static void verifyMappingFor(String path) {
        verify(RequestPatternBuilder.newRequestPattern(RequestMethod.POST, urlPathEqualTo(path)));
    }
}