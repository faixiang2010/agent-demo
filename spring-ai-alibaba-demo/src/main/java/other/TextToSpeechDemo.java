package other;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioSpeechApi;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeModel;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 文本转语音（Text-To-Speech）Demo。
 *
 * <p>对应文档：<a href="https://java2ai.com/integration/multimodals/audio/speech/dashscope-speech">DashScope 语音合成</a>。
 * 纯 API 调用，不依赖 Spring 容器；运行方式：IDE 中右键运行 main 方法。</p>
 *
 * <p>输出：在项目根目录 {@code target/tts-output.mp3} 生成音频文件。</p>
 */
public class TextToSpeechDemo {

    /** DashScope API Key（与 Demo1 一致，硬编码便于独立运行）。 */
    private static final String DASHSCOPE_API_KEY = "System.getenv("AI_DASHSCOPE_API_KEY")";

    public static void main(String[] args) {
        // 1. 构建 DashScopeAudioSpeechApi（底层 HTTP 客户端，本类无 builder，使用构造器）
        DashScopeAudioSpeechApi speechApi = new DashScopeAudioSpeechApi(DASHSCOPE_API_KEY);

        // 2. 构建 TextToSpeechModel（builder 方法名为 audioSpeechApi）
        TextToSpeechModel audioModel = DashScopeAudioSpeechModel.builder()
                .audioSpeechApi(speechApi)
                .defaultOptions(DashScopeAudioSpeechOptions.builder()
                        .model(DashScopeModel.AudioModel.COSYVOICE_V1.getValue())
                        .voice("longhua")                                                // 发音人
                        .responseFormat(DashScopeAudioSpeechApi.ResponseFormat.MP3)
                        .speed(1.0)                                                       // 语速倍率
                        .sampleRate(48000)                                                // 采样率
                        .volume(50)                                                       // 音量 0~100
                        .pitch(1.0)                                                       // 音调倍率
                        .build())
                .build();

        // 3. 准备合成文本
        String text = "你好，这是一个文本转语音的测试示例。今天天气晴朗，适合户外活动。";

        // 4. 调用模型，Speech.getOutput() 直接返回 byte[]
        TextToSpeechPrompt prompt = new TextToSpeechPrompt(text);
        TextToSpeechResponse response = audioModel.call(prompt);
        byte[] audioBytes = response.getResult().getOutput();

        // 5. 写入 target/tts-output.mp3
        try {
            Files.createDirectories(Paths.get("target"));
            java.nio.file.Path outPath = Paths.get("target", "tts-output.mp3");
            Files.write(outPath, audioBytes);
            System.out.println("合成成功，字节数 = " + audioBytes.length);
            System.out.println("输出文件路径 = " + outPath.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("写入音频文件失败", e);
        }
    }
}
