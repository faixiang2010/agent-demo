package other;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioTranscriptionApi;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioTranscriptionModel;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioTranscriptionOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeModel;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.UrlResource;

import java.util.List;

/**
 * 语音转文档（Speech-To-Text）Demo。
 *
 * <p>对应文档：<a href="https://java2ai.com/integration/multimodals/audio/transcriptions/dashscope-transcriptions">DashScope 语音转写</a>。
 * 纯 API 调用，不依赖 Spring 容器；运行方式：IDE 中右键运行 main 方法。</p>
 *
 * <p><b>关键约束</b>：DashScope 异步任务模式要求音频必须是<b>公网可访问的 URL</b>，
 * SDK 内部调用 {@code Resource.getURL().toString()} 作为 file_url 提交。
 * 因此不能用 {@code ClassPathResource}/{@code FileSystemResource}（指向本机文件系统，DashScope 服务端无法访问），
 * 必须使用 {@link UrlResource} 包装公网 URL。</p>
 *
 * <p>说明：如需"语音转文档"（如摘要/结构化），可将本 demo 输出的转写文本
 * 再交给 ChatModel 做摘要或结构化处理。</p>
 */
public class SpeechToTextDemo {

    /** DashScope API Key（与 Demo1 一致，硬编码便于独立运行）。 */
    private static final String DASHSCOPE_API_KEY = "System.getenv("AI_DASHSCOPE_API_KEY")";

    /**
     * DashScope 官方公开示例音频（WAV）。可替换为自己的公网音频 URL。
     *
     * <p><b>注意</b>：早期文档示例 {@code title.wav} 已 404 失效，
     * DashScope 服务端访问不到 → 任务 FAILED → SDK 在 FAILED 状态下仍调用
     * {@code toResponse} 取 {@code results[0].transcriptionUrl()} 得到 null，
     * 进而在 {@code URI.create(null)} 处抛 NPE。改用现行文档示例即可正常转写。</p>
     */
    private static final String AUDIO_URL =
            "https://dashscope.oss-cn-beijing.aliyuncs.com/samples/audio/paraformer/hello_world_female2.wav";

    public static void main(String[] args) {
        // 抑制 Spring AI RetryUtils 的 "Retry error" 噪音日志：
        // DashScope 异步任务（语音转写）需轮询任务状态，PENDING 时 SDK 抛 TransientAiException
        // 触发 RetryTemplate 重试，RetryUtils 默认按 WARN 记录每次 retry —— 但这是正常轮询，不是错误。
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger("org.springframework.ai.retry.RetryUtils"))
                .setLevel(ch.qos.logback.classic.Level.ERROR);

        try {
            // 1. 构建 DashScopeAudioTranscriptionApi（build() 强制要求 model 非空）
            DashScopeAudioTranscriptionApi transcriptionApi = DashScopeAudioTranscriptionApi.builder()
                    .apiKey(DASHSCOPE_API_KEY)
                    .model(DashScopeModel.AudioModel.PARAFORMER_V1.getValue())
                    .build();

            // 2. 构建 TranscriptionModel（builder 方法名为 audioTranscriptionApi）
            TranscriptionModel transcriptionModel = DashScopeAudioTranscriptionModel.builder()
                    .audioTranscriptionApi(transcriptionApi)
                    .defaultOptions(DashScopeAudioTranscriptionOptions.builder()
                            .model(DashScopeModel.AudioModel.PARAFORMER_V1.getValue())
                            .format(DashScopeAudioTranscriptionApi.AudioFormat.WAV)
                            .languageHints(List.of("zh", "en"))               // 语言提示
                            .punctuationPredictionEnabled(true)                // 标点预测
                            .build())
                    .build();

            // 3. 准备音频资源（必须公网可访问）
            UrlResource audioResource = new UrlResource(AUDIO_URL);

            // 4. 调用模型转写，AudioTranscription.getOutput() 直接返回 String
            AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioResource);
            AudioTranscription transcription = transcriptionModel.call(prompt).getResult();
            String text = transcription.getOutput();

            // 5. 输出结果
            System.out.println("转写文本：");
            System.out.println(text);
        } catch (Exception e) {
            throw new RuntimeException("语音转写失败：" + e.getMessage(), e);
        }
    }
}
