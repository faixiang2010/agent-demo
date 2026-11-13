package other;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CreateDatabaseParam;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 独立 main 示例：知识入库 -> Milvus 向量检索 -> 上下文增强 -> 通义千问回答。
 * 文档：https://java2ai.com/integration/rag/retrieval-augmented-generation
 * 配置与运行方式见同目录 RAG-README.md；需要 JDK 17 和 Milvus Standalone。
 */
public class RagDemo {

    /** 与 Demo1 使用同一个 DashScope Key。 */
    private static final String DASHSCOPE_API_KEY = "System.getenv("AI_DASHSCOPE_API_KEY")";
    private static final int EMBEDDING_DIMENSION = 1024;
    private static final int TOP_K = 3;
    private static final double SIMILARITY_THRESHOLD = 0.50;

    public static void main(String[] args) throws Exception {
        var config = loadConfig();
        try {
            if (args.length > 0 && "--preview".equals(args[0])) {
                readWebsite(config);
                return;
            }
            // 启动只展示菜单；选择操作后才连接数据库或调用模型。
            var scanner = new java.util.Scanner(System.in);
            while (true) {
                System.out.println("\n===== DeepSeek知识库 =====");
                System.out.println("1. 入库 / 更新官网知识");
                System.out.println("2. 查询知识库");
                System.out.println("0. 退出");
                System.out.print("请选择：");
                if (!scanner.hasNextLine()) {
                    return;
                }
                String choice = scanner.nextLine().trim();
                if ("0".equals(choice)) {
                    System.out.println("已退出。");
                    return;
                }
                try {
                    switch (choice) {
                        case "1" -> importKnowledge(config);
                        case "2" -> {
                            System.out.println("已进入连续查询，输入 0 返回主菜单，输入 exit 退出程序。");
                            while (true) {
                                System.out.print("请输入问题：");
                                if (!scanner.hasNextLine()) {
                                    return;
                                }
                                String question = scanner.nextLine().trim();
                                if ("0".equals(question)) {
                                    break;
                                }
                                if ("exit".equalsIgnoreCase(question)) {
                                    System.out.println("已退出。");
                                    return;
                                }
                                if (question.isBlank()) {
                                    System.out.println("问题不能为空，请输入问题、0 或 exit。");
                                    continue;
                                }
                                try {
                                    queryKnowledge(config, question);
                                } catch (Exception e) {
                                    System.err.println("查询失败：" + e.getMessage());
                                }
                            }
                        }
                        default -> System.out.println("无效选择，请输入 1、2 或 0。");
                    }
                } catch (Exception e) {
                    System.err.println("操作失败：" + e.getMessage());
                    // 单次操作失败后保留菜单，可以重试或选择退出。
                }
            }
        } finally {
            // 独立控制台程序退出时释放 HTTP 连接池，避免后台线程阻止 JVM 退出。
            reactor.netty.http.HttpResources.disposeLoopsAndConnections();
        }
    }

    private static StandardEnvironment loadConfig() throws Exception {
        var config = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("rag", new ClassPathResource("application.yml"))) {
            config.getPropertySources().addLast(source);
        }
        if ("default".equals(config.getRequiredProperty("rag.milvus.database"))) {
            throw new IllegalArgumentException("官网知识必须使用专属数据库，不能使用 default");
        }
        return config;
    }

    /** 入库：抓取并分块，创建缺失的数据库/集合，更新该网页的知识。 */
    public static void importKnowledge(StandardEnvironment config) throws Exception {
        List<Document> documents = readWebsite(config);
        var client = connectMilvus(config);
        try {
            var store = createVectorStore(client, config, true);
            String url = config.getRequiredProperty("rag.knowledge.url");
            // 仅删除同来源旧分块，不删除数据库、集合或其他来源。请单进程导入。
            store.delete(new FilterExpressionBuilder().eq("source", url).build());
            store.add(documents);
            System.out.println("入库完成：" + config.getRequiredProperty("rag.milvus.database") + "."
                    + config.getRequiredProperty("rag.milvus.collection") + "，文档块：" + documents.size());
        } finally {
            client.close(5);
        }
    }

    /** 查询：只使用已有知识，不抓取网页、创建数据库/集合或执行写入。 */
    public static void queryKnowledge(StandardEnvironment config, String question) throws Exception {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        var client = connectMilvus(config);
        try {
            var store = createVectorStore(client, config, false);
            List<Document> matches = store.similaritySearch(SearchRequest.builder()
                    .query(question).topK(TOP_K).similarityThreshold(SIMILARITY_THRESHOLD).build());
            System.out.println("检索命中文档数：" + matches.size());
            if (matches.isEmpty()) {
                System.out.println("没有找到相关知识。若尚未入库，请返回菜单选择 1。");
                return;
            }
            for (Document document : matches) {
                System.out.printf("来源：%s，相似度：%s%n%s%n", document.getMetadata().get("source"),
                        document.getScore(), document.getText());
            }
            var api = DashScopeApi.builder().apiKey(DASHSCOPE_API_KEY).build();
            var chatModel = DashScopeChatModel.builder().dashScopeApi(api)
                    .defaultOptions(DashScopeChatOptions.builder().model("qwen-plus").temperature(0.0).build())
                    .build();
            var advisor = RetrievalAugmentationAdvisor.builder()
                    // 控制台串行问答使用同步执行器，避免默认线程池阻止程序退出。
                    .taskExecutor(new org.springframework.core.task.SyncTaskExecutor())
                    .documentRetriever(VectorStoreDocumentRetriever.builder()
                            .vectorStore(store).topK(TOP_K).similarityThreshold(SIMILARITY_THRESHOLD).build())
                    .build();
            String answer = ChatClient.builder(chatModel).build().prompt()
                    .system("你是企业知识库助手。请用中文，仅依据检索上下文回答；没有相关资料时明确说明无法回答。")
                    .advisors(advisor).user(question).call().content();
            System.out.println("RAG 回答：\n" + answer);
        } finally {
            client.close(5);
        }
    }

    /** MCP Server 使用的只读检索入口：返回原始知识块，不在服务端再次调用聊天模型。 */
    public static List<Document> retrieveKnowledge(String question) throws Exception {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        var config = loadConfig();
        var client = connectMilvus(config);
        try {
            var store = createVectorStore(client, config, false);
            return store.similaritySearch(SearchRequest.builder().query(question)
                    .topK(TOP_K).similarityThreshold(SIMILARITY_THRESHOLD).build());
        } finally {
            client.close(5);
        }
    }

    private static List<Document> readWebsite(StandardEnvironment config) throws Exception {
        return knowledge(config.getRequiredProperty("rag.knowledge.url"),
                config.getRequiredProperty("rag.knowledge.chunk-size", Integer.class),
                config.getRequiredProperty("rag.knowledge.chunk-overlap", Integer.class));
    }

    private static MilvusServiceClient connectMilvus(StandardEnvironment config) throws Exception {
        String host = config.getRequiredProperty("rag.milvus.host");
        int port = config.getRequiredProperty("rag.milvus.port", Integer.class);
        var connection = ConnectParam.newBuilder().withHost(host).withPort(port)
                .withConnectTimeout(5, TimeUnit.SECONDS).withRpcDeadline(30, TimeUnit.SECONDS)
                .withSecure(config.getProperty("rag.milvus.ssl", Boolean.class, false));
        String username = config.getProperty("rag.milvus.username");
        String password = config.getProperty("rag.milvus.password");
        if ((username != null && !username.isBlank()) || (password != null && !password.isBlank())) {
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                throw new IllegalArgumentException("Milvus 认证需要同时配置 MILVUS_USERNAME 和 MILVUS_PASSWORD");
            }
            connection.withAuthorization(username, password);
        }
        var client = new MilvusServiceClient(connection.build());
        try {
            var health = client.checkHealth();
            if (health.getStatus() != 0 || health.getData() == null || !health.getData().getIsHealthy()) {
                throw new IllegalStateException("Milvus 尚未就绪，请检查 " + host + ":" + port
                        + " 的服务状态。响应：" + health);
            }
            return client;
        } catch (Exception e) {
            client.close(5);
            throw e;
        }
    }

    private static MilvusVectorStore createVectorStore(MilvusServiceClient client,
            StandardEnvironment config, boolean initializeSchema) throws Exception {
        String database = config.getRequiredProperty("rag.milvus.database");
        String collection = config.getRequiredProperty("rag.milvus.collection");
        var databases = client.listDatabases();
        if (databases.getStatus() != 0 || databases.getData() == null) {
            throw new IllegalStateException("无法读取 Milvus 数据库列表：" + databases);
        }
        if (!databases.getData().getDbNamesList().contains(database)) {
            if (!initializeSchema) {
                throw new IllegalStateException("知识库数据库不存在，请先选择 1 入库。");
            }
            var created = client.createDatabase(CreateDatabaseParam.newBuilder().withDatabaseName(database).build());
            if (created.getStatus() != 0) {
                throw new IllegalStateException("创建数据库失败：" + created);
            }
            System.out.println("已创建数据库：" + database);
        }
        if (!initializeSchema) {
            var exists = client.hasCollection(io.milvus.param.collection.HasCollectionParam.newBuilder()
                    .withDatabaseName(database).withCollectionName(collection).build());
            if (exists.getStatus() != 0) {
                throw new IllegalStateException("检查知识库集合失败：" + exists);
            }
            if (!Boolean.TRUE.equals(exists.getData())) {
                throw new IllegalStateException("知识库集合不存在，请先选择 1 入库。");
            }
            // 加载已有集合供检索，不创建或修改集合结构，不写入知识。
            var loaded = client.loadCollection(io.milvus.param.collection.LoadCollectionParam.newBuilder()
                    .withDatabaseName(database).withCollectionName(collection).withSyncLoad(true).build());
            if (loaded.getStatus() != 0) {
                throw new IllegalStateException("加载知识库集合失败：" + loaded);
            }
        }
        var api = DashScopeApi.builder().apiKey(DASHSCOPE_API_KEY).build();
        var embeddings = DashScopeEmbeddingModel.builder().dashScopeApi(api).metadataMode(MetadataMode.NONE)
                .defaultOptions(DashScopeEmbeddingOptions.builder().model("text-embedding-v3")
                        .dimensions(EMBEDDING_DIMENSION).build()).build();
        var store = MilvusVectorStore.builder(client, embeddings).databaseName(database).collectionName(collection)
                .embeddingDimension(EMBEDDING_DIMENSION).indexType(IndexType.FLAT).metricType(MetricType.COSINE)
                .initializeSchema(initializeSchema).build();
        store.afterPropertiesSet();
        return store;
    }

    private static List<Document> knowledge(String url, int chunkSize, int overlap) throws Exception {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("要求 0 <= chunk-overlap < chunk-size");
        }
        var html = Jsoup.connect(url).timeout(30000).maxBodySize(5 * 1024 * 1024)
                .userAgent("Mozilla/5.0 (compatible; DeepSeekKnowledgeImporter/1.0)").get();
        String title = html.title();
        html.select("script,style,noscript,nav,header,svg,button,[hidden]").remove();
        String text = html.body().wholeText().replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        if (text.length() < 300 || !text.contains("DeepSeek")) {
            throw new IllegalStateException("网页正文异常，终止入库，保留已有知识");
        }
        Path snapshot = Path.of("target", "deepseek-home-knowledge.txt");
        Files.createDirectories(snapshot.getParent());
        Files.writeString(snapshot, "来源：" + url + "\n标题：" + title + "\n\n" + text, StandardCharsets.UTF_8);
        List<Document> documents = new ArrayList<>();
        String fetchedAt = Instant.now().toString();
        for (int start = 0, index = 0; start < text.length(); index++) {
            int end = Math.min(start + chunkSize, text.length());
            String id = UUID.nameUUIDFromBytes((url + "#" + index).getBytes(StandardCharsets.UTF_8)).toString();
            documents.add(new Document(id, "DeepSeek官网：" + title + "\n" + text.substring(start, end),
                    Map.of("source", url, "title", title, "chunk_index", index, "fetched_at", fetchedAt)));
            if (end == text.length()) {
                break;
            }
            start = end - overlap;
        }
        System.out.println("官网正文：" + text.length() + " 字符，分块：" + documents.size()
                + "，快照：" + snapshot.toAbsolutePath());
        return documents;
    }
}
