package net.covers1624.jdkutils;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.covers1624.jdkutils.JdkInstallationManager.ProvisionRequest;
import net.covers1624.jdkutils.provisioning.adoptium.AdoptiumProvisioner;
import net.covers1624.quack.net.httpapi.okhttp.OkHttpEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Arrays.asList;

/**
 * Created by covers1624 on 25/11/21.
 */
public class InstallationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstallationTest.class);

    public static void main(String[] args) throws Throwable {
        OptionParser parser = new OptionParser();

        OptionSpec<Void> helpOpt = parser.acceptsAll(asList("h", "help"), "Prints this help").forHelp();
        OptionSpec<String> javaVersionOpt = parser.accepts("version", "The java version.")
                .withRequiredArg();
        OptionSpec<String> semverOpt = parser.accepts("semver", "The java semver version.")
                .withRequiredArg();
        OptionSpec<Void> forceX64MacOpt = parser.accepts("forceX64Mac", "If AArch64 Mac should be treated as X64.");
        OptionSpec<Void> preferJreOpt = parser.accepts("jre", "If a JRE is all that is required.");
        OptionSet optSet = parser.parse(args);

        if (optSet.has(helpOpt)) {
            parser.printHelpOn(System.err);
            System.exit(-1);
        }

        String version = optSet.valueOf(javaVersionOpt);
        String semver = optSet.valueOf(semverOpt);

        JavaVersion javaVersion;
        if (version != null) {
            javaVersion = JavaVersion.parse(version);
        } else if (semver != null) {
            javaVersion = JavaVersion.parse(semver);
        } else {
            javaVersion = JavaVersion.JAVA_16;
        }

        JdkInstallationManager jdkInstallationManager = new JdkInstallationManager(
                Paths.get("jdks"),
                new AdoptiumProvisioner(OkHttpEngine.create())
        );
        assert javaVersion != null;
        ProvisionRequest.Builder builder = new ProvisionRequest.Builder()
                .forVersion(javaVersion)
                .preferJRE(optSet.has(preferJreOpt))
                .forceX64OnMac(optSet.has(forceX64MacOpt))
                .downloadListener(new StatusRequestListener());
        if (semver != null) {
            builder.withSemver(semver);
        }
        Path homeDir = jdkInstallationManager.provisionJdk(builder.build());
        LOGGER.info("Provisioned Java home installation: {}", homeDir);

        LOGGER.info("Testing installed JDK..");
        JavaInstall install = JavaInstall.parse(JavaInstall.getJavaExecutable(homeDir, false));
        if (install == null) {
            LOGGER.info("Failed to parse java install.");
        } else {
            LOGGER.info("Version: '{}', Lang version {}, Architecture {}, Home '{}', Has Compiler: '{}'", install.implVersion, install.langVersion, install.architecture, install.javaHome, install.hasCompiler);
        }
    }
}
