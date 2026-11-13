package other;

import com.alibaba.cloud.ai.dashscope.api.DashScopeImageApi;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeModel;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

/**
 * 文生图（Image Generation）Demo。
 *
 * <p>对应文档：<a href="https://java2ai.com/integration/multimodals/image/dashscope-image">DashScope 图像生成</a>。
 * 纯 API 调用，不依赖 Spring 容器；运行方式：IDE 中右键运行 main 方法。</p>
 *
 * <p>输出：在项目根目录 {@code target/image-output.png} 生成图片文件。</p>
 */
public class ImageGenerationDemo {

    /** DashScope API Key（与 Demo1 一致，硬编码便于独立运行）。 */
    private static final String DASHSCOPE_API_KEY = "System.getenv("AI_DASHSCOPE_API_KEY")";

    public static void main(String[] args) {
        // 抑制 Spring AI RetryUtils 的 "Retry error" 噪音日志：
        // DashScope 异步任务（文生图）需轮询任务状态，PENDING 时 SDK 抛 TransientAiException
        // 触发 RetryTemplate 重试，RetryUtils 默认按 WARN 记录每次 retry —— 但这是正常轮询，不是错误。
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger("org.springframework.ai.retry.RetryUtils"))
                .setLevel(ch.qos.logback.classic.Level.ERROR);

        // 1. 构建 DashScopeImageApi（本类有 builder()）
        DashScopeImageApi imageApi = DashScopeImageApi.builder()
                .apiKey(DASHSCOPE_API_KEY)
                .build();

        // 2. 构建 ImageModel（builder 方法名为 dashScopeApi，注意不是 dashScopeImageApi）
        ImageModel imageModel = DashScopeImageModel.builder()
                .dashScopeApi(imageApi)
                .defaultOptions(DashScopeImageOptions.builder()
                        .model(DashScopeModel.ImageModel.WAN_2_2_T_2_I_FLASH.getValue())  // 通义万相文生图
                        .n(1)                                                              // 生成数量
                        .width(1024)                                                         // 图片宽度
                        .height(1024)                                                        // 图片高度
                        .style("photography")                                               // 风格
                        .responseFormat("b64_json")                                          // 返回 base64（字符串形式）
                        .seed(12345)                                                         // 随机种子
                        .build())
                .build();

        // 3. 准备提示词
        String promptText = "杭州西湖十景之一，断桥残雪，水墨画风格";

        // 4. 调用模型
        ImagePrompt prompt = new ImagePrompt(promptText);
        ImageResponse response = imageModel.call(prompt);
        List<ImageGeneration> results = response.getResults();

        // 5. 处理结果
        if (results.isEmpty()) {
            System.out.println("未生成任何图像。");
            return;
        }

        try {
            Files.createDirectories(Paths.get("target"));
        } catch (Exception e) {
            throw new RuntimeException("创建 target 目录失败", e);
        }

        int idx = 0;
        for (ImageGeneration gen : results) {
            Image image = gen.getOutput();
            String url = image.getUrl();
            String b64 = image.getB64Json();   // 注意是 getB64Json，不是 getBase64Data

            System.out.println("图像 #" + idx + "：");
            if (url != null && !url.isBlank()) {
                System.out.println("  URL = " + url);
            }
            if (b64 != null && !b64.isBlank()) {
                java.nio.file.Path outPath = Paths.get("target", "image-output-" + idx + ".png");
                byte[] bytes = Base64.getDecoder().decode(b64);
                try {
                    Files.write(outPath, bytes);
                    System.out.println("  已写入 " + outPath.toAbsolutePath() + "（" + bytes.length + " 字节）");
                } catch (Exception e) {
                    throw new RuntimeException("写入图片文件失败", e);
                }
            } else {
                System.out.println("  无 base64 数据，请访问 URL 下载。");
            }
            idx++;
        }
    }
}
