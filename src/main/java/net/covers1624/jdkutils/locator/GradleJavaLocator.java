package net.covers1624.jdkutils.locator;

import net.covers1624.jdkutils.JavaInstall;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by covers1624 on 13/3/24.
 */
public class GradleJavaLocator extends JavaLocator {

    GradleJavaLocator(boolean useJavaw) {
        super(useJavaw);
    }

    @Override
    public List<JavaInstall> findJavaVersions() throws IOException {
        List<JavaInstall> installs = new ArrayList<>();
        findJavasInFolder(installs, Paths.get(System.getProperty("user.home"), ".gradle/jdks"));
        return installs;
    }
}
