package com.konfigyr.test;

import com.konfigyr.Artifact;
import lombok.experimental.UtilityClass;
import org.junit.platform.commons.util.ClassLoaderUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 15.10.22, Sat
 **/
@UtilityClass
public class TestFactories {

    public static Artifact artifact() {
        return artifact("1.0.0");
    }

    public static Artifact artifact(String version) {
        return artifact("acme", version);
    }

    public static Artifact artifact(String name, String version) {
        return new Artifact("com.acme", name, version);
    }

    public static ClassLoader classLoader() {
        return ClassLoaderUtils.getDefaultClassLoader();
    }

    public static File loadResource(String location) throws FileNotFoundException {
        final URL resource = classLoader().getResource(location);

        if (resource == null) {
            throw new FileNotFoundException("Failed to load resource: " + location);
        }

        return new File(resource.getFile());
    }

    public static Iterable<File> loadResources(String... locations) throws FileNotFoundException {
        final List<File> resources = new ArrayList<>();

        for (String location : locations) {
            resources.add(loadResource(location));
        }

        return Collections.unmodifiableList(resources);
    }

}
