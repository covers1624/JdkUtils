package net.covers1624.jdkutils.provisioning.adoptium;

import net.covers1624.jdkutils.JdkInstallationManager;
import net.covers1624.jdkutils.JdkInstallationManager.ProvisionRequest;
import net.covers1624.jdkutils.utils.Utils;
import net.covers1624.jdkutils.provisioning.adoptium.AdoptiumApiUtils.ReleaseResult;
import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.net.httpapi.EngineRequest;
import net.covers1624.quack.net.httpapi.EngineResponse;
import net.covers1624.quack.net.httpapi.HttpEngine;
import net.covers1624.quack.net.httpapi.WebBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.nio.file.StandardOpenOption.*;
import static net.covers1624.jdkutils.utils.ArchiveUtils.extractArchive;
import static net.covers1624.jdkutils.utils.Utils.SHA_256;

/**
 * Created by covers1624 on 15/3/24.
 */
public class AdoptiumProvisioner implements JdkInstallationManager.JdkProvisioner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdoptiumProvisioner.class);
    private final HttpEngine http;

    public AdoptiumProvisioner(HttpEngine http) {
        this.http = http;
    }

    @Override
    public JdkInstallationManager.ProvisionResult provisionJdk(Path baseDir, ProvisionRequest request) throws IOException {
        LOGGER.info("Attempting to provision Adoptium jvm for {}({}).", request.version, request.semver);
        ReleaseResult result = AdoptiumApiUtils.findRelease(http, request.version, request.semver, request.jre, request.forceX64OnMac);
        if (result == null || result.releases.isEmpty()) throw new FileNotFoundException("Adoptium can't provide jvm for " + request.version + "(" + request.semver + ")");

        AdoptiumRelease release = result.releases.get(0);
        if (release.binaries.isEmpty()) throw new FileNotFoundException("Adoptium returned a release, but id did not contain any binaries.. " + request.version + "(" + request.semver + ")");
        if (release.binaries.size() != 1) {
            LOGGER.warn("Adoptium returned more than one binary? Api change? Using first!");
        }

        AdoptiumRelease.Binary binary = release.binaries.get(0);
        AdoptiumRelease.Package pkg = binary.pkg;
        LOGGER.info("Release found {}, Download {}", release.version_data.openjdk_version, pkg.link);

        Path archive = baseDir.resolve(pkg.name);
        LOGGER.debug("Downloading archive to {}", archive);
        EngineRequest req = http.newRequest()
                .method("GET", null)
                .url(pkg.link);
        if (request.requestListener != null) {
            req.listener(request.requestListener);
        }
        try (EngineResponse resp = req.execute()) {
            int code = resp.statusCode();
            if (code < 200 || code >= 300) throw new IOException("Failed to download adoptium release. Got non 2XX response " + code);
            WebBody body = resp.body();
            if (body == null) throw new IOException("Failed to download adoptium release. Got 2XX response with no body..");

            try (ReadableByteChannel rc = body.openChannel();
                 WritableByteChannel wc = Files.newByteChannel(IOUtils.makeParents(archive), WRITE, CREATE, TRUNCATE_EXISTING)
            ) {
                IOUtils.copy(rc, wc);
            }
        }
        LOGGER.debug("File downloaded.");

        long size = Files.size(archive);
        if (size != pkg.size) throw new IOException("Downloaded archive size incorrect. Expected " + pkg.size + ", Got: " + size);
        String hash = Utils.hashFile(SHA_256, archive);
        if (!hash.equals(pkg.checksum)) throw new IOException("Downloaded archive hash incorrect. Expected " + pkg.checksum + ", Got: " + hash);

        Path javaHome = extractArchive(baseDir, archive);
        Files.deleteIfExists(archive);

        return new JdkInstallationManager.ProvisionResult(release.version_data.openjdk_version, javaHome, "jdk".equals(binary.image_type), result.architecture);
    }
}
