package com.konfigyr;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.konfigyr.test.TestFactories;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

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
    }

    @Test
    void shouldFailDueToAuthenticationException(WireMockRuntimeInfo wireMock) throws Exception {
        final ConfigurationMetadataUploader uploader = create(wireMock, "invalid-access-token");

        assertThatThrownBy(uploader::upload)
                .isInstanceOf(HttpResponseException.class)
                .extracting(HttpResponseException.class::cast)
                .returns(HttpStatus.SC_UNAUTHORIZED, HttpResponseException::getStatus);

        wireMock.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
    }

    @Test
    void shouldFailDueToPermissionException(WireMockRuntimeInfo wireMock) throws Exception {
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
}