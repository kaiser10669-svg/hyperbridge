import subprocess
import time
import threading

ADB = r"C:\Users\kaiser\AppData\Local\Android\Sdk\platform-tools\adb.exe"
APK = r"C:\Users\kaiser\.gemini\antigravity\brain\ce73d3a2-1af3-409a-99c4-3af029a80a16\hyperbridge\out\hyperbridge-phone.apk"

def find_vivo_device():
    subprocess.run([ADB, "connect", "192.168.1.140:5555"], capture_output=True)
    res = subprocess.run([ADB, "devices", "-l"], capture_output=True, text=True)
    for line in res.stdout.splitlines():
        if ("PD2547" in line or "V2547A" in line or "10AG3H00EZ001GT" in line or "192.168.1.140" in line) and "device" in line:
            parts = line.split()
            if parts:
                return parts[0]
    return "192.168.1.140:5555"

DEV = find_vivo_device()
print(f"Targeting Vivo X300 Ultra on: {DEV}")

def tap_worker():
    for _ in range(25):
        time.sleep(0.3)
        # wake screen
        subprocess.run([ADB, "-s", DEV, "shell", "input", "keyevent", "224"], capture_output=True)
        # tap checkbox coordinates (OriginOS threat dialog)
        subprocess.run([ADB, "-s", DEV, "shell", "input", "tap", "230", "2315"], capture_output=True)
        subprocess.run([ADB, "-s", DEV, "shell", "input", "tap", "180", "2315"], capture_output=True)
        # tap continue install button coordinates
        subprocess.run([ADB, "-s", DEV, "shell", "input", "tap", "630", "2470"], capture_output=True)
        subprocess.run([ADB, "-s", DEV, "shell", "input", "tap", "630", "2615"], capture_output=True)

t = threading.Thread(target=tap_worker)
t.start()

# Setup reverse port forwarding
subprocess.run([ADB, "-s", DEV, "reverse", "tcp:48899", "tcp:48899"], capture_output=True)

print("Installing APK to Vivo X300 Ultra...")
cmd = [ADB, "-s", DEV, "install", "-r", "-d", "--bypass-low-target-sdk-block", APK]
res = subprocess.run(cmd, capture_output=True, text=True)
print("STDOUT:", res.stdout)
print("STDERR:", res.stderr)
t.join()

if "Success" in res.stdout:
    print("Launching PhoneMainActivity...")
    subprocess.run([ADB, "-s", DEV, "shell", "am", "start", "-n", "com.kaiserdev.hyperbridge/.PhoneMainActivity"], capture_output=True)
    time.sleep(3)
    # Take screenshot
    shot_path = r"C:\Users\kaiser\.gemini\antigravity\brain\ce73d3a2-1af3-409a-99c4-3af029a80a16\cinematic_cockpit_vivo.png"
    subprocess.run([ADB, "-s", DEV, "shell", "screencap", "-p", "/sdcard/cinematic.png"], capture_output=True)
    subprocess.run([ADB, "-s", DEV, "pull", "/sdcard/cinematic.png", shot_path], capture_output=True)
    print(f"Screenshot saved to {shot_path}")
