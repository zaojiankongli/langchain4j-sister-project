package com.zjkl.ai.chat.service;

import com.zjkl.ai.image.service.ImageDescriptionService;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.service.EmotionService;
import com.zjkl.memory.service.GraphSnapshotService;
import com.zjkl.memory.service.PromptCacheService;
import com.zjkl.memory.service.SummaryMemoryService;
import com.zjkl.user.domain.vo.UserProfileVO;
import com.zjkl.user.service.UserProfileService;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SisterChatServiceTest {

    private QwenStreamingChatModel qwenStreamingChatModel;
    private ChatMemoryProvider chatMemoryProvider;
    private EmotionService emotionService;
    private PromptTemplateService promptTemplateService;
    private PromptCacheService promptCacheService;
    private ImageDescriptionService imageDescriptionService;
    private UserProfileService userProfileService;
    private SummaryMemoryService summaryMemoryService;
    private GraphSnapshotService graphSnapshotService;
    private GraphQueryService graphQueryService;
    private RagRouter ragRouter;
    private Executor asyncExecutor;
    private SisterChatService service;

    @BeforeEach
    void setUp() {
        qwenStreamingChatModel = mock(QwenStreamingChatModel.class);
        chatMemoryProvider = mock(ChatMemoryProvider.class);
        emotionService = mock(EmotionService.class);
        promptTemplateService = mock(PromptTemplateService.class);
        promptCacheService = mock(PromptCacheService.class);
        imageDescriptionService = mock(ImageDescriptionService.class);
        userProfileService = mock(UserProfileService.class);
        summaryMemoryService = mock(SummaryMemoryService.class);
        graphSnapshotService = mock(GraphSnapshotService.class);
        graphQueryService = mock(GraphQueryService.class);
        ragRouter = mock(RagRouter.class);
        asyncExecutor = Runnable::run; // synchronous executor for testing

        service = new SisterChatService(
                qwenStreamingChatModel,
                chatMemoryProvider,
                emotionService,
                promptTemplateService,
                promptCacheService,
                imageDescriptionService,
                userProfileService,
                summaryMemoryService,
                graphSnapshotService,
                graphQueryService,
                ragRouter,
                asyncExecutor
        );

        when(emotionService.getUserEmotion(anyString())).thenReturn(new EmotionalState(0.1, 0.2, 0.3));
        when(emotionService.getUserMoodDescription(anyString())).thenReturn("平静");
        when(promptTemplateService.render(anyString(), any())).thenReturn("user prompt");
        when(promptCacheService.getTemplate(anyString())).thenReturn("system prompt");
        UserProfileVO profile = new UserProfileVO();
        profile.setUsername("哥哥");
        profile.setHobbies("摄影");
        profile.setUserProfile("测试用户");
        when(userProfileService.getProfile(anyString())).thenReturn(profile);
        when(chatMemoryProvider.get(anyString())).thenReturn(null);
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onCompleteResponse(mock(ChatResponse.class));
            return null;
        }).when(qwenStreamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
    }

    @Test
    void shouldUseGraphOnlyWhenRouterRequestsGraphWithoutMemory() {
        when(graphSnapshotService.getSnapshot("u1")).thenReturn("snapshot");
        when(ragRouter.analyzeQuery(anyString(), any())).thenReturn(new RouterResult(false, true, "graph", null, null, null));
        when(graphQueryService.buildGraphBlock("u1", "小王最近怎么样")).thenReturn("graph-block");

        service.chatWithVoice("小王最近怎么样", "u1", null);

        verify(summaryMemoryService, never()).buildMemoryBlock(anyString(), anyString(), any());
        verify(graphQueryService).buildGraphBlock("u1", "小王最近怎么样");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(qwenStreamingChatModel).chat(captor.capture(), any(StreamingChatResponseHandler.class));
        List<ChatMessage> messages = captor.getValue().messages();
        String injected = ((SystemMessage) messages.get(1)).text();
        assertThat(injected).contains("图谱快照").contains("graph-block");
    }

    @Test
    void shouldUseMemoryOnlyWhenRouterRequestsMemoryWithoutGraph() {
        when(graphSnapshotService.getSnapshot("u1")).thenReturn("");
        when(ragRouter.analyzeQuery(anyString(), any())).thenReturn(new RouterResult(true, false, "memory", "最近一周", null, null));
        when(summaryMemoryService.buildMemoryBlock(anyString(), anyString(), any())).thenReturn("memory-block");

        service.chatWithVoice("上次那件事你还记得吗", "u1", null);

        verify(summaryMemoryService).buildMemoryBlock(anyString(), anyString(), any());
        verify(graphQueryService, never()).buildGraphBlock(anyString(), anyString());

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(qwenStreamingChatModel).chat(captor.capture(), any(StreamingChatResponseHandler.class));
        List<ChatMessage> messages = captor.getValue().messages();
        String injected = ((SystemMessage) messages.get(1)).text();
        assertThat(injected).contains("memory-block");
    }
}
