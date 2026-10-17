"""Checks a built APK before release and prints a report.

Usage: python scripts/check_apk.py APK [BUILD_TOOLS_DIR]
Checks: ABIs, ELF type and 16 KB LOAD alignment of every .so, engine hash vs jniLibs,
zipalign -P 16, and min/target SDK (the last two need BUILD_TOOLS_DIR).
"""
import hashlib
import os
import struct
import subprocess
import sys
import zipfile

ENGINE = "lib/arm64-v8a/libstockfish.so"
JNI_ENGINE = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "jniLibs", "arm64-v8a", "libstockfish.so")
PAGE = 16384


def elf_info(data):
    """Returns (is 64-bit, machine, smallest PT_LOAD alignment)."""
    if data[:4] != b"\x7fELF":
        raise ValueError("not ELF")
    is64 = data[4] == 2
    machine = struct.unpack_from("<H", data, 18)[0]
    phoff, = struct.unpack_from("<Q", data, 32)
    phentsize, phnum = struct.unpack_from("<HH", data, 54)
    aligns = []
    for i in range(phnum):
        off = phoff + i * phentsize
        p_type, = struct.unpack_from("<I", data, off)
        if p_type == 1:  # PT_LOAD
            aligns.append(struct.unpack_from("<Q", data, off + 48)[0])
    return is64, machine, min(aligns)


def main(apk, tools=None):
    ok = True
    z = zipfile.ZipFile(apk)
    libs = [i for i in z.infolist() if i.filename.startswith("lib/") and i.filename.endswith(".so")]
    abis = sorted({i.filename.split("/")[1] for i in libs})
    print(f"APK: {apk} ({os.path.getsize(apk)} bytes)")
    print(f"ABIs: {abis}")
    if abis != ["arm64-v8a"]:
        ok = False
        print("  FAIL: expected only arm64-v8a")
    for i in libs:
        data = z.read(i.filename)
        is64, machine, align = elf_info(data)
        stored = i.compress_type == zipfile.ZIP_STORED
        line = f"{i.filename}: {len(data)} bytes, {'stored' if stored else 'compressed'}, LOAD align {align}"
        bad = not is64 or machine != 183 or align < PAGE
        # A stored .so is mapped straight from the APK, so its data must start on a 16 KB boundary.
        if stored and (i.header_offset + 30 + len(i.filename) + len(i.extra)) % PAGE:
            bad = True
            line += ", not 16 KB aligned in zip"
        print(("  FAIL " if bad else "  ok   ") + line)
        ok &= not bad
    engine = hashlib.sha256(z.read(ENGINE)).hexdigest()
    print(f"Engine SHA-256: {engine}")
    if os.path.exists(JNI_ENGINE):
        same = hashlib.sha256(open(JNI_ENGINE, "rb").read()).hexdigest() == engine
        print(("  ok   " if same else "  FAIL ") + "matches app/src/main/jniLibs copy")
        ok &= same
    if tools:
        exe = ".exe" if os.name == "nt" else ""
        r = subprocess.run([os.path.join(tools, "zipalign" + exe), "-c", "-P", "16", "4", apk], capture_output=True, text=True)
        print(("  ok   " if r.returncode == 0 else "  FAIL ") + "zipalign -c -P 16 4")
        ok &= r.returncode == 0
        badging = subprocess.run([os.path.join(tools, "aapt2" + exe), "dump", "badging", apk], capture_output=True, text=True).stdout
        for l in badging.splitlines():
            if l.startswith(("package:", "minSdkVersion", "sdkVersion", "targetSdkVersion", "native-code")):
                print("  " + l)
    print("RESULT: " + ("PASS" if ok else "FAIL"))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main(*sys.argv[1:])
