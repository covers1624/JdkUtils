package net.covers1624.jdkutils;

import net.covers1624.quack.annotation.Requires;
import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.platform.Architecture;
import net.covers1624.quack.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static net.covers1624.quack.collection.ColUtils.iterable;

/**
 * Capable of locating Java installations on the current system.
 * <p>
 * Created by covers1624 on 28/10/21.
 */
@Requires ("org.slf4j:slf4j-api")
@Requires ("net.rubygrapefruit:native-platform")
@Requires ("net.rubygrapefruit:native-platform-windows-amd64")
@Requires ("net.rubygrapefruit:native-platform-windows-i386")
public abstract class JavaLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaLocator.class);
    private static final boolean DEBUG = Boolean.getBoolean("net.covers1624.jdkutils.JavaLocator.debug");
    protected final LocatorProps props;

    protected JavaLocator(LocatorProps props) {
        this.props = props;
    }

    /**
     * Create a {@link Builder} for a {@link JavaLocator} for the current system.
     *
     * @return A builder.
     */
    public static Builder builder() {
        OperatingSystem os = OperatingSystem.current();
        if (!os.isWindows() && !os.isMacos() && !os.isLinux()) {
            throw new UnsupportedOperationException("Unsupported Operating System. " + os);
        }
        return new Builder(os);
    }

    /**
     * Performs the searching of Java installations.
     * <p>
     * The result of this should be cached.
     * <p>
     * This can be re-run at any time.
     *
     * @return The located Java installations.
     * @throws IOException If an IO error occurred whilst searching folders, etc.
     */
    public abstract List<JavaInstall> findJavaVersions() throws IOException;

    // Internal used by JavaLocator implementations.
    protected void findJavasInFolder(Map<String, JavaInstall> installs, Path folder) throws IOException {
        if (Files.notExists(folder)) return;
        for (Path path : iterable(Files.list(folder))) {
            if (!Files.isDirectory(path)) continue;
            Path javaExecutable = getJavaExecutable(JavaInstall.getHomeDirectory(path));
            JavaInstall install = parseInstall(javaExecutable);
            considerJava(installs, install);
        }
    }

    // Internal used by JavaLocator implementations
    protected void considerJava(Map<String, JavaInstall> installs, @Nullable JavaInstall install) {
        if (install == null) return;
        if (props.filter != null && props.filter != install.langVersion) return;
        if (props.ignoreOpenJ9 && install.isOpenJ9) return;
        if (props.ignoreJres && !install.hasCompiler) return;

        if (!installs.containsKey(install.javaHome.toString())) {
            installs.put(install.javaHome.toString(), install);
        }
    }

    protected Path getJavaExecutable(Path javaHome) {
        return JavaInstall.getJavaExecutable(javaHome, props.useJavaw);
    }

    @Nullable
    public static JavaInstall parseInstall(Path executable) {
        if (DEBUG) {
            LOGGER.info("Attempting to parse install from executable '{}'", executable);
        }
        if (Files.notExists(executable)) return null;
        try {
            Path tempDir = Files.createTempDirectory("java_prop_extract");
            JavaPropExtractGenerator.writeClass(tempDir);
            List<String> args = new LinkedList<>(Arrays.asList(
                    executable.normalize().toAbsolutePath().toString(),
                    "-Dfile.encoding=UTF8",
                    "-cp",
                    ".",
                    "PropExtract"
            ));
            Collections.addAll(args, JavaPropExtractGenerator.DEFAULTS);
            ProcessBuilder builder = new ProcessBuilder()
                    .directory(tempDir.toFile())
                    .command(args);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Process process = builder.start();
            IOUtils.copy(process.getInputStream(), os);
            try {
                boolean exited = process.waitFor(30, TimeUnit.SECONDS);
                if (!exited) {
                    LOGGER.warn("Waited more than 30 seconds for {}. Force closing..", executable);
                    process.destroyForcibly();
                    return null;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted.", e);
            }

            Map<String, String> properties = new HashMap<>();
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] split = line.split("=", 2);
                    if (split.length != 2) continue;
                    properties.put(split[0], split[1]);
                }
            }

            Path javaHome = Paths.get(requireNonNull(properties.get("java.home"), "Missing 'java.home' property for vm: " + executable)).toAbsolutePath();

            // If we are in a 'jre' folder and the parent has a 'bin' folder, then the parent is our java home directory.
            if (javaHome.getFileName().toString().equals("jre") && Files.exists(javaHome.getParent().resolve("bin"))) {
                javaHome = javaHome.getParent();
            }

            return new JavaInstall(
                    javaHome,
                    requireNonNull(properties.get("java.vendor"), "Missing 'java.vendor' property for vm: " + executable),
                    requireNonNull(properties.get("java.vm.name"), "Missing 'java.name' property for vm: " + executable),
                    requireNonNull(properties.get("java.version"), "Missing 'java.version' property for vm: " + executable),
                    requireNonNull(properties.get("java.runtime.name"), "Missing 'java.runtime.name' property for vm: " + executable),
                    requireNonNull(properties.get("java.runtime.version"), "Missing 'java.runtime.version' property for vm: " + executable),
                    Architecture.parse(requireNonNull(properties.get("os.arch"), "Missing 'os.arch' property for vm: " + executable))
            );
        } catch (Throwable e) {
            if (JavaLocator.DEBUG) {
                LOGGER.error("Failed to parse Java install.", e);
            }
            return null;
        }
    }

    /**
     * A Builder to build a {@link JavaLocator}.
     */
    public static class Builder {

        private final OperatingSystem os;
        private boolean useJavaw;
        private boolean findIntellijJdks;
        private boolean findGradleJdks;
        private boolean ignoreOpenJ9;
        private boolean ignoreJres;
        @Nullable
        private JavaVersion filter;

        private Builder(OperatingSystem os) {
            this.os = os;
        }

        /**
         * If the {@link WindowsJavaLocator} should use 'javaw.exe' instead.
         *
         * @return The same builder.
         */
        public Builder useJavaw() {
            useJavaw = true;
            return this;
        }

        /**
         * If all {@link JavaLocator}s should locate JDK's provisioned by Intellij.
         *
         * @return The same builder.
         */
        public Builder findIntellijJdks() {
            findIntellijJdks = true;
            return this;
        }

        /**
         * If all {@link JavaLocator}s should locate JDK's provisioned by Gradle.
         *
         * @return The same builder.
         */
        public Builder findGradleJdks() {
            findGradleJdks = true;
            return this;
        }

        /**
         * If OpenJ9 should be ignored.
         * <p>
         * OpenJ9 is known to cause issues due to it fundamentally changing large portions
         * of the JVM, compared to the HotSpot standard.
         *
         * @return The same builder.
         */
        public Builder ignoreOpenJ9() {
            ignoreOpenJ9 = true;
            return this;
        }

        /**
         * If Java installations not containing a compiler should be ignored.
         *
         * @return The same builder.
         */
        public Builder ignoreJres() {
            ignoreJres = true;
            return this;
        }

        /**
         * If all {@link JavaLocator}s should filter for a specific Java version.
         *
         * @param filter The filter.
         * @return The same builder.
         */
        public Builder filter(JavaVersion filter) {
            this.filter = filter;
            return this;
        }

        /**
         * The built {@link JavaLocator}.
         *
         * @return The locator.
         */
        public JavaLocator build() {
            LocatorProps props = new LocatorProps(
                    os, useJavaw,
                    findIntellijJdks,
                    findGradleJdks,
                    ignoreOpenJ9,
                    ignoreJres,
                    filter
            );
            if (os.isWindows()) {
                return new WindowsJavaLocator(props);
            }
            if (os.isLinux()) {
                return new LinuxJavaLocator(props);
            }
            if (os.isMacos()) {
                return new MacosJavaLocator(props);
            }
            throw new AssertionError("Not implemented yet.");
        }
    }

    /**
     * Properties for a Locator. Internal.
     */
    public static class LocatorProps {

        public final OperatingSystem os;
        public final boolean useJavaw;
        public final boolean findIntellijJdks;
        public final boolean findGradleJdks;
        public final boolean ignoreOpenJ9;
        public final boolean ignoreJres;
        @Nullable
        public final JavaVersion filter;

        public LocatorProps(OperatingSystem os, boolean useJavaw, boolean findIntellijJdks, boolean findGradleJdks, boolean ignoreOpenJ9, boolean ignoreJres, @Nullable JavaVersion filter) {
            this.os = os;
            this.useJavaw = useJavaw;
            this.findIntellijJdks = findIntellijJdks;
            this.findGradleJdks = findGradleJdks;
            this.ignoreOpenJ9 = ignoreOpenJ9;
            this.ignoreJres = ignoreJres;
            this.filter = filter;
        }
    }
}
