package com.jeff_media.jefflib.data;

import org.bukkit.Bukkit;

/**
 * Minimalist replacement for JeffLib's {@code McVersion}.
 */
public final class McVersion {

    private static final McVersion CURRENT = parse(Bukkit.getBukkitVersion());

    private final int major;
    private final int minor;
    private final int patch;

    private McVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static McVersion current() {
        return CURRENT;
    }

    public boolean isAtLeast(int major, int minor, int patch) {
        if (this.major != major) {
            return this.major > major;
        }
        if (this.minor != minor) {
            return this.minor > minor;
        }
        return this.patch >= patch;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    private static McVersion parse(String version) {
        if (version == null || version.isEmpty()) {
            return new McVersion(1, 0, 0);
        }
        String[] parts = version.split("-")[0].split("\\.");

        int major = parsePart(parts, 0);
        int minor = parsePart(parts, 1);
        int patch = parsePart(parts, 2);
        return new McVersion(major, minor, patch);
    }

    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("\\D", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
