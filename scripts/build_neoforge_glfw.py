"""Build NeoForge's GLFW jar without OpenVR classes already shipped by Vivecraft."""

import sys
import zipfile


def build(base_path, vivecraft_path, output_path):
    prefix = "org/lwjgl/openvr/"
    with zipfile.ZipFile(base_path) as base, zipfile.ZipFile(vivecraft_path) as vivecraft:
        redundant = {name for name in base.namelist()
                     if name.startswith(prefix) and name.endswith(".class")}
        provided = set(vivecraft.namelist())
        if not redundant or not redundant.issubset(provided):
            raise ValueError("NeoForge Vivecraft does not provide all launcher OpenVR classes")

        with zipfile.ZipFile(output_path, "w") as output:
            for entry in base.infolist():
                # The jar index would be stale after removing a package.
                if entry.filename.startswith(prefix) or entry.filename == "META-INF/INDEX.LIST":
                    continue
                output.writestr(entry, base.read(entry))

    with zipfile.ZipFile(output_path) as result:
        assert "org/lwjgl/glfw/GLFW.class" in result.namelist()
        assert not any(name.startswith(prefix) for name in result.namelist())
    print(f"NeoForge GLFW: Vivecraft supplies {len(redundant)} OpenVR classes")


if __name__ == "__main__":
    build(*sys.argv[1:4])
