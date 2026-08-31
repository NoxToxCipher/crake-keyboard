# Crake Keyboard — Brand & Icon Asset Guide

This directory contains the official **Peregrine Falcon** branding and launcher icon assets for **Crake Keyboard**. 

Use this guide to restore or apply the Peregrine Falcon brand identity to any version or branch of Crake Keyboard / FlorisBoard.

---

## 1. Asset Inventory

| File | Resolution / Format | Purpose |
| :--- | :--- | :--- |
| [`crake_keyboard_falcon_1024.png`](file:///C:/Users/lochr/crake-keyboard/artwork/crake_keyboard_falcon_1024.png) | 1024×1024 PNG | Master High-Res Icon & Store Graphic |
| [`crake_keyboard_falcon_512.png`](file:///C:/Users/lochr/crake-keyboard/artwork/crake_keyboard_falcon_512.png) | 512×512 PNG | Google Play / F-Droid Store Icon |
| [`crake_keyboard_falcon_round_512.png`](file:///C:/Users/lochr/crake-keyboard/artwork/crake_keyboard_falcon_round_512.png) | 512×512 PNG | Master Round Icon |
| [`crake_keyboard_falcon_foreground_512.png`](file:///C:/Users/lochr/crake-keyboard/artwork/crake_keyboard_falcon_foreground_512.png) | 512×512 PNG | Adaptive Icon Foreground Layer |
| [`bird_icons/1_falcon.svg`](file:///C:/Users/lochr/crake-keyboard/artwork/bird_icons/1_falcon.svg) | Scalable Vector SVG | Master Vector Asset |
| [`bird_icons/1_falcon_minimal.jpg`](file:///C:/Users/lochr/crake-keyboard/artwork/bird_icons/1_falcon_minimal.jpg) | High-Res Raster JPG | Raw Visual Reference |
| [`bird_icons/`](file:///C:/Users/lochr/crake-keyboard/artwork/bird_icons/) | JPG & SVG Suite | Alternate bird identities (Falcon, Raven, Swift, Kingfisher) |

---

## 2. Color Palette

* **Ground / Chassis**: `#0F1418` (Matte Obsidian Slate)
* **Primary Falcon Wing**: `#2DD4BF` (Electric Teal)
* **Secondary Wing Flow**: `#14B8A6` (Emerald Teal)
* **Aerodynamic Highlight**: `#5EEAD4` (Ice Teal)
* **Border Line**: `#222D35`

---

## 3. Android Integration Reference

### A. Background Color (`app/src/main/res/values/colors.xml`)
```xml
<color name="ic_app_icon_background">#0F1418</color>
```

### B. Adaptive Launcher Icon (`app/src/main/res/mipmap-anydpi-v26/floris_app_icon.xml`)
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_app_icon_background"/>
    <foreground android:drawable="@drawable/ic_app_icon_foreground"/>
    <monochrome android:drawable="@drawable/ic_app_icon_monochrome"/>
</adaptive-icon>
```

### C. Vector Foreground (`app/src/main/res/drawable/ic_app_icon_foreground.xml`)
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

  <group
      android:pivotX="54"
      android:pivotY="54">
    <!-- Peregrine Falcon Body and Wings -->
    <path
        android:pathData="M 24,32 L 68,68 L 44,74 L 20,86 L 48,88 L 78,98 C 88,102 96,98 100,90 C 102,86 102,82 98,78 L 82,58 C 76,50 70,38 68,26 L 56,22 L 58,44 L 38,24 Z"
        android:strokeLineJoin="round">
      <aapt:attr name="android:fillColor">
        <gradient
            android:startX="20"
            android:startY="22"
            android:endX="100"
            android:endY="98"
            android:type="linear">
          <item android:offset="0.0" android:color="#FF2DD4BF" />
          <item android:offset="0.6" android:color="#FF14B8A6" />
          <item android:offset="1.0" android:color="#FF0D9488" />
        </gradient>
      </aapt:attr>
    </path>

    <!-- Secondary Aerodynamic Wing Highlight -->
    <path
        android:pathData="M 68,26 L 82,58 C 76,50 70,38 68,26 Z"
        android:fillColor="#FF5EEAD4" />

    <!-- Piercing Falcon Eye Cutout -->
    <path
        android:pathData="M 90,82 A 2.2,2.2 0 1,1 90,82.01 Z"
        android:fillColor="#FF0F1418" />
  </group>
</vector>
```

### D. Monochrome Vector for Themed Icons (`app/src/main/res/drawable/ic_app_icon_monochrome.xml`)
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108"
    android:tint="@android:color/white">

  <!-- Peregrine Falcon Monochrome Silhouette -->
  <path
      android:pathData="M 24,32 L 68,68 L 44,74 L 20,86 L 48,88 L 78,98 C 88,102 96,98 100,90 C 102,86 102,82 98,78 L 82,58 C 76,50 70,38 68,26 L 56,22 L 58,44 L 38,24 Z"
      android:fillColor="#FFFFFFFF" />
</vector>
```

---

## 4. Generating Mipmap PNGs (Automated Script)

Run the Python generator whenever you need to regenerate density folders:

```python
import os
from PIL import Image, ImageDraw

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

src = r"artwork/crake_keyboard_falcon_512.png"
res = r"app/src/main/res"

img = Image.open(src).convert("RGBA")
for folder, size in DENSITIES.items():
    out_dir = os.path.join(res, folder)
    os.makedirs(out_dir, exist_ok=True)
    
    # Square / squircle icon
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(os.path.join(out_dir, "floris_app_icon.png"), "PNG")
    
    # Round icon
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    round_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    round_img.paste(resized, (0, 0), mask)
    round_img.save(os.path.join(out_dir, "floris_app_icon_round.png"), "PNG")
```
