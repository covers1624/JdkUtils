package net.covers1624.jdkutils.locator;

import net.covers1624.jdkutils.JavaInstall;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Capable of finding JDK installations in common locations
 * on a MacOSX system.
 * <p>
 * Created by covers1624 on 12/11/21.
 */
public class MacosJavaLocator extends JavaLocator {

    MacosJavaLocator() {
        super(false);
    }

    @Override
    public List<JavaInstall> findJavaVersions() throws IOException {
        List<JavaInstall> installs = new ArrayList<>();
        findJavasInFolder(installs, Paths.get("/Library/Java/JavaVirtualMachines/"));
        findJavasInFolder(installs, Paths.get("/System/Library/Java/JavaVirtualMachines/"));
        return installs;
    }
}
