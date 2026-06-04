package com.zjkl.user.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

/**
 * HTTP 客户端工具类（含 SSRF 防护）
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
        validateUrl(url);
        try {
            return restClient.post()
                    .uri(url)
                    .headers(httpHeaders -> {
                        addHeaders(httpHeaders, headers);
                        // POST 请求设置 JSON 内容类型
                        if (!httpHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
                            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                        }
                    })
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
        validateUrl(url);
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
        validateUrl(url);
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
     * SSRF 防护：校验 URL 不允许访问内网/私有 IP 地址
     */
    private void validateUrl(String url) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IOException("URL 不能为空");
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IOException("仅支持 HTTP(S) 协议");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IOException("URL 缺少合法主机名");
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isPrivateOrLocalAddress(address)) {
                    throw new IOException("不允许访问内网或本地地址: " + host);
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("URL 校验失败: " + e.getMessage(), e);
        }
    }

    private boolean isPrivateOrLocalAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            // fc00::/7 — IPv6 Unique Local Address
            return bytes.length == 16 && (bytes[0] & (byte) 0xFE) == (byte) 0xFC;
        }
        return false;
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
    }
}
