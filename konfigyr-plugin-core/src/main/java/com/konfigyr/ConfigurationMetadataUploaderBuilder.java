package com.konfigyr;

import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 02.10.22, Sun
 **/
@RequiredArgsConstructor(staticName = "create")
public final class ConfigurationMetadataUploaderBuilder {

    /**
     * Host where the Konfigyr server is reachable, can't be {@code null}.
     */
    private HttpHost host;

    /**
     * Konfgyr Access token used to authenticate the metadata upload requests, can't be {@code null}.
     */
    private String token;

    /**
     * Logger instance to be used by the {@link ConfigurationMetadataUploader} instance.
     */
    private Logger logger = LoggerFactory.getLogger(ConfigurationMetadataUploader.class);

    /**
     * Artifacts and their respective configuration property metadata locations that should be
     * uploaded to the defined Konfigyre instance defined by the {@link #host} parameter.
     */
    private final MultiValueMap<Artifact, File> artifacts = new LinkedMultiValueMap<>();

    /**
     * Class loader instance that would be used to resolve classes defined in the Spring generated
     * configuration property metadata definitions. Defaults to the current class loader which may
     * cause issues when reading classes which are not part of the plugin classpath.
     */
    private ClassLoader classLoader;

    public ConfigurationMetadataUploaderBuilder host(String host) {
        Assert.hasText(host, "Konfigyr host can not be blank");

        try {
            this.host = HttpHost.create(host);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid host", e);
        }

        return this;
    }

    public ConfigurationMetadataUploaderBuilder token(String token) {
        Assert.notNull(token, "Konfigyr Access Token can not be null");
        this.token = token;
        return this;
    }

    public ConfigurationMetadataUploaderBuilder logger(Logger logger) {
        Assert.notNull(logger, "Logger can not be null");
        this.logger = logger;
        return this;
    }

    public ConfigurationMetadataUploaderBuilder artifact(Artifact artifact, File... metadata) {
        return artifact(artifact, Arrays.asList(metadata));
    }

    public ConfigurationMetadataUploaderBuilder artifact(Artifact artifact, Iterable<File> metadata) {
        Assert.notNull(artifact, "Artifact can not be null");
        Assert.notNull(metadata, "Artifact metadata files can not be null");

        metadata.forEach(it -> artifacts.add(artifact, it));

        return this;
    }

    public ConfigurationMetadataUploaderBuilder classpath(@Nonnull Iterable<File> files) {
        return classpath(ClassUtils.getDefaultClassLoader(), files);
    }

    public ConfigurationMetadataUploaderBuilder classpath(@Nullable ClassLoader parent, @Nonnull Iterable<File> files) {
        final URL[] classpath = StreamSupport.stream(files.spliterator(), false)
                .map(file -> {
                    try {
                        return new URL("jar:file:" + file.getAbsolutePath() + "!/");
                    } catch (MalformedURLException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray(URL[]::new);

        classLoader = new URLClassLoader(classpath, parent);

        return this;
    }

    public ConfigurationMetadataUploader build() {
        Assert.notNull(host, "Konfigyr host can not be null");
        Assert.notNull(token, "Konfigyr Access Token can not be null");

        final HttpClient client = HttpClientBuilder.create()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .setUserAgent("Konfigyr-Plugin")
                .setDefaultHeaders(Arrays.asList(
                        new BasicHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token),
                        new BasicHeader(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8),
                        new BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, Locale.US)
                ))
                .build();

        final ConfigurationMetadataParser parser = new ConfigurationMetadataParser(classLoader);

        final List<Supplier<ConfigurationMetadata>> metadata = artifacts.entrySet().stream()
                .map(entry -> (Supplier<ConfigurationMetadata>) () -> parser.parse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        return new HttpClientConfigurationMetadataUploader(logger, host, client, metadata);
    }

}
