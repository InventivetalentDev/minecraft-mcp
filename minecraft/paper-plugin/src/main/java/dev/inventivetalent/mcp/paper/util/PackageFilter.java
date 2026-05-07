package dev.inventivetalent.mcp.paper.util;

import java.util.List;

/**
 * Determines whether a fully-qualified class name is permitted for reflection.
 * An entry may be an exact class name or end in ".*" as a package wildcard.
 */
public class PackageFilter {

    private final List<String> allowed;
    private final List<String> blocked;

    public PackageFilter(List<String> allowed, List<String> blocked) {
        this.allowed = allowed;
        this.blocked = blocked;
    }

    public boolean isAllowed(String className) {
        // Blocked takes precedence
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
