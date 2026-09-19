#!/usr/bin/env bash
set -euo pipefail

echo "Syncing VoxyQuest submodules..."
git submodule sync --recursive
git submodule update --init --recursive

echo ""
echo "Pinned dependencies:"
git submodule status --recursive

echo ""
echo "Dependency bootstrap complete."
