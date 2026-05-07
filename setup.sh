#!/usr/bin/env bash
# One-time setup: install JS deps and bootstrap Gradle wrappers.
set -e

echo "==> Installing Node packages..."
npm install

echo "==> Bootstrapping Gradle wrappers (requires Gradle 8+ on PATH)..."
for dir in minecraft/paper-plugin minecraft/fabric-mod; do
  echo "    $dir"
  (cd "$dir" && gradle wrapper --gradle-version 8.8)
done

echo ""
echo "Setup complete."
echo ""
echo "Next steps:"
echo "  Build Paper plugin:  cd minecraft/paper-plugin && ./gradlew shadowJar"
echo "  Build Fabric mod:    cd minecraft/fabric-mod  && ./gradlew build"
echo "  Run Paper MCP server: npm run dev:paper"
echo "  Run Fabric MCP server: npm run dev:fabric"
