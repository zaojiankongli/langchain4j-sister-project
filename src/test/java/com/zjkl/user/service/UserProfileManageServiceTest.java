package com.zjkl.user.service;

import com.zjkl.auth.dto.CompleteProfileRequest;
import com.zjkl.user.domain.User;
import com.zjkl.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.zjkl.settings.service.SettingsService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileManageServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private SettingsService settingsService;

    private UserProfileManageService userProfileManageService;

    @BeforeEach
    void setUp() {
        userProfileManageService = new UserProfileManageService(userMapper, settingsService);
    }

    @Test
    void completeProfile_shouldRejectInvalidBirthdayInsteadOfSilentlyIgnoring() {
        User user = new User();
        user.setId("u1");
        user.setUsername("old-name");
        when(userMapper.findById("u1")).thenReturn(user);

        CompleteProfileRequest request = new CompleteProfileRequest(
                "new-name",
                1,
                2,
                List.of("摄影"),
                "2026-99-99",
                "https://example.com/avatar.png"
        );

        assertThrows(IllegalArgumentException.class,
                () -> userProfileManageService.completeProfile("u1", request));

        verify(userMapper, never()).update(user);
    }
}
