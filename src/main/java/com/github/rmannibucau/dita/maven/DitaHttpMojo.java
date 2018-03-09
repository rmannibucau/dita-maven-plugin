package com.github.rmannibucau.dita.maven;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.meecrowave.Meecrowave;

@Mojo(name = "http")
public class DitaHttpMojo extends DitaRenderMojo {

    @Parameter(property = "dita.port", defaultValue = "8080")
    private int port;

    @Override
    public void execute() {
        outputDir.mkdirs(); // ensure it exists before starting the server

        final AtomicBoolean running = new AtomicBoolean(true);
        final Semaphore renderSemaphore = new Semaphore(1);
        final Thread renderingThread = new Thread(() -> {
            while (running.get()) {
                try {
                    renderSemaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (running.get()) {
                    try {
                        super.execute();
                    } catch (MojoExecutionException | MojoFailureException e) {
                        getLog().error(e.getMessage(), e);
                    }
                }
            }
        });
        renderingThread.setName("dita-rendering-thread");
        renderingThread.start();

        final Meecrowave.Builder builder = new Meecrowave.Builder();
        builder.setUseLog4j2JulLogManager(false);
        builder.setLoggingGlobalSetup(false);
        builder.setUseShutdownHook(false);
        builder.setWebResourceCached(false);
        builder.setHttpPort(port);
        builder.setTempDir(new File(ditaTempDir, "http-server").getAbsolutePath());
        try (final Meecrowave meecrowave = new Meecrowave(builder)) {
            meecrowave.start();
            meecrowave.deployClasspath(new Meecrowave.DeploymentMeta("", outputDir, ctx -> {}));

            final Scanner scanner = new Scanner(System.in);
            String command;
            while ((command = scanner.next()) != null) {
                if ("quit".equalsIgnoreCase(command) || "exit".equalsIgnoreCase(command)) {
                    break;
                }
                if ("reload".equalsIgnoreCase(command) || "r".equalsIgnoreCase(command)) {
                    renderSemaphore.release();
                }
            }
        } finally {
            running.set(false);
            renderSemaphore.release();
            try {
                renderingThread.join(TimeUnit.MINUTES.toMillis(1));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
