package net.covers1624.jdkutils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.covers1624.jdkutils.locator.JavaLocator;
import net.covers1624.quack.annotation.Requires;
import net.covers1624.quack.collection.ColUtils;
import net.covers1624.quack.collection.StreamableIterable;
import net.covers1624.quack.gson.JsonUtils;
import net.covers1624.quack.net.download.DownloadListener;
import net.covers1624.quack.platform.Architecture;
import net.covers1624.quack.platform.OperatingSystem;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Capable of managing custom installations of Java JDK's.
 * <p>
 * Created by covers1624 on 12/11/21.
 */
@Requires ("org.slf4j:slf4j-api")
@Requires ("com.google.code.gson")
public class JdkInstallationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaLocator.class);

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
        List<Installation> installs = new ArrayList<>();
        if (Files.exists(manifestPath)) {
            try {
                JsonElement parsed = JsonUtils.parse(GSON, manifestPath, JsonElement.class);
                installs = GSON.fromJson(Installation.migrate(parsed), INSTALLS_TYPE);
            } catch (IOException | JsonParseException e) {
                // TODO, we may be better off throwing an error here.
                LOGGER.error("Failed to parse json {}. Ignoring..", manifestPath, e);
            }
        }
        installations = installs;
    }

    /**
     * Find an existing JDK for the specified java Major version.
     *
     * @param version The Java Major version to use.
     * @param semver  An optional semver filter.
     * @param jre     If a JRE is all that is required.
     * @return The Found JDK home directory. Otherwise <code>null</code>.
     */
    @Nullable
    public Path findJdk(JavaVersion version, @Nullable String semver, boolean jre, boolean ignoreMacosAArch64) {
        OperatingSystem os = OperatingSystem.current();
        LinkedList<Installation> candidates = StreamableIterable.of(installations)
                .filter(e -> version == JavaVersion.parse(e.version))
                // On mac, if we are ignoring aarch64, filter it away, otherwise only include it.
                .filter(e -> os != OperatingSystem.MACOS || ignoreMacosAArch64 != (e.architecture == Architecture.AARCH64))
                .filter(e -> semver == null || semver.equals(e.version)) // If we have a semver filter, do the filter.
                .filter(e -> jre || e.isJdk) // If we require a JDK, make sure we get a JDK.
                .toLinkedList();
        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.<Installation, ComparableVersion>comparing(e -> new ComparableVersion(e.version)).reversed());
        return JavaInstall.getHomeDirectory(Paths.get(candidates.getFirst().path));
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
        Path existing = findJdk(request.version, request.semver, request.preferJre, request.ignoreMacosAArch64);
        if (existing != null) return existing;

        ProvisionResult result = provisioner.provisionJdk(baseDir, request);

        assert Files.exists(result.baseDir);

        installations.add(new Installation(result.semver, result.isJdk, result.architecture, hashInstallation(result.baseDir), result.baseDir.toAbsolutePath().toString()));
        saveManifest();
        return JavaInstall.getHomeDirectory(result.baseDir);
    }

    private void saveManifest() {
        try {
            JsonUtils.write(GSON, manifestPath, installations, INSTALLS_TYPE);
        } catch (IOException e) {
            LOGGER.error("Failed to save JDKInstallation manifest!", e);
        }
    }

    @Nullable
    private String hashInstallation(Path installation) {
        try (Stream<Path> files = Files.walk(installation)) {
            MessageDigest digest = Utils.getDigest("SHA256");
            // Sort files before hashing for stability.
            for (Path file : ColUtils.iterable(files.sorted())) {
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
        public final boolean preferJre;

        /**
         * If macOS AArch64 should be treated as x64.
         */
        public final boolean ignoreMacosAArch64;

        public final @Nullable DownloadListener downloadListener;

        public ProvisionRequest(Builder builder) {
            if (builder.version == null) {
                throw new IllegalStateException("Expected either a version, or semver filter");
            }

            version = builder.version;
            semver = builder.semver;
            preferJre = builder.preferJre;
            ignoreMacosAArch64 = builder.ignoreMacosAArch64;
            downloadListener = builder.downloadListener;
        }

        public static final class Builder {

            private @Nullable JavaVersion version;
            private @Nullable String semver;
            private boolean preferJre;
            private boolean ignoreMacosAArch64;
            private @Nullable DownloadListener downloadListener;

            public Builder forVersion(JavaVersion version) {
                this.version = version;
                return this;
            }

            public Builder withSemver(String semver) {
                forVersion(requireNonNull(JavaVersion.parse(semver)));
                this.semver = semver;
                return this;
            }

            public Builder preferJRE(boolean preferJre) {
                this.preferJre = preferJre;
                return this;
            }

            public Builder ignoreMacosAArch64(boolean ignoreMacosAArch64) {
                this.ignoreMacosAArch64 = ignoreMacosAArch64;
                return this;
            }

            public Builder downloadListener(DownloadListener downloadListener) {
                this.downloadListener = downloadListener;
                return this;
            }

            public ProvisionRequest build() {
                return new ProvisionRequest(this);
            }
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

        public String version = "";

        public boolean isJdk = false;

        @Nullable
        public Architecture architecture;

        @Nullable
        public String hash;

        public String path = "";

        public Installation() {
        }

        public Installation(String version, boolean isJdk, Architecture architecture, @Nullable String hash, String path) {
            this();
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
    }
}
