#!/usr/bin/env python3
"""Generate 6 combat spear pixel art textures (16x16) in Minecraft style.

Each spear has a long shaft from lower-left to upper-right,
a pointed spearhead tip, and a short grip/pommel.
"""

from PIL import Image


def hex_to_rgba(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(color, factor):
    r, g, b, a = color
    r = max(0, min(255, int(r * factor)))
    g = max(0, min(255, int(g * factor)))
    b = max(0, min(255, int(b * factor)))
    return (r, g, b, a)


def draw_spear(img, blade_color, shaft_color, accent_color=None):
    """Draw a spear on a 16x16 image.

    Layout (0-indexed x,y with origin top-left):
      - Pommel/grip at ~(1,15)-(2,14)
      - Shaft diagonal from (2,14) to (11,5)
      - Spearhead from (11,5) to (14,1) with pointed tip
    """
    px = img.load()

    blade_light = shade(blade_color, 1.30)
    blade_mid = blade_color
    blade_dark = shade(blade_color, 0.70)
    blade_edge = shade(blade_color, 1.50)

    shaft_light = shade(shaft_color, 1.20)
    shaft_dark = shade(shaft_color, 0.70)
    shaft_mid = shaft_color

    if accent_color is None:
        accent_color = shade(shaft_color, 1.40)
    accent_dark = shade(accent_color, 0.75)

    # --- Pommel / Grip end ---
    px[1, 15] = shaft_dark
    px[2, 15] = shaft_mid
    px[1, 14] = shaft_mid
    px[2, 14] = shaft_light

    # --- Long shaft (diagonal) ---
    shaft_pixels = [
        (3, 13), (4, 12), (5, 11), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6),
    ]
    for i, (x, y) in enumerate(shaft_pixels):
        px[x, y] = shaft_light if i % 2 == 0 else shaft_dark

    # Shaft width (parallel offset for thickness)
    shaft_shadow = [
        (3, 14), (4, 13), (5, 12), (6, 11), (7, 10), (8, 9), (9, 8), (10, 7),
    ]
    for x, y in shaft_shadow:
        px[x, y] = shaft_dark

    # --- Binding / wrap where blade meets shaft ---
    px[10, 6] = accent_color
    px[11, 5] = accent_color
    px[10, 7] = accent_dark
    px[11, 6] = accent_dark

    # --- Spearhead (pointed diamond shape) ---
    # Main blade body
    px[11, 5] = blade_mid
    px[12, 4] = blade_mid
    px[13, 3] = blade_mid
    px[14, 2] = blade_light

    # Blade edge (bright side)
    px[11, 4] = blade_light
    px[12, 3] = blade_light
    px[13, 2] = blade_edge

    # Blade spine (dark side)
    px[12, 5] = blade_dark
    px[13, 4] = blade_dark

    # Tip
    px[14, 1] = blade_edge
    px[15, 0] = shade(blade_edge, 1.1)

    # Small barb detail
    px[13, 5] = blade_dark
    px[11, 3] = shade(blade_light, 0.9)


def draw_netherite_spear(img, blade_color, shaft_color, accent_color):
    draw_spear(img, blade_color, shaft_color,
               accent_color=shade(accent_color, 1.3))
    px = img.load()
    accent_rgba = accent_color if isinstance(accent_color, tuple) else hex_to_rgba(accent_color)
    accent_subtle = shade(accent_rgba, 0.9)
    px[12, 4] = accent_subtle
    px[13, 3] = shade(accent_rgba, 0.7)


def generate_spear(path, blade_hex, shaft_hex, accent_hex=None, netherite=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    blade = hex_to_rgba(blade_hex)
    shaft = hex_to_rgba(shaft_hex)
    accent = hex_to_rgba(accent_hex) if accent_hex else None

    if netherite:
        draw_netherite_spear(img, blade, shaft, accent if accent else blade)
    else:
        draw_spear(img, blade, shaft, accent_color=accent)

    img.save(path)
    print(f"Saved: {path}")


if __name__ == "__main__":
    base = "/home/dkjsiogu/文档/mod/mod-1/src/main/resources/assets/mod_1/textures/item"

    spears = [
        {
            "name": "combat_wood_spear",
            "blade": "#C4A46C",
            "shaft": "#6B4226",
            "accent": "#8B6914",
        },
        {
            "name": "combat_stone_spear",
            "blade": "#9A9A9A",
            "shaft": "#6B4226",
            "accent": "#7A7A7A",
        },
        {
            "name": "combat_iron_spear",
            "blade": "#D8D8D8",
            "shaft": "#6B4226",
            "accent": "#A0A0A0",
        },
        {
            "name": "combat_gold_spear",
            "blade": "#FCDB05",
            "shaft": "#6B4226",
            "accent": "#D4A800",
        },
        {
            "name": "combat_diamond_spear",
            "blade": "#5DECF5",
            "shaft": "#6B4226",
            "accent": "#3CB0B8",
        },
        {
            "name": "combat_netherite_spear",
            "blade": "#4A3B3B",
            "shaft": "#332222",
            "accent": "#6B2020",
            "netherite": True,
        },
    ]

    for s in spears:
        path = f"{base}/{s['name']}.png"
        generate_spear(
            path,
            blade_hex=s["blade"],
            shaft_hex=s["shaft"],
            accent_hex=s.get("accent"),
            netherite=s.get("netherite", False),
        )

    print("\nAll 6 spear textures generated successfully.")
