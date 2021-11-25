/*
 * This file is part of JdkUtils and is Licensed under the MIT License.
 *
 * Copyright (c) 2018-2021 covers1624 <https://github.com/covers1624>
 */
package net.covers1624.jdkutils;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capable of finding JDK installations in common locations
 * on a Linux system.
 *
 * Created by covers1624 on 29/10/21.
 */
public class LinuxJavaLocator extends JavaLocator {

    LinuxJavaLocator(LocatorProps props) {
        super(props);
    }

    @Override
    public List<JavaInstall> findJavaVersions() throws IOException {
        Map<String, JavaInstall> installs = new LinkedHashMap<>();
        // Oracle
        findJavasInFolder(installs, Paths.get("/usr/java"));

        // Common distro locations
        findJavasInFolder(installs, Paths.get("/usr/lib/jvm"));
        findJavasInFolder(installs, Paths.get("/usr/lib32/jvm"));

        // Manually installed locations
        findJavasInFolder(installs, Paths.get("/opt/jdk"));
        findJavasInFolder(installs, Paths.get("/opt/jdks"));

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
