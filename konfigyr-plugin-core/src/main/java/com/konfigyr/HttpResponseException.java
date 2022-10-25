package com.konfigyr;

import org.apache.hc.core5.http.HttpResponse;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 02.10.22, Sun
 **/
public class HttpResponseException extends RuntimeException {
    private static final long serialVersionUID = -5997352612003235143L;

    private final HttpResponse response;

    public HttpResponseException(String message, HttpResponse response) {
        super(message);
        this.response = response;
    }

    public HttpResponseException(String message, HttpResponse response, Throwable cause) {
        super(message, cause);
        this.response = response;
    }

    public int getStatus() {
        return response.getCode();
    }

}
