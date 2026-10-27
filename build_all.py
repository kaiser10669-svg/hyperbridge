import os
import sys
import subprocess
import zipfile
import shutil

JAVA_HOME = r"C:\Program Files\Android\Android Studio\jbr"
os.environ["JAVA_HOME"] = JAVA_HOME
os.environ["PATH"] = os.path.join(JAVA_HOME, "bin") + ";" + os.environ.get("PATH", "")

SDK_DIR = r"C:\Users\kaiser\AppData\Local\Android\Sdk"
BUILD_TOOLS = os.path.join(SDK_DIR, "build-tools", "34.0.0")
PLATFORM_JAR = os.path.join(SDK_DIR, "platforms", "android-34", "android.jar")
AAPT2 = os.path.join(BUILD_TOOLS, "aapt2.exe")
D8 = os.path.join(BUILD_TOOLS, "d8.bat")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign.exe")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner.bat")
JAVAC = os.path.join(JAVA_HOME, "bin", "javac.exe")
KEYSTORE = os.path.expanduser(r"~/.android/debug.keystore")

BASE_DIR = r"C:\Users\kaiser\.gemini\antigravity\brain\ce73d3a2-1af3-409a-99c4-3af029a80a16\hyperbridge"
OUT_DIR = os.path.join(BASE_DIR, "out")

def run(cmd, cwd=None):
    print("RUN:", " ".join(cmd) if isinstance(cmd, list) else cmd)
    res = subprocess.run(cmd, cwd=cwd, shell=isinstance(cmd, str), capture_output=True, text=True)
    if res.returncode != 0:
        print("STDOUT:", res.stdout)
        print("STDERR:", res.stderr)
        raise RuntimeError(f"Command failed with code {res.returncode}")
    return res.stdout

def build_app(name, app_dir):
    print(f"\n==================== BUILDING {name.upper()} ====================")
    work_dir = os.path.join(OUT_DIR, name)
    if os.path.exists(work_dir):
        shutil.rmtree(work_dir)
    os.makedirs(work_dir, exist_ok=True)

    res_dir = os.path.join(app_dir, "res")
    manifest = os.path.join(app_dir, "AndroidManifest.xml")
    compiled_res = os.path.join(work_dir, "compiled_res.zip")
    gen_dir = os.path.join(work_dir, "gen")
    os.makedirs(gen_dir, exist_ok=True)
    classes_dir = os.path.join(work_dir, "classes")
    os.makedirs(classes_dir, exist_ok=True)
    dex_dir = os.path.join(work_dir, "dex")
    os.makedirs(dex_dir, exist_ok=True)

    raw_apk = os.path.join(work_dir, f"{name}-raw.apk")
    aligned_apk = os.path.join(work_dir, f"{name}-aligned.apk")
    final_apk = os.path.join(OUT_DIR, f"{name}.apk")

    # 1. Compile resources with AAPT2
    print("1. Compiling resources...")
    run([AAPT2, "compile", "--dir", res_dir, "-o", compiled_res])

    # 2. Link resources with AAPT2
    print("2. Linking resources...")
    run([
        AAPT2, "link",
        "-I", PLATFORM_JAR,
        "--manifest", manifest,
        "--java", gen_dir,
        "-o", raw_apk,
        compiled_res
    ])

    # 3. Collect Java source files
    src_files = []
    for root, _, files in os.walk(os.path.join(app_dir, "src")):
        for f in files:
            if f.endswith(".java"):
                src_files.append(os.path.join(root, f))
    for root, _, files in os.walk(gen_dir):
        for f in files:
            if f.endswith(".java"):
                src_files.append(os.path.join(root, f))

    print(f"3. Compiling {len(src_files)} Java sources...")
    javac_cmd = [
        JAVAC,
        "-source", "8",
        "-target", "8",
        "-encoding", "UTF-8",
        "-cp", PLATFORM_JAR,
        "-d", classes_dir
    ] + src_files
    run(javac_cmd)

    # 4. Create classes.jar and Dex with D8
    print("4. Packing classes.jar and dexing with D8...")
    classes_jar = os.path.join(work_dir, "classes.jar")
    with zipfile.ZipFile(classes_jar, "w") as z:
        for root, _, files in os.walk(classes_dir):
            for f in files:
                if f.endswith('.class'):
                    full_p = os.path.join(root, f)
                    rel_p = os.path.relpath(full_p, classes_dir)
                    z.write(full_p, rel_p)

    d8_cmd = f'"{D8}" --min-api 26 --output "{dex_dir}" --lib "{PLATFORM_JAR}" "{classes_jar}"'
    run(d8_cmd)

    # 5. Add classes.dex into APK
    print("5. Packing classes.dex into APK...")
    dex_file = os.path.join(dex_dir, "classes.dex")
    with zipfile.ZipFile(raw_apk, "a", compression=zipfile.ZIP_DEFLATED) as z:
        z.write(dex_file, "classes.dex")

    # 6. Zipalign
    print("6. Zipaligning APK...")
    if os.path.exists(aligned_apk):
        os.remove(aligned_apk)
    run([ZIPALIGN, "-p", "-f", "4", raw_apk, aligned_apk])

    # 7. Sign APK
    print("7. Signing APK...")
    sign_cmd = f'"{APKSIGNER}" sign --ks "{KEYSTORE}" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out "{final_apk}" "{aligned_apk}"'
    run(sign_cmd)

    print(f"SUCCESS: Created {final_apk} ({os.path.getsize(final_apk):,} bytes)")
    return final_apk

if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    watch_apk = build_app("hyperbridge-watch", os.path.join(BASE_DIR, "watch"))
    phone_apk = build_app("hyperbridge-phone", os.path.join(BASE_DIR, "phone"))
    print("\n================== ALL APKS BUILT SUCCESSFULLY ==================")
    print(f"Watch APK: {watch_apk} ({os.path.getsize(watch_apk):,} bytes)")
    print(f"Phone APK: {phone_apk} ({os.path.getsize(phone_apk):,} bytes)")
