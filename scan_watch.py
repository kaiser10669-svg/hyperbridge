import socket

ip = "192.168.1.57"
print(f"Scanning {ip}...")

# Check common ADB ports and 30000-50000
ports = [5555] + list(range(35000, 48000))
open_ports = []
for p in ports:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(0.05)
    res = s.connect_ex((ip, p))
    if res == 0:
        print(f"OPEN PORT: {p}")
        open_ports.append(p)
    s.close()

print(f"Done. Open ports: {open_ports}")
