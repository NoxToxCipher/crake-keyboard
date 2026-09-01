import os
import numpy as np
from PIL import Image, ImageDraw

def create_borderless_assets():
    src_path = r"C:\Users\lochr\tox-client\artwork\bird_icons\1_falcon_minimal.jpg"
    img = Image.open(src_path).convert("RGBA")
    arr = np.array(img)
    
    BG_RGB = (20, 27, 32) # #141B20
    
    # Restrict strictly to bird bounding area
    bird_box = np.zeros((1024, 1024), dtype=bool)
    bird_box[240:780, 180:840] = True
    
    # Detect true vibrant teal bird pixels
    r, g, b, a = arr[:,:,0], arr[:,:,1], arr[:,:,2], arr[:,:,3]
    teal_mask = bird_box & (g > 100) & (b > 100) & (r < 90)
    
    # We create a clean transparent bird layer
    bird_layer = np.zeros((1024, 1024, 4), dtype=np.uint8)
    
    y_idx, x_idx = np.where(teal_mask)
    min_x, max_x = x_idx.min(), x_idx.max()
    min_y, max_y = y_idx.min(), y_idx.max()
    
    for y in range(min_y, max_y + 1):
        for x in range(min_x, max_x + 1):
            if teal_mask[y, x]:
                bird_layer[y, x] = arr[y, x]
            else:
                # Check for dark eye cutout inside the head region (x > 750, y > 650)
                if x > 750 and y > 650:
                    if arr[y, x, 0] < 50 and arr[y, x, 1] < 50 and arr[y, x, 2] < 50:
                        window = teal_mask[max(0, y-12):min(1024, y+12), max(0, x-12):min(1024, x+12)]
                        if np.sum(window) > 40:
                            bird_layer[y, x] = (20, 27, 32, 255)

    bird_pil = Image.fromarray(bird_layer, mode="RGBA")
    
    # 1. Master 1024x1024 icon with solid #141B20 background (EDGE TO EDGE, ZERO BORDERS)
    master_1024 = Image.new("RGBA", (1024, 1024), (20, 27, 32, 255))
    master_1024.paste(bird_pil, (0, 0), bird_pil)
    
    # 2. Master 512x512
    master_512 = master_1024.resize((512, 512), Image.Resampling.LANCZOS)
    
    # 3. Round 512x512 (circular mask with transparent corners)
    round_512 = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    mask_circle = Image.new("L", (512, 512), 0)
    draw_c = ImageDraw.Draw(mask_circle)
    draw_c.ellipse((0, 0, 512, 512), fill=255)
    round_512.paste(master_512, (0, 0), mask_circle)
    
    # 4. Foreground 512x512 (transparent background)
    fg_512 = bird_pil.resize((512, 512), Image.Resampling.LANCZOS)

    art_dirs = [
        r"C:\Users\lochr\crake-keyboard\artwork",
        r"C:\Users\lochr\tox-client\artwork",
        r"C:\Users\lochr\.gemini\antigravity\brain\8c119181-9706-426c-ae12-f77d43b2d472"
    ]
    for d in art_dirs:
        os.makedirs(d, exist_ok=True)
        master_1024.save(os.path.join(d, "crake_keyboard_falcon_1024.png"), "PNG")
        master_512.save(os.path.join(d, "crake_keyboard_falcon_512.png"), "PNG")
        round_512.save(os.path.join(d, "crake_keyboard_falcon_round_512.png"), "PNG")
        fg_512.save(os.path.join(d, "crake_keyboard_falcon_foreground_512.png"), "PNG")

    # 5. Generate all density mipmaps for crake-keyboard
    kb_res = r"C:\Users\lochr\crake-keyboard\app\src\main\res"
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        out_dir = os.path.join(kb_res, folder)
        os.makedirs(out_dir, exist_ok=True)
        
        sq = master_1024.resize((size, size), Image.Resampling.LANCZOS)
        sq.save(os.path.join(out_dir, "floris_app_icon.png"), "PNG")
        
        circ_mask = Image.new("L", (size, size), 0)
        draw = ImageDraw.Draw(circ_mask)
        draw.ellipse((0, 0, size, size), fill=255)
        rd = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        rd.paste(sq, (0, 0), circ_mask)
        rd.save(os.path.join(out_dir, "floris_app_icon_round.png"), "PNG")
        
        fg = bird_pil.resize((size, size), Image.Resampling.LANCZOS)
        fg.save(os.path.join(out_dir, "ic_app_icon_foreground.png"), "PNG")

    print("Clean borderless Peregrine Falcon generated with 100% pure flat background!")

if __name__ == "__main__":
    create_borderless_assets()
