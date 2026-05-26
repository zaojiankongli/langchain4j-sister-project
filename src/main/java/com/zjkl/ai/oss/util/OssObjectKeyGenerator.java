package com.zjkl.ai.oss.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * OSS 对象键生成工具类
 * <p>
 * 用于生成各种 OSS 文件路径（ObjectKey），从 URL 提取文件名等。
 */
@Slf4j
public class OssObjectKeyGenerator {

    public OssObjectKeyGenerator() {
        // 无参构造，工具类
    }

    /**
     * 生成头像文件的 OSS 对象键
     * 格式：avatars/{userId}/{uuid}.{ext}
     */
    public String generateAvatarObjectKey(String userId, String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String uniqueFilename = "avatar_" + UUID.randomUUID().toString().replace("-", "");
        return "avatars/" + userId + "/" + uniqueFilename + "." + extension;
    }

    /**
     * 生成聊天图片的 OSS 对象键
     * 格式：MessageImage/{年}/{月}/{日}/{userId}/{uuid}.{ext}
     */
    public String generateMessageImageObjectKey(String userId, String originalFilename) {
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
    public String generateObjectKey(String folder, String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueFilename = UUID.randomUUID().toString().replace("-", "");
        return folder + "/" + date + "/" + uniqueFilename + "." + extension;
    }

    /**
     * 从 URL 或 Content-Disposition 提取文件名
     */
    public String extractFilenameFromUrl(String fileUrl, HttpURLConnection connection) {
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
    public String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "jpg";
        }
        int lastDot = filename.lastIndexOf(".");
        if (lastDot < 0) {
            return "jpg";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }
}
