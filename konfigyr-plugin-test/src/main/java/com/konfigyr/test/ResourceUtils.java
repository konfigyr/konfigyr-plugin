package com.konfigyr.test;

import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.NonNull;
import org.junit.platform.commons.function.Try;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

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

    @NonNull
    public static Resource loadResource(String location) throws IOException {
        return Try.call(() -> loader.getResource(location))
                .andThenTry(resource -> resource != null && resource.exists() ? resource : null)
                .getNonNullOrThrow(ex -> new IOException("Could not load resource from location: " + location, ex));
    }

    @NonNull
    public static String readResource(String location) throws IOException {
        return IOUtils.toString(loadResource(location).getInputStream(), StandardCharsets.UTF_8);
    }

    @NonNull
    public static Iterable<Resource> loadResources(String... locations) throws IOException {
        final List<Resource> resources = new ArrayList<>();

        for (String location : locations) {
            resources.add(loadResource(location));
        }

        return Collections.unmodifiableList(resources);
    }

}
