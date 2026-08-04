from PIL import Image, ImageDraw, ImageFont
import os

W, H = 1080, 2400
OUT = os.path.join(os.path.dirname(__file__), "screenshots")

# Colors
BLUE = (30, 136, 229)
DARK_BG = (18, 18, 18)
LIGHT_BG = (250, 250, 250)
SURFACE = (40, 40, 40)
SURFACE_LIGHT = (230, 235, 240)
WHITE = (255, 255, 255)
GRAY = (158, 158, 158)
TEXT = (224, 224, 224)
TEXT_DARK = (28, 27, 31)
TEAL = (0, 191, 165)
GREEN = (0, 200, 83)
RED = (211, 47, 47)

def get_font(size):
    for name in ["arial.ttf", "Arial.ttf", "DejaVuSans.ttf"]:
        try:
            return ImageFont.truetype(name, size)
        except:
            pass
    return ImageFont.load_default()

def draw_phone_frame(draw, bg_color):
    draw.rounded_rectangle([0, 0, W-1, H-1], radius=0, fill=bg_color)

def draw_status_bar(draw, dark=True):
    c = WHITE if dark else TEXT_DARK
    draw.text((50, 20), "12:00", fill=c, font=get_font(28))
    draw.text((W-200, 20), "100%", fill=c, font=get_font(28))
    draw.ellipse([W-130, 22, W-100, 52], outline=c, width=2)

def draw_bottom_nav(draw, selected=0, dark=True):
    y = H - 180
    c_text = WHITE if dark else TEXT_DARK
    c_sel = BLUE

    draw.line([(0, y), (W, y)], fill=GRAY if dark else (200,200,200), width=1)
    labels = ["Home", "Downloads", "History"]
    icons_y = y + 30
    spacing = W // 3

    for i, label in enumerate(labels):
        x = spacing * i + spacing // 2
        color = c_sel if i == selected else GRAY
        font = get_font(26)
        bbox = draw.textbbox((0, 0), label, font=font)
        tw = bbox[2] - bbox[0]
        draw.text((x - tw//2, icons_y + 50), label, fill=color, font=font)

        if i == selected:
            draw.rounded_rectangle([x-30, icons_y-5, x+30, icons_y+40], radius=20, fill=c_sel)

# === SCREEN 1: Home (Dark) ===
def home_dark():
    img = Image.new("RGB", (W, H), DARK_BG)
    draw = ImageDraw.Draw(img)
    draw_status_bar(draw)

    # Title
    draw.text((60, 100), "Vydra", fill=WHITE, font=get_font(72))

    # Card
    draw.rounded_rectangle([40, 230, W-40, 700], radius=32, fill=SURFACE)
    draw.text((80, 270), "Download", fill=WHITE, font=get_font(48))

    # Input field
    draw.rounded_rectangle([80, 370, W-80, 470], radius=16, outline=GRAY, width=2)
    draw.text((130, 395), "Paste a link...", fill=GRAY, font=get_font(32))

    # Buttons
    draw.rounded_rectangle([80, 510, W//2-20, 610], radius=16, fill=SURFACE)
    draw.text((160, 535), "Paste", fill=WHITE, font=get_font(34))

    draw.rounded_rectangle([W//2+20, 510, W-80, 610], radius=16, fill=BLUE)
    draw.text((W//2+80, 535), "Download", fill=WHITE, font=get_font(34))

    # Recent
    draw.text((60, 740), "Recent Downloads", fill=WHITE, font=get_font(40))

    for i in range(3):
        y = 810 + i * 140
        draw.rounded_rectangle([40, y, W-40, y+120], radius=16, fill=SURFACE)
        draw.rounded_rectangle([60, y+10, 160, y+110], radius=12, fill=(60,60,60))
        draw.text((180, y+25), f"Video {i+1}", fill=WHITE, font=get_font(30))
        draw.text((180, y+70), f"youtube.com", fill=GRAY, font=get_font(24))

    draw_bottom_nav(draw, 0)
    img.save(os.path.join(OUT, "home-dark.png"))

# === SCREEN 2: Home (Light) ===
def home_light():
    img = Image.new("RGB", (W, H), LIGHT_BG)
    draw = ImageDraw.Draw(img)
    draw_status_bar(draw, dark=False)

    draw.text((60, 100), "Vydra", fill=TEXT_DARK, font=get_font(72))

    draw.rounded_rectangle([40, 230, W-40, 700], radius=32, fill=WHITE)
    draw.text((80, 270), "Download", fill=TEXT_DARK, font=get_font(48))

    draw.rounded_rectangle([80, 370, W-80, 470], radius=16, outline=(180,180,180), width=2)
    draw.text((130, 395), "Paste a link...", fill=GRAY, font=get_font(32))

    draw.rounded_rectangle([80, 510, W//2-20, 610], radius=16, fill=(230,235,240))
    draw.text((160, 535), "Paste", fill=BLUE, font=get_font(34))

    draw.rounded_rectangle([W//2+20, 510, W-80, 610], radius=16, fill=BLUE)
    draw.text((W//2+80, 535), "Download", fill=WHITE, font=get_font(34))

    draw.text((60, 740), "Recent Downloads", fill=TEXT_DARK, font=get_font(40))

    for i in range(3):
        y = 810 + i * 140
        draw.rounded_rectangle([40, y, W-40, y+120], radius=16, fill=WHITE)
        draw.rounded_rectangle([60, y+10, 160, y+110], radius=12, fill=(200,210,220))
        draw.text((180, y+25), f"Video {i+1}", fill=TEXT_DARK, font=get_font(30))
        draw.text((180, y+70), f"youtube.com", fill=GRAY, font=get_font(24))

    draw_bottom_nav(draw, 0, dark=False)
    img.save(os.path.join(OUT, "home-light.png"))

# === SCREEN 3: Downloads (Dark) ===
def downloads_dark():
    img = Image.new("RGB", (W, H), DARK_BG)
    draw = ImageDraw.Draw(img)
    draw_status_bar(draw)

    draw.text((60, 100), "Downloads", fill=WHITE, font=get_font(60))

    # Filter chips
    chips = ["Active", "Queued", "Completed", "Failed"]
    cx = 60
    for i, chip in enumerate(chips):
        w = len(chip) * 18 + 40
        if i == 0:
            draw.rounded_rectangle([cx, 190, cx+w, 240], radius=20, fill=BLUE)
            draw.text((cx+20, 198), chip, fill=WHITE, font=get_font(28))
        else:
            draw.rounded_rectangle([cx, 190, cx+w, 240], radius=20, outline=GRAY, width=1)
            draw.text((cx+20, 198), chip, fill=GRAY, font=get_font(28))
        cx += w + 16

    # Download cards
    for i in range(3):
        y = 290 + i * 280
        draw.rounded_rectangle([40, y, W-40, y+260], radius=24, fill=SURFACE)
        draw.rounded_rectangle([60, y+15, 160, y+115], radius=12, fill=(60,60,60))
        draw.text((180, y+25), f"My Video Title {i+1}", fill=WHITE, font=get_font(32))
        draw.text((180, y+75), "youtube.com", fill=GRAY, font=get_font(24))

        # Wavy progress bar
        bar_y = y + 140
        draw.rounded_rectangle([60, bar_y, W-60, bar_y+24], radius=12, fill=(50,50,50))
        pw = int((W-120) * (0.9 - i*0.3))
        draw.rounded_rectangle([60, bar_y, 60+pw, bar_y+24], radius=12, fill=BLUE)

        pct = [90, 60, 30][i]
        draw.text((60, bar_y+35), f"{pct}%", fill=BLUE, font=get_font(26))
        draw.text((W-200, bar_y+35), "2.5 MB/s", fill=GRAY, font=get_font(26))

        # Buttons
        draw.rounded_rectangle([W-200, y+200, W-60, y+240], radius=12, outline=GRAY, width=1)
        draw.text((W-180, y+207), "Pause", fill=GRAY, font=get_font(24))

    draw_bottom_nav(draw, 1)
    img.save(os.path.join(OUT, "downloads-dark.png"))

# === SCREEN 4: Settings (Dark) ===
def settings_dark():
    img = Image.new("RGB", (W, H), DARK_BG)
    draw = ImageDraw.Draw(img)
    draw_status_bar(draw)

    draw.text((60, 100), "Settings", fill=WHITE, font=get_font(60))

    # Appearance section
    draw.text((60, 200), "Appearance", fill=BLUE, font=get_font(30))
    draw.rounded_rectangle([40, 240, W-40, 520], radius=20, fill=SURFACE)

    draw.text((80, 270), "Theme", fill=TEXT, font=get_font(32))
    draw.text((80, 315), "System default", fill=GRAY, font=get_font(24))

    draw.line([(80, 370), (W-80, 370)], fill=(50,50,50), width=1)

    draw.text((80, 400), "Dynamic Color", fill=TEXT, font=get_font(32))
    draw.text((80, 445), "Material You colors", fill=GRAY, font=get_font(24))

    # Toggle
    draw.rounded_rectangle([W-160, 400, W-80, 440], radius=20, fill=BLUE)
    draw.ellipse([W-150, 403, W-120, 437], fill=WHITE)

    # Downloads section
    draw.text((60, 570), "Downloads", fill=BLUE, font=get_font(30))
    draw.rounded_rectangle([40, 610, W-40, 800], radius=20, fill=SURFACE)
    draw.text((80, 640), "Concurrent Downloads", fill=TEXT, font=get_font(32))
    draw.text((W-160, 640), "3", fill=BLUE, font=get_font(32))

    # Engine section
    draw.text((60, 850), "Engine", fill=BLUE, font=get_font(30))
    draw.rounded_rectangle([40, 890, W-40, 1120], radius=20, fill=SURFACE)
    draw.text((80, 920), "yt-dlp Version", fill=TEXT, font=get_font(32))
    draw.text((80, 965), "2025.01.15", fill=GRAY, font=get_font(24))

    draw.line([(80, 1010), (W-80, 1010)], fill=(50,50,50), width=1)

    draw.rounded_rectangle([80, 1040, W-80, 1110], radius=12, fill=BLUE)
    draw.text((320, 1055), "Update yt-dlp", fill=WHITE, font=get_font(32))

    draw_bottom_nav(draw, 1)
    img.save(os.path.join(OUT, "settings-dark.png"))

# Generate all
os.makedirs(OUT, exist_ok=True)
home_dark()
home_light()
downloads_dark()
settings_dark()
print("4 screenshots generated in screenshots/")
