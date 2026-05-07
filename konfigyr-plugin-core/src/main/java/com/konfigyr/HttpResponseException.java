package com.konfigyr;

import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Exception that is thrown when a non-ok HTTP response is received.
 *
 * @author : vladimir.spasic@ebf.com
 * @since : 02.10.22, Sun
 **/
public class HttpResponseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -5997352612003235143L;

    /**
     * The actual HTTP request that caused the exception.
     */
    private final HttpRequest request;

    /**
     * The actual HTTP response that contains the error response body.
     */
    private final HttpResponse<String> response;

    /**
     * Creates a new instance of {@link HttpResponseException} from the incoming {@link HttpRequest} and
     * {@link HttpResponse}.
     *
     * @param message the exception message, can be {@literal null}.
     * @param request the HTTP request that caused the exception, cannot be {@literal null}.
     * @param response the HTTP response that caused the exception, cannot be {@literal null}.
     */
    public HttpResponseException(String message, HttpRequest request, HttpResponse<String> response) {
        super(message);
        this.request = request;
        this.response = response;
    }

    /**
     * The actual HTTP request that caused the exception.
     *
     * @return the HTTP request, never {@literal null}.
     */
    @NonNull
    public HttpRequest getRequest() {
        return request;
    }

    /**
     * The actual HTTP response that caused the exception.
     *
     * @return the HTTP response, never {@literal null}.
     */
    @NonNull
    public HttpResponse<String> getResponse() {
        return response;
    }

    /**
     * The HTTP response status code extracted from the {@link HttpResponse}.
     *
     * @return the HTTP status code, never {@literal null}.
     */
    public int getStatus() {
        return response.statusCode();
    }

}
