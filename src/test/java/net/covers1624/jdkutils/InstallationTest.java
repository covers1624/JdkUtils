/*
 * This file is part of JdkUtils and is Licensed under the MIT License.
 *
 * Copyright (c) 2018-2021 covers1624 <https://github.com/covers1624>
 */
package net.covers1624.jdkutils;

import net.covers1624.quack.net.DownloadAction;
import net.covers1624.quack.net.okhttp.OkHttpDownloadAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by covers1624 on 25/11/21.
 */
public class InstallationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocatorTest.class);

    public static void main(String[] args) throws Throwable {
        JavaVersion javaVersion = JavaVersion.JAVA_16;
        boolean ignoreAArch64 = false;
        if (args.length >= 1) {
            javaVersion = JavaVersion.parse(args[0]);
        }
        if (args.length >= 2) {
            ignoreAArch64 = Boolean.parseBoolean(args[1]);
        }
        JdkInstallationManager jdkInstallationManager = new JdkInstallationManager(
                Paths.get("jdks"),
                new AdoptiumProvisioner(() -> {
                    DownloadAction action = new OkHttpDownloadAction();
                    action.setQuiet(false);
                    action.setDownloadListener(new StatusDownloadListener());
                    return action;
                }),
                ignoreAArch64
        );
        assert javaVersion != null;
        Path homeDir = jdkInstallationManager.provisionJdk(javaVersion);
        LOGGER.info("Provisioned Java home installation: {}", homeDir);

        LOGGER.info("Testing installed JDK..");
        JavaInstall install = JavaLocator.parseInstall(JavaInstall.getJavaExecutable(homeDir, false));
        if (install == null) {
            LOGGER.info("Failed to parse java install.");
        } else {
            LOGGER.info("Installed JDK: Version '{}', Lang Version '{}'.", install.implVersion, install.langVersion);
        }
    }
}
