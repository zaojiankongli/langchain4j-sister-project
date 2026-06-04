package com.zjkl.recommendation.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.recommendation.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private RecommendationService recommendationService;

    @Mock
    private UserContext userContext;

    private RecommendationController controller;

    @BeforeEach
    void setUp() {
        controller = new RecommendationController(recommendationService, userContext);
    }

    @Test
    void generateRecommendations_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.getUserId()).thenReturn(null);

        DeferredResult<Result<String>> result = controller.generateRecommendations();

        assertNotNull(result.getResult());
        Result<?> payload = (Result<?>) result.getResult();
        assertEquals(401, payload.getCode());
        assertEquals("请先登录", payload.getMessage());
        verify(recommendationService, never()).generateRecommendations(null);
    }

    @Test
    void generateRecommendations_shouldReturnSuccessWhenServiceCompletes() {
        when(userContext.getUserId()).thenReturn("u1");
        when(recommendationService.generateRecommendations("u1")).thenReturn(List.of());

        DeferredResult<Result<String>> result = controller.generateRecommendations();

        Result<?> payload;
        long deadline = System.currentTimeMillis() + 5000;
        do {
            payload = (Result<?>) result.getResult();
            if (payload != null) {
                break;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        } while (System.currentTimeMillis() < deadline);

        assertNotNull(payload);
        assertEquals(200, payload.getCode());
        assertEquals("success", payload.getMessage());
        assertEquals("生成了 0 条推荐", payload.getData());
        verify(recommendationService).generateRecommendations("u1");
    }
}
