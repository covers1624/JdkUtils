package net.covers1624.jdkutils.provisioning.adoptium;

import net.covers1624.jdkutils.JavaVersion;
import net.covers1624.quack.net.httpapi.EngineRequest;
import net.covers1624.quack.net.httpapi.EngineResponse;
import net.covers1624.quack.net.httpapi.HttpEngine;
import net.covers1624.quack.net.httpapi.WebBody;
import net.covers1624.quack.platform.Architecture;
import net.covers1624.quack.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Created by covers1624 on 15/3/24.
 */
public class AdoptiumApiUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdoptiumApiUtils.class);
    private static final String ADOPTIUM_API = "https://api.adoptium.net";

    public static @Nullable ReleaseResult findRelease(HttpEngine http, JavaVersion version, @Nullable String semver, boolean jre, boolean forceX64OnMac) throws IOException {
        OperatingSystem os = os();
        Architecture arch = arch();
        if (os.isMacos() && arch == Architecture.AARCH64 && forceX64OnMac) {
            LOGGER.info("Forcing x64 resolve for arm64 mac.");
            arch = Architecture.X64;
        }

        EngineRequest request = http.newRequest()
                .method("GET", null)
                .url(makeURL(os, arch, version, semver, jre));
        try (EngineResponse resp = request.execute()) {
            int code = resp.statusCode();
            if (code != 404) {
                if (code >= 200 && code < 300) {
                    WebBody body = resp.body();
                    if (body == null) throw new IOException("Http response was 2XX without a body..");
                    try (InputStream is = body.open()) {
                        return new ReleaseResult(AdoptiumRelease.parseReleases(is), arch);
                    }
                }
                // Nothing we can do, we did not get 404 or 2xx
                return null;
            }
        }

        // If we have a 404, we can do some stuff.
        // Outside the try to release http resources early.

        // If we are on mac arm64, we can try x64 because of rosetta.
        if (os.isMacos() && arch == Architecture.AARCH64) {
            LOGGER.warn("Failed to find arm64 macos jvm for {}({}), Trying x64", version, semver);
            return findRelease(http, version, semver, jre, true);
        }

        // We failed to find a jre, find a jdk.
        if (jre) {
            LOGGER.warn("Failed to find JRE for {}({}), Tying JDK.", version, semver);
            return findRelease(http, version, semver, false, forceX64OnMac);
        }
        // Nothing else we can do. We tried everything we could..
        return null;
    }

    private static String makeURL(OperatingSystem os, Architecture arch, JavaVersion version, @Nullable String semver, boolean jre) {
        String url = ADOPTIUM_API + "/v3/assets";
        if (semver != null) {
            url += "/version/" + semver;
        } else {
            url += "/feature_releases/" + version.shortString + "/ga";
        }
        String platform;
        if (os.isWindows()) {
            platform = "windows";
        } else if (os.isLinux()) {
            platform = "linux";
        } else if (os.isMacos()) {
            platform = "mac";
        } else {
            throw new UnsupportedOperationException("Unsupported operating system.");
        }
        return url
               + "?project=jdk"
               + "&image_type=" + (jre ? "jre" : "jdk")
               + "&vendor=eclipse"
               + "&jvm_impl=hotspot"
               + "&heap_size=normal"
               + "&architecture=" + arch.name().toLowerCase(Locale.ROOT)
               + "&os=" + platform;
    }

    public static class ReleaseResult {

        public final List<AdoptiumRelease> releases;
        public final Architecture architecture;

        public ReleaseResult(List<AdoptiumRelease> releases, Architecture architecture) {
            this.releases = releases;
            this.architecture = architecture;
        }
    }

    private static Architecture arch() {
        String override = System.getProperty("AdoptiumApiUtils.testing.arch");
        if (override != null) {
            return Architecture.valueOf(override);
        }
        return Architecture.current();
    }

    private static OperatingSystem os() {
        String override = System.getProperty("AdoptiumApiUtils.testing.os");
        if (override != null) {
            return OperatingSystem.valueOf(override);
        }
        return OperatingSystem.current();
    }
}
