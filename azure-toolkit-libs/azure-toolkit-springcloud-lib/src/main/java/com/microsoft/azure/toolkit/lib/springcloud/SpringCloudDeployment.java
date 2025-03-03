/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.springcloud;

import com.azure.core.util.ExpandableStringEnum;
import com.azure.resourcemanager.appplatform.models.DeploymentInstance;
import com.azure.resourcemanager.appplatform.models.DeploymentSettings;
import com.azure.resourcemanager.appplatform.models.SpringAppDeployment;
import com.google.common.base.Charsets;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import io.netty.handler.codec.http.HttpHeaders;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class SpringCloudDeployment extends AbstractAzResource<SpringCloudDeployment, SpringCloudApp, SpringAppDeployment> {

    protected SpringCloudDeployment(@Nonnull String name, @Nonnull SpringCloudDeploymentModule module) {
        super(name, module);
    }

    /**
     * copy constructor
     */
    protected SpringCloudDeployment(@Nonnull SpringCloudDeployment origin) {
        super(origin);
    }

    protected SpringCloudDeployment(@Nonnull SpringAppDeployment remote, @Nonnull SpringCloudDeploymentModule module) {
        super(remote.name(), module);
    }

    // MODIFY
    @AzureOperation(name = "resource.start_resource.resource", params = {"this.name()"}, type = AzureOperation.Type.SERVICE)
    public void start() {
        this.doModify(() -> Objects.requireNonNull(this.getRemote()).start(), Status.STARTING);
    }

    @AzureOperation(name = "resource.stop_resource.resource", params = {"this.name()"}, type = AzureOperation.Type.SERVICE)
    public void stop() {
        this.doModify(() -> Objects.requireNonNull(this.getRemote()).stop(), Status.STOPPING);
    }

    @AzureOperation(name = "resource.restart_resource.resource", params = {"this.name()"}, type = AzureOperation.Type.SERVICE)
    public void restart() {
        this.doModify(() -> Objects.requireNonNull(this.getRemote()).restart(), Status.RESTARTING);
    }

    @Nonnull
    @Override
    public String loadStatus(@Nonnull SpringAppDeployment remote) {
        return Optional.of(remote)
            .map(SpringAppDeployment::status)
            .map(ExpandableStringEnum::toString)
            .orElse(Status.UNKNOWN);
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, SpringCloudDeployment, ?>> getSubModules() {
        return Collections.emptyList();
    }

    @Nonnull
    @SneakyThrows
    public Flux<String> streamLogs(final String instance) {
        return streamLogs(instance, 0, 10, 0, true);
    }

    @Nonnull
    @SneakyThrows
    public Flux<String> streamLogs(final String instance, int sinceSeconds, int tailLines, int limitBytes, boolean follow) {
        final HttpClient client = HttpClient.create().keepAlive(true);
        final URIBuilder endpoint = new URIBuilder(this.getParent().getLogStreamingEndpoint(instance));
        endpoint.addParameter("follow", String.valueOf(follow));
        if (sinceSeconds > 0) {
            endpoint.addParameter("sinceSeconds", String.valueOf(sinceSeconds));
        }
        if (tailLines > 0) {
            endpoint.addParameter("tailLines", String.valueOf(tailLines));
        }
        if (limitBytes > 0) {
            endpoint.addParameter("limitBytes", String.valueOf(limitBytes));
        }
        final String password = this.getParent().getParent().getTestKey();
        final String userPass = "primary:" + password;
        final String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userPass.getBytes()));
        final Consumer<? super HttpHeaders> headerBuilder = header -> header.set("Authorization", basicAuth);
        return client.headers(headerBuilder)
            .responseTimeout(Duration.of(10, ChronoUnit.MINUTES))
            .get()
            .uri(endpoint.build())
            .response((resp, cont) -> resp.status().code() == 200 ? cont.asString(Charsets.UTF_8) : Mono.empty());
    }

    @AzureOperation(
        name = "springcloud.wait_until_deployment_ready.deployment|app",
        params = {"this.getName()", "this.getParent().getName()"},
        type = AzureOperation.Type.SERVICE
    )
    public boolean waitUntilReady(int timeoutInSeconds) {
        AzureMessager.getMessager().info("Getting deployment status...");
        final SpringCloudDeployment deployment = Utils.pollUntil(() -> {
            this.invalidateCache();
            return this;
        }, Utils::isDeploymentDone, timeoutInSeconds);
        return Utils.isDeploymentDone(deployment);
    }

    @Nullable
    public Double getCpu() {
        return Optional.ofNullable(this.getRemote())
            .map(SpringAppDeployment::cpu)
            .orElse(null);
    }

    @Nullable
    public Double getMemoryInGB() {
        return Optional.ofNullable(this.getRemote())
            .map(SpringAppDeployment::memoryInGB)
            .orElse(null);
    }

    @Nullable
    public String getRuntimeVersion() {
        return Optional.ofNullable(this.getRemote())
            .map(SpringAppDeployment::runtimeVersion)
            .map(ExpandableStringEnum::toString)
            .orElse(null);
    }

    @Nullable
    public String getJvmOptions() {
        return Optional.ofNullable(this.getRemote())
            .map(SpringAppDeployment::jvmOptions)
            .orElse(null);
    }

    @Nullable
    public Map<String, String> getEnvironmentVariables() {
        return Optional.ofNullable(this.getRemote())
            .map(SpringAppDeployment::settings)
            .map(DeploymentSettings::environmentVariables)
            .map(v -> {
                final HashMap<String, String> variables = new HashMap<>(v);
                if (this.getParent().getParent().isEnterpriseTier() && StringUtils.isBlank(variables.get("JAVA_OPTS"))) {
                    // jvmOptions are part of environment variables in enterprise tier.
                    // refer to `com.azure.resourcemanager.appplatform.implementation.SpringAppDeploymentImpl.jvmOptions`
                    variables.remove("JAVA_OPTS");
                }
                return variables;
            }).orElse(null);
    }

    @Nonnull
    public List<DeploymentInstance> getInstances() {
        return Optional.ofNullable(this.getRemote()).map(SpringAppDeployment::instances).orElse(Collections.emptyList());
    }

    @Nullable
    public Integer getInstanceNum() {
        if (Objects.nonNull(this.getRemote())) {
            return this.getRemote().instances().size();
        }
        return null;
    }

    @Nonnull
    public Boolean isActive() {
        return Optional.ofNullable(this.getRemote()).map(SpringAppDeployment::isActive).orElse(false);
    }

    @Override
    public void setStatus(@Nonnull String status) {
        super.setStatus(status);
        // update app status when active deployment status changed
        if (this.isActive()) {
            getParent().reloadStatus();
        }
    }
}
