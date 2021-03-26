/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.cache;

import lombok.extern.java.Log;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

@Log
public class Preloader {

    private static final String INVALID_PRELOAD_METHOD = "@Preload annotated method(%s.%s) should have (no args or only varargs) " +
            "and must be (static or in a singleton class)";

    public static Collection<Method> load() {
        log.log(Level.INFO, "Start Scanning for @Preload");
        final Set<Method> methods = getPreloadingMethods();
        log.log(Level.INFO, String.format("Found %d @Preload annotated methods.", methods.size()));
        log.log(Level.INFO, "End Scanning for @Preload");
        log.log(Level.INFO, "Start Preloading");
        methods.parallelStream().forEach((m) -> {
            Object instance = null;
            // TODO: maybe support predefined variables, e.g. selected subscriptions
            if ((m.getParameterCount() == 0 || m.isVarArgs())
                    && (Modifier.isStatic(m.getModifiers()) || Objects.nonNull(instance = getSingleton(m)))) {
                log.log(Level.INFO, String.format("preloading [%s]", m.getName()));
                invoke(m, instance);
                log.log(Level.INFO, String.format("preloaded [%s]", m.getName()));
            } else {
                log.warning(String.format(INVALID_PRELOAD_METHOD, m.getDeclaringClass().getSimpleName(), m.getName()));
            }
        });
        log.log(Level.INFO, "End Preloading");
        return methods;
    }

    private static void invoke(final Method m, final Object instance) {
        try {
            if (m.isVarArgs()) {
                final Class<?> varargType = m.getParameterTypes()[0].getComponentType();
                m.invoke(instance, Array.newInstance(varargType, 0));
            } else {
                m.invoke(instance);
            }
        } catch (final IllegalAccessException | InvocationTargetException ignored) {
            // swallow all exceptions
        }
    }

    @Nullable
    private static Object getSingleton(final Method m) {
        final Class<?> clazz = m.getDeclaringClass();
        try {
            final Method getInstance = clazz.getDeclaredMethod("getInstance");
            if (Modifier.isStatic(getInstance.getModifiers()) && getInstance.getParameterCount() == 0) {
                getInstance.setAccessible(true);
                return getInstance.invoke(null);
            }
        } catch (final IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            // swallow all exceptions
        }
        return null;
    }

    private static Set<Method> getPreloadingMethods() {
        final ConfigurationBuilder configuration = new ConfigurationBuilder()
                .forPackages("com.microsoft.azure.toolkit","com.microsoft.azuretools")
                .setScanners(new MethodAnnotationsScanner());
        final Reflections reflections = new Reflections(configuration);
        return reflections.getMethodsAnnotatedWith(Preload.class);
    }
}
