package net.covers1624.jdkutils;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import net.covers1624.quack.annotation.Requires;
import net.covers1624.quack.collection.ColUtils;
import net.covers1624.quack.collection.StreamableIterable;
import net.covers1624.quack.gson.HashCodeAdapter;
import net.covers1624.quack.gson.JsonUtils;
import net.covers1624.quack.net.download.DownloadListener;
import net.covers1624.quack.util.HashUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
@Requires ("com.google.guava:guava")
@SuppressWarnings ("UnstableApiUsage")
public class JdkInstallationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaLocator.class);

    private static final Gson GSON = new Gson();
    private static final Type INSTALLS_TYPE = new TypeToken<List<Installation>>() { }.getType();

    private final Path baseDir;
    private final JdkProvisioner provisioner;
    private final boolean ignoreMacosAArch64;
    private final Path manifestPath;
    private final List<Installation> installations;

    /**
     * Create a new Jdk Installation Manager.
     *
     * @param baseDir            The base directory to install JDK's to.
     * @param provisioner        The provisioner to provision new JDK's.
     * @param ignoreMacosAArch64 If AArch64 macOS aarch64 should be treated as amd64.
     */
    public JdkInstallationManager(Path baseDir, JdkProvisioner provisioner, boolean ignoreMacosAArch64) {
        this.baseDir = baseDir;
        this.provisioner = provisioner;
        this.ignoreMacosAArch64 = ignoreMacosAArch64;
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
    public Path findJdk(JavaVersion version, @Nullable String semver, boolean jre) {
        LinkedList<Installation> candidates = StreamableIterable.of(installations)
                .filter(e -> version == JavaVersion.parse(e.version))
                .filter(e -> semver == null || semver.equals(e.version)) // If we have a semver filter, do the filter.
                .filter(e -> jre || e.isJdk) // If we require a JDK, make sure we get a JDK.
                .toLinkedList();
        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.<Installation, ComparableVersion>comparing(e -> new ComparableVersion(e.version)).reversed());
        return JavaInstall.getHomeDirectory(Paths.get(candidates.getFirst().path));
    }

    /**
     * Provision a JDK for the specified version.
     * <p>
     * If one already exists. That will be returned instead.
     * <p>
     * Overload of {@link #provisionJdk(JavaVersion, String, boolean, DownloadListener)} which
     * only takes a semver version.
     *
     * @param semver   An optional semver filter.
     * @param listener The {@link DownloadListener} to use when provisioning the new JDK. May be <code>null</code>.
     * @return The JDK home directory.
     * @throws IOException Thrown if an error occurs whilst provisioning the JDK.
     */
    public Path provisionJdk(String semver, boolean jre, @Nullable DownloadListener listener) throws IOException {
        return provisionJdk(requireNonNull(JavaVersion.parse(semver)), semver, jre, listener);
    }

    /**
     * Provision a JDK for the specified version.
     * <p>
     * If one already exists. That will be returned instead.
     *
     * @param version  The java Major version to use.
     * @param semver   An optional semver filter for the specified major version.
     * @param listener The {@link DownloadListener} to use when provisioning the new JDK. May be <code>null</code>.
     * @return The JDK home directory.
     * @throws IOException Thrown if an error occurs whilst provisioning the JDK.
     */
    public Path provisionJdk(JavaVersion version, @Nullable String semver, boolean jre, @Nullable DownloadListener listener) throws IOException {
        Path existing = findJdk(version, semver, jre);
        if (existing != null) return existing;

        ProvisionResult result = provisioner.provisionJdk(new ProvisionRequest(baseDir, version, semver, jre, ignoreMacosAArch64), listener);

        assert Files.exists(result.baseDir);

        installations.add(new Installation(result.semver, result.isJdk, hashInstallation(result.baseDir), result.baseDir.toAbsolutePath().toString()));
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
    private HashCode hashInstallation(Path installation) {
        try (Stream<Path> files = Files.walk(installation)) {
            Hasher hasher = Hashing.sha256().newHasher();
            // Sort files before hashing for stability.
            for (Path file : ColUtils.iterable(files.sorted())) {
                if (!Files.isRegularFile(file)) continue;

                HashUtils.addToHasher(hasher, file);
            }
            return hasher.hash();
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
         * Provision a JDK with the given Java major version.
         *
         * @param request  The provision request.
         * @param listener The {@link DownloadListener} to use when provisioning the new JDK. May be <code>null</code>.
         * @return A {@link ProvisionResult} containing the properties about the provisioned jdk.
         * @throws IOException If there was an error provisioning the JDK.
         */
        ProvisionResult provisionJdk(ProvisionRequest request, @Nullable DownloadListener listener) throws IOException;
    }

    public static class ProvisionRequest {

        /**
         * The folder to place the JDK/JRE folder.
         */
        public final Path baseFolder;

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
        public final boolean ignoreMacosAArch64;

        public ProvisionRequest(Path baseFolder, JavaVersion version, @Nullable String semver, boolean jre, boolean ignoreMacosAArch64) {
            this.baseFolder = baseFolder;
            this.version = version;
            this.semver = semver;
            this.jre = jre;
            this.ignoreMacosAArch64 = ignoreMacosAArch64;
        }
    }

    public static class ProvisionResult {

        public final String semver;
        public final Path baseDir;
        public final boolean isJdk;

        public ProvisionResult(String semver, Path baseDir, boolean isJdk) {
            this.semver = semver;
            this.baseDir = baseDir;
            this.isJdk = isJdk;
        }
    }

    public static class Installation {

        public String version = "";

        public boolean isJdk = false;

        @Nullable
        @JsonAdapter (HashCodeAdapter.class)
        public HashCode hash;

        public String path = "";

        public Installation() {
        }

        public Installation(String version, boolean isJdk, @Nullable HashCode hash, String path) {
            this();
            this.version = version;
            this.isJdk = isJdk;
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
