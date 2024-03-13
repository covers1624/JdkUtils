package net.covers1624.jdkutils;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.covers1624.jdkutils.locator.JavaLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Created by covers1624 on 25/11/21.
 */
public class LocatorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocatorTest.class);

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();

        OptionSpec<Void> helpOpt = parser.acceptsAll(asList("h", "help"), "Prints this help").forHelp();
        OptionSpec<Void> findGradleJdks = parser.accepts("find-gradle", "Find JDK's installed by Gradle.");
        OptionSpec<Void> findIntellijJdks = parser.accepts("find-intellij", "Find JDK's installed by Intellij.");
        OptionSpec<Void> ignoreOpenJ9 = parser.accepts("ignore-openj9", "Ignore OpenJ9 VMs.");
        OptionSpec<Void> useJavaw = parser.accepts("use-javaw", "If javaw should be preferred on Windows.");
        OptionSpec<Void> ignoreJres = parser.accepts("ignore-jres", "If installations not containing javac should be ignored.");
        OptionSpec<Void> rawOpt = parser.accepts("raw", "Dump raw data.");
        OptionSet optSet = parser.parse(args);

        if (optSet.has(helpOpt)) {
            parser.printHelpOn(System.err);
            System.exit(-1);
        }

        JavaLocator.Builder builder = JavaLocator.builder();

        if (optSet.has(findGradleJdks)) {
            builder.findGradleJdks();
        }
        if (optSet.has(findIntellijJdks)) {
            builder.findIntellijJdks();
        }
        if (optSet.has(ignoreOpenJ9)) {
            builder.ignoreOpenJ9();
        }
        if (optSet.has(useJavaw)) {
            builder.useJavaw();
        }
        if (optSet.has(ignoreJres)) {
            builder.requireJdk();
        }

        List<JavaInstall> installs = builder.build().findJavaVersions();
        LOGGER.info("Found {} Java installs.", installs.size());
        for (JavaInstall install : installs) {
            if (optSet.has(rawOpt)) {
                LOGGER.info("{}", install);
            } else {
                LOGGER.info("Version: '{}', Lang version {}, Home '{}', Has Compiler: '{}'", install.implVersion, install.langVersion, install.javaHome, install.hasCompiler);
            }
        }
    }
}
