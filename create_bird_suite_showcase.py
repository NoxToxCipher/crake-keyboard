import os
from PIL import Image, ImageDraw, ImageFont

def create_showcase():
    bird_info = [
        ("crake", "Crake Messenger", "Spotted Crake • Primary E2EE Client"),
        ("falcon", "Crake Keyboard", "Peregrine Falcon • Predictive Gesture IME"),
        ("swift", "Crake Tunnel", "Swift • Encrypted WireGuard / Obfuscated Proxy"),
        ("kingfisher", "Crake Vault", "Kingfisher • Encrypted Secret & Password Store"),
        ("raven", "Crake Patrol", "Raven • Security Telemetry & Terminal"),
        ("swallow", "Crake Mesh", "Swallow • P2P BLE & Wi-Fi Direct Sync"),
        ("osprey", "Crake Armor", "Osprey • On-Device Firewall & Shield"),
        ("owl", "Crake Radar", "Night Owl • Silent Relay & DHT Diagnostics"),
        ("albatross", "Crake Bridge", "Albatross • Long-Range Bridge & Transports")
    ]
    
    art_dir = r"C:\Users\lochr\crake-keyboard\artwork\birds"
    
    # 3x3 grid canvas: width = 3 * 600 + 4 * 40 = 1960, height = 3 * 720 + 4 * 40 = 2320
    cell_w, cell_h = 560, 680
    pad = 40
    grid_w = 3 * cell_w + 4 * pad
    grid_h = 3 * cell_h + 4 * pad + 120 # header
    
    canvas = Image.new("RGBA", (grid_w, grid_h), (15, 20, 24, 255)) # #0F1418
    draw = ImageDraw.Draw(canvas)
    
    # Title
    draw.text((pad, 35), "CRAKE ECOSYSTEM — BRAND & APP IDENTITY SUITE", fill=(45, 212, 191, 255))
    draw.text((pad, 75), "Unified Minimalist Avian Design Language • Electric Teal (#2DD4BF) on Matte Obsidian (#0F1418)", fill=(140, 160, 175, 255))
    
    for idx, (b_id, title, subtitle) in enumerate(bird_info):
        row = idx // 3
        col = idx % 3
        
        x = pad + col * (cell_w + pad)
        y = 140 + pad + row * (cell_h + pad)
        
        # Load 512x512 master icon
        icon_path = os.path.join(art_dir, b_id, f"{b_id}_512.png")
        if os.path.exists(icon_path):
            icon_img = Image.open(icon_path).convert("RGBA")
            # Draw cell card background
            draw.rounded_rectangle([x, y, x + cell_w, y + cell_h], radius=24, fill=(20, 27, 32, 255), outline=(34, 45, 53, 255), width=2)
            
            # Paste icon inside card
            icon_resized = icon_img.resize((480, 480), Image.Resampling.LANCZOS)
            canvas.paste(icon_resized, (x + 40, y + 30), icon_resized)
            
            # Draw text label
            draw.text((x + 40, y + 535), title, fill=(255, 255, 255, 255))
            draw.text((x + 40, y + 575), subtitle, fill=(45, 212, 191, 255))

    out_paths = [
        r"C:\Users\lochr\crake-keyboard\artwork\crake_ecosystem_9_birds_showcase.png",
        r"C:\Users\lochr\tox-client\artwork\crake_ecosystem_9_birds_showcase.png",
        r"C:\Users\lochr\.gemini\antigravity\brain\8c119181-9706-426c-ae12-f77d43b2d472\crake_ecosystem_9_birds_showcase.png"
    ]
    for p in out_paths:
        canvas.save(p, "PNG")
    print("Generated 9-bird showcase grid successfully!")

if __name__ == "__main__":
    create_showcase()
