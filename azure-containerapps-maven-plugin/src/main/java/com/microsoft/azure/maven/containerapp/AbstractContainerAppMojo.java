/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.maven.appservice;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.microsoft.azure.maven.AbstractAzureMojo;
import com.microsoft.azure.maven.exception.MavenDecryptException;
import com.microsoft.azure.maven.model.DeploymentResource;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.account.IAzureAccount;
import com.microsoft.azure.toolkit.lib.appservice.AzureAppService;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.auth.AzureToolkitAuthenticationException;
import com.microsoft.azure.toolkit.lib.common.exception.AzureExecutionException;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.logging.Log;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.common.utils.TextUtils;
import com.microsoft.azure.toolkit.lib.legacy.appservice.DeploymentSlotSetting;
import lombok.Getter;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;


public abstract class AbstractContainerAppServiceMojo extends AbstractAzureMojo {
    protected static final String MAVEN_PLUGIN_POSTFIX = "-maven-plugin";

    /**
     * Name of the resource group. It will be created if it doesn't exist.
     */
    @JsonProperty
    @Parameter(property = "resourceGroup", required = false)
    protected String resourceGroup;

    /**
     * Name of the container app environment. It will be created if it doesn't exist.
     */
    @JsonProperty
    @Parameter(property = "environmentName", required = false)
    protected String environmentName;

    /**
     * Name of the container app. It will be created if it doesn't exist.
     */
    @JsonProperty
    @Parameter(property = "appName", required = false)
    protected String appName;

    /**
     * Application settings of App Service, in the form of name-value pairs.
     * <pre>
     * {@code
     * <appSettings>
     *         <property>
     *                 <name>setting-name</name>
     *                 <value>setting-value</value>
     *         </property>
     * </appSettings>
     * }
     * </pre>
     */
    @JsonProperty
    @Parameter
    protected Properties appSettings;

    @JsonIgnore
    protected AzureAppService appServiceClient;

    public String getResourceGroup() {
        return resourceGroup;
    }

    public String getEnvironmentName() {
        return environmentName;
    }

    public String getAppName() {
        return appName;
    }

    public Map getAppSettings() {
        if (appSettings == null) {
            appSettings = new Properties();
        }
        return appSettings;
    }

    public List<DeploymentResource> getResources() {
        return Collections.emptyList();
    }

    public String getDeploymentStagingDirectoryPath() {
        final String outputFolder = this.getPluginName().replaceAll(MAVEN_PLUGIN_POSTFIX, "");
        return Paths.get(
                this.getBuildDirectoryAbsolutePath(),
                outputFolder, this.getAppName()
        ).toString();
    }

    protected AzureAppService initAzureAppServiceClient() {
        if (appServiceClient == null) {
            try {
                final Account account = loginAzure();
                final List<Subscription> subscriptions = account.getSubscriptions();
                final String targetSubscriptionId = getTargetSubscriptionId(getSubscriptionId(), subscriptions, account.getSelectedSubscriptions());
                AbstractAzureMojo.checkSubscription(subscriptions, targetSubscriptionId);
                com.microsoft.azure.toolkit.lib.Azure.az(AzureAccount.class).account().setSelectedSubscriptions(Collections.singletonList(targetSubscriptionId));
                appServiceClient = Azure.az(AzureAppService.class);
                printCurrentSubscription(appServiceClient);
                this.subscriptionId = targetSubscriptionId;
            } catch (AzureToolkitAuthenticationException | AzureExecutionException | IOException | MavenDecryptException e) {
                throw new AzureToolkitRuntimeException("Cannot authenticate", e);
            }
        }
        return appServiceClient;
    }

    // todo: Replace same method in AbstractAzureMojo after function track2 migration
    protected void printCurrentSubscription(AzureAppService appServiceClient) {
        if (appServiceClient == null) {
            return;
        }
        final List<Subscription> subscriptions = Azure.az(IAzureAccount.class).account().getSelectedSubscriptions();
        final Subscription subscription = subscriptions.get(0);
        if (subscription != null) {
            Log.info(String.format(AbstractAzureMojo.SUBSCRIPTION_TEMPLATE, TextUtils.cyan(subscription.getName()), TextUtils.cyan(subscription.getId())));
        }
    }
}
