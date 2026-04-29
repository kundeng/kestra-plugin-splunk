package io.kestra.plugin.splunk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import okhttp3.*;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Splunk search query",
    description = "Creates a search job on Splunk, polls until complete, and returns results."
)
@Plugin(examples = {})
public class SplunkSearch extends Task implements RunnableTask<SplunkSearch.Output>, SplunkConnection {

    @Schema(title = "Splunk host URL")
    @PluginProperty(dynamic = true)
    @NotNull
    private String host;

    @Schema(title = "Splunk management port")
    @PluginProperty(dynamic = true)
    @Builder.Default
    private String port = "8089";

    @Schema(title = "Authentication token")
    @PluginProperty(dynamic = true)
    private String token;

    @Schema(title = "Username for basic auth")
    @PluginProperty(dynamic = true)
    private String username;

    @Schema(title = "Password for basic auth")
    @PluginProperty(dynamic = true)
    private String password;

    @Schema(title = "URL scheme")
    @PluginProperty(dynamic = true)
    @Builder.Default
    private String scheme = "https";

    @Schema(title = "Skip SSL verification")
    @PluginProperty
    @Builder.Default
    private Boolean insecureSkipVerify = true;

    @Schema(title = "Splunk search query", description = "SPL query to execute, e.g., 'search index=main | head 10'")
    @PluginProperty(dynamic = true)
    @NotNull
    private String query;

    @Schema(title = "Output mode", description = "json or csv")
    @PluginProperty(dynamic = true)
    @Builder.Default
    private String outputMode = "json";

    @Schema(title = "Max results to fetch per batch")
    @PluginProperty
    @Builder.Default
    private Integer batchSize = 10000;

    @Schema(title = "Poll interval in seconds while waiting for search job")
    @PluginProperty
    @Builder.Default
    private Integer pollIntervalSeconds = 2;

    @Schema(title = "Max wait time for search job in seconds")
    @PluginProperty
    @Builder.Default
    private Integer maxWaitSeconds = 3600;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Output run(RunContext runContext) throws Exception {
        String renderedHost = runContext.render(host);
        String renderedPort = runContext.render(port);
        String renderedScheme = runContext.render(scheme);
        String renderedQuery = runContext.render(query);
        String renderedOutputMode = runContext.render(outputMode);

        String baseUrl = renderedScheme + "://" + renderedHost + ":" + renderedPort;
        String searchEndpoint = baseUrl + "/services/search/jobs";

        OkHttpClient client = buildClient();

        // Step 1: Create search job
        runContext.logger().info("Starting Splunk search job: {}", renderedQuery);
        RequestBody createBody = new FormBody.Builder()
            .add("search", renderedQuery)
            .add("output_mode", "json")
            .build();

        Request createRequest = addAuth(new Request.Builder()
            .url(searchEndpoint)
            .post(createBody), runContext)
            .build();

        String sid;
        try (Response response = client.newCall(createRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create search job: " + response.code() + " " + response.body().string());
            }
            Map<String, Object> body = MAPPER.readValue(response.body().string(), new TypeReference<>() {});
            sid = (String) body.get("sid");
        }
        runContext.logger().info("Search job created with SID: {}", sid);

        // Step 2: Poll for completion
        String statusUrl = searchEndpoint + "/" + sid;
        long deadline = System.currentTimeMillis() + (maxWaitSeconds * 1000L);

        while (System.currentTimeMillis() < deadline) {
            Request statusRequest = addAuth(new Request.Builder()
                .url(statusUrl + "?output_mode=json")
                .get(), runContext)
                .build();

            try (Response response = client.newCall(statusRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to check job status: " + response.code());
                }
                Map<String, Object> body = MAPPER.readValue(response.body().string(), new TypeReference<>() {});
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entry = (List<Map<String, Object>>) body.get("entry");
                @SuppressWarnings("unchecked")
                Map<String, Object> content = (Map<String, Object>) entry.get(0).get("content");
                Boolean isDone = (Boolean) content.get("isDone");
                if (Boolean.TRUE.equals(isDone)) {
                    runContext.logger().info("Search job {} completed", sid);
                    break;
                }
            }
            Thread.sleep(pollIntervalSeconds * 1000L);
        }

        // Step 3: Fetch results with pagination
        String resultsUrl = searchEndpoint + "/" + sid + "/results";
        List<Map<String, Object>> allResults = new ArrayList<>();
        int offset = 0;

        while (true) {
            Request resultsRequest = addAuth(new Request.Builder()
                .url(resultsUrl + "?output_mode=json&count=" + batchSize + "&offset=" + offset)
                .get(), runContext)
                .build();

            try (Response response = client.newCall(resultsRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to fetch results: " + response.code());
                }
                Map<String, Object> body = MAPPER.readValue(response.body().string(), new TypeReference<>() {});
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
                if (results == null || results.isEmpty()) {
                    break;
                }
                allResults.addAll(results);
                if (results.size() < batchSize) {
                    break;
                }
                offset += batchSize;
            }
        }

        runContext.logger().info("Fetched {} results from Splunk", allResults.size());
        runContext.metric(Counter.of("results.count", allResults.size()));

        // Write results to internal storage
        File tempFile = runContext.workingDir().createTempFile(".json").toFile();
        MAPPER.writeValue(tempFile, allResults);
        URI storedUri = runContext.storage().putFile(tempFile);

        return Output.builder()
            .uri(storedUri)
            .resultCount(allResults.size())
            .sid(sid)
            .build();
    }

    private OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(insecureSkipVerify)) {
            try {
                javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    }
                };
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
                sslContext.init(null, trustAll, new java.security.SecureRandom());
                builder.sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAll[0]);
                builder.hostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure insecure SSL", e);
            }
        }

        return builder.build();
    }

    private Request.Builder addAuth(Request.Builder builder, RunContext runContext) throws Exception {
        String renderedToken = token != null ? runContext.render(token) : null;
        String renderedUsername = username != null ? runContext.render(username) : null;
        String renderedPassword = password != null ? runContext.render(password) : null;

        if (renderedToken != null && !renderedToken.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + renderedToken);
        } else if (renderedUsername != null && renderedPassword != null) {
            String credentials = Base64.getEncoder().encodeToString(
                (renderedUsername + ":" + renderedPassword).getBytes(StandardCharsets.UTF_8)
            );
            builder.addHeader("Authorization", "Basic " + credentials);
        } else {
            throw new IllegalArgumentException("Either token or username+password must be provided");
        }
        return builder;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "URI to the stored results file (JSON)")
        private final URI uri;

        @Schema(title = "Number of results returned")
        private final Integer resultCount;

        @Schema(title = "Splunk search job SID")
        private final String sid;
    }
}
