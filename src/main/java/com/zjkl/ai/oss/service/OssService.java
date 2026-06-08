package com.zjkl.ai.oss.service;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.zjkl.ai.oss.config.OssConfig;
import com.zjkl.ai.oss.util.OssObjectKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 阿里云 OSS 文件服务（基于官方最佳实践）
 * 
 * 参考文档：
 * - https://help.aliyun.com/zh/oss/developer-reference/oss-java-sdk/
 * - https://help.aliyun.com/zh/oss/user-guide/oss-sdk-quick-start
 */
@Slf4j
@Service
public class OssService {
    
    private final OSS ossClient;
    private final String bucketName;
    private final String endpoint;
    private final OssObjectKeyGenerator objectKeyGenerator = new OssObjectKeyGenerator();
    
    // 允许的头像文件扩展名
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp"
    );

    // 允许的音频文件扩展名
    private static final List<String> ALLOWED_AUDIO_EXTENSIONS = Arrays.asList(
            "mp3", "wav", "m4a", "aac", "amr", "ogg", "webm"
    );
    
    // 最大文件大小 5MB（简单上传限制）
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    
    public OssService(OSS ossClient, OssConfig ossConfig) {
        this.ossClient = ossClient;
        this.bucketName = ossConfig.getBucketName();
        this.endpoint = ossConfig.getEndpoint();
        log.info("OSS 服务初始化成功 - bucket: {}, endpoint: {}", bucketName, endpoint);
    }
    
    // ==================== 上传方法 ====================

    /**
     * 上传头像到 OSS
     */
    public String uploadAvatar(String userId, MultipartFile file) throws IOException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        validateImageFile(file, "头像");
        String objectKey = objectKeyGenerator.generateAvatarObjectKey(userId, file.getOriginalFilename());
        return uploadToOSS(file, objectKey, "头像");
    }

    /**
     * 上传任意文件到 OSS（仅允许图片）
     */
    public String uploadFile(String folder, MultipartFile file) throws IOException {
        if (folder == null || folder.isBlank()) {
            throw new IllegalArgumentException("文件夹路径不能为空");
        }
        validateImageFile(file, "文件");
        String objectKey = objectKeyGenerator.generateObjectKey(folder, file.getOriginalFilename());
        return uploadToOSS(file, objectKey, "文件");
    }

    /**
     * 上传聊天图片到 OSS
     */
    public String uploadMessageImage(MultipartFile file, String userId) throws IOException {
        validateImageFile(file, "聊天图片");
        String objectKey = objectKeyGenerator.generateMessageImageObjectKey(userId, file.getOriginalFilename());
        return uploadToOSS(file, objectKey, "聊天图片");
    }

    /**
     * 上传语音文件到 OSS
     */
    public String uploadVoice(String userId, MultipartFile file) throws IOException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        validateAudioFile(file, "语音");
        String objectKey = objectKeyGenerator.generateVoiceObjectKey(userId, file.getOriginalFilename());
        return uploadToOSS(file, objectKey, "语音");
    }

    /**
     * 从 URL 下载文件并上传到 OSS
     *
     * @param fileUrl 文件的 URL 地址
     * @param folder 文件夹路径（如：avatars, documents 等），null 则使用默认文件夹
     * @return OSS 上的文件访问 URL
     * @throws IOException 上传失败时抛出
     */
    public String uploadFromUrl(String fileUrl, String folder) throws IOException {
        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new IllegalArgumentException("文件 URL 不能为空");
        }

        URI fileUri = validateRemoteFileUrl(fileUrl);
        
        log.info("开始从远程 URL 下载并上传 - source: {}", safeUrlForLog(fileUri));
        
        HttpURLConnection connection = null;
        try {
            // 从 URL 下载文件
            URL url = new URL(fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false); // 禁用自动重定向，防止 DNS rebinding 攻击
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            validateResolvedHost(fileUri.getHost());
            
            int responseCode = connection.getResponseCode();
            validateResolvedHost(fileUri.getHost());
            // 拒绝重定向响应，避免 SSRF
            if (responseCode >= 300 && responseCode < 400) {
                throw new IllegalArgumentException("不允许重定向的文件 URL");
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("下载文件失败，HTTP 状态码：" + responseCode);
            }

            // 校验远程服务器返回的 Content-Type 必须为图片类型，防止非图片文件伪装上传
            String remoteContentType = connection.getContentType();
            if (remoteContentType == null || !remoteContentType.toLowerCase().trim().startsWith("image/")) {
                throw new IllegalArgumentException("远程 URL 返回的内容类型不是图片（Content-Type: " + remoteContentType + "）");
            }
            
            // 获取文件名
            String filename = objectKeyGenerator.extractFilenameFromUrl(fileUrl, connection);
            
            // 获取文件大小
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("文件大小不能超过 5MB");
            }
            
            try (InputStream inputStream = openBoundedStream(connection, contentLength)) {
                // 生成 ObjectKey
                String targetFolder = (folder != null && !folder.isBlank()) ? folder : "downloads";
                String objectKey = objectKeyGenerator.generateObjectKey(targetFolder, filename);
                
                ObjectMetadata metadata = createMetadata(
                    connection.getContentType(), 
                    contentLength > 0 ? contentLength : null
                );
                
                PutObjectResult result = putObject(objectKey, inputStream, metadata, "文件上传失败：");
                log.info("从 URL 上传文件成功 - bucket: {}, objectKey: {}, ETag: {}", 
                    bucketName, objectKey, result.getETag());
                
                return generateFileUrl(objectKey);
            }
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private InputStream openBoundedStream(HttpURLConnection connection, long contentLength) throws IOException {
        if (contentLength > 0) {
            return connection.getInputStream();
        }

        return new java.io.FilterInputStream(connection.getInputStream()) {
            private long bytesRead = 0;

            @Override
            public int read() throws IOException {
                int value = super.read();
                if (value != -1) {
                    bytesRead++;
                    ensureWithinLimit();
                }
                return value;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int count = super.read(b, off, len);
                if (count > 0) {
                    bytesRead += count;
                    ensureWithinLimit();
                }
                return count;
            }

            private void ensureWithinLimit() throws IOException {
                if (bytesRead > MAX_FILE_SIZE) {
                    throw new IOException("文件下载失败：文件大小不能超过 5MB");
                }
            }
        };
    }
    
    // ==================== 下载方法 ====================
    
    /**
     * 从 OSS 下载文件
     * 
     * @param objectKey 对象键
     * @return 文件输入流（调用者负责关闭）
     * @throws IOException 下载失败时抛出
     * 
     * 使用示例：
     * try (InputStream in = ossService.downloadFile(objectKey)) {
     *     // 读取流
     * }
     */
    public InputStream downloadFile(String objectKey) throws IOException {
        if (objectKey == null || objectKey.isEmpty()) {
            throw new IllegalArgumentException("ObjectKey 不能为空");
        }
        
        log.info("开始下载文件 - bucket: {}, objectKey: {}", bucketName, objectKey);
        
        try {
            OSSObject ossObject = ossClient.getObject(bucketName, objectKey);
            log.info("文件下载成功 - bucket: {}, objectKey: {}", bucketName, objectKey);
            return ossObject.getObjectContent();
            
        } catch (OSSException e) {
            log.error("OSS 错误 - Code: {}, Message: {}", e.getErrorCode(), e.getErrorMessage());
            throw new IOException("文件下载失败：" + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("客户端错误 - Message: {}", e.getMessage());
            throw new IOException("文件下载失败：" + e.getMessage(), e);
        }
    }
    
    // ==================== 删除方法 ====================
    
    /**
     * 删除 OSS 上的文件
     * 
     * @param objectKey 对象键
     */
    public void deleteFile(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            log.warn("删除文件失败 - ObjectKey 为空");
            return;
        }
        
        try {
            ossClient.deleteObject(bucketName, objectKey);
            log.info("文件删除成功 - bucket: {}, objectKey: {}", bucketName, objectKey);
        } catch (Exception e) {
            log.error("文件删除失败 - bucket: {}, objectKey: {}", bucketName, objectKey, e);
        }
    }
    
    // ==================== URL 生成方法 ====================
    
    /**
     * 生成文件访问 URL（公共读文件）
     * 
     * 如果 Bucket 是私有，需要使用 generatePresignedUrl 生成签名 URL
     */
    private String generateFileUrl(String objectKey) {
        // 格式：https://{bucket}.{endpoint}/{objectKey}
        // 从 endpoint 提取域名（去除 https:// 前缀）
        String domain = endpoint.replace("https://", "").replace("http://", "");
        // 对 objectKey 进行 URL 编码（保留 / 分隔符）
        String encodedKey = Arrays.stream(objectKey.split("/"))
            .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
            .collect(Collectors.joining("/"));
        return "https://" + bucketName + "." + domain + "/" + encodedKey;
    }
    
    /**
     * 生成签名 URL（用于私有 Bucket，带过期时间）
     * 
     * @param objectKey 对象键
     * @param expirationMinutes 过期时间（分钟）
     * @return 签名后的 URL
     */
    public String generatePresignedUrl(String objectKey, int expirationMinutes) {
        if (objectKey == null || objectKey.isEmpty()) {
            throw new IllegalArgumentException("ObjectKey 不能为空");
        }
        
        Date expiration = new Date(System.currentTimeMillis() + expirationMinutes * 60 * 1000L);
        java.net.URL url = ossClient.generatePresignedUrl(bucketName, objectKey, expiration);
        log.info("生成签名 URL 成功 - objectKey: {}, expiration: {} minutes", objectKey, expirationMinutes);
        return url.toString();
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 创建对象元数据
     */
    private ObjectMetadata createMetadata(String contentType, Long contentLength) {
        ObjectMetadata metadata = new ObjectMetadata();
        if (contentType != null && !contentType.isEmpty()) {
            metadata.setContentType(contentType);
        }
        if (contentLength != null && contentLength > 0) {
            metadata.setContentLength(contentLength);
        }
        return metadata;
    }
    
    /**
     * 上传文件到 OSS 并统一处理异常
     *
     * @param objectKey 对象键
     * @param inputStream 文件输入流
     * @param metadata 对象元数据
     * @param errorMessagePrefix 异常消息前缀
     * @return PutObjectResult
     * @throws IOException 上传失败时抛出
     */
    private PutObjectResult putObject(String objectKey, InputStream inputStream, ObjectMetadata metadata, String errorMessagePrefix) throws IOException {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
            bucketName,
            objectKey,
            inputStream,
            metadata
        );

        try {
            return ossClient.putObject(putObjectRequest);
        } catch (OSSException e) {
            log.error("OSS 错误 - Code: {}, Message: {}, RequestId: {}",
                e.getErrorCode(), e.getErrorMessage(), e.getRequestId());
            throw new IOException(errorMessagePrefix + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("客户端错误 - Message: {}", e.getMessage());
            throw new IOException(errorMessagePrefix + e.getMessage(), e);
        }
    }
    
    /**
     * 统一的图片文件校验
     */
    private void validateImageFile(MultipartFile file, String fileType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(fileType + "不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(fileType + "大小不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException(fileType + "只支持图片文件（JPG/PNG/GIF/WebP）");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException(fileType + "文件名不能为空");
        }
        String extension = objectKeyGenerator.getFileExtension(originalFilename);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException(fileType + "只支持 JPG/PNG/GIF/WebP 格式");
        }
    }

    private void validateAudioFile(MultipartFile file, String fileType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(fileType + "不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(fileType + "大小不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.startsWith("audio/") && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException(fileType + "只支持音频文件（MP3/WAV/M4A/AAC/AMR/OGG/WEBM）");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException(fileType + "文件名不能为空");
        }
        String extension = objectKeyGenerator.getFileExtension(originalFilename);
        if (!ALLOWED_AUDIO_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException(fileType + "只支持 MP3/WAV/M4A/AAC/AMR/OGG/WEBM 格式");
        }
    }

    private URI validateRemoteFileUrl(String fileUrl) {
        try {
            URI uri = URI.create(fileUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("文件 URL 仅支持 HTTP(S)");
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("文件 URL 缺少合法主机名");
            }

            validateResolvedHost(host);
            return uri;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("文件 URL 非法", e);
        }
    }

    private void validateResolvedHost(String host) throws java.net.UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (isLocalOrPrivateAddress(address)) {
                throw new IllegalArgumentException("不允许上传来自本地或内网地址的文件");
            }
        }
    }

    private String safeUrlForLog(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        return uri.getScheme() + "://" + uri.getHost() + path;
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

    /**
     * 通用 OSS 上传（校验 → 元数据 → putObject → URL）
     */
    private String uploadToOSS(MultipartFile file, String objectKey, String fileType) throws IOException {
        log.info("开始上传{} - objectKey: {}, filename: {}, size: {} bytes",
            fileType, objectKey, file.getOriginalFilename(), file.getSize());

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = createMetadata(file.getContentType(), file.getSize());
            PutObjectResult result = putObject(objectKey, inputStream, metadata, fileType + "上传失败：");
            log.info("{}上传成功 - bucket: {}, objectKey: {}, ETag: {}",
                fileType, bucketName, objectKey, result.getETag());
            return generateFileUrl(objectKey);
        }
    }
}
