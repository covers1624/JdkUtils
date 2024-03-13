package net.covers1624.jdkutils.locator;

import net.covers1624.jdkutils.JavaInstall;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Capable of finding JDK installations in common locations
 * on a Linux system.
 * <p>
 * Created by covers1624 on 29/10/21.
 */
public class LinuxJavaLocator extends JavaLocator {

    LinuxJavaLocator() {
        super(false);
    }

    @Override
    public List<JavaInstall> findJavaVersions() throws IOException {
        List<JavaInstall> installs = new ArrayList<>();
        // Oracle
        findJavasInFolder(installs, Paths.get("/usr/java"));

        // Common distro locations
        findJavasInFolder(installs, Paths.get("/usr/lib/jvm"));
        findJavasInFolder(installs, Paths.get("/usr/lib32/jvm"));

        // Manually installed locations
        findJavasInFolder(installs, Paths.get("/opt/jdk"));
        findJavasInFolder(installs, Paths.get("/opt/jdks"));

        // Locally installed locations
        findJavasInFolder(installs, Paths.get(System.getProperty("user.home"), ".local/jdks"));
        return installs;
    }
}
