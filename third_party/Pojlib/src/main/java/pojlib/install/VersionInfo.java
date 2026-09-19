package pojlib.install;

import com.google.gson.annotations.SerializedName;

public class VersionInfo {
    @SerializedName("id")
    public String id;
    @SerializedName("type")
    public String type;
    @SerializedName("assetIndex")
    public AssetIndex assetIndex;
    @SerializedName("downloads")
    public Downloads downloads;
    @SerializedName("libraries")
    public Library[] libraries;
    @SerializedName("mainClass")
    public String mainClass;
    @SerializedName("arguments")
    public Arguments arguments;
    @SerializedName("assets")
    public String assets;

    public static class AssetIndex {
        @SerializedName("id")
        public String id;
        @SerializedName("totalSize")
        public int totalSize;
        @SerializedName("url")
        public String url;
    }

    public static class Downloads {
        @SerializedName("client")
        public Client client;

        public static class Client {
            @SerializedName("sha1")
            public String sha1;
            @SerializedName("url")
            public String url;
        }
    }

    public static class Arguments {
        @SerializedName("game")
        public Object[] game;
        @SerializedName("jvm")
        public Object[] jvm;

        public static class ArgValue {
            @SerializedName("rules")
            public ArgRules[] rules;
            @SerializedName("value")
            public String value;

            public static class ArgRules {
                @SerializedName("action")
                public String action;
                @SerializedName("features")
                public String features;
                @SerializedName("os")
                public ArgOS os;

                public static class ArgOS {
                    @SerializedName("name")
                    public String name;
                    @SerializedName("version")
                    public String version;
                }
            }
        }
    }

    public static class Library {
        public Rule[] rules;
        public static class Rule {
            public String action;
            public OS os;
            public static class OS { public String name; public String arch; }
        }
        public boolean allowedOnAndroid() {
            if (rules == null || rules.length == 0) return true;
            boolean allowed = false;
            for (Rule rule : rules) {
                boolean matches = rule.os == null ||
                    ((rule.os.name == null || "linux".equals(rule.os.name)) &&
                     (rule.os.arch == null || "aarch64".equals(rule.os.arch) || "arm64".equals(rule.os.arch)));
                if (matches) allowed = "allow".equals(rule.action);
            }
            return allowed;
        }

        @SerializedName("downloads")
        public Downloads downloads;
        @SerializedName("name")
        public String name;
        @SerializedName("url")
        public String url;

        public static class Downloads {
            @SerializedName("artifact")
            public Artifact artifact;
        }

        public static class Artifact {
            @SerializedName("path")
            public String path;
            @SerializedName("sha1")
            public String sha1;
            @SerializedName("size")
            public int size;
            @SerializedName("url")
            public String url;
        }
    }

    public static class Asset {
        @SerializedName("hash")
        public String hash;
        @SerializedName("size")
        public int size;
    }
}
