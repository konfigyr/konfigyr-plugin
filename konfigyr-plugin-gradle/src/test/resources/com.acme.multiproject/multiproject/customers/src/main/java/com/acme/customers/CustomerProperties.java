package com.acme;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "acme.customers")
public class CustomerProperties {

    /**
     * Duration specifying how long the customer can be inactive.
     */
    private Duration expiration = Duration.ofDays(30);

    public Duration getExpiration() {
        return expiration;
    }

    public void setExpiration(Duration expiration) {
        this.expiration = expiration;
    }
}
