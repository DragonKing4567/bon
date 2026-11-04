import zlib, struct, os
A = os.path.join(os.path.dirname(__file__), "src/main/resources/assets/bondrewd/textures")

def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(w)) for y in range(h))
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    open(path, "wb").write(data)

def blank(w, h): return [[(0, 0, 0, 0)] * w for _ in range(h)]
def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1): px[y][x] = c

# Skin (64x64): black cloak, pale insectoid helmet with dark slit eyes
S = blank(64, 64)
DARK, DARKER, PURPLE, MASK, EYE = (24, 22, 32, 255), (14, 12, 20, 255), (92, 52, 140, 255), (205, 205, 214, 255), (10, 6, 14, 255)
rect(S, 0, 0, 32, 16, MASK)            # head (all faces)
rect(S, 9, 12, 15, 13, EYE)            # mouth grille
rect(S, 9, 10, 11, 11, EYE); rect(S, 13, 10, 15, 11, EYE)  # eye slits
rect(S, 8, 8, 16, 9, DARKER)           # brow band
rect(S, 0, 16, 16, 32, DARK)           # right leg
rect(S, 16, 16, 40, 32, DARK)          # torso
rect(S, 20, 20, 28, 21, PURPLE)        # chest strap
rect(S, 40, 16, 56, 32, DARKER)        # right arm
rect(S, 16, 48, 32, 64, DARK)          # left leg
rect(S, 32, 48, 48, 64, DARKER)        # left arm
rect(S, 20, 26, 28, 28, PURPLE)        # belt
png(os.path.join(A, "entity/bondrewd.png"), 64, 64, S)

# Item: Umbra Hand (purple-black claw)
H = blank(16, 16)
for (x0, y0, x1, y1) in [(6,9,11,14),(5,5,6,10),(7,3,8,9),(9,4,10,9),(11,6,12,10),(7,13,10,15)]:
    rect(H, x0, y0, x1, y1, (30, 14, 50, 255))
rect(H, 7, 10, 10, 12, (140, 80, 210, 255))
png(os.path.join(A, "item/umbra_hand.png"), 16, 16, H)

# Item: Dawn Cartridge
C = blank(16, 16)
rect(C, 5, 2, 11, 14, (60, 60, 72, 255)); rect(C, 6, 3, 10, 13, (230, 190, 90, 255))
rect(C, 7, 6, 9, 10, (255, 245, 200, 255)); rect(C, 5, 2, 11, 3, (30, 30, 40, 255))
png(os.path.join(A, "item/dawn_cartridge.png"), 16, 16, C)

# Effect icon (18x18)
E = blank(18, 18)
rect(E, 2, 2, 16, 16, (42, 18, 69, 255)); rect(E, 5, 5, 13, 13, (130, 70, 200, 255)); rect(E, 7, 7, 11, 11, (14, 8, 24, 255))
png(os.path.join(A, "mob_effect/abyss_curse.png"), 18, 18, E)
print("textures written")
