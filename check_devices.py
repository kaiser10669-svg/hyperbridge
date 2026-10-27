import subprocess

ps_cmd = 'Get-PnpDevice -PresentOnly | Select-Object FriendlyName, InstanceId | Format-Table -AutoSize'
res = subprocess.run(["powershell", "-NoProfile", "-Command", ps_cmd], capture_output=True, text=True, encoding="utf-8", errors="replace")
if res.stdout:
    for line in res.stdout.splitlines():
        if any(k in line.lower() for k in ["usb", "composite", "phone", "vivo", "android", "portable", "device", "serial"]):
            print(line[:100])
