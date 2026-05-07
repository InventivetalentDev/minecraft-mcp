package dev.inventivetalent.mcp.fabric.util;

import java.util.List;

public class PackageFilter {

    private final List<String> allowed;
    private final List<String> blocked;

    public PackageFilter(List<String> allowed, List<String> blocked) {
        this.allowed = allowed;
        this.blocked = blocked;
    }

    public boolean isAllowed(String className) {
        if (matches(className, blocked)) return false;
        return matches(className, allowed);
    }

    private boolean matches(String className, List<String> patterns) {
        for (String pattern : patterns) {
            if (pattern.endsWith(".*")) {
                String pkg = pattern.substring(0, pattern.length() - 2);
                if (className.equals(pkg) || className.startsWith(pkg + ".")) return true;
            } else {
                if (className.equals(pattern)) return true;
            }
        }
        return false;
    }
}
