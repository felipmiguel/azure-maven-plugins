/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.springcloud;

import com.azure.resourcemanager.appplatform.models.DeploymentInstance;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import rx.Observable;
import rx.schedulers.Schedulers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class Utils {
    private static final int POLLING_INTERVAL = 1;

//    protected static final List<String> DEPLOYMENT_PROCESSING_STATUS =
//            Arrays.asList(DeploymentResourceStatus.COMPILING.toString(), DeploymentResourceStatus.ALLOCATING.toString(), DeploymentResourceStatus.UPGRADING.toString());

    public static boolean isDeploymentDone(@Nullable SpringCloudDeployment deployment) {
        if (deployment == null) {
            return false;
        }
        final List<DeploymentInstance> instances = deployment.getInstances();
        if (CollectionUtils.isEmpty(instances)) {
            return false;
        }
        // refer to https://learn.microsoft.com/en-us/azure/spring-apps/concept-app-status
        final boolean isInstanceRunning = instances.stream().anyMatch(instance ->
            StringUtils.equalsIgnoreCase(instance.status(), "running"));
        if (deployment.getParent().getParent().isEnterpriseTier()) {
            // refer to https://learn.microsoft.com/en-us/azure/spring-apps/concept-app-status
            // Eureka isn't applicable to enterprise tier.
            return isInstanceRunning;
        }
        final String finalDiscoverStatus = BooleanUtils.isTrue(deployment.isActive()) ? "UP" : "OUT_OF_SERVICE";
        final boolean isInstanceDiscoverable = instances.stream().anyMatch(instance ->
            StringUtils.equalsIgnoreCase(instance.discoveryStatus(), finalDiscoverStatus));
        return isInstanceRunning && isInstanceDiscoverable;
    }

    /**
     * Get resource repeatedly until it match the predicate or timeout, will return null when meet exception
     * with default pollingInterval = 1s
     *
     * @param callable         callable to get resource
     * @param predicate        function that evaluate the resource
     * @param timeOutInSeconds max time for the method
     * @return the first resource which fit the predicate or the last result before timeout
     */
    public static <T> T pollUntil(Callable<T> callable, @Nonnull Predicate<T> predicate, int timeOutInSeconds) {
        return Utils.pollUntil(callable, predicate, timeOutInSeconds, POLLING_INTERVAL);
    }

    /**
     * Get resource repeatedly until it match the predicate or timeout, will return null when meet exception
     *
     * @param callable         callable to get resource
     * @param predicate        function that evaluate the resource
     * @param timeOutInSeconds max time for the method
     * @param pollingInterval  polling interval
     * @return the first resource which fit the predicate or the last result before timeout
     */
    public static <T> T pollUntil(Callable<T> callable, @Nonnull Predicate<T> predicate, int timeOutInSeconds, int pollingInterval) {
        final long timeout = System.currentTimeMillis() + timeOutInSeconds * 1000L;
        return Observable.interval(pollingInterval, TimeUnit.SECONDS)
            .timeout(timeOutInSeconds, TimeUnit.SECONDS)
            .flatMap(aLong -> Observable.fromCallable(callable))
            .subscribeOn(Schedulers.io())
            .takeUntil(resource -> predicate.test(resource) || System.currentTimeMillis() > timeout)
            .toBlocking().last();
    }
}
