import os
import subprocess
import zipfile

JAVA_HOME = r"C:\Program Files\Android\Android Studio\jbr"
os.environ["JAVA_HOME"] = JAVA_HOME
os.environ["PATH"] = os.path.join(JAVA_HOME, "bin") + ";" + os.environ.get("PATH", "")
D8 = r"C:\Users\kaiser\AppData\Local\Android\Sdk\build-tools\34.0.0\d8.bat"
PLATFORM = r"C:\Users\kaiser\AppData\Local\Android\Sdk\platforms\android-34\android.jar"
classes_dir = r"C:\Users\kaiser\.gemini\antigravity\brain\ce73d3a2-1af3-409a-99c4-3af029a80a16\hyperbridge\out\hyperbridge-watch\classes"
dex_dir = r"C:\Users\kaiser\.gemini\antigravity\brain\ce73d3a2-1af3-409a-99c4-3af029a80a16\hyperbridge\out\hyperbridge-watch\dex"
jar_file = r"C:\Users\kaiser\.gemini\antigravity\brain\ce73d3a2-1af3-409a-99c4-3af029a80a16\hyperbridge\out\hyperbridge-watch\classes.jar"

with zipfile.ZipFile(jar_file, "w") as z:
    for root, _, files in os.walk(classes_dir):
        for f in files:
            if f.endswith('.class'):
                full_path = os.path.join(root, f)
                rel_path = os.path.relpath(full_path, classes_dir)
                z.write(full_path, rel_path)

print("Created classes.jar with", len(z.namelist()), "files")

cmd = f'"{D8}" --min-api 26 --output "{dex_dir}" --lib "{PLATFORM}" "{jar_file}"'
res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
print("Return code:", res.returncode)
if res.returncode == 0:
    print("SUCCESS! Dex created from jar!")
else:
    print("STDOUT:\n", res.stdout)
    print("STDERR:\n", res.stderr)
