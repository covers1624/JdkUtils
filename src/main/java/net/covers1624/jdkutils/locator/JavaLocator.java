package net.covers1624.jdkutils.locator;

import net.covers1624.jdkutils.JavaInstall;
import net.covers1624.jdkutils.JavaVersion;
import net.covers1624.quack.annotation.Requires;
import net.covers1624.quack.collection.ColUtils;
import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Capable of locating Java installations on the current system.
 * <p>
 * Created by covers1624 on 28/10/21.
 */
@Requires ("org.slf4j:slf4j-api")
@Requires ("net.rubygrapefruit:native-platform")
@Requires ("net.rubygrapefruit:native-platform-windows-amd64")
@Requires ("net.rubygrapefruit:native-platform-windows-i386")
public abstract class JavaLocator {

    private final boolean useJavaw;

    protected JavaLocator(boolean useJavaw) {
        this.useJavaw = useJavaw;
    }

    /**
     * Create a {@link Builder} for a {@link JavaLocator} for the current system.
     *
     * @return A builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Performs the searching of Java installations.
     * <p>
     * The result of this should be cached.
     * <p>
     * This can be re-run at any time.
     *
     * @return The located Java installations.
     * @throws IOException If an IO error occurred whilst searching folders, etc.
     */
    public abstract List<JavaInstall> findJavaVersions() throws IOException;

    // Internal used by JavaLocator implementations.
    protected void findJavasInFolder(List<JavaInstall> installs, Path folder) throws IOException {
        if (Files.notExists(folder)) return;
        for (Path path : listDir(folder)) {
            if (!Files.isDirectory(path)) continue;
            addJavaInstall(installs, JavaInstall.parse(getJavaExecutable(path)));
        }
    }

    protected Path getJavaExecutable(Path path) {
        return JavaInstall.getJavaExecutable(JavaInstall.getHomeDirectory(path), useJavaw);
    }

    protected static void addJavaInstall(List<JavaInstall> installs, @Nullable JavaInstall install) {
        if (install == null) return;
        // Simple duplicate filter.
        if (!ColUtils.anyMatch(installs, e -> e.javaHome.equals(install.javaHome))) {
            installs.add(install);
        }
    }

    protected static List<Path> listDir(Path dir) throws IOException {
        if (Files.notExists(dir)) return Collections.emptyList();

        try (Stream<Path> files = Files.list(dir)) {
            return files.collect(Collectors.toList());
        }
    }

    /**
     * A Builder to build a {@link JavaLocator}.
     */
    public static class Builder {

        private final List<JavaLocator> locators = new ArrayList<>(3);
        private boolean useJavaw;
        private boolean findIntellijJdks;
        private boolean findGradleJdks;
        private boolean ignoreOpenJ9;
        private boolean requireJdk;
        private @Nullable JavaVersion filter;
        private boolean withoutSystemSearch;

        /**
         * If 'javaw.exe' should be located instead of 'java.exe' on Windows.
         *
         * @return The same builder.
         */
        public Builder useJavaw() {
            useJavaw = true;
            return this;
        }

        /**
         * If all {@link JavaLocator}s should locate JDK's provisioned by Intellij.
         *
         * @return The same builder.
         */
        public Builder findIntellijJdks() {
            findIntellijJdks = true;
            return this;
        }

        /**
         * If all {@link JavaLocator}s should locate JDK's provisioned by Gradle.
         *
         * @return The same builder.
         */
        public Builder findGradleJdks() {
            findGradleJdks = true;
            return this;
        }

        /**
         * If OpenJ9 should be ignored.
         * <p>
         * OpenJ9 is known to cause issues due to it fundamentally changing large portions
         * of the JVM, compared to the HotSpot standard.
         *
         * @return The same builder.
         */
        public Builder ignoreOpenJ9() {
            ignoreOpenJ9 = true;
            return this;
        }

        /**
         * If Java installations not containing a compiler should be ignored.
         *
         * @return The same builder.
         */
        public Builder requireJdk() {
            requireJdk = true;
            return this;
        }

        /**
         * If all {@link JavaLocator}s should filter for a specific Java version.
         *
         * @param filter The filter.
         * @return The same builder.
         */
        public Builder filter(JavaVersion filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Instruct the built JavaLocator to not search for system installations.
         *
         * @return The same builder.
         */
        public Builder withoutSystemSearch() {
            withoutSystemSearch = true;
            return this;
        }

        /**
         * Add a custom {@link JavaLocator} to the search.
         *
         * @param locator The locator.
         * @return The same builder.
         */
        public Builder withCustomLocator(JavaLocator locator) {
            locators.add(locator);
            return this;
        }

        /**
         * The built {@link JavaLocator}.
         *
         * @return The locator.
         */
        public JavaLocator build() {
            if (!withoutSystemSearch) {
                OperatingSystem os = OperatingSystem.current();
                switch (os) {
                    case WINDOWS:
                        locators.add(new WindowsJavaLocator(useJavaw));
                        break;
                    case LINUX:
                        locators.add(new LinuxJavaLocator());
                        break;
                    case MACOS:
                        locators.add(new MacosJavaLocator());
                        break;
                    default:
                        throw new AssertionError("Unable to locate Java installations on " + os);
                }
            }
            if (findIntellijJdks) {
                locators.add(new IntelliJJavaLocator(useJavaw));
            }
            if (findGradleJdks) {
                locators.add(new GradleJavaLocator(useJavaw));
            }
            if (locators.isEmpty()) {
                throw new IllegalStateException("No locators configured.");
            }
            return new CompositeLocator(locators, ignoreOpenJ9, requireJdk, filter);
        }
    }

    private static class CompositeLocator extends JavaLocator {

        private final List<JavaLocator> locators;
        private final boolean ignoreOpenJ9;
        private final boolean requireJdk;
        private final @Nullable JavaVersion filter;

        public CompositeLocator(List<JavaLocator> locators, boolean ignoreOpenJ9, boolean requireJdk, @Nullable JavaVersion filter) {
            super(false);
            this.locators = locators;
            this.ignoreOpenJ9 = ignoreOpenJ9;
            this.requireJdk = requireJdk;
            this.filter = filter;
        }

        @Override
        public List<JavaInstall> findJavaVersions() throws IOException {
            List<JavaInstall> installs = new ArrayList<>();
            for (JavaLocator locator : locators) {
                installs.addAll(locator.findJavaVersions());
            }
            return FastStream.of(installs)
                    .filter(e -> filter == null || filter == e.langVersion)
                    .filter(e -> !ignoreOpenJ9 || !e.isOpenJ9)
                    .filter(e -> !requireJdk || e.hasCompiler)
                    .toList();
        }
    }
}
