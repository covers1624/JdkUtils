package net.covers1624.jdkutils;

import net.covers1624.jdkutils.locator.JavaLocator;
import net.covers1624.jdkutils.utils.JavaPropExtractGenerator;
import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.platform.Architecture;
import net.covers1624.quack.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Defines properties and helpers for specific Java installations.
 * <p>
 * A {@link JavaInstall} can be extracted form a specific known Java installation using
 * the {@link JavaInstall#parse(Path)}.
 * <p>
 * One can extract all known installed Java installations off a given System using {@link JavaLocator} and the
 * associated Builder.
 * <p>
 * Concepts:<br/>
 * Installation Directory - A Java installation directory refers to the root directory of the java installation.
 * This directory usually has the java version within its name. However, is not guaranteed.
 * <p>
 * Java Home - Java home in this context is defined as the location in which the <code>bin</code> folder
 * containing Java executables can be found. On Linux and Windows, this is generally the same as the
 * Installation Directory, However on macOs this is usually the <code>Contents/Home/</code> folder within
 * the Installation Directory.
 * <p>
 * Created by covers1624 on 30/10/21.
 */
public class JavaInstall {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaInstall.class);
    private static final boolean DEBUG = Boolean.getBoolean("net.covers1624.jdkutils.JavaInstall.debug");

    public final JavaVersion langVersion;
    public final Path javaHome;
    public final String vendor;
    public final String implName;
    public final String implVersion;
    public final String runtimeName;
    public final String runtimeVersion;
    public final Architecture architecture;
    public final boolean isOpenJ9;
    public final boolean hasCompiler;

    public JavaInstall(Path javaHome, String vendor, String implName, String implVersion, String runtimeName, String runtimeVersion, Architecture architecture) {
        langVersion = requireNonNull(JavaVersion.parse(implVersion), "Unable to parse java version: " + implVersion);
        this.javaHome = javaHome;
        this.vendor = vendor;
        this.implName = implName;
        this.implVersion = implVersion;
        this.runtimeName = runtimeName;
        this.runtimeVersion = runtimeVersion;
        this.architecture = architecture;
        isOpenJ9 = implName.contains("J9");
        // If the installation has javac, It's highly likely it's a full JDK.
        hasCompiler = Files.exists(getExecutable(javaHome, "javac"));
    }

    /**
     * Gets the bin directory for a given java installation.
     *
     * @param installationDir The installation directory.
     * @return The bin directory.
     */
    public static Path getBinDirectory(Path installationDir) {
        return getHomeDirectory(installationDir).resolve("bin");
    }

    /**
     * Gets the Java home directory from a specific Installation.
     * This method uses the detected currently running Operating System to
     * determine how to transform the given Installation Directory into a
     * Java Home Directory and is not stable across Operating Systems.
     *
     * @param installationDir The installation directory.
     * @return The Java home.
     */
    public static Path getHomeDirectory(Path installationDir) {
        if (OperatingSystem.current().isMacos()) {
            return installationDir.resolve("Contents/Home");
        }
        return installationDir;
    }

    /**
     * Gets the Java executable for a given home directory.
     *
     * @param homeDir  The java home directory.
     * @param useJavaw If <code>javaw</code> should be used on Windows instead of <code>java</code>.
     * @return The executable.
     * @see JavaInstall Root javadoc contains definitions for <code>homeDir</code>
     */
    public static Path getJavaExecutable(Path homeDir, boolean useJavaw) {
        OperatingSystem os = OperatingSystem.current();
        return getExecutable(homeDir, os.isWindows() && useJavaw ? "javaw" : "java");
    }

    /**
     * Gets the given executable within the given java home directory.
     *
     * @param homeDir    The home directory.
     * @param executable The executable name.
     * @return The executable path.
     */
    public static Path getExecutable(Path homeDir, String executable) {
        return homeDir.resolve("bin").resolve(OperatingSystem.current().exeSuffix(executable));
    }

    @Nullable
    public static JavaInstall parse(Path executable) {
        if (DEBUG) {
            LOGGER.info("Attempting to parse install from executable '{}'", executable);
        }
        if (Files.notExists(executable)) {
            if (DEBUG) {
                LOGGER.error(" Executable does not exist!");
            }
            return null;
        }
        try {
            Path tempDir = Files.createTempDirectory("java_prop_extract");
            JavaPropExtractGenerator.writeClass(tempDir);
            List<String> args = new LinkedList<>(Arrays.asList(
                    executable.toRealPath().toString(),
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
            if (DEBUG) {
                LOGGER.error("Failed to parse Java install.", e);
            }
            return null;
        }
    }

    @Override
    public String toString() {
        return "JavaInstall{" +
               "langVersion=" + langVersion +
               ", javaHome=" + javaHome +
               ", vendor='" + vendor + '\'' +
               ", implName='" + implName + '\'' +
               ", implVersion='" + implVersion + '\'' +
               ", runtimeName='" + runtimeName + '\'' +
               ", runtimeVersion='" + runtimeVersion + '\'' +
               ", architecture=" + architecture +
               ", isOpenJ9=" + isOpenJ9 +
               ", hasCompiler=" + hasCompiler +
               '}';
    }
}
