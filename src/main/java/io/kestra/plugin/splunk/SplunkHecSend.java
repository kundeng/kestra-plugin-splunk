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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send events to Splunk HTTP Event Collector (HEC)",
    description = "Posts event data to Splunk's HEC endpoint. Supports both raw string events and file-based input."
)
@Plugin(examples = {})
public class SplunkHecSend extends Task implements RunnableTask<SplunkHecSend.Output>, SplunkConnection {

    @Schema(title = "Splunk HEC host", description = "e.g., http-inputs-michmed.splunkcloud.com")
    @PluginProperty(dynamic = true)
    @NotNull
    private String host;

    @Schema(title = "Splunk HEC port")
    @PluginProperty(dynamic = true)
    @Builder.Default
    private String port = "443";

    @Schema(title = "Splunk HEC token")
    @PluginProperty(dynamic = true)
    @NotNull
    private String token;

    @Schema(title = "Username (not used for HEC)")
    @PluginProperty(dynamic = true)
    private String username;

    @Schema(title = "Password (not used for HEC)")
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

    @Schema(title = "Event data", description = "Raw event data string or JSON. If inputFile is provided, this is ignored.")
    @PluginProperty(dynamic = true)
    private String eventData;

    @Schema(title = "Input file URI", description = "Kestra internal storage URI containing event data (JSON)")
    @PluginProperty(dynamic = true)
    private String inputFile;

    @Schema(title = "Splunk index")
    @PluginProperty(dynamic = true)
    private String index;

    @Schema(title = "Splunk sourcetype")
    @PluginProperty(dynamic = true)
    private String sourcetype;

    @Schema(title = "Splunk source")
    @PluginProperty(dynamic = true)
    private String source;

    @Schema(title = "Splunk host field")
    @PluginProperty(dynamic = true)
    private String hostField;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Output run(RunContext runContext) throws Exception {
        String renderedHost = runContext.render(host);
        String renderedPort = runContext.render(port);
        String renderedScheme = runContext.render(scheme);
        String renderedToken = runContext.render(token);

        String hecUrl = renderedScheme + "://" + renderedHost + ":" + renderedPort + "/services/collector";

        // Resolve event data
        String eventPayload;
        String renderedInputFile = inputFile != null ? runContext.render(inputFile) : null;
        if (renderedInputFile != null && !renderedInputFile.isEmpty()) {
            try (InputStream is = runContext.storage().getFile(java.net.URI.create(renderedInputFile.trim()));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                eventPayload = sb.toString();
            }
        } else if (eventData != null) {
            eventPayload = runContext.render(eventData);
        } else {
            throw new IllegalArgumentException("Either eventData or inputFile must be provided");
        }

        // Build HEC payload
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", eventPayload);

        if (index != null) payload.put("index", runContext.render(index));
        if (sourcetype != null) payload.put("sourcetype", runContext.render(sourcetype));
        if (source != null) payload.put("source", runContext.render(source));
        if (hostField != null) payload.put("host", runContext.render(hostField));

        String payloadJson = MAPPER.writeValueAsString(payload);

        runContext.logger().info("Sending event to Splunk HEC: {} ({} bytes)", hecUrl, payloadJson.length());

        OkHttpClient client = buildClient();

        RequestBody body = RequestBody.create(
            payloadJson.getBytes(StandardCharsets.UTF_8),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(hecUrl)
            .addHeader("Authorization", "Splunk " + renderedToken)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build();

        try (Response response = client.newCall(request).execute()) {
            int statusCode = response.code();
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException(
                    "Failed to send event to Splunk HEC. Code: " + statusCode + ", Body: " + responseBody
                );
            }

            runContext.logger().info("Splunk HEC response: {} {}", statusCode, response.message());
            runContext.metric(Counter.of("events.sent", 1));

            return Output.builder()
                .statusCode(statusCode)
                .statusMessage(response.message())
                .build();
        }
    }

    private OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS);

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

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "HTTP status code from Splunk HEC")
        private final Integer statusCode;

        @Schema(title = "HTTP status message")
        private final String statusMessage;
    }
}
