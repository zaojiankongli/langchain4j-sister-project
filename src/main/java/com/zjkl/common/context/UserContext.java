package com.zjkl.common.context;

import org.springframework.stereotype.Component;

@Component
public class UserContext {

    private static final ThreadLocal<String> userIdHolder = new InheritableThreadLocal<>();

    public void setUserId(String userId) {
        userIdHolder.set(userId);
    }

    public String getUserId() {
        return userIdHolder.get();
    }

    public void clear() {
        userIdHolder.remove();
    }
}
