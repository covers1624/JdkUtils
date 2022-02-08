package net.covers1624.jdkutils;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capable of finding JDK installations in common locations
 * on a MacOSX system.
 * <p>
 * Created by covers1624 on 12/11/21.
 */
public class MacosJavaLocator extends JavaLocator {

    MacosJavaLocator(LocatorProps props) {
        super(props);
    }

    @Override
    public List<JavaInstall> findJavaVersions() throws IOException {
        Map<String, JavaInstall> installs = new LinkedHashMap<>();
        findJavasInFolder(installs, Paths.get("/Library/Java/JavaVirtualMachines/"));
        findJavasInFolder(installs, Paths.get("/System/Library/Java/JavaVirtualMachines/"));

        if (props.findGradleJdks) {
            // Gradle installed
            findJavasInFolder(installs, Paths.get(System.getProperty("user.home"), ".gradle/jdks"));
        }
        if (props.findIntellijJdks) {
            // Intellij installed
            findJavasInFolder(installs, Paths.get(System.getProperty("user.home"), ".jdks"));
        }
        return new ArrayList<>(installs.values());
    }
}
