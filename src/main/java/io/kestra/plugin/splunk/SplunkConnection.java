package io.kestra.plugin.splunk;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Common Splunk connection properties shared by all tasks.
 */
public interface SplunkConnection {

    @Schema(title = "Splunk host URL", description = "e.g., https://michmed.splunkcloud.com")
    @PluginProperty(dynamic = true)
    @NotNull
    String getHost();

    @Schema(title = "Splunk management port", description = "Default 8089 for REST API, 8088 for HEC")
    @PluginProperty(dynamic = true)
    String getPort();

    @Schema(title = "Authentication token", description = "Bearer token for Splunk REST API or HEC token")
    @PluginProperty(dynamic = true)
    String getToken();

    @Schema(title = "Username for basic auth", description = "Used when token is not provided")
    @PluginProperty(dynamic = true)
    String getUsername();

    @Schema(title = "Password for basic auth", description = "Used when token is not provided")
    @PluginProperty(dynamic = true)
    String getPassword();

    @Schema(title = "Scheme", description = "http or https", defaultValue = "https")
    @PluginProperty(dynamic = true)
    String getScheme();

    @Schema(title = "Verify SSL", description = "Whether to verify SSL certificates", defaultValue = "false")
    @PluginProperty
    Boolean getInsecureSkipVerify();
}
