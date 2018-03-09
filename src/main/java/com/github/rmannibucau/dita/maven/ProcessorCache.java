package com.github.rmannibucau.dita.maven;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.maven.plugin.logging.Log;
import org.slf4j.Logger;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProcessorCache {
    private final ClassLoader loader;
    private final File ditaDir;
    private final File tempDir;
    private final String transtype;
    private final boolean cleanOnFailure;
    private final boolean createDebugLog;
    private final String mode;
    private final Map<String, String> properties;
    private final Log log;

    private final Queue<Object> factories = new ConcurrentLinkedQueue<>();

    public void withProcessor(final Consumer<Object> consumer) {
        final Object poll = getFactory();
        try {
            inContext(() -> {
                try {
                    final Object processor = poll.getClass().getMethod("newProcessor", String.class).invoke(poll, transtype);
                    set(processor, "cleanOnFailure", cleanOnFailure, boolean.class);
                    set(processor, "createDebugLog", createDebugLog, boolean.class);
                    set(processor, "setProperties", properties, Map.class);
                    set(processor, "setLogger", new MavenLoggerBridge(log), Logger.class);

                    final Class<?> mode = loader.loadClass("org.dita.dost.util.Configuration$Mode");
                    set(processor, "setMode", mode.getMethod("valueOf", String.class).invoke(null, this.mode), mode);

                    consumer.accept(processor);
                } catch (final ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
                    throw new IllegalStateException(e);
                } catch (final InvocationTargetException e) {
                    throw new IllegalStateException(e.getTargetException());
                }
                return null;
            });
        } finally {
            factories.offer(poll);
        }
    }

    private void set(final Object instance, final String mtd, final Object value, final Class<?> type) {
        try {
            instance.getClass().getMethod(mtd, type).invoke(instance, value);
        } catch (final NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (final InvocationTargetException e) {
            throw new IllegalStateException(e.getTargetException());
        }
    }

    private Object getFactory() {
        Object poll = factories.poll();
        if (poll == null) {
            poll = inContext(() -> {
                try {
                    final Class<?> pf = loader.loadClass("org.dita.dost.ProcessorFactory");
                    final Object instance = pf.getMethod("newInstance", File.class).invoke(null, ditaDir);
                    pf.getMethod("setBaseTempDir", File.class).invoke(instance, tempDir);
                    return instance;
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        return poll;
    }

    private <T> T inContext(final Supplier<T> supplier) {
        final Thread thread = Thread.currentThread();
        final ClassLoader old = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return supplier.get();
        } finally {
            thread.setContextClassLoader(old);
        }
    }
}
