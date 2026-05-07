package com.acme;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acme.orders")
public class OrderProperties {

    /**
     * Minimum order value, defaults to {@code 10}.
     */
    private int lowestThreshold = 10;

    public int getLowestThreshold() {
        return lowestThreshold;
    }

    public void setLowestThreshold(int lowestThreshold) {
        this.lowestThreshold = lowestThreshold;
    }
}
