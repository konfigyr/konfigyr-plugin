package com.konfigyr;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 02.10.22, Sun
 **/
public interface ConfigurationMetadataUploader {

    @Nonnull
    List<ConfigurationMetadata> upload();

}
