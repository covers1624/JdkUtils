package net.covers1624.jdkutils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.covers1624.jdkutils.locator.JavaLocator;
import net.covers1624.jdkutils.utils.Utils;
import net.covers1624.quack.annotation.Requires;
import net.covers1624.quack.collection.ColUtils;
import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.gson.JsonUtils;
import net.covers1624.quack.net.httpapi.RequestListener;
import net.covers1624.quack.platform.Architecture;
import net.covers1624.quack.platform.OperatingSystem;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static net.covers1624.jdkutils.utils.Utils.SHA_256;

/**
 * Capable of managing custom installations of Java JDK's.
 * <p>
 * Created by covers1624 on 12/11/21.
 */
@Requires ("org.slf4j:slf4j-api")
@Requires ("com.google.code.gson")
public class JdkInstallationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdkInstallationManager.class);

    private static final Gson GSON = new Gson();
    private static final Type INSTALLS_TYPE = new TypeToken<List<Installation>>() { }.getType();

    private final Path baseDir;
    private final JdkProvisioner provisioner;
    private final Path manifestPath;
    private final List<Installation> installations;

    /**
     * Create a new Jdk Installation Manager.
     *
     * @param baseDir     The base directory to install JDK's to.
     * @param provisioner The provisioner to provision new JDK's.
     */
    public JdkInstallationManager(Path baseDir, JdkProvisioner provisioner) {
        this.baseDir = baseDir;
        this.provisioner = provisioner;
        manifestPath = baseDir.resolve("installations.json");
        LOGGER.debug("Starting with baseDir {} manifestPath {}", baseDir, manifestPath);
        List<Installation> installs = new ArrayList<>();
        if (Files.exists(manifestPath)) {
            try {
                JsonElement parsed = JsonUtils.parse(GSON, manifestPath, JsonElement.class, StandardCharsets.UTF_8);
                installs = GSON.fromJson(Installation.migrate(parsed), INSTALLS_TYPE);
                LOGGER.debug("Loaded {} installs.", installs.size());
                for (Installation install : installs) {
                    LOGGER.debug("  {}", install);
                }
            } catch (IOException | JsonParseException e) {
                // TODO, we may be better off throwing an error here.
                LOGGER.error("Failed to parse json {}. Ignoring..", manifestPath, e);
            }
        }
        installations = installs;
        validateInstallations();
    }

    private void validateInstallations() {
        LOGGER.info("Validating java installations.");
        for (ListIterator<Installation> iterator = installations.listIterator(); iterator.hasNext(); ) {
            Installation installation = iterator.next();
            LOGGER.debug("Checking install at {}", installation.path);
            // Check all installations can be executed.
            if (!installation.isExecutable(baseDir)) {
                LOGGER.warn("Removing cache of un-executable installation at {}", installation.getPath(baseDir));
                iterator.remove();
                continue;
            }

            // If they are using absolute paths, replace with relative paths.
            Path path = Paths.get(installation.path);
            if (path.isAbsolute()) {
                LOGGER.info("Converting installation '{}' to relative paths.", installation.path);
                installation.path = baseDir.relativize(path).toString();
            }
            // TODO validate hashes?
        }
        saveManifest();

        try (Stream<Path> stream = Files.list(baseDir)) {
            // Iterate all files inside the base directory.
            // There is usually just the manifest in here, then a series of directories for each extracted java installation.
            for (Path path : FastStream.of(stream)) {
                // We only care about directories.
                if (!Files.isDirectory(path)) continue;

                // Check if we have already know about whats inside this directory.
                String rel = baseDir.relativize(path).toString();
                // e.path == rel when the installation is old-format, baseDir/<folder>/bin
                // e.path.startsWith(rel + /) when the installation is new-format. baseDir/<zip name>/<folder>/bin
                if (ColUtils.anyMatch(installations, e -> e.path.equals(rel) || e.path.startsWith(rel + "/"))) continue;

                // We have found a directory we don't currently know about. May have been a lost installation due to
                // the above yeeting an installation with an invalid path.
                Installation newInstall = tryFindInstallation(path);
                if (newInstall != null) {
                    LOGGER.info("Recovered installation in {}", path);
                    installations.add(newInstall);
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to scan installations dir.", ex);
        }
        saveManifest();
    }

    private @Nullable Installation tryFindInstallation(Path searchDir) {
        JavaInstall install = JavaInstall.parse(JavaInstall.getJavaExecutable(JavaInstall.getHomeDirectory(searchDir), true));
        if (install != null) {
            return new Installation(
                    install.runtimeVersion,
                    install.hasCompiler,
                    install.architecture,
                    hashInstallation(searchDir),
                    baseDir.relativize(searchDir).toString()
            );
        }

        List<Path> folders;
        try (Stream<Path> files = Files.list(searchDir)) {
            folders = FastStream.of(files)
                    .filter(Files::isDirectory)
                    .toList();
        } catch (IOException ex) {
            LOGGER.warn("Failed to iterate directory.", ex);
            return null;
        }

        if (folders.size() != 1) return null;
        return tryFindInstallation(folders.get(0));
    }

    /**
     * Find an existing JDK for the specified java Major version.
     *
     * @param version       The Java Major version to use.
     * @param semver        An optional semver filter.
     * @param jre           If a JRE is all that is required.
     * @param forceX64OnMac If Mac should force the use of x64 vms. Assumes rosetta support.
     * @return The Found JDK home directory. Otherwise <code>null</code>.
     */
    @Nullable
    public Path findJdk(JavaVersion version, @Nullable String semver, boolean jre, boolean forceX64OnMac) {
        LOGGER.debug("Trying to find previously installed jvm matching Version: {} Semver: {} Jre: {} ForgeX64OnMac: {}", version, semver, jre, forceX64OnMac);
        OperatingSystem os = OperatingSystem.current();
        LinkedList<Installation> candidates = FastStream.of(installations)
                .filter(e -> version == JavaVersion.parse(e.version))
                // On mac, if we are forcing x64, filter it away, otherwise only include it.
                .filter(e -> os != OperatingSystem.MACOS || forceX64OnMac == (e.architecture == Architecture.X64))
                .filter(e -> semver == null || semver.equals(e.version)) // If we have a semver filter, do the filter.
                .filter(e -> jre || e.isJdk) // If we require a JDK, make sure we get a JDK.
                .toLinkedList();
        if (candidates.isEmpty()) {
            LOGGER.debug(" Did not find a candidate.");
            return null;
        }

        LOGGER.debug(" Found {} candidates", candidates.size());
        candidates.forEach(e -> LOGGER.debug("  {}", e));
        candidates.sort(Comparator.<Installation, ComparableVersion>comparing(e -> new ComparableVersion(e.version)).reversed());
        LOGGER.debug(" Sorted");
        candidates.forEach(e -> LOGGER.debug("  {}", e));
        Installation chosen = candidates.getFirst();
        LOGGER.debug(" Chose {}", chosen);
        return JavaInstall.getHomeDirectory(candidates.getFirst().getPath(baseDir));
    }

    /**
     * Provision a JDK/JRE as per the request.
     * <p>
     * If one already exists. That will be returned instead.
     *
     * @param request The {@link ProvisionRequest}.
     * @return The JDK home directory.
     * @throws IOException Thrown if an error occurs whilst provisioning the JDK.
     */
    public Path provisionJdk(ProvisionRequest request) throws IOException {
        LOGGER.debug("Provision request: {}", request);
        Path existing = findJdk(request.version, request.semver, request.jre, request.forceX64OnMac);
        if (existing != null) {
            LOGGER.debug(" Filled request with existing jvm {}", existing);
            return existing;
        }

        LOGGER.debug("Did not find an existing jvm. Requesting a new one..");
        ProvisionResult result = provisioner.provisionJdk(baseDir, request);

        assert Files.exists(result.baseDir);

        installations.add(new Installation(
                result.semver,
                result.isJdk,
                result.architecture,
                hashInstallation(result.baseDir),
                baseDir.relativize(result.baseDir).toString() // Store path as relative.
        ));
        saveManifest();
        return JavaInstall.getHomeDirectory(result.baseDir);
    }

    private void saveManifest() {
        try {
            JsonUtils.write(GSON, manifestPath, installations, INSTALLS_TYPE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save JDKInstallation manifest!", e);
        }
    }

    @Nullable
    private String hashInstallation(Path installation) {
        try (Stream<Path> files = Files.walk(installation)) {
            MessageDigest digest = Utils.getDigest(SHA_256);
            // Sort files before hashing for stability.
            for (Path file : FastStream.of(files).sorted()) {
                if (!Files.isRegularFile(file)) continue;

                Utils.addToDigest(digest, file);
            }
            return Utils.finishHash(digest);
        } catch (IOException e) {
            LOGGER.error("Failed to hash java installation.", e);
            return null;
        }
    }

    /**
     * Responsible for provisioning a JDK.
     */
    public interface JdkProvisioner {

        /**
         * Provision a JDK/JRE from the given request.
         *
         * @param baseDir The folder to place the JDK/JRE folder
         * @param request The provision request.
         * @return A {@link ProvisionResult} containing the properties about the provisioned jdk.
         * @throws IOException If there was an error provisioning the JDK.
         */
        ProvisionResult provisionJdk(Path baseDir, ProvisionRequest request) throws IOException;
    }

    public static class ProvisionRequest {

        /**
         * The java Major version to provision.
         */
        public final JavaVersion version;

        /**
         * An optional Semver filter.
         */
        @Nullable
        public final String semver;

        /**
         * If only a JRE is required.
         */
        public final boolean jre;

        /**
         * If macOS AArch64 should be treated as x64.
         */
        public final boolean forceX64OnMac;

        public final @Nullable RequestListener requestListener;

        public ProvisionRequest(Builder builder) {
            if (builder.version == null) {
                throw new IllegalStateException("Expected either a version, or semver filter");
            }

            version = builder.version;
            semver = builder.semver;
            jre = builder.jre;
            forceX64OnMac = builder.forceX64OnMac;
            requestListener = builder.requestListener;
        }

        public static final class Builder {

            private @Nullable JavaVersion version;
            private @Nullable String semver;
            private boolean jre;
            private boolean forceX64OnMac;
            private @Nullable RequestListener requestListener;

            public Builder forVersion(JavaVersion version) {
                this.version = version;
                return this;
            }

            public Builder withSemver(String semver) {
                forVersion(requireNonNull(JavaVersion.parse(semver)));
                this.semver = semver;
                return this;
            }

            public Builder preferJRE(boolean jre) {
                this.jre = jre;
                return this;
            }

            public Builder forceX64OnMac(boolean forceX64OnMac) {
                this.forceX64OnMac = forceX64OnMac;
                return this;
            }

            public Builder downloadListener(RequestListener requestListener) {
                this.requestListener = requestListener;
                return this;
            }

            public ProvisionRequest build() {
                return new ProvisionRequest(this);
            }
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ProvisionRequest.class.getSimpleName() + "[", "]")
                    .add("version=" + version)
                    .add("semver='" + semver + "'")
                    .add("jre=" + jre)
                    .add("forceX64OnMac=" + forceX64OnMac)
                    .toString();
        }
    }

    public static class ProvisionResult {

        public final String semver;
        public final Path baseDir;
        public final boolean isJdk;
        public final Architecture architecture;

        public ProvisionResult(String semver, Path baseDir, boolean isJdk, Architecture architecture) {
            this.semver = semver;
            this.baseDir = baseDir;
            this.isJdk = isJdk;
            this.architecture = architecture;
        }
    }

    public static class Installation {

        public String version;
        public boolean isJdk;
        public @Nullable Architecture architecture;
        public @Nullable String hash;
        public String path;

        public Installation(String version, boolean isJdk, Architecture architecture, @Nullable String hash, String path) {
            this.version = version;
            this.isJdk = isJdk;
            this.architecture = architecture;
            this.hash = hash;
            this.path = path;
        }

        private static JsonElement migrate(JsonElement element) {
            if (!element.isJsonObject()) return element;
            JsonObject obj = element.getAsJsonObject();
            JsonArray array = new JsonArray();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue; // What? Don't hard crash tho.
                JsonObject entryObj = entry.getValue().getAsJsonObject();
                entryObj.addProperty("isJdk", true);
                entryObj.addProperty("version", entry.getKey());
                array.add(entryObj);
            }
            return array;
        }

        public boolean isExecutable(Path baseDir) {
            Path executable = JavaInstall.getJavaExecutable(JavaInstall.getHomeDirectory(getPath(baseDir)), true);
            return JavaInstall.parse(executable) != null;
        }

        public Path getPath(Path baseDir) {
            Path path = Paths.get(this.path);
            if (path.isAbsolute()) {
                return path;
            }
            return baseDir.resolve(path);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Installation.class.getSimpleName() + "[", "]")
                    .add("version='" + version + "'")
                    .add("isJdk=" + isJdk)
                    .add("architecture=" + architecture)
                    .add("hash='" + hash + "'")
                    .add("path='" + path + "'")
                    .toString();
        }
    }
}
