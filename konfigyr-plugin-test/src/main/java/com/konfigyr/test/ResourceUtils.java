package com.konfigyr.test;

import org.jspecify.annotations.NonNull;
import org.junit.platform.commons.function.Try;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import wiremock.org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resource utilities used by JUnit tests.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 **/
public final class ResourceUtils {

    private static final ResourceLoader loader = new DefaultResourceLoader();

    private ResourceUtils() {
        // noop
    }

    /**
     * Loads a {@link Resource} from the specified location using the Spring {@link ResourceLoader}.
     * <p>
     * The location can be a classpath location (e.g., "classpath:test.json"), file system location
     * (e.g., "file:/path/to/file"), or any other location format supported by Spring's ResourceLoader.
     *
     * @param location the resource location, cannot be {@literal null}
     * @return the loaded resource, never {@literal null}
     * @throws IOException if the resource cannot be loaded or does not exist
     */
    @NonNull
    public static Resource loadResource(String location) throws IOException {
        return Try.call(() -> loader.getResource(location))
                .andThenTry(resource -> resource != null && resource.exists() ? resource : null)
                .getNonNullOrThrow(ex -> new IOException("Could not load resource from location: " + location, ex));
    }

    /**
     * Reads the content of a resource from the specified location as a UTF-8 encoded string.
     * <p>
     * This method combines {@link #loadResource(String)} and reading the resource content
     * into a single convenient operation.
     *
     * @param location the resource location, cannot be {@literal null}
     * @return the resource content as a string, never {@literal null}
     * @throws IOException if the resource cannot be loaded, read, or does not exist
     */
    @NonNull
    public static String readResource(String location) throws IOException {
        return IOUtils.toString(loadResource(location).getInputStream(), StandardCharsets.UTF_8);
    }

    /**
     * Loads multiple {@link Resource}s from the specified locations.
     * <p>
     * All resources are loaded using {@link #loadResource(String)} and returned as an
     * unmodifiable collection.
     *
     * @param locations the resource locations, cannot be {@literal null}
     * @return an iterable of loaded resources, never {@literal null}
     * @throws IOException if any of the resources cannot be loaded or do not exist
     */
    @NonNull
    public static Iterable<? extends Resource> loadResources(String... locations) throws IOException {
        final List<Resource> resources = new ArrayList<>();

        for (String location : locations) {
            resources.add(loadResource(location));
        }

        return Collections.unmodifiableList(resources);
    }

}
