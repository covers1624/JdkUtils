package net.covers1624.jdkutils;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import net.covers1624.jdkutils.JdkInstallationManager.ProvisionRequest;
import net.covers1624.jdkutils.JdkInstallationManager.ProvisionResult;
import net.covers1624.quack.annotation.Requires;
import net.covers1624.quack.gson.JsonUtils;
import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.net.DownloadAction;
import net.covers1624.quack.net.HttpResponseException;
import net.covers1624.quack.net.download.DownloadListener;
import net.covers1624.quack.platform.Architecture;
import net.covers1624.quack.platform.OperatingSystem;
import net.covers1624.quack.util.HashUtils;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

import static net.covers1624.quack.collection.ColUtils.iterable;

/**
 * A {@link JdkInstallationManager.JdkProvisioner} capable of provisioning
 * JDK's from https://adoptium.net
 * <p>
 * Created by covers1624 on 13/11/21.
 */

@Requires ("org.slf4j:slf4j-api")
@Requires ("com.google.code.gson")
@Requires ("com.google.guava:guava")
@Requires ("org.apache.commons:commons-lang3")
@Requires ("org.apache.commons:commons-compress")
public class AdoptiumProvisioner implements JdkInstallationManager.JdkProvisioner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdoptiumProvisioner.class);
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<AdoptiumRelease>>() { }.getType();

    private static final String ADOPTIUM_URL = "https://api.adoptium.net";
    private static final OperatingSystem OS = OperatingSystem.current();

    private final Supplier<DownloadAction> downloadActionSupplier;

    public AdoptiumProvisioner(Supplier<DownloadAction> downloadActionSupplier) {
        this.downloadActionSupplier = downloadActionSupplier;
    }

    @Override
    @SuppressWarnings ("UnstableApiUsage")
    public ProvisionResult provisionJdk(ProvisionRequest request, @Nullable DownloadListener listener) throws IOException {
        LOGGER.info("Attempting to provision Adoptium JDK for {}.", request.version);
        List<AdoptiumRelease> releases = getReleases(request.version, request.jre, request.ignoreMacosAArch64);
        if (releases.isEmpty()) throw new FileNotFoundException("Adoptium does not have any releases for " + request.version);
        AdoptiumRelease release = releases.get(0);
        if (release.binaries.isEmpty()) throw new FileNotFoundException("Adoptium returned a release, but no binaries? " + request.version);
        if (release.binaries.size() != 1) {
            LOGGER.warn("Adoptium returned more than one binary! Api change? Using first!");
        }

        AdoptiumRelease.Binary binary = release.binaries.get(0);
        AdoptiumRelease.Package pkg = binary._package;
        LOGGER.info("Found release '{}', Download '{}'", release.version_data.semver, pkg.link);

        Path tempFile = request.baseFolder.resolve(pkg.name);
        tempFile.toFile().deleteOnExit();
        DownloadAction action = downloadActionSupplier.get();
        if (listener != null) {
            action.setDownloadListener(listener);
        }
        action.setUrl(pkg.link);
        action.setDest(tempFile);
        action.execute();

        long size = Files.size(tempFile);
        HashCode hash = HashUtils.hash(Hashing.sha256(), tempFile);
        if (size != pkg.size) {
            throw new IOException("Invalid Adoptium download - Size incorrect. Expected: " + pkg.size + ", Got: " + size);
        }
        if (!HashUtils.equals(hash, pkg.checksum)) {
            throw new IOException("Invalid Adoptium download - SHA256 Hash incorrect. Expected: " + pkg.checksum + ", Got: " + hash);
        }

        Path extractedFolder = extract(request.baseFolder, tempFile);

        return new ProvisionResult(release.version_data.semver, extractedFolder, "jdk".equals(binary.image_type));
    }

    private List<AdoptiumRelease> getReleases(JavaVersion version, boolean jre, boolean ignoreMacosAArch64) throws IOException {
        DownloadAction action = downloadActionSupplier.get();
        Architecture architecture = Architecture.current();
        if (OS.isMacos() && architecture == Architecture.AARCH64 && ignoreMacosAArch64) {
            LOGGER.info("Forcing x64 JDK for macOS AArch64.");
            architecture = Architecture.X64;
        }
        StringWriter sw = new StringWriter();
        action.setUrl(makeURL(version, jre, architecture));
        action.setDest(sw);
        try {
            action.execute();
        } catch (HttpResponseException ex) {
            // Non 404 is always fatal.
            if (ex.code != 404) {
                throw ex;
            }

            // If we are on macOS, and we are AArch64, try x86.
            if (OS.isMacos() && architecture == Architecture.AARCH64) {
                LOGGER.warn("Failed to find AArch64 macOS jdk for java {}. Trying x64.", version);
                // Try again, but let's get an ADM64 build because Rosetta exists.
                return getReleases(version, jre, true);
            }

            // We failed to find a JRE, find a JDK.
            if (jre) {
                LOGGER.warn("Failed to find JRE for java {}. Trying JDK.", version);
                return getReleases(version, false, ignoreMacosAArch64);
            }

            // On macos,
            throw ex;

        }
        return AdoptiumRelease.parseReleases(sw.toString());
    }

    private static String makeURL(JavaVersion version, boolean jre, Architecture architecture) {
        String platform;
        if (OS.isWindows()) {
            platform = "windows";
        } else if (OS.isLinux()) {
            platform = "linux";
        } else if (OS.isMacos()) {
            platform = "mac";
        } else {
            throw new UnsupportedOperationException("Unsupported operating system.");
        }
        return ADOPTIUM_URL
                + "/v3/assets/feature_releases/"
                + version.shortString
                + "/ga"
                + "?project=jdk"
                + "&image_type=" + (jre ? "jre" : "jdk")
                + "&vendor=eclipse"
                + "&jvm_impl=hotspot"
                + "&heap_size=normal"
                + "&architecture=" + architecture.name().toLowerCase(Locale.ROOT)
                + "&os=" + platform;
    }

    private static Path extract(Path jdksDir, Path jdkArchive) throws IOException {
        LOGGER.info("Extracting Adoptium archive '{}' into '{}' ", jdkArchive, jdksDir);
        Path jdkDir = jdksDir.resolve(getBasePath(jdkArchive));
        try (ArchiveInputStream is = createStream(jdkArchive)) {
            ArchiveEntry entry;
            while ((entry = is.getNextEntry()) != null) {
                Path file = jdksDir.resolve(entry.getName()).toAbsolutePath();
                if (entry.isDirectory()) {
                    Files.createDirectories(file);
                } else {
                    Files.createDirectories(file.getParent());
                    try (OutputStream os = Files.newOutputStream(file)) {
                        IOUtils.copy(is, os);
                    }
                }
                setAttributes(file, entry);
            }
        }

        return jdkDir;
    }

    private static String getBasePath(Path jdkArchive) throws IOException {
        try (ArchiveInputStream is = createStream(jdkArchive)) {
            ArchiveEntry entry;
            while ((entry = is.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    return entry.getName();
                }
            }
        }
        throw new RuntimeException("Unable to find base path for archive. " + jdkArchive);
    }

    private static ArchiveInputStream createStream(Path jdkArchive) throws IOException {
        String fileName = jdkArchive.getFileName().toString();
        if (fileName.endsWith(".tar.gz")) {
            return new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(jdkArchive)));
        }
        if (fileName.endsWith(".zip")) {
            return new ZipArchiveInputStream(Files.newInputStream(jdkArchive));
        }
        throw new UnsupportedOperationException("Unable to determine archive format of file: " + fileName);
    }

    private static void setAttributes(Path file, ArchiveEntry entry) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(entry.getLastModifiedDate().getTime()));

        if (OS.isUnixLike() && entry instanceof TarArchiveEntry) {
            Files.setPosixFilePermissions(file, IOUtils.parseMode(fixMode(((TarArchiveEntry) entry).getMode())));
        }
    }

    private static int fixMode(int mode) {
        // TarArchiveEntry represents the defaults 755
        if (mode == TarArchiveEntry.DEFAULT_DIR_MODE || mode == TarArchiveEntry.DEFAULT_FILE_MODE) {
            return mode;
        }
        // But parses as 493. Convert to octal represented as base 10. (755)
        return Integer.parseInt(Integer.toOctalString(mode));
    }

    public static class AdoptiumRelease {

        public static List<AdoptiumRelease> parseReleases(String json) throws IOException, JsonParseException {
            return JsonUtils.parse(GSON, new StringReader(json), LIST_TYPE);
        }

        public List<Binary> binaries = new ArrayList<>();
        public String release_name;
        public VersionData version_data;

        public static class Binary {

            public String image_type;

            @SerializedName ("package")
            public Package _package;
        }

        public static class Package {

            public String checksum;
            public String link;
            public String name;
            public int size;
        }

        public static class VersionData {

            public String semver;
        }
    }
}
