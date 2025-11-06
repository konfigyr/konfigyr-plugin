package com.konfigyr.test;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "konfigyr.server")
public class TestApplicationProperties {

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
     * The value using the enumeration as a value. This should provide value hints for per enumeration value.
     */
    @NotNull
    private EnumeratedOptions enumeratedOption = EnumeratedOptions.OPTION_1;

    /**
     * The value using the enumeration as a value in a collection. This should provide value hints
     * for per enumeration value.
     */
    private List<EnumeratedOptions> enumeratedOptions = List.of(EnumeratedOptions.OPTION_1);

    /**
     * The map using enumeration as a map key. This should produce a property definition per enumeration.
     */
    private Map<EnumeratedKey, EnumeratedValue> enumeratedMap;

    /**
     * Property that would trigger {@code NoClassDefFoundError} for missing {@code EmbeddedDatabaseType} type.
     */
    private EmbeddedDatabaseConnection embeddedDatabaseConnection;

    /**
     * Property that would trigger {@code NoClassDefFoundError} for missing {@code Saml2MessageBinding} type.
     */
    private Map<String, Saml2RelyingPartyProperties.AssertingParty> assertingParties;

    /**
     * Dynamic configuration properties.
     */
    private Map<String, DynamicConfigurationType> dynamic;

    @NestedConfigurationProperty
    private Nested nested = new Nested();

    @DeprecatedConfigurationProperty(reason = "Deprecation reason", replacement = "acme.server.hostname")
    public String getHost() {
        return host;
    }

    public enum EnumeratedOptions {
        OPTION_1, OPTION_2, OPTION_3;
    }

    public enum EnumeratedKey {
        VALUE_1, VALUE_2, VALUE_3;
    }

    @Data
    public static class EnumeratedValue {
        /**
         * The value used by the enumerated value.
         */
        @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Value must be alphanumeric")
        private String value;
    }

    @Data
    public static final class Nested {

        /**
         * Duration used by the nested property.
         */
        private Duration duration = Duration.ofSeconds(10);
    }

}
