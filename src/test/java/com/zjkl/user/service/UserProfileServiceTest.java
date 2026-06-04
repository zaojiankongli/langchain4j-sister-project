package com.zjkl.user.service;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.ai.oss.service.OssService;
import com.zjkl.user.domain.User;
import com.zjkl.user.domain.dto.UserProfileUpdateDTO;
import com.zjkl.user.domain.vo.UserProfileVO;
import com.zjkl.user.mapper.UserProfileMapper;
import com.zjkl.user.service.impl.UserProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private OssService ossService;

    @Mock
    private UserActivityTracker userActivityTracker;

    @InjectMocks
    private UserProfileServiceImpl userProfileService;

    private static final String TEST_USER_ID = "1";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        // Common mocks
        lenient().when(userActivityTracker.getLastActiveTime(anyString())).thenReturn(System.currentTimeMillis());
    }

    @Test
    void fetchProfileReturnsUserData() {
        // Arrange
        User testUser = new User();
        testUser.setId(TEST_USER_ID);
        testUser.setUsername(TEST_USERNAME);
        testUser.setEmail(TEST_EMAIL);
        testUser.setUserProfile("测试用户");

        when(userProfileMapper.findUserById(TEST_USER_ID)).thenReturn(testUser);
        when(userProfileMapper.findLevelInfo(TEST_USER_ID)).thenReturn(null);
        when(userProfileMapper.findLatestEmotion(TEST_USER_ID)).thenReturn(null);
        when(userProfileMapper.findInterestTags(TEST_USER_ID)).thenReturn(List.of());
        when(userProfileMapper.countMessages(TEST_USER_ID)).thenReturn(0);
        when(userProfileMapper.findFirstChatDate(TEST_USER_ID)).thenReturn(null);

        // Act
        UserProfileVO result = userProfileService.getProfile(TEST_USER_ID);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_USER_ID, result.getId());
        assertEquals(TEST_USERNAME, result.getUsername());
        assertEquals(TEST_EMAIL, result.getEmail());
        assertEquals("测试用户", result.getUserProfile());
        assertEquals(1, result.getCurrentLevel()); // default level
        assertEquals(0, result.getCurrentExp()); // default exp
        assertEquals(100, result.getLevelUpExp()); // default level up exp
        assertEquals(0, result.getTotalExp()); // default total exp
        assertEquals(0, result.getMessageCount()); // default message count
        assertEquals(0, result.getMeetDays()); // default meet days
        assertNotNull(result.getInterestTags());
        assertTrue(result.getInterestTags().isEmpty());

        verify(userProfileMapper).findUserById(TEST_USER_ID);
        verify(userProfileMapper).findLevelInfo(TEST_USER_ID);
        verify(userProfileMapper).findLatestEmotion(TEST_USER_ID);
        verify(userProfileMapper).findInterestTags(TEST_USER_ID);
        verify(userProfileMapper).countMessages(TEST_USER_ID);
        verify(userProfileMapper).findFirstChatDate(TEST_USER_ID);
        verify(userActivityTracker).getLastActiveTime(TEST_USER_ID);
    }

    @Test
    void updateBasicUpdatesUserFields() {
        // Arrange
        String newUsername = "newusername";
        Integer newGender = 1;

        when(userProfileMapper.updateUserBasic(TEST_USER_ID, newUsername, newGender)).thenReturn(1);

        // Act
        userProfileService.updateBasic(TEST_USER_ID, newUsername, newGender);

        // Assert
        verify(userProfileMapper).updateUserBasic(TEST_USER_ID, newUsername, newGender);
        verifyNoMoreInteractions(userProfileMapper);
    }
}