package com.acme;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "acme.core")
public class CoreProperties {

    /**
     * The hostname for the Acme server.
     */
    @NotEmpty
    private String hostname;

    /**
     * The port number used by the Acme server. Defaults to 8080.
     */
    @NotNull
    @Positive
    private Integer port = 8080;

    /**
     * The maximum upload size.
     */
    private DataSize uploadSize = DataSize.ofMegabytes(1);

    /**
     * The ssl bundle location.
     */
    private Resource ssl;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public DataSize getUploadSize() {
        return uploadSize;
    }

    public void setUploadSize(DataSize uploadSize) {
        this.uploadSize = uploadSize;
    }

    public Resource getSsl() {
        return ssl;
    }

    public void setSsl(Resource ssl) {
        this.ssl = ssl;
    }
}
