package com.konfigyr;

import com.konfigyr.artifactory.*;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.module.SimpleModule;

final class KonfigyrJacksonModule extends SimpleModule {

    public KonfigyrJacksonModule() {
        super("konfigyr-jackson-module", tools.jackson.core.Version.unknownVersion());
    }

    @Override
    public void setupModule(SetupContext context) {
        setNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        addAbstractTypeMapping(Artifact.class, DefaultArtifact.class);
        addAbstractTypeMapping(Manifest.class, DefaultManifest.class);
        addAbstractTypeMapping(Release.class, DefaultRelease.class);
        addAbstractTypeMapping(ArtifactMetadata.class, DefaultArtifactMetadata.class);
        addAbstractTypeMapping(PropertyDescriptor.class, DefaultPropertyDescriptor.class);

        super.setupModule(context);
    }

}
