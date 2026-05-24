package com.zjkl.ai.oss.service;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.zjkl.ai.oss.config.OssConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
    
    // 允许的头像文件扩展名
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp"
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
     * 
     * @param userId 用户 ID（必填）
     * @param file 头像文件
     * @return OSS 上的文件访问 URL
     * @throws IOException 上传失败时抛出
     */
    public String uploadAvatar(String userId, MultipartFile file) throws IOException {
        // 参数校验
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        
        validateAvatarFile(file);
        
        // 生成 ObjectKey：avatars/{userId}/{uuid}.{ext}
        String objectKey = generateAvatarObjectKey(userId, file.getOriginalFilename());
        
        log.info("开始上传头像 - userId: {}, filename: {}, size: {} bytes", 
            userId, file.getOriginalFilename(), file.getSize());
        
        try (InputStream inputStream = file.getInputStream()) {
            // 设置元数据
            ObjectMetadata metadata = createMetadata(file.getContentType(), file.getSize());
            
            // 上传到 OSS
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                bucketName, 
                objectKey, 
                inputStream,
                metadata
            );
            
            PutObjectResult result = ossClient.putObject(putObjectRequest);
            log.info("头像上传成功 - bucket: {}, objectKey: {}, ETag: {}", 
                bucketName, objectKey, result.getETag());
            
            return generateFileUrl(objectKey);
            
        } catch (OSSException e) {
            log.error("OSS 错误 - Code: {}, Message: {}, RequestId: {}", 
                e.getErrorCode(), e.getErrorMessage(), e.getRequestId());
            throw new IOException("头像上传失败：" + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("客户端错误 - Message: {}", e.getMessage());
            throw new IOException("头像上传失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 上传任意文件到 OSS
     * 
     * @param folder 文件夹路径（如：avatars, documents 等）
     * @param file 文件
     * @return OSS 上的文件访问 URL
     * @throws IOException 上传失败时抛出
     */
    public String uploadFile(String folder, MultipartFile file) throws IOException {
        // 参数校验
        if (folder == null || folder.isBlank()) {
            throw new IllegalArgumentException("文件夹路径不能为空");
        }
        
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        
        // 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过 5MB");
        }
        
        // 生成 ObjectKey：{folder}/{yyyy/MM/dd}/{uuid}.{ext}
        String objectKey = generateObjectKey(folder, file.getOriginalFilename());
        
        log.info("开始上传文件 - folder: {}, filename: {}, size: {} bytes", 
            folder, file.getOriginalFilename(), file.getSize());
        
        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = createMetadata(file.getContentType(), file.getSize());
            
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                bucketName, 
                objectKey, 
                inputStream,
                metadata
            );
            
            PutObjectResult result = ossClient.putObject(putObjectRequest);
            log.info("文件上传成功 - bucket: {}, objectKey: {}, ETag: {}", 
                bucketName, objectKey, result.getETag());
            
            return generateFileUrl(objectKey);
            
        } catch (OSSException e) {
            log.error("OSS 错误 - Code: {}, Message: {}, RequestId: {}", 
                e.getErrorCode(), e.getErrorMessage(), e.getRequestId());
            throw new IOException("文件上传失败：" + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("客户端错误 - Message: {}", e.getMessage());
            throw new IOException("文件上传失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 上传聊天图片到 OSS
     *
     * @param file 上传的图片文件
     * @param userId 用户 ID
     * @return OSS 上的文件访问 URL
     * @throws IOException 上传失败时抛出
     */
    public String uploadMessageImage(MultipartFile file, String userId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只支持图片文件（JPG/PNG/GIF/WebP）");
        }

        String objectKey = generateMessageImageObjectKey(userId, file.getOriginalFilename());

        log.info("开始上传聊天图片 - userId: {}, filename: {}, size: {} bytes",
            userId, file.getOriginalFilename(), file.getSize());

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = createMetadata(file.getContentType(), file.getSize());

            PutObjectRequest putObjectRequest = new PutObjectRequest(
                bucketName,
                objectKey,
                inputStream,
                metadata
            );

            PutObjectResult result = ossClient.putObject(putObjectRequest);
            log.info("聊天图片上传成功 - bucket: {}, objectKey: {}, ETag: {}",
                bucketName, objectKey, result.getETag());

            return generateFileUrl(objectKey);

        } catch (OSSException e) {
            log.error("OSS 错误 - Code: {}, Message: {}", e.getErrorCode(), e.getErrorMessage());
            throw new IOException("聊天图片上传失败：" + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("客户端错误 - Message: {}", e.getMessage());
            throw new IOException("聊天图片上传失败：" + e.getMessage(), e);
        }
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
        
        log.info("开始从 URL 下载并上传 - fileUrl: {}", fileUrl);
        
        HttpURLConnection connection = null;
        try {
            // 从 URL 下载文件
            URL url = new URL(fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("下载文件失败，HTTP 状态码：" + responseCode);
            }
            
            // 获取文件名
            String filename = extractFilenameFromUrl(fileUrl, connection);
            
            // 获取文件大小
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("文件大小不能超过 5MB");
            }
            
            try (InputStream inputStream = connection.getInputStream()) {
                // 生成 ObjectKey
                String targetFolder = (folder != null && !folder.isBlank()) ? folder : "downloads";
                String objectKey = generateObjectKey(targetFolder, filename);
                
                ObjectMetadata metadata = createMetadata(
                    connection.getContentType(), 
                    contentLength > 0 ? contentLength : null
                );
                
                PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName, 
                    objectKey, 
                    inputStream,
                    metadata
                );
                
                PutObjectResult result = ossClient.putObject(putObjectRequest);
                log.info("从 URL 上传文件成功 - bucket: {}, objectKey: {}, ETag: {}", 
                    bucketName, objectKey, result.getETag());
                
                return generateFileUrl(objectKey);
                
            } catch (OSSException e) {
                log.error("OSS 错误 - Code: {}, Message: {}", e.getErrorCode(), e.getErrorMessage());
                throw new IOException("文件上传失败：" + e.getErrorMessage(), e);
            } catch (ClientException e) {
                log.error("客户端错误 - Message: {}", e.getMessage());
                throw new IOException("文件上传失败：" + e.getMessage(), e);
            }
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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
        return "https://" + bucketName + "." + domain + "/" + objectKey;
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
     * 生成头像文件的 OSS 对象键
     * 格式：avatars/{userId}/{uuid}.{ext}
     */
    private String generateAvatarObjectKey(String userId, String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String uniqueFilename = "avatar_" + UUID.randomUUID().toString().replace("-", "");
        return "avatars/" + userId + "/" + uniqueFilename + "." + extension;
    }

    /**
     * 生成聊天图片的 OSS 对象键
     * 格式：MessageImage/{年}/{月}/{日}/{userId}/{uuid}.{ext}
     */
    private String generateMessageImageObjectKey(String userId, String originalFilename) {
        String extension = getFileExtension(originalFilename);
        LocalDate now = LocalDate.now();
        String uniqueFilename = UUID.randomUUID().toString().replace("-", "");
        return String.format("MessageImage/%d/%02d/%02d/%s/%s.%s",
            now.getYear(), now.getMonthValue(), now.getDayOfMonth(), userId, uniqueFilename, extension);
    }
    
    /**
     * 生成通用对象键
     * 格式：{folder}/{yyyy/MM/dd}/{uuid}.{ext}
     */
    private String generateObjectKey(String folder, String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueFilename = UUID.randomUUID().toString().replace("-", "");
        return folder + "/" + date + "/" + uniqueFilename + "." + extension;
    }
    
    /**
     * 从 URL 或 Content-Disposition 提取文件名
     */
    private String extractFilenameFromUrl(String fileUrl, HttpURLConnection connection) {
        // 尝试从 Content-Disposition 头获取文件名
        String disposition = connection.getHeaderField("Content-Disposition");
        if (disposition != null && !disposition.isEmpty()) {
            int filenameIndex = disposition.indexOf("filename=");
            if (filenameIndex > 0) {
                String filename = disposition.substring(filenameIndex + 9);
                filename = filename.replace("\"", "").trim();
                return filename;
            }
        }
        
        // 从 URL 路径提取文件名
        int queryIndex = fileUrl.indexOf('?');
        String urlWithoutQuery = queryIndex > 0 ? fileUrl.substring(0, queryIndex) : fileUrl;
        int lastSlash = urlWithoutQuery.lastIndexOf('/');
        if (lastSlash > 0 && lastSlash < urlWithoutQuery.length() - 1) {
            return urlWithoutQuery.substring(lastSlash + 1);
        }
        
        // 默认文件名
        return "file_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "jpg";
        }
        int lastDot = filename.lastIndexOf(".");
        if (lastDot < 0) {
            return "jpg";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }
    
    /**
     * 校验头像文件
     */
    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        
        long fileSize = file.getSize();
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("头像大小不能超过 5MB");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只支持图片文件（JPG/PNG/GIF/WebP）");
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        
        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("只支持 JPG/PNG/GIF/WebP 格式");
        }
    }
}
