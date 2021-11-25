/*
 * This file is part of JdkUtils and is Licensed under the MIT License.
 *
 * Copyright (c) 2018-2021 covers1624 <https://github.com/covers1624>
 */
package net.covers1624.jdkutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Created by covers1624 on 25/11/21.
 */
public class LocatorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocatorTest.class);

    public static void main(String[] args) throws IOException {
        JavaLocator locator = JavaLocator.builder()
                .findGradleJdks()
                .findIntellijJdks()
                .ignoreOpenJ9()
                .useJavaw()
                .build();

        List<JavaInstall> installs = locator.findJavaVersions();
        LOGGER.info("Found {} Java installs.", installs.size());
        for (JavaInstall install : installs) {
            LOGGER.info("Version: '{}', Lang version {}, Home '{}'", install.implVersion, install.langVersion, install.javaHome);
        }
    }

}
