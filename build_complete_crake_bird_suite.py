import os
import re
import numpy as np
from PIL import Image, ImageDraw

def rdp(points, epsilon):
    if len(points) < 3:
        return points
    start, end = points[0], points[-1]
    d = end - start
    norm_d = np.hypot(d[0], d[1])
    if norm_d == 0:
        dists = np.hypot(points[:,0] - start[0], points[:,1] - start[1])
    else:
        dists = np.abs(d[0] * (start[1] - points[:,1]) - d[1] * (start[0] - points[:,0])) / norm_d
    max_idx = np.argmax(dists)
    max_d = dists[max_idx]
    if max_d > epsilon:
        left = rdp(points[:max_idx+1], epsilon)
        right = rdp(points[max_idx:], epsilon)
        return np.vstack((left[:-1], right))
    else:
        return np.array([start, end])

def trace_all_boundaries(mask):
    H, W = mask.shape
    contours = []
    visited = np.zeros_like(mask, dtype=bool)
    offsets = [(-1, 0), (-1, 1), (0, 1), (1, 1), (1, 0), (1, -1), (0, -1), (-1, -1)]
    for y in range(1, H-1):
        for x in range(1, W-1):
            if mask[y, x] and not visited[y, x]:
                if not (mask[y-1,x] and mask[y+1,x] and mask[y,x-1] and mask[y,x+1]):
                    start = (y, x)
                    boundary = [start]
                    curr = start
                    backtrack = 6
                    for _ in range(50000):
                        found = False
                        for i in range(8):
                            dir_idx = (backtrack + i) % 8
                            ny, nx = curr[0] + offsets[dir_idx][0], curr[1] + offsets[dir_idx][1]
                            if 0 <= ny < H and 0 <= nx < W and mask[ny, nx]:
                                next_p = (ny, nx)
                                backtrack = (dir_idx + 5) % 8
                                found = True
                                break
                        if not found or next_p == start:
                            break
                        boundary.append(next_p)
                        visited[next_p[0], next_p[1]] = True
                        curr = next_p
                    if len(boundary) > 20:
                        contours.append(np.array(boundary))
    return contours

def generate_svg_path(contour, scale=1.0):
    pts = []
    for y, x in contour:
        px = round(x * scale, 1)
        py = round(y * scale, 1)
        pts.append(f"{px},{py}")
    return "M " + " L ".join(pts) + " Z"

# The complete 9-bird Crake Ecosystem Suite
BIRD_DEFINITIONS = [
    {
        "id": "crake",
        "name": "Crake (Spotted Crake)",
        "app": "Crake Messenger (Tox E2EE Client)",
        "role": "Primary secure communication & identity core",
        "src": r"C:\Users\lochr\.gemini\antigravity\brain\8c119181-9706-426c-ae12-f77d43b2d472\crake_simple_icon_1788192107784.jpg",
        "box": (150, 150, 880, 880)
    },
    {
        "id": "falcon",
        "name": "Peregrine Falcon",
        "app": "Crake Keyboard",
        "role": "High-velocity input, predictive gestures & privacy IME",
        "src": r"C:\Users\lochr\tox-client\artwork\bird_icons\1_falcon_minimal.jpg",
        "box": (180, 240, 840, 780)
    },
    {
        "id": "swift",
        "name": "Swift",
        "app": "Crake Tunnel (VPN / Obfuscated Proxy)",
        "role": "Encrypted tunneling, Pluggable Transports & fast transit",
        "src": r"C:\Users\lochr\tox-client\artwork\bird_icons\3_swift_minimal.jpg",
        "box": (150, 150, 880, 880)
    },
    {
        "id": "kingfisher",
        "name": "Kingfisher",
        "app": "Crake Vault (Encrypted Storage & Password Manager)",
        "role": "Zero-knowledge encrypted key store & credentials",
        "src": r"C:\Users\lochr\tox-client\artwork\bird_icons\5_kingfisher_minimal.jpg",
        "box": (180, 180, 840, 840)
    },
    {
        "id": "raven",
        "name": "Raven",
        "app": "Crake Patrol / Terminal",
        "role": "Operational logs, shift chronographs, telemetry & guard terminal",
        "src": r"C:\Users\lochr\tox-client\artwork\bird_icons\2_raven_minimal.jpg",
        "box": (150, 150, 880, 880)
    },
    {
        "id": "swallow",
        "name": "Swallow",
        "app": "Crake Mesh / Nearby",
        "role": "Local peer-to-peer, BLE & Wi-Fi Direct ad-hoc synchronization",
        "src": r"C:\Users\lochr\.gemini\antigravity\brain\8c119181-9706-426c-ae12-f77d43b2d472\tunnel_bird_nightjar_1788274555819.jpg",
        "box": (150, 150, 880, 880)
    },
    {
        "id": "osprey",
        "name": "Osprey",
        "app": "Crake Armor / Shield",
        "role": "On-device firewall, network integrity & attack protection",
        "src": r"C:\Users\lochr\.gemini\antigravity\brain\8c119181-9706-426c-ae12-f77d43b2d472\bird_osprey_minimal_1788274997475.jpg",
        "box": (150, 150, 880, 880)
    },
    {
        "id": "owl",
        "name": "Owl (Night Owl)",
        "app": "Crake Radar / Health",
        "role": "Silent background relay monitoring & DHT bootstrap diagnostics",
        "src": r"C:\Users\lochr\.gemini\antigravity\brain\8c119181-9706-426c-ae12-f77d43b2d472\bird_owl_minimal_1788275009262.jpg",
        "box": (150, 150, 880, 880)
    },
    {
        "id": "albatross",
        "name": "Albatross",
        "app": "Crake Bridge",
        "role": "Long-range federation bridges, Lyrebird & Snowflake relays",
        "src": r"C:\Users\lochr\.gemini\antigravity\brain\8c119181-9706-426c-ae12-f77d43b2d472\bird_albatross_minimal_1788275025435.jpg",
        "box": (100, 100, 924, 924)
    }
]

def process_bird(b, root_dirs):
    b_id = b["id"]
    name = b["name"]
    app = b["app"]
    src_path = b["src"]
    min_bx, min_by, max_bx, max_by = b["box"]

    img = Image.open(src_path).convert("RGBA")
    arr = np.array(img)
    
    # Isolate bird pixels inside bounding window
    box_mask = np.zeros((1024, 1024), dtype=bool)
    box_mask[min_by:max_by, min_bx:max_bx] = True
    
    r, g, b_ch = arr[:,:,0], arr[:,:,1], arr[:,:,2]
    teal_mask = box_mask & (g_ch := g > 80) & (b_ch > 80) & ((g.astype(int) + b_ch.astype(int)) > (2 * r.astype(int) + 20))

    y_idx, x_idx = np.where(teal_mask)
    if len(y_idx) == 0:
        print(f"Warning: No teal pixels found for {b_id}")
        return

    bird_layer = np.zeros((1024, 1024, 4), dtype=np.uint8)
    min_x, max_x = x_idx.min() - 2, x_idx.max() + 2
    min_y, max_y = y_idx.min() - 2, y_idx.max() + 2
    
    min_x, max_x = max(0, min_x), min(1023, max_x)
    min_y, max_y = max(0, min_y), min(1023, max_y)

    for y in range(min_y, max_y + 1):
        for x in range(min_x, max_x + 1):
            if teal_mask[y, x]:
                bird_layer[y, x] = arr[y, x]
            else:
                if arr[y, x, 0] < 45 and arr[y, x, 1] < 45 and arr[y, x, 2] < 45:
                    win = teal_mask[max(0, y-10):min(1024, y+10), max(0, x-10):min(1024, x+10)]
                    if np.sum(win) > 25:
                        bird_layer[y, x] = (20, 27, 32, 255)

    bird_pil = Image.fromarray(bird_layer, mode="RGBA")

    # 1. Master 1024x1024
    master_1024 = Image.new("RGBA", (1024, 1024), (20, 27, 32, 255))
    master_1024.paste(bird_pil, (0, 0), bird_pil)

    # 2. Master 512x512
    master_512 = master_1024.resize((512, 512), Image.Resampling.LANCZOS)

    # 3. Round 512x512
    round_512 = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    mask_c = Image.new("L", (512, 512), 0)
    draw_c = ImageDraw.Draw(mask_c)
    draw_c.ellipse((0, 0, 512, 512), fill=255)
    round_512.paste(master_512, (0, 0), mask_c)

    # 4. Foreground 512x512
    fg_512 = bird_pil.resize((512, 512), Image.Resampling.LANCZOS)

    # Vector extraction
    contours = trace_all_boundaries(teal_mask)
    simplified = [rdp(c, 1.8) for c in contours if len(c) > 20]

    scale_108 = 108.0 / 1024.0
    scale_100 = 100.0 / 1024.0

    paths_108 = [generate_svg_path(c, scale_108) for c in simplified]
    d_108 = " ".join(paths_108)

    paths_100 = [generate_svg_path(c, scale_100) for c in simplified]
    d_100 = " ".join(paths_100)

    # Write assets across target repos
    for root in root_dirs:
        bird_dir = os.path.join(root, "artwork", "birds", b_id)
        os.makedirs(bird_dir, exist_ok=True)

        master_1024.save(os.path.join(bird_dir, f"{b_id}_1024.png"), "PNG")
        master_512.save(os.path.join(bird_dir, f"{b_id}_512.png"), "PNG")
        round_512.save(os.path.join(bird_dir, f"{b_id}_round_512.png"), "PNG")
        fg_512.save(os.path.join(bird_dir, f"{b_id}_foreground_512.png"), "PNG")

        # SVG
        svg_content = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" role="img" aria-label="{name}">
  <path d="{d_100}" fill="#2DD4BF"/>
</svg>
'''
        with open(os.path.join(bird_dir, f"{b_id}.svg"), "w", encoding="utf-8") as f:
            f.write(svg_content)

        # Android Vector Foreground XML
        xml_fg = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path
      android:pathData="{d_108}"
      android:fillColor="#2DD4BF" />
</vector>
'''
        with open(os.path.join(bird_dir, "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
            f.write(xml_fg)

        # Android Vector Monochrome XML
        xml_mono = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108"
    android:tint="@android:color/white">
  <path
      android:pathData="{d_108}"
      android:fillColor="#FFFFFFFF" />
</vector>
'''
        with open(os.path.join(bird_dir, "ic_launcher_monochrome.xml"), "w", encoding="utf-8") as f:
            f.write(xml_mono)

        # Generate mipmaps folder inside bird package
        densities = {
            "mipmap-mdpi": 48,
            "mipmap-hdpi": 72,
            "mipmap-xhdpi": 96,
            "mipmap-xxhdpi": 144,
            "mipmap-xxxhdpi": 192,
        }
        for f_name, sz in densities.items():
            mip_dir = os.path.join(bird_dir, f_name)
            os.makedirs(mip_dir, exist_ok=True)
            sq = master_1024.resize((sz, sz), Image.Resampling.LANCZOS)
            sq.save(os.path.join(mip_dir, "ic_launcher.png"), "PNG")
            
            c_m = Image.new("L", (sz, sz), 0)
            draw_m = ImageDraw.Draw(c_m)
            draw_m.ellipse((0, 0, sz, sz), fill=255)
            rd = Image.new("RGBA", (sz, sz), (0, 0, 0, 0))
            rd.paste(sq, (0, 0), c_m)
            rd.save(os.path.join(mip_dir, "ic_launcher_round.png"), "PNG")
            
            fg = bird_pil.resize((sz, sz), Image.Resampling.LANCZOS)
            fg.save(os.path.join(mip_dir, "ic_launcher_foreground.png"), "PNG")

    print(f"Processed brand identity for [{name}] -> {app}")

def main():
    root_dirs = [
        r"C:\Users\lochr\crake-keyboard",
        r"C:\Users\lochr\tox-client",
        r"C:\Users\lochr\.gemini\antigravity\brain\8c119181-9706-426c-ae12-f77d43b2d472"
    ]
    for b in BIRD_DEFINITIONS:
        process_bird(b, root_dirs)

    print("\nAll 9 Crake Ecosystem bird brands generated with complete vector, PNG, XML, and mipmap assets!")

if __name__ == "__main__":
    main()
