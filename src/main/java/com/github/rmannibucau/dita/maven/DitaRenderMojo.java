package com.github.rmannibucau.dita.maven;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PROCESS_SOURCES;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

@Mojo(defaultPhase = PROCESS_SOURCES, name = "render")
public class DitaRenderMojo extends AbstractMojo {

    @Parameter(property = "dita.ditaDir", defaultValue = "${project.basedir}/src/main/dita")
    private File ditaDir;

    @Parameter(property = "dita.patterns", defaultValue = "dm\\-.*ditamap")
    private Collection<String> patterns;

    @Parameter(property = "dita.includes")
    private Collection<String> includes;

    @Parameter(property = "dita.outputDir", defaultValue = "${project.build.directory}/dita/output")
    protected File outputDir;

    @Parameter(property = "dita.ditaTempDir", defaultValue = "${project.build.directory}/dita/temp")
    protected File ditaTempDir;

    @Parameter(property = "dita.transtype", defaultValue = "html5")
    private String transtype;

    @Parameter(property = "dita.cleanOnFailure", defaultValue = "true")
    private boolean cleanOnFailure;

    @Parameter(property = "dita.createDebugLog", defaultValue = "false")
    private boolean createDebugLog;

    @Parameter(property = "dita.parallelism", defaultValue = "-1")
    private int parallelism;

    @Parameter(property = "dita.properties")
    protected Map<String, String> properties;

    @Parameter(property = "dita.mode", defaultValue = "STRICT")
    private String mode;

    @Parameter(property = "dita.version", defaultValue = "3.0.2")
    private String ditaVersion;

    @Parameter(property = "dita.downloadUrl", defaultValue = "https://github.com/dita-ot/dita-ot/releases/download/$version/dita-ot-$version.zip")
    private String ditaDownloadUrl;

    @Parameter(property = "dita.cacheDistribution", defaultValue = "true")
    private boolean cacheDistribution;

    @Parameter(defaultValue = "${settings.localRepository}")
    private File localRepository;

    @Component
    private ArtifactResolver resolver;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File distribution;
        try {
            distribution = findDistribution();
        } catch (final MalformedURLException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        doExecute(distribution);
    }

    private void doExecute(final File distribution) throws MojoFailureException {
        final Collection<Throwable> errors = new ArrayList<>();
        try (final URLClassLoader loader = new URLClassLoader(findClassLoaderUrls(distribution),
                Thread.currentThread().getContextClassLoader())) {

            final ProcessorCache processors = new ProcessorCache(loader, distribution, ditaTempDir, transtype, cleanOnFailure,
                    createDebugLog, mode, properties, getLog());

            final Path srcPath = ditaDir.toPath();
            final Collection<Pattern> patterns = (this.patterns == null ? Stream.<Pattern> empty()
                    : this.patterns.stream().map(Pattern::compile)).collect(toSet());

            final Executor executor = parallelism == 0 ? Runnable::run
                    : Executors.newFixedThreadPool(parallelism < 0 ? Runtime.getRuntime().availableProcessors() : parallelism);
            final Semaphore semaphore = new Semaphore(0);
            long count = 0;
            try {
                count = Stream.of(Objects.requireNonNull(ditaDir.listFiles(), "no dita children for " + ditaDir))
                              .filter(f ->
                                (includes != null && includes.contains(f.getName()))
                                || (includes == null && patterns.isEmpty() || patterns.stream().anyMatch(e -> e.matcher(f.getName()).matches())))
                              .peek(file -> executor.execute(() -> processors.withProcessor(processor -> {
                                  try {
                                      final File output = new File(outputDir,
                                              srcPath.relativize(file.getParentFile().toPath()).toString());
                                      getLog().info("Processing " + file.getAbsolutePath());

                                      final Class<?> pc = processor.getClass();

                                      pc.getMethod("setInput", File.class).invoke(processor, file.getAbsoluteFile());
                                      pc.getMethod("setOutputDir", File.class).invoke(processor, output);
                                      pc.getMethod("run").invoke(processor);
                                  } catch (final NoSuchMethodException | IllegalAccessException e) {
                                      throw new IllegalStateException(e);
                                  } catch (final InvocationTargetException e) {
                                      errors.add(e.getTargetException());
                                      throw new IllegalStateException(e.getTargetException());
                                  } finally {
                                      getLog().info("Finished processing: " + file);
                                      semaphore.release();
                                  }
                              }))).count();
            } finally {
                if (ExecutorService.class.isInstance(executor)) {
                    final ExecutorService es = ExecutorService.class.cast(executor);
                    es.shutdown();
                    try {
                        semaphore.acquire((int) count);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            getLog().info("Rendered " + count + " files");
            if (!errors.isEmpty()) {
                final MojoFailureException exception = new MojoFailureException("Some errors occured:\n"
                        + errors.stream().map(e -> e.getMessage() == null ? e.getCause().getMessage() : e.getMessage())
                                .collect(joining("\n  -", "  -", "")));
                errors.forEach(exception::addSuppressed);
                throw exception;
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private URL[] findClassLoaderUrls(final File distribution) {
        final File config = new File(distribution, "config");
        final File lib = new File(distribution, "lib");
        if (!config.isDirectory() || !lib.isDirectory()) {
            throw new IllegalStateException("No config or lib folder in " + distribution);
        }
        return Stream.concat(Stream.of(config, lib),
                Stream.of(Objects.requireNonNull(lib.listFiles((dir, name) -> name.endsWith(".jar"))))).map(f -> {
                    try {
                        return f.toURI().toURL();
                    } catch (final MalformedURLException e) {
                        throw new IllegalStateException(e);
                    }
                }).toArray(URL[]::new);
    }

    private File findDistribution() throws MalformedURLException, MojoExecutionException {
        final File downloadOutput = new File(ditaTempDir, "dita_distribution");
        if (downloadOutput.exists()) {
            return downloadOutput;
        }
        final File zip = findDistributionZip(new URL(ditaDownloadUrl.replace("$version", ditaVersion)), downloadOutput);
        unzip(zip, downloadOutput, true);
        return downloadOutput;
    }

    private File findDistributionZip(final URL url, final File downloadOutput) throws MojoExecutionException {
        File zip;
        final Artifact artifact = new DefaultArtifact("com.github.rmannibucau.dita.maven", "dita-distribution", "zip",
                ditaVersion);
        try {
            final ArtifactResult artifactResult = resolver.resolveArtifact(session.getRepositorySession(),
                    new ArtifactRequest(artifact, emptyList(), null));
            if (artifactResult.isMissing()) {
                throw new IllegalStateException("Didn't find dita distribution");
            }
            zip = artifactResult.getArtifact().getFile();
        } catch (final ArtifactResolutionException e) {
            zip = new File(downloadOutput.getParentFile(), downloadOutput.getName() + ".zip");
            zip.getParentFile().mkdirs();
            try (final InputStream source = url.openStream()) {
                Files.copy(source, zip.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (final IOException e1) {
                throw new MojoExecutionException(e1.getMessage(), e1);
            }

            if (cacheDistribution) {
                final File localLocation = new File(localRepository,
                        artifact.getGroupId().replace(".", "/") + '/' + artifact.getArtifactId() + '/' + artifact.getVersion()
                                + '/' + artifact.getArtifactId() + '-' + artifact.getVersion() + '.' + artifact.getExtension());
                localLocation.getParentFile().mkdirs();
                if (!localLocation.exists()) {
                    try {
                        Files.copy(zip.toPath(), localLocation.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (final IOException e1) {
                        throw new MojoExecutionException(e1.getMessage(), e1);
                    }
                }
            }
        }
        return zip;
    }

    private void unzip(final File zipFile, final File destination, final boolean noparent) {
        getLog().info(String.format("Extracting '%s' to '%s'", zipFile.getAbsolutePath(), destination.getAbsolutePath()));
        try {
            final ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String path = entry.getName();
                if (noparent) {
                    path = path.replaceFirst("^[^/]+/", "");
                }
                final File file = new File(destination, path);

                if (entry.isDirectory()) {
                    file.mkdirs();
                    continue;
                }

                file.getParentFile().mkdirs();
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            in.close();
        } catch (final Exception e) {
            throw new IllegalStateException("Unable to unzip " + zipFile.getAbsolutePath(), e);
        }
    }
}
