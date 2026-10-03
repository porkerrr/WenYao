"""Deterministic, original vector contours and brush masks; no fonts or downloaded artwork.
Run from any directory with Python 3. Standard library only. PNG wordmark remains unchanged.
"""
import json
import math
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"


def fixed(value):
    return {"a": 0, "k": value}


def layer(index, shapes, position=(0, 0, 0), rotation=0):
    return {"ddd": 0, "ind": index + 1, "ty": 4, "nm": f"authored contour {index}",
            "sr": 1, "ks": {"o": fixed(100), "r": fixed(rotation), "p": fixed(position),
                            "a": fixed([0, 0, 0]), "s": fixed([100, 100, 100])},
            "ao": 0, "shapes": shapes, "ip": 0, "op": 100, "st": 0, "bm": 0}


def asset(name, width, height, layers):
    return {"v": "5.12.2", "fr": 100, "ip": 0, "op": 100, "w": width, "h": height,
            "nm": name, "ddd": 0, "assets": [], "layers": layers}


def linear(start, end, first, last):
    return {"a": 1, "k": [{"t": start, "s": first, "e": last,
                            "o": {"x": .33, "y": .33}, "i": {"x": .67, "y": .67}},
                           {"t": end, "s": last}]}


def ink():
    layers = []
    for n in range(7):
        vertices, incoming, outgoing = [], [], []
        for i in range(16):
            angle = i * math.tau / 16
            radius = (92 - n * 8) * (1 + .13 * math.sin(angle * 3 + n * .9) + .08 * math.cos(angle * 5 + n))
            vertices.append([math.cos(angle) * radius, math.sin(angle) * radius * .73])
            incoming.append([math.sin(angle) * radius * .13, -math.cos(angle) * radius * .13])
            outgoing.append([-math.sin(angle) * radius * .13, math.cos(angle) * radius * .13])
        first = {"c": True, "v": vertices, "i": incoming, "o": outgoing}
        last = dict(first, v=[[x * (1 + .055 * math.sin(i * 1.7)), y * (1 + .07 * math.cos(i * 1.2))]
                              for i, (x, y) in enumerate(vertices)])
        layers.append(layer(n, [{"ty": "sh", "ks": linear(0, 100, [first], [last])},
                                {"ty": "fl", "c": fixed([31 / 255] * 3 + [1]),
                                 "o": fixed(2.5 + (n + 1) * .4), "r": 1}], (160, 120, 0), n * 9))
    return asset("WenYao ink - linear morph driven by shared eased progress", 320, 240, layers)


def brush_mask():
    # Coordinates match the existing 2172 x 724 transparent wordmark. These are revealing
    # regions along the observed brush direction, not a claim of historical stroke order.
    strokes = [
        [615, 75, 675, 130, 590, 200], [585, 240, 580, 410, 550, 580],
        [725, 210, 955, 135, 965, 350, 965, 585, 865, 550],
        [715, 315, 740, 405, 850, 350, 800, 295, 715, 335],
        [1450, 80, 1460, 160, 1190, 345], [1230, 180, 1380, 220, 1515, 295],
        [1410, 345, 1420, 410, 1270, 535, 1150, 565],
        [1190, 390, 1300, 410, 1460, 530, 1640, 575],
    ]
    starts = [0, 13, 32, 58, 68, 82, 90, 97]
    spans = [18, 24, 35, 20, 23, 16, 8.5, 3]
    layers = []
    for n, points in enumerate(strokes):
        vertices = [points[i:i + 2] for i in range(0, len(points), 2)]
        path = {"c": False, "v": vertices, "i": [[0, 0]] * len(vertices), "o": [[0, 0]] * len(vertices)}
        layers.append(layer(n, [
            {"ty": "sh", "ks": fixed(path)},
            {"ty": "st", "c": fixed([1, 1, 1, 1]), "o": fixed(100), "w": fixed(180), "lc": 2, "lj": 2},
            {"ty": "tm", "s": fixed(0), "e": linear(starts[n], starts[n] + spans[n], [0], [100]),
             "o": fixed(0), "m": 1},
        ]))
    return asset("WenYao brush-region alpha mask", 2172, 724, layers)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, content in [("wenyao_ink", ink()), ("wenyao_brush_mask", brush_mask())]:
        target = OUT / (name + ".json")
        target.write_text(json.dumps(content, separators=(",", ":")), encoding="utf-8")
        print(f"{target.name}: {target.stat().st_size} bytes")
