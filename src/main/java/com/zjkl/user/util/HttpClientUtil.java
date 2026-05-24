package com.zjkl.user.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * HTTP 客户端工具类
 */
@Component
public class HttpClientUtil {

    private final RestClient restClient;


    public HttpClientUtil(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 发送 POST 请求
     *
     * @param url     请求 URL
     * @param headers 请求头
     * @param body    请求体
     * @return 响应体字符串
     * @throws IOException 网络异常
     */
    public String post(String url, Map<String, String> headers, String body) throws IOException {
        try {
            return restClient.post()
                    .uri(url)
                    .headers(httpHeaders -> addHeaders(httpHeaders, headers))
                    .body(body != null ? body : "")
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new IOException("HTTP POST 请求失败：" + e.getMessage(), e);
        }
    }

    /**
     * 发送 GET 请求
     *
     * @param url     请求 URL
     * @param headers 请求头
     * @return 响应体字符串
     * @throws IOException 网络异常
     */
    public String get(String url, Map<String, String> headers) throws IOException {
        try {
            return restClient.get()
                    .uri(url)
                    .headers(httpHeaders -> addHeaders(httpHeaders, headers))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new IOException("HTTP GET 请求失败：" + e.getMessage(), e);
        }
    }

    /**
     * 发送 GET 请求并返回输入流（流式读取，适合大文件下载）
     *
     * @param url     请求 URL
     * @param headers 请求头
     * @return 响应输入流
     * @throws IOException 网络异常
     */
    public InputStream getInputStream(String url, Map<String, String> headers) throws IOException {
        try {
            return restClient.get()
                    .uri(url)
                    .headers(httpHeaders -> addHeaders(httpHeaders, headers))
                    .retrieve()
                    .body(InputStream.class);
        } catch (Exception e) {
            throw new IOException("HTTP GET 流式请求失败：" + e.getMessage(), e);
        }
    }

    /**
     * 添加请求头到 HttpHeaders
     *
     * @param httpHeaders Spring HttpHeaders 对象
     * @param headers     请求头 Map
     */
    private void addHeaders(HttpHeaders httpHeaders, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(httpHeaders::set);
        }
        // 默认设置 JSON 内容类型
        if (!httpHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        }
    }
}
