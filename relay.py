import socket
import threading
import sys

WATCH_IP = "192.168.1.57"
PORT = 48899

def forward(src, dst, tag):
    try:
        while True:
            data = src.recv(4096)
            if not data:
                break
            dst.sendall(data)
    except:
        pass
    finally:
        try: src.close()
        except: pass
        try: dst.close()
        except: pass

def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        server.bind(('0.0.0.0', PORT))
    except Exception as e:
        print(f"Bind error: {e}", flush=True)
        return
    server.listen(5)
    print(f"HyperBridge TCP Relay listening on 0.0.0.0:{PORT} -> {WATCH_IP}:{PORT}", flush=True)

    while True:
        client, addr = server.accept()
        print(f"Incoming connection from {addr}", flush=True)
        try:
            target = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            target.settimeout(5)
            target.connect((WATCH_IP, PORT))
            target.settimeout(None) # Remove timeout so stream stays alive indefinitely!
            print(f"Connected to watch {WATCH_IP}:{PORT}!", flush=True)
            t1 = threading.Thread(target=forward, args=(client, target, "c->w"))
            t2 = threading.Thread(target=forward, args=(target, client, "w->c"))
            t1.daemon = True
            t2.daemon = True
            t1.start()
            t2.start()
        except Exception as e:
            print(f"Error bridging to watch: {e}", flush=True)
            client.close()

if __name__ == '__main__':
    main()
