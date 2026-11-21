#!/usr/bin/env python3
"""Buat .icns valid dari PNG (icp4/icp5/ic07/ic08) tanpa alat eksternal."""
import struct, sys, io
from PIL import Image

def entry(typ: str, im: Image.Image) -> bytes:
    buf = io.BytesIO()
    im.save(buf, "PNG")
    png = buf.getvalue()
    return struct.pack(">4sI", typ.encode(), len(png) + 8) + png

src, out = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGBA")
data = b"".join([
    entry("icp4", im.resize((16, 16), Image.LANCZOS)),
    entry("icp5", im.resize((32, 32), Image.LANCZOS)),
    entry("ic07", im.resize((128, 128), Image.LANCZOS)),
    entry("ic08", im.resize((256, 256), Image.LANCZOS)),
])
with open(out, "wb") as f:
    f.write(struct.pack(">4sI", b"icns", len(data) + 8) + data)
print("icns OK:", out)
