"""Check that every launcher version has a usable, internally consistent catalog entry."""

import json
import re
from pathlib import Path
from urllib.parse import unquote, urlparse

ROOT = Path(__file__).resolve().parents[2]
catalog = json.loads((ROOT / "third_party/Pojlib/src/main/assets/voxyquest/runtime_mods.json").read_text())
supported = json.loads((ROOT / "third_party/Pojlib/supportedVersions.json").read_text())["supportedVersions"]
bridge = (ROOT / "launcher/scripts/runtime_bridge.gd").read_text()
fallback = re.search(r"const BUNDLED_INSTALL_VERSIONS := \[(.*?)\]", bridge, re.S)
assert fallback, "Launcher fallback versions missing"
fallback_versions = re.findall(r'"([^"]+)"', fallback.group(1))
versions = [entry["name"] for entry in catalog["versions"]]
assert len(versions) == len(set(versions)), "Duplicate Minecraft version"
assert versions == supported == fallback_versions, "Launcher, installer, and catalog versions differ"

for entry in catalog["versions"]:
    version = entry["name"]
    mods = entry["coreMods"] + entry.get("defaultMods", [])
    slugs = [mod["slug"] for mod in mods]
    assert len(slugs) == len(set(slugs)), f"{version}: duplicate mod name"
    assert entry.get("defaultMods") == [], f"{version}: optional mods should start empty"
    assert slugs == ["Vivecraft"], f"{version}: new Fabric instances must start with Vivecraft alone"
    for mod in mods:
        assert re.fullmatch(r"[A-Za-z0-9_-]+", mod["slug"]), f"{version}: bad mod name"
        url = urlparse(mod["download_link"])
        assert url.scheme == "https" and url.netloc and url.path.endswith(".jar"), (
            f"{version}: invalid download URL for {mod['slug']}"
        )
        filename = unquote(url.path.rsplit("/", 1)[-1])
        if mod["slug"] == "Vivecraft":
            assert version in filename, f"{version}: {mod['slug']} points to {filename}"

print(f"Catalog entries checked: {len(versions)}")
