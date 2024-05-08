package net.covers1624.jdkutils.provisioning.adoptium;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import net.covers1624.quack.gson.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * @author covers1624
 */
public class AdoptiumRelease {

    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<AdoptiumRelease>>() { }.getType();

    public List<AdoptiumRelease.Binary> binaries = new ArrayList<>();
    public String release_name;
    public AdoptiumRelease.VersionData version_data;

    public static List<AdoptiumRelease> parseReleases(InputStream is) throws IOException, JsonParseException {
        return JsonUtils.parse(GSON, is, LIST_TYPE);
    }

    public static class Binary {

        public String image_type;

        @SerializedName ("package")
        public AdoptiumRelease.Package pkg;
    }

    public static class Package {

        public String checksum;
        public String link;
        public String name;
        public int size;
    }

    public static class VersionData {

        public String openjdk_version;
    }
}
