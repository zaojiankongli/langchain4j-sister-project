package com.zjkl.recommendation.assistant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zjkl.recommendation.util.JsonUtils;
import com.zjkl.recommendation.util.RecommendationConstants;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Non-AI Agent: 为缺少 imageUrl 的推荐条目抓取配图
 * 通过 HTTP GET 请求解析 HTML 页面的 OG:image / Twitter:image
 * 零 Token 消耗，纯 Java 操作
 */
@Slf4j
public class ImageUrlFetcher {

    private static final int MAX_FETCH_COUNT = 10;
    private static final int HTTP_TIMEOUT_MS = 5000;

    private static final Pattern OG_IMAGE_PATTERN = Pattern.compile(
            "<meta\\s+[^>]*property=\"(?:og:image|twitter:image)\"[^>]*content=\"([^\"]+)\"[^>]*>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OG_IMAGE_ALT_PATTERN = Pattern.compile(
            "<meta\\s+[^>]*content=\"([^\"]+)\"[^>]*property=\"(?:og:image|twitter:image)\"[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    @Agent(value = "为缺少 imageUrl 的推荐条目抓取 OG:image 配图",
            outputKey = RecommendationConstants.OUTPUT_KEY_PASSING_RECOMMENDATIONS)
    public String fetchImages(@V(RecommendationConstants.OUTPUT_KEY_PASSING_RECOMMENDATIONS) String passingJson) {
        JsonArray arr = JsonUtils.parseJsonArray(passingJson);
        if (arr.isEmpty()) {
            return passingJson;
        }

        // 收集需要抓图的条目
        List<JsonObject> toFetch = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            String imageUrl = obj.has("imageUrl") ? obj.get("imageUrl").getAsString() : "";
            if (imageUrl != null && !imageUrl.isBlank()) continue;
            String url = obj.has("url") ? obj.get("url").getAsString() : "";
            if (!url.isBlank()) toFetch.add(obj);
        }

        // 虚拟线程并行抓取（最多 MAX_FETCH_COUNT 条）
        if (!toFetch.isEmpty()) {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<String>> futures = new ArrayList<>();
                for (int i = 0; i < Math.min(toFetch.size(), MAX_FETCH_COUNT); i++) {
                    final JsonObject obj = toFetch.get(i);
                    futures.add(executor.submit(() ->
                            fetchOgImage(obj.get("url").getAsString())));
                }
                for (int i = 0; i < futures.size(); i++) {
                    try {
                        String ogImage = futures.get(i).get(HTTP_TIMEOUT_MS + 2000, TimeUnit.MILLISECONDS);
                        if (ogImage != null && !ogImage.isBlank()) {
                            toFetch.get(i).addProperty("imageUrl", ogImage);
                        }
                    } catch (Exception e) {
                        log.debug("ImageUrlFetcher 并行抓取超时: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("ImageUrlFetcher 并行执行异常", e);
            }
        }

        log.info("ImageUrlFetcher 完成: 共 {} 条, 需抓图 {} 条", arr.size(), toFetch.size());
        return JsonUtils.toJson(arr);
    }

    /**
     * 通过 HTTP GET 请求页面 HTML，提取 OG:image 或 Twitter:image
     */
    private String fetchOgImage(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URI uri = new URI(urlStr);
            validateRemoteUrl(uri);
            URL url = uri.toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; ZjklBot/1.0)");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);

            int responseCode = conn.getResponseCode();
            if (responseCode >= 300 && responseCode < 400) {
                log.debug("ImageUrlFetcher 重定向跳过: urlLength={}", urlStr != null ? urlStr.length() : 0);
                return null;
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                log.debug("ImageUrlFetcher HTTP {} 跳过: urlLength={}", responseCode, urlStr != null ? urlStr.length() : 0);
                return null;
            }

            String contentType = conn.getContentType();
            if (contentType != null && !contentType.toLowerCase().contains("text/html")) {
                log.debug("ImageUrlFetcher 非 HTML 内容跳过: {}", contentType);
                return null;
            }

            StringBuilder html = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    html.append(line);
                    if (html.length() > 100_000) {
                        break; // 只检查前 100KB
                    }
                }
            }

            String htmlStr = html.toString();
            Matcher matcher = OG_IMAGE_PATTERN.matcher(htmlStr);
            if (matcher.find()) {
                return matcher.group(1);
            }

            matcher = OG_IMAGE_ALT_PATTERN.matcher(htmlStr);
            if (matcher.find()) {
                return matcher.group(1);
            }

            return null;

        } catch (Exception e) {
            log.debug("ImageUrlFetcher 抓取失败: urlLength={}, error={}", urlStr != null ? urlStr.length() : 0, e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void validateRemoteUrl(URI uri) throws Exception {
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("unsupported scheme");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("missing host");
        }

        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (isLocalOrPrivateAddress(address)) {
                throw new IllegalArgumentException("local/private address blocked");
            }
        }
    }

    private boolean isLocalOrPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }

        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            return bytes.length == 16 && (bytes[0] & (byte) 0xFE) == (byte) 0xFC;
        }

        return false;
    }
}
