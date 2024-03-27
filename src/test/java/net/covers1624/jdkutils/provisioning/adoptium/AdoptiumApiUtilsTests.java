package net.covers1624.jdkutils.provisioning.adoptium;

import net.covers1624.jdkutils.JavaVersion;
import net.covers1624.jdkutils.provisioning.adoptium.AdoptiumApiUtils.ReleaseResult;
import net.covers1624.quack.net.httpapi.HttpEngine;
import net.covers1624.quack.net.httpapi.okhttp.OkHttpEngine;
import net.covers1624.quack.platform.Architecture;
import net.covers1624.quack.platform.OperatingSystem;
import net.covers1624.quack.util.SneakyUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static net.covers1624.jdkutils.provisioning.adoptium.AdoptiumApiUtils.findRelease;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by covers1624 on 15/3/24.
 */
public class AdoptiumApiUtilsTests {

    private static final String ARCH_PROP = "AdoptiumApiUtils.testing.arch";
    private static final String OS_PROP = "AdoptiumApiUtils.testing.os";
    private static final HttpEngine http = OkHttpEngine.create();

    @Test
    public void testLinuxResolve() throws Throwable {
        spoofOS(Architecture.X64, OperatingSystem.LINUX, () -> {
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, true, false));
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, false, false));
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, true, true));
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, false, true));
        });
        spoofOS(Architecture.AARCH64, OperatingSystem.LINUX, () -> {
            assertResolves(Architecture.AARCH64, findRelease(http, JavaVersion.JAVA_17, null, true, false));
            assertResolves(Architecture.AARCH64, findRelease(http, JavaVersion.JAVA_17, null, false, false));
            assertResolves(Architecture.AARCH64, findRelease(http, JavaVersion.JAVA_17, null, true, true));
            assertResolves(Architecture.AARCH64, findRelease(http, JavaVersion.JAVA_17, null, false, true));
        });
    }

    @Test
    public void testWindowsResolve() throws Throwable {
        spoofOS(Architecture.X64, OperatingSystem.WINDOWS, () -> {
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, true, false));
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, false, false));
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, true, true));
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, false, true));
        });
        // AArch64 not available for windows.
//        spoofOS(Architecture.AARCH64, OperatingSystem.WINDOWS, () -> {
//            assertResolves(Architecture.AARCH64, findRelease(http, JavaVersion.JAVA_17, null, true, false));
//            assertResolves(Architecture.AARCH64, findRelease(http, JavaVersion.JAVA_17, null, false, false));
//            assertResolves(Architecture.AARCH64, findRelease(http, JavaVersion.JAVA_17, null, true, true));
//            assertResolves(Architecture.AARCH64, findRelease(http, JavaVersion.JAVA_17, null, false, true));
//        });
    }

    @Test
    public void testMacResolve() throws Throwable {
        spoofOS(Architecture.X64, OperatingSystem.MACOS, () -> {
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, true, false));
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, false, false));
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, true, true));
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, false, true));
        });
        spoofOS(Architecture.AARCH64, OperatingSystem.MACOS, () -> {
            assertResolves(Architecture.AARCH64, findRelease(http, JavaVersion.JAVA_17, null, true, false));
            assertResolves(Architecture.AARCH64, findRelease(http, JavaVersion.JAVA_17, null, false, false));
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, true, true));
            assertResolves(Architecture.X64, findRelease(http, JavaVersion.JAVA_17, null, false, true));
        });
    }

    private static void assertResolves(Architecture arch, @Nullable ReleaseResult result) {
        assertNotNull(result);
        assertFalse(result.releases.isEmpty());
        assertEquals(arch, result.architecture);
    }

    private static void spoofOS(Architecture arch, OperatingSystem os, SneakyUtils.ThrowingRunnable<Throwable> r) throws Throwable {
        System.setProperty(ARCH_PROP, arch.name());
        System.setProperty(OS_PROP, os.name());
        try {
            r.run();
        } finally {
            System.clearProperty(ARCH_PROP);
            System.clearProperty(OS_PROP);
        }
    }
}
