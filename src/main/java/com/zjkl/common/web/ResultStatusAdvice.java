package com.zjkl.common.web;

import com.zjkl.common.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
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
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?> result && response instanceof ServletServerHttpResponse servletResponse) {
            Integer code = result.getCode();
            if (code != null && code >= 400) {
                HttpStatus status = HttpStatus.resolve(code);
                servletResponse.getServletResponse().setStatus(
                        status != null ? status.value() : HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
        }
        return body;
    }
}
