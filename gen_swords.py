#!/usr/bin/env python3
"""Generate 6 combat sword pixel art textures (16x16) in Minecraft style.

Each sword has a katana-influenced diagonal blade (lower-left to upper-right),
a guard/crossguard, a short handle, and a pommel.
"""

from PIL import Image


def hex_to_rgba(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(color, factor):
    """Darken (factor<1) or lighten (factor>1) a color."""
    r, g, b, a = color
    r = max(0, min(255, int(r * factor)))
    g = max(0, min(255, int(g * factor)))
    b = max(0, min(255, int(b * factor)))
    return (r, g, b, a)


def draw_sword(img, blade_color, handle_color, guard_color=None, accent_color=None):
    """Draw a katana-style combat sword on a 16x16 image.

    Layout (0-indexed x,y with origin top-left):
      - Pommel at ~(2,14)
      - Handle from (2,14) up-right to (4,12)
      - Guard/crossguard around (5,11)-(6,10)
      - Blade from (6,10) up-right to (14,2) with slight curve
    """
    px = img.load()
    TRANSPARENT = (0, 0, 0, 0)

    blade_light = shade(blade_color, 1.25)
    blade_mid = blade_color
    blade_dark = shade(blade_color, 0.70)
    blade_edge = shade(blade_color, 1.45)  # bright edge highlight

    handle_light = shade(handle_color, 1.20)
    handle_dark = shade(handle_color, 0.70)

    if guard_color is None:
        guard_color = shade(handle_color, 1.40)
    guard_dark = shade(guard_color, 0.75)

    if accent_color is None:
        accent_color = guard_color

    # --- Pommel (lower-left) ---
    px[2, 14] = handle_dark
    px[3, 14] = handle_light
    px[3, 13] = handle_dark
    px[2, 13] = shade(handle_color, 0.85)

    # --- Handle ---
    # Wrapping pattern for grip texture
    px[3, 13] = handle_dark
    px[3, 12] = handle_light
    px[4, 13] = handle_light
    px[4, 12] = handle_dark
    px[4, 11] = handle_light
    px[5, 12] = handle_dark
    px[5, 11] = handle_light

    # --- Guard / Crossguard ---
    # A wider crossguard perpendicular to the blade direction
    px[4, 10] = guard_dark
    px[5, 10] = guard_color
    px[6, 10] = guard_color
    px[7, 10] = guard_dark
    # Second row of guard (perpendicular)
    px[5, 11] = guard_color
    px[7, 11] = guard_dark
    px[6, 11] = accent_color
    px[5, 9] = guard_dark
    px[6, 9] = guard_color

    # --- Blade ---
    # The blade goes diagonally from (7,9) to upper-right ~(14,2)
    # It's 2 pixels wide for most of the length, tapering to 1 at the tip.
    # We give it a slight katana curve by offsetting some pixels.

    # Blade body pixels: main diagonal + one pixel offset for width
    blade_pixels_main = [
        (7, 9), (8, 8), (9, 7), (10, 6), (11, 5), (12, 4), (13, 3),
    ]
    blade_pixels_edge = [
        (7, 8), (8, 7), (9, 6), (10, 5), (11, 4), (12, 3), (13, 2),
    ]
    # Curved back edge (katana spine) - offset the other direction
    blade_pixels_back = [
        (8, 9), (9, 8), (10, 7), (11, 6), (12, 5),
    ]

    # Tip of the blade (single pixel, bright)
    px[14, 2] = blade_edge
    px[14, 1] = shade(blade_edge, 1.1)

    # Draw blade body (center)
    for x, y in blade_pixels_main:
        px[x, y] = blade_mid

    # Draw blade leading edge (bright/sharp side)
    for x, y in blade_pixels_edge:
        px[x, y] = blade_light

    # Draw blade spine (darker back)
    for x, y in blade_pixels_back:
        px[x, y] = blade_dark

    # Extra tip shaping
    px[13, 2] = blade_light
    px[14, 2] = blade_edge

    # Add a subtle highlight line along the cutting edge
    highlight_pixels = [
        (8, 6), (9, 5), (10, 4), (11, 3),
    ]
    for x, y in highlight_pixels:
        px[x, y] = blade_edge


def draw_netherite_sword(img, blade_color, handle_color, accent_color):
    """Netherite variant with extra accent detailing."""
    draw_sword(img, blade_color, handle_color,
               guard_color=shade(accent_color, 1.3),
               accent_color=accent_color)
    px = img.load()
    # Add subtle red accent streaks on the blade
    accent_rgba = hex_to_rgba(accent_color) if isinstance(accent_color, str) else accent_color
    accent_subtle = shade(accent_rgba, 0.9)
    px[9, 7] = accent_subtle
    px[11, 5] = accent_subtle
    px[10, 7] = shade(accent_rgba, 0.7)


def generate_sword(path, blade_hex, handle_hex, guard_hex=None, accent_hex=None, netherite=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    blade = hex_to_rgba(blade_hex)
    handle = hex_to_rgba(handle_hex)
    guard = hex_to_rgba(guard_hex) if guard_hex else None
    accent = hex_to_rgba(accent_hex) if accent_hex else None

    if netherite:
        draw_netherite_sword(img, blade, handle, accent if accent else blade)
    else:
        draw_sword(img, blade, handle, guard_color=guard, accent_color=accent)

    img.save(path)
    print(f"Saved: {path}")


if __name__ == "__main__":
    base = "/home/dkjsiogu/文档/mod/mod-1/src/main/resources/assets/combat_arts/textures/item"

    swords = [
        {
            "name": "combat_wood_sword",
            "blade": "#C4A46C",
            "handle": "#6B4226",
            "guard": "#8B6914",
        },
        {
            "name": "combat_stone_sword",
            "blade": "#9A9A9A",
            "handle": "#6B4226",
            "guard": "#7A7A7A",
        },
        {
            "name": "combat_iron_sword",
            "blade": "#D8D8D8",
            "handle": "#6B4226",
            "guard": "#A0A0A0",
        },
        {
            "name": "combat_gold_sword",
            "blade": "#FCDB05",
            "handle": "#6B4226",
            "guard": "#D4A800",
        },
        {
            "name": "combat_diamond_sword",
            "blade": "#5DECF5",
            "handle": "#6B4226",
            "guard": "#3CB0B8",
        },
        {
            "name": "combat_netherite_sword",
            "blade": "#4A3B3B",
            "handle": "#332222",
            "accent": "#6B2020",
            "netherite": True,
        },
    ]

    for s in swords:
        path = f"{base}/{s['name']}.png"
        generate_sword(
            path,
            blade_hex=s["blade"],
            handle_hex=s["handle"],
            guard_hex=s.get("guard"),
            accent_hex=s.get("accent"),
            netherite=s.get("netherite", False),
        )

    print("\nAll 6 sword textures generated successfully.")
