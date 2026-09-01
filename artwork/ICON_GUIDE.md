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
* **Primary Falcon Wings & Body**: `#2DD4BF` (Electric Teal)
* **Monochrome Themed Tint**: `#FFFFFF`
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
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path
      android:pathData="M 43.3,28.0 L 51.5,33.5 L 59.5,38.8 L 66.8,43.7 L 73.1,49.0 L 77.0,55.3 L 78.0,62.2 L 72.8,59.3 L 69.4,56.7 L 66.6,53.2 L 64.9,49.6 L 61.9,44.9 L 57.3,40.1 L 49.3,34.0 L 43.3,28.1 Z M 21.0,28.0 L 26.9,31.4 L 32.7,34.4 L 40.2,38.1 L 48.0,41.9 L 55.4,45.8 L 62.4,50.3 L 68.3,55.4 L 71.3,61.0 L 73.8,63.0 L 76.5,63.7 L 79.5,65.3 L 83.3,68.0 L 85.8,70.5 L 86.9,73.1 L 86.7,75.0 L 84.8,78.0 L 83.0,77.3 L 84.5,74.7 L 83.5,73.4 L 81.3,73.0 L 77.9,72.4 L 73.8,72.0 L 68.8,70.7 L 62.8,68.8 L 56.6,66.3 L 50.8,62.7 L 46.2,59.1 L 47.9,62.4 L 51.8,62.4 L 44.7,62.4 L 39.5,62.4 L 25.4,55.8 L 36.3,57.7 L 44.8,57.7 L 48.6,56.2 L 44.7,53.2 L 40.8,49.9 L 36.6,46.7 L 31.9,41.9 L 38.3,45.0 L 44.3,47.7 L 50.1,50.4 L 44.7,46.8 L 38.8,43.2 L 32.7,39.1 L 28.5,35.4 L 24.3,31.5 L 21.0,28.0 Z M 80.9,70.4 L 81.9,70.8 L 84.6,72.6 L 82.7,72.2 L 81.1,71.5 L 80.9,70.5 Z"
      android:fillColor="#2DD4BF" />
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
  <path
      android:pathData="M 43.3,28.0 L 51.5,33.5 L 59.5,38.8 L 66.8,43.7 L 73.1,49.0 L 77.0,55.3 L 78.0,62.2 L 72.8,59.3 L 69.4,56.7 L 66.6,53.2 L 64.9,49.6 L 61.9,44.9 L 57.3,40.1 L 49.3,34.0 L 43.3,28.1 Z M 21.0,28.0 L 26.9,31.4 L 32.7,34.4 L 40.2,38.1 L 48.0,41.9 L 55.4,45.8 L 62.4,50.3 L 68.3,55.4 L 71.3,61.0 L 73.8,63.0 L 76.5,63.7 L 79.5,65.3 L 83.3,68.0 L 85.8,70.5 L 86.9,73.1 L 86.7,75.0 L 84.8,78.0 L 83.0,77.3 L 84.5,74.7 L 83.5,73.4 L 81.3,73.0 L 77.9,72.4 L 73.8,72.0 L 68.8,70.7 L 62.8,68.8 L 56.6,66.3 L 50.8,62.7 L 46.2,59.1 L 47.9,62.4 L 51.8,62.4 L 44.7,62.4 L 39.5,62.4 L 25.4,55.8 L 36.3,57.7 L 44.8,57.7 L 48.6,56.2 L 44.7,53.2 L 40.8,49.9 L 36.6,46.7 L 31.9,41.9 L 38.3,45.0 L 44.3,47.7 L 50.1,50.4 L 44.7,46.8 L 38.8,43.2 L 32.7,39.1 L 28.5,35.4 L 24.3,31.5 L 21.0,28.0 Z M 80.9,70.4 L 81.9,70.8 L 84.6,72.6 L 82.7,72.2 L 81.1,71.5 L 80.9,70.5 Z"
      android:fillColor="#FFFFFFFF" />
</vector>
```
