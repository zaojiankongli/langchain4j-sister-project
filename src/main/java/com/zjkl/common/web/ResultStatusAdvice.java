package com.zjkl.common.web;

import com.zjkl.common.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Keeps the existing Result envelope while exposing failures through HTTP status codes.
 */
@ControllerAdvice
public class ResultStatusAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return Result.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?> result && response instanceof ServletServerHttpResponse servletResponse) {
            Integer code = result.getCode();
            if (code != null && code >= 400) {
                servletResponse.getServletResponse().setStatus(mapToHttpStatus(code));
            }
        }
        return body;
    }

    /**
     * Maps business error codes to appropriate HTTP status codes.
     * Standard HTTP codes (400-599) pass through; business domain codes are mapped by module range.
     */
    private static int mapToHttpStatus(int code) {
        // Standard HTTP status codes pass through
        if (code >= 100 && code < 600) {
            return code;
        }
        // Map business error code ranges to HTTP statuses
        return switch (code / 1000) {
            case 1 -> 401;  // Auth module → Unauthorized
            case 2 -> 422;  // Emotion engine → Unprocessable Entity
            case 3 -> 502;  // Voice/TTS → Bad Gateway
            case 4 -> 502;  // AI services → Bad Gateway
            case 5 -> 500;  // Storage/OSS → Internal Server Error
            case 6 -> 404;  // User → Not Found
            case 7 -> 500;  // Recommendation → Internal Server Error
            case 8 -> 500;  // Memory/Summary → Internal Server Error
            default -> 500; // Unknown codes → Internal Server Error
        };
    }
}
