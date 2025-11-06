package com.acme;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "acme.server")
public class AcmeProperties {

    /**
     * The host for the Acme server.
     */
    private String host;

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
     * The value using the enumeration as a value. This should privide value hints for per enumeration value.
     */
    @NotNull
    private EnumeratedOptions enumeratedOption = EnumeratedOptions.OPTION_1;

    /**
     * The value using the enumeration as a value in a collection. This should privide value hints
     * for per enumeration value.
     */
    private List<EnumeratedOptions> enumeratedOptions = List.of(EnumeratedOptions.OPTION_1);

    /**
     * The map using enumeration as a map key. This should produce a property definition per enumeration.
     */
    private Map<EnumeratedKey, EnumeratedValue> enumeratedMap;

    @NestedConfigurationProperty
    private Nested nested = new Nested();

    @DeprecatedConfigurationProperty(reason = "Deprecation reason", replacement = "acme.server.hostname")
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

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

    public Nested getNested() {
        return nested;
    }

    public void setNested(Nested nested) {
        this.nested = nested;
    }

    public EnumeratedOptions getEnumeratedOption() {
        return enumeratedOption;
    }

    public void setEnumeratedOption(EnumeratedOptions enumeratedOption) {
        this.enumeratedOption = enumeratedOption;
    }

    public List<EnumeratedOptions> getEnumeratedOptions() {
        return enumeratedOptions;
    }

    public void setEnumeratedOptions(List<EnumeratedOptions> enumeratedOptions) {
        this.enumeratedOptions = enumeratedOptions;
    }

    public Map<EnumeratedKey, EnumeratedValue> getEnumeratedMap() {
        return enumeratedMap;
    }

    public void setEnumeratedMap(Map<EnumeratedKey, EnumeratedValue> enumeratedMap) {
        this.enumeratedMap = enumeratedMap;
    }

    public enum EnumeratedOptions {
        OPTION_1, OPTION_2, OPTION_3;
    }

    public enum EnumeratedKey {
        VALUE_1, VALUE_2, VALUE_3;
    }

    public static class EnumeratedValue {
        /**
         * The value used by the enumerated value.
         */
        @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Value must be alphanumeric")
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class Nested {

        /**
         * Name used by the nested property.
         */
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

}
