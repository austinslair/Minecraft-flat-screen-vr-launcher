"""Ensure embedded Java can load the APK's native libraries as extracted files."""
import sys
import zipfile

REQUIRED = ("libpojavexec.so", "liblwjgl.so", "libjnidispatch.so")


def verify(path):
    with zipfile.ZipFile(path) as apk:
        for name in REQUIRED:
            entry = apk.getinfo("lib/arm64-v8a/" + name)
            if entry.compress_type != zipfile.ZIP_DEFLATED:
                raise ValueError(f"{name} must be compressed for filesystem extraction")
            header = apk.read(entry)[:20]
            if header[:5] != b"\x7fELF\x02" or int.from_bytes(header[18:20], "little") != 183:
                raise ValueError(f"{name} is not an arm64 ELF library")
    print("PASS: required ARM64 libraries packaged for extraction")


if __name__ == "__main__":
    verify(sys.argv[1])
