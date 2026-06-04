package com.zjkl.common.controller;

import com.zjkl.ai.image.controller.ImageController;
import com.zjkl.ai.oss.controller.OssController;
import com.zjkl.memory.controller.MemorySearchController;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.validation.annotation.Validated;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ControllerParameterValidationTest {

    @Test
    void memorySearchController_shouldValidateLimitBounds() throws NoSuchMethodException {
        assertNotNull(MemorySearchController.class.getAnnotation(Validated.class),
                "MemorySearchController must enable method parameter validation");

        Parameter searchLimit = parameter(MemorySearchController.class, "search", 1, String.class, int.class);
        assertBounds(searchLimit, 1, 20);

        Parameter byDateLimit = parameter(MemorySearchController.class, "searchByDate", 3,
                String.class, String.class, String.class, int.class);
        assertBounds(byDateLimit, 1, 20);
    }

    @Test
    void ossController_shouldValidatePresignedUrlExpirationBounds() throws NoSuchMethodException {
        assertNotNull(OssController.class.getAnnotation(Validated.class),
                "OssController must enable method parameter validation");

        Parameter expirationMinutes = parameter(OssController.class, "getPresignedUrl", 1, String.class, int.class);
        assertBounds(expirationMinutes, 1, 480);
    }

    @Test
    void imageController_shouldValidateImageUrlFormat() throws NoSuchMethodException {
        assertNotNull(ImageController.class.getAnnotation(Validated.class),
                "ImageController must enable method parameter validation");

        assertUrlPattern(parameter(ImageController.class, "describe", 0, String.class));
        assertUrlPattern(parameter(ImageController.class, "describeForPeek", 0, String.class));
    }

    private Parameter parameter(Class<?> type, String methodName, int parameterIndex, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        return method.getParameters()[parameterIndex];
    }

    private void assertBounds(Parameter parameter, long min, long max) {
        Min minAnnotation = parameter.getAnnotation(Min.class);
        Max maxAnnotation = parameter.getAnnotation(Max.class);

        assertNotNull(minAnnotation, "parameter must declare @Min");
        assertNotNull(maxAnnotation, "parameter must declare @Max");
        assertEquals(min, minAnnotation.value());
        assertEquals(max, maxAnnotation.value());
    }

    private void assertUrlPattern(Parameter parameter) {
        Pattern pattern = parameter.getAnnotation(Pattern.class);

        assertNotNull(pattern, "imageUrl parameter must declare @Pattern");
        assertEquals("^https?://.+", pattern.regexp());
    }
}
