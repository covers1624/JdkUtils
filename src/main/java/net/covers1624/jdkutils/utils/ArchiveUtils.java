package net.covers1624.jdkutils.utils;

import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.platform.OperatingSystem;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Created by covers1624 on 15/3/24.
 */
@ApiStatus.Internal
public class ArchiveUtils {

    public static Path extractArchive(Path baseDir, Path archive) throws IOException {
        Path extractionDir = baseDir.resolve(getNameWithoutExtension(archive.getFileName()));
        Path basePath = null;
        try (ArchiveInputStream<?> is = open(archive)) {
            ArchiveEntry entry;
            while ((entry = is.getNextEntry()) != null) {
                Path file = entry.resolveIn(extractionDir);
                if (entry.isDirectory()) {
                    if (basePath == null) basePath = file;
                    Files.createDirectories(file);
                } else {
                    Files.copy(is, IOUtils.makeParents(file));
                }
                writeAttributes(file, entry);
            }
        }
        return Objects.requireNonNull(basePath, "Base path was not set during extraction. Empty zip? Archive with no files?");
    }

    private static void writeAttributes(Path file, ArchiveEntry entry) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(entry.getLastModifiedDate().getTime()));

        if (OperatingSystem.current().isUnixLike() && entry instanceof TarArchiveEntry) {
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

    private static String getNameWithoutExtension(Path file) {
        return file.toString()
                .replace(".zip", "")
                .replace(".tar.gz", "");
    }

    private static ArchiveInputStream<?> open(Path archive) throws IOException {
        String fName = archive.getFileName().toString();
        if (fName.endsWith(".zip")) return new ZipArchiveInputStream(Files.newInputStream(archive));
        if (fName.endsWith(".tar.gz")) return new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(archive)));

        throw new UnsupportedOperationException("Unable to extract archive. Unhandled file format: " + fName);
    }
}
