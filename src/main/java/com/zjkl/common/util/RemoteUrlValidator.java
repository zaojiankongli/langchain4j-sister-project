package com.zjkl.common.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;

public final class RemoteUrlValidator {

    private RemoteUrlValidator() {
    }

    public static URI requirePublicHttpUrl(String rawUrl, boolean httpsOnly) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("URL 不能为空");
        }

        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("URL 仅支持 HTTP(S)");
            }
            if (httpsOnly && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("URL 必须使用 HTTPS");
            }

            validatePublicHost(uri.getHost());
            return uri;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("URL 非法", e);
        }
    }

    public static void validatePublicHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL 缺少合法主机名");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isLocalOrPrivateAddress(address)) {
                    throw new IllegalArgumentException("不允许访问本地或内网地址");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("URL 主机解析失败", e);
        }
    }

    private static boolean isLocalOrPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }

        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            return bytes.length == 16 && (bytes[0] & (byte) 0xFE) == (byte) 0xFC;
        }

        return false;
    }
}
