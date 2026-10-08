#!/usr/bin/env python3
"""Pixel-level inspection of screenshots, for verifying layout bounds (edge-to-edge,
letterboxing, etc.) precisely instead of eyeballing a downscaled screenshot render.

Usage:
  pixel.py <png> at <x> <y>              # print the pixel colour at one point
  pixel.py <png> row <y>                 # print colour transitions along row y
  pixel.py <png> col <x>                 # print colour transitions along column x
"""
import sys
from PIL import Image


def classify(px):
    rgb = px[:3]
    if rgb == (0, 0, 0):
        return "black"
    if rgb == (255, 255, 255):
        return "white"
    return str(rgb)


def main():
    if len(sys.argv) < 3:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    path, cmd = sys.argv[1], sys.argv[2]
    img = Image.open(path)
    w, h = img.size

    if cmd == "at":
        x, y = int(sys.argv[3]), int(sys.argv[4])
        print(f"({x},{y}) = {img.getpixel((x, y))}")
    elif cmd == "row":
        y = int(sys.argv[3])
        prev = None
        for x in range(w):
            cur = classify(img.getpixel((x, y)))
            if cur != prev:
                print(f"x={x} {cur}")
                prev = cur
    elif cmd == "col":
        x = int(sys.argv[3])
        prev = None
        for y in range(h):
            cur = classify(img.getpixel((x, y)))
            if cur != prev:
                print(f"y={y} {cur}")
                prev = cur
    else:
        print(__doc__, file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
