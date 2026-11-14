import argparse
import hashlib
import io
import json
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


def pack(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, content in sorted(entries):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, content)
    return output.getvalue()


def main():
    parser = argparse.ArgumentParser(description="Build the XLua Rider navigation plugin.")
    parser.add_argument("--rider-home", default=os.environ.get("RIDER_HOME"))
    parser.add_argument("--lua-plugin", default=os.environ.get("LUA_PLUGIN_PATH"))
    args = parser.parse_args()
    if not args.rider_home or not args.lua_plugin:
        parser.error("Set RIDER_HOME and LUA_PLUGIN_PATH, or pass --rider-home and --lua-plugin.")
    root = Path(__file__).resolve().parent
    rider = Path(args.rider_home).expanduser().resolve()
    if (rider / "Contents").is_dir():
        rider /= "Contents"
    lua = Path(args.lua_plugin).expanduser().resolve()
    if lua.is_dir():
        lua /= "lib/lua.jar"
    sdk = json.loads((root / "sdk.json").read_text(encoding="utf-8"))
    product_path = rider / "product-info.json"
    if not product_path.is_file():
        product_path = rider / "Resources/product-info.json"
    product = json.loads(product_path.read_text(encoding="utf-8"))
    if product.get("productCode") != "RD" or product.get("buildNumber") != sdk["rider_build"]:
        parser.error(f"Expected Rider build {sdk['rider_build']}; update sdk.json only after compatibility validation.")
    with zipfile.ZipFile(lua) as archive:
        lua_meta = ET.fromstring(archive.read("META-INF/plugin.xml"))
    if lua_meta.findtext("id") != "com.intellij.lua" or lua_meta.findtext("version") != sdk["lua_version"]:
        parser.error(f"Expected JetBrains Lua {sdk['lua_version']}.")
    metadata = ET.parse(root / "src/main/resources/META-INF/plugin.xml").getroot()
    version = metadata.findtext("version")
    if os.environ.get("GITHUB_REF_TYPE") == "tag" and os.environ.get("GITHUB_REF_NAME") != f"v{version}":
        parser.error("The release tag must match the version in plugin.xml.")
    javac = rider / ("jbr/bin/javac.exe" if os.name == "nt" else "jbr/bin/javac")
    if not javac.is_file():
        javac = rider / "jbr/Contents/Home/bin/javac"
    classpath = os.pathsep.join(str(p) for p in [*sorted((rider / "lib").glob("*.jar")), lua])
    sources = sorted((root / "src/main/java").rglob("*.java"))
    build = root / "build"
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="compile-", dir=build) as directory:
        temporary = Path(directory)
        classes = temporary / "classes"
        classes.mkdir()
        arguments = temporary / "javac.args"
        values = ["-encoding", "UTF-8", "--release", "25", "-proc:none", "-classpath", classpath, "-d", classes, *sources]
        arguments.write_text("\n".join('"' + str(v).replace("\\", "/") + '"' for v in values), encoding="utf-8")
        subprocess.run([str(javac), "@" + str(arguments)], check=True)
        entries = [(p.relative_to(classes).as_posix(), p.read_bytes()) for p in classes.rglob("*.class")]
        resources = root / "src/main/resources"
        entries.extend((p.relative_to(resources).as_posix(), p.read_bytes()) for p in resources.rglob("*") if p.is_file())
        entries.extend((f"META-INF/{name}", (root / name).read_bytes()) for name in ("LICENSE", "NOTICE"))
        jar = pack(entries)
    dist = root / "dist"
    dist.mkdir(exist_ok=True)
    package = dist / f"xlua-rider-bridge-{version}.zip"
    package.write_bytes(pack([("xlua-rider-bridge/lib/xlua-rider-bridge.jar", jar)]))
    checksum = hashlib.sha256(package.read_bytes()).hexdigest()
    (dist / "SHA256SUMS").write_text(f"{checksum}  {package.name}\n", encoding="utf-8")
    print(package)
    print(f"SHA256: {checksum}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        sys.exit(f"Build failed: {error}")
