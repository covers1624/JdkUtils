/*
 * This file is part of JdkUtils and is Licensed under the MIT License.
 *
 * Copyright (c) 2018-2021 covers1624 <https://github.com/covers1624>
 */
package net.covers1624.jdkutils;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import net.covers1624.quack.annotation.Requires;
import net.covers1624.quack.collection.ColUtils;
import net.covers1624.quack.gson.HashCodeAdapter;
import net.covers1624.quack.gson.JsonUtils;
import net.covers1624.quack.gson.PathTypeAdapter;
import net.covers1624.quack.util.HashUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Capable of managing custom installations of Java JDK's.
 * <p>
 * Created by covers1624 on 12/11/21.
 */
@Requires ("org.slf4j:slf4j-api")
@Requires ("com.google.code.gson")
@Requires ("com.google.guava:guava")
@Requires ("org.apache.commons:commons-lang3")
@SuppressWarnings ("UnstableApiUsage")
public class JdkInstallationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaLocator.class);

    private static final Gson GSON = new Gson();
    private static final Type INSTALLS_TYPE = new TypeToken<Map<String, Installation>>() { }.getType();

    private final Path baseDir;
    private final JdkProvisioner provisioner;
    private final boolean ignoreMacosAArch64;
    private final Path manifestPath;
    private final Map<String, Installation> installations;

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
        Map<String, Installation> installs = new HashMap<>();
        if (Files.exists(manifestPath)) {
            try {
                installs = JsonUtils.parse(GSON, manifestPath, INSTALLS_TYPE);
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
     * @return The Found JDK home directory. Otherwise <code>null</code>.
     */
    @Nullable
    public Path findJdk(JavaVersion version) {
        for (Map.Entry<String, Installation> entry : installations.entrySet()) {
            if (version == JavaVersion.parse(entry.getKey())) {
                return JavaInstall.getHomeDirectory(entry.getValue().path);
            }
        }
        return null;
    }

    /**
     * Provision a JDK for the specified version.
     * <p>
     * If one already exists. That will be returned instead.
     *
     * @param version The java Major version to use.
     * @return The JDK home directory.
     * @throws IOException Thrown if an error occurs whilst provisioning the JDK.
     */
    public Path provisionJdk(JavaVersion version) throws IOException {
        Path existing = findJdk(version);
        if (existing != null) return existing;

        Pair<String, Path> pair = provisioner.provisionJdk(baseDir, version, ignoreMacosAArch64);

        Path installation = pair.getRight();
        assert Files.exists(installation);

        installations.put(pair.getLeft(), new Installation(hashInstallation(installation), installation));
        saveManifest();
        return JavaInstall.getHomeDirectory(installation);
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
         * @param baseFolder The folder to place the JDK folder.
         * @param version    The Java major version to install.
         *                   // TODO fix this return javadoc
         * @return The full semver version for the provisioned JDK.
         * @throws IOException If there was an error provisioning the JDK.
         */
        Pair<String, Path> provisionJdk(Path baseFolder, JavaVersion version, boolean ignoreMacosAArch64) throws IOException;
    }

    public static class Installation {

        @Nullable
        @JsonAdapter (HashCodeAdapter.class)
        public HashCode hash;

        @JsonAdapter (PathTypeAdapter.class)
        public Path path;

        public Installation() {
        }

        public Installation(@Nullable HashCode hash, Path path) {
            this();
            this.hash = hash;
            this.path = path;
        }
    }
}
