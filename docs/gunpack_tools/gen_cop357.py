#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_cop357.py — 程序化生成 "COP 357 四管德林格手枪" 的完整 TACZ 枪包。

一切从真实尺寸出发（官方比例 1000mm = 48 grid）：
    COP 357 真实尺寸：全长 142mm / 高 103mm / 宽 27mm / 枪管 82.5mm
    → 模型 6.82 × 4.94 × 1.30 grid → 取 0.125 倍数：6.75 × 4.875 × 1.25

输出：
    cop357_gunpack/                 未压缩枪包目录（可自行修改研究）
    cop357_gunpack.zip              可直接丢进 .minecraft/tacz/ 的成品

结构（完全参照默认枪包写法）：
    gunpack.meta.json
    assets/cop357/geo_models/gun/cop357_geo.json   模型（Bedrock geo 1.12.0）
    assets/cop357/textures/gun/uv/cop357.png       模型贴图 128x128
    assets/cop357/textures/gun/slot/cop357.png     背包 2D 图 32x32
    assets/cop357/display/guns/cop357_display.json 客户端配置
    assets/cop357/lang/en_us.json / zh_cn.json     语言
    data/cop357/index/guns/cop357.json             枪械定义（注册）
    data/cop357/data/guns/cop357_data.json         枪械数值
    data/cop357/recipe/gun/cop357.json             合成配方

依赖：仅标准库（PNG 用手写的编码器，无 Pillow 需求）。
"""
import json
import math
import os
import random
import struct
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, 'cop357_gunpack')
ZIP_PATH = os.path.join(HERE, 'cop357_gunpack.zip')
NS = 'cop357'  # 枪包命名空间

# --------------------------------------------------------------------------
# 1. 最小 PNG 编码器（RGBA）
# --------------------------------------------------------------------------

def _chunk(tag, data):
    return (struct.pack('>I', len(data)) + tag + data
            + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff))


def save_png(path, w, h, pixels):
    """pixels: 一维列表 [(r,g,b,a), ...]，长度 w*h。"""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b''
    for y in range(h):
        raw += b'\x00'  # filter: none
        for x in range(w):
            r, g, b, a = pixels[y * w + x]
            raw += bytes((r, g, b, a))
    ihdr = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)  # 8bit RGBA
    data = (b'\x89PNG\r\n\x1a\n' + _chunk(b'IHDR', ihdr)
            + _chunk(b'IDAT', zlib.compress(raw, 9)) + _chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(data)


# --------------------------------------------------------------------------
# 2. 模型数据（方块坐标全部 0.125 倍数；枪口朝 -Z；关于 Z-Y 平面对称）
# --------------------------------------------------------------------------
# 材质 -> 128x128 贴图里的 16x16 格子 (col, row)
CELL = {
    'steel_dark': (0, 0),    # 枪管（深蓝黑金属）
    'steel_mid':  (3, 0),    # 机匣（中灰金属）
    'steel_frame':(3, 1),    # 枪身（深灰）
    'silver':     (2, 0),    # 击锤/扳机/枪口饰（亮银）
    'black':      (4, 0),    # 瞄具/护圈（哑黑）
    'wood':       (5, 0),    # 握把（木）
    'wood_dark':  (5, 1),    # 握把底饰（深木）
}
# 每种材质分配 3 个相邻格子，避免色差导致面接缝明显
CELL_MAP = {
    'steel_dark': [(0, 0), (1, 0), (0, 1)],
    'steel_mid':  [(3, 0), (3, 1)],
    'steel_frame':[(4, 1), (4, 2)],
    'silver':     [(2, 0), (2, 1)],
    'black':      [(6, 0), (6, 1)],
    'wood':       [(5, 0), (5, 1)],
    'wood_dark':  [(7, 0)],
}

# 方块： (name, 所在骨骼, origin, size, 材质, 可选 dict(rotation/pivot))
CUBES = [
    # 四根枪管 2x2 排列（每根 0.5x0.5 截面 x 4.0 长；总宽 1.25，总高 1.0）
    ('barrel_tl', 'barrels', [-0.625, 2.75, -5.0], [0.5, 0.5, 4.0], 'steel_dark'),
    ('barrel_tr', 'barrels', [0.125, 2.75, -5.0], [0.5, 0.5, 4.0], 'steel_dark'),
    ('barrel_bl', 'barrels', [-0.625, 2.25, -5.0], [0.5, 0.5, 4.0], 'steel_dark'),
    ('barrel_br', 'barrels', [0.125, 2.25, -5.0], [0.5, 0.5, 4.0], 'steel_dark'),
    # 枪口面饰（COP 标志性的方形枪口）
    ('muzzle_face', 'barrels', [-0.625, 2.25, -5.125], [1.25, 1.0, 0.125], 'silver'),
    # 后膛块（枪管组件与枪身的连接部，德林格铰链结构）
    ('receiver', 'receiver', [-0.625, 2.625, -1.0], [1.25, 1.25, 1.625], 'steel_mid'),
    # 枪身（含扳机座）
    ('frame', 'frame', [-0.5, 2.25, -0.625], [1.0, 0.5, 1.875], 'steel_frame'),
    # 顶部瞄准槽（开在枪管顶面的一条槽）
    ('sight', 'sight', [-0.125, 3.875, -4.375], [0.25, 0.125, 3.25], 'black'),
    # 外露击锤
    ('hammer', 'hammer', [-0.25, 3.625, 0.875], [0.5, 1.0, 0.25], 'silver'),
    # 扳机
    ('trigger', 'trigger', [-0.125, 1.5, 0.2], [0.25, 0.625, 0.125], 'silver'),
    # 扳机护圈（三段拼成门字形）
    ('guard_l', 'trigger_guard', [-0.625, 1.625, -0.125], [0.125, 0.5, 0.75], 'black'),
    ('guard_r', 'trigger_guard', [0.5, 1.625, -0.125], [0.125, 0.5, 0.75], 'black'),
    ('guard_f', 'trigger_guard', [-0.625, 1.5, -0.25], [1.25, 0.125, 0.125], 'black'),
    # 握把（向后倾斜 18°，德林格经典造型）
    ('grip', 'grip', [-0.5, 0.0, 0.5], [1.0, 3.0, 0.875], 'wood'),
    ('grip_plate', 'grip', [-0.375, -0.125, 0.625], [0.75, 0.125, 0.625], 'wood_dark'),
]

# 骨骼树： name -> (pivot, rotation, parent)
BONES = {
    'root':      ([0, 2.0, 0.5], None, None),
    'cop357':    ([0, 2.0, 0.5], None, 'root'),
    'barrels':   ([0, 2.75, -3.0], None, 'cop357'),
    'receiver':  ([0, 3.0, 0.3], None, 'cop357'),
    'frame':     ([0, 2.5, 0.3], None, 'cop357'),
    'sight':     ([0, 3.9, -2.5], None, 'cop357'),
    'hammer':    ([0, 4.0, 1.0], None, 'cop357'),
    'trigger':   ([0, 1.8, 0.3], None, 'cop357'),
    'trigger_guard': ([0, 1.8, 0.3], None, 'cop357'),
    'grip':      ([0, 2.375, 0.625], [-18, 0, 0], 'cop357'),
    # 配件定位组（COP 无配件，只留枪口火焰定位）
    'muzzle_flash': ([0, 2.75, -5.4], None, 'root'),
    # 第一人称视觉定位（照 Glock 17 的位置关系缩放：眼睛在枪后上方）
    'camera':    ([0.5, 6.5, 7.5], None, None),
    'views':     ([0, 0, 0], None, None),
    'idle_view': ([0.5, 6.5, 7.5], None, 'views'),
    'iron_view': ([0, 6.0, 7.0], None, 'views'),
    # 第三人称定位
    'positioning': ([0, 0, 0], None, None),
    'ground':    ([0, 0, -1.5], None, 'positioning'),
    'thirdperson_hand': ([0, 1.6, 0.9], None, 'positioning'),
    'fixed':     ([0, 2.4, -1.2], None, 'positioning'),
    # 改枪界面相机（照 Glock 缩放）
    'refit':     ([0, 0, 0], None, None),
    'refit_view': ([8, 3, 0], None, 'refit'),
    'refit_muzzle_view': ([8, 3, -12], None, 'refit'),
    'refit_scope_view': ([6, 5, 4], None, 'refit'),
    'refit_stock_view': ([6, 1.5, 8], None, 'refit'),
    'refit_extended_mag_view': ([6, 1, 3], None, 'refit'),
    'refit_grip_view': ([6, 2, 3], None, 'refit'),
    'refit_laser_view': ([6, 3, -8], None, 'refit'),
}


def box_uv_face(uv, size):
    """生成标准"箱子式展开"的六面 UV（Minecraft 箱子模板），k=1.5 px/grid。"""
    k = 1.5
    sx, sy, sz = (v * k for v in size)
    u0, v0 = uv
    faces = {
        'north': ([u0, v0 + sz], [sx, sy]),        # -Z
        'south': ([u0 + sx + sz, v0 + sz], [sx, sy]),  # +Z
        'east':  ([u0 + sx, v0 + sz], [sz, sy]),   # +X
        'west':  ([u0 + sx + sz + sz, v0 + sz], [sz, sy]),  # -X
        'up':    ([u0 + sx, v0], [sx, sz]),
        'down':  ([u0 + sx + sz, v0], [sx, sz]),
    }
    return {k: {'uv': [round(x, 4) for x in f[0]],
                'uv_size': [round(x, 4) for x in f[1]]}
            for k, f in faces.items()}


def build_geo():
    bones_out = []
    for name, (pivot, rot, parent) in BONES.items():
        bone = {'name': name, 'pivot': pivot}
        if parent:
            bone['parent'] = parent
        if rot:
            bone['rotation'] = rot
        bones_out.append(bone)
    for name, parent, origin, size, mat in CUBES:
        # 在所属骨骼下追加（保持顺序：先骨骼后方块 —— Blockbench 里方块挂在骨骼下）
        cube = {'origin': origin, 'size': size,
                'uv': box_uv_face(CELL[mat], size)}
        # 找到骨骼并追加 cubes
        for b in bones_out:
            if b['name'] == parent:
                b.setdefault('cubes', []).append(cube)
                break
    # 骨骼按 BONES 顺序 + cubes；整理输出（把 cubes 挂在正确骨骼）
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.cop357',
                'texture_width': 128,
                'texture_height': 128,
                'visible_bounds_width': 3,
                'visible_bounds_height': 3,
                'visible_bounds_offset': [0, 1.5, 0],
            },
            'bones': bones_out,
        }],
    }


# --------------------------------------------------------------------------
# 3. 贴图生成
# --------------------------------------------------------------------------

def make_texture(path):
    rng = random.Random(357)
    w = h = 128
    px = [(0, 0, 0, 0)] * (w * h)
    # 材质配色（基色, 明暗幅度）
    palette = {
        'steel_dark':  ((42, 50, 62), 10),   # 深蓝黑钢
        'steel_mid':   ((96, 104, 116), 10), # 中灰钢
        'steel_frame': ((70, 76, 88), 8),    # 深灰
        'silver':      ((168, 176, 190), 12),# 亮银
        'black':       ((26, 28, 32), 6),    # 哑黑
        'wood':        ((122, 84, 48), 12),  # 胡桃木
        'wood_dark':   ((88, 58, 30), 8),
    }
    for mat, cells in CELL_MAP.items():
        base, amp = palette[mat]
        for (col, row) in cells:
            u0, v0 = col * 16, row * 16
            for v in range(16):
                for u in range(16):
                    # 轻微横向渐变（金属感）+ 噪点
                    grad = (u / 15 - 0.5) * amp * 0.8
                    noise = rng.randint(-amp, amp) * 0.5
                    lum = grad + noise
                    r = max(0, min(255, base[0] + lum))
                    g = max(0, min(255, base[1] + lum))
                    b = max(0, min(255, base[2] + lum))
                    # 木纹：深色竖条纹
                    if mat == 'wood' and u % 4 == 0:
                        r, g, b = r * 0.75, g * 0.75, b * 0.75
                    px[(v0 + v) * w + (u0 + u)] = (int(r), int(g), int(b), 255)
    save_png(path, w, h, px)


def make_slot(path):
    """32x32 背包图：右侧视图剪影（枪口朝左）。"""
    w = h = 32
    px = [(0, 0, 0, 0)] * (w * h)
    color = (58, 66, 80, 255)
    edge = (110, 122, 140, 255)

    def rect(x0, y0, x1, y1, c):
        for y in range(max(0, y0), min(h, y1)):
            for x in range(max(0, x0), min(w, x1)):
                px[y * w + x] = c

    # 枪管组件（含枪口饰）
    rect(2, 14, 18, 20, color)
    rect(2, 14, 3, 20, edge)
    # 后膛块
    rect(17, 12, 21, 20, color)
    # 枪身
    rect(20, 9, 25, 13, color)
    # 击锤
    rect(21, 19, 23, 23, color)
    # 握把（两段模拟后倾）
    rect(22, 3, 26, 9, color)
    rect(25, 1, 29, 6, color)
    # 外轮廓亮边（左=枪口方向）
    for y in range(14, 20):
        px[y * w + 2] = edge
    save_png(path, w, h, px)


# --------------------------------------------------------------------------
# 4. 数据文件
# --------------------------------------------------------------------------

DISPLAY = {
    'model': 'cop357:gun/cop357_geo',
    'texture': 'cop357:gun/uv/cop357',
    'slot': 'cop357:gun/slot/cop357',
    'use_default_animation': 'pistol',
    'player_animator_3rd': 'tacz:pistol_default.player_animation',
    'transform': {
        'scale': {
            'thirdperson': [0.8, 0.8, 0.8],
            'ground': [0.8, 0.8, 0.8],
            'fixed': [1.2, 1.2, 1.2],
        }
    },
    'muzzle_flash': {
        'texture': 'tacz:flash/common_muzzle_flash',
        'scale': 0.45,
    },
    'iron_zoom': 1.35,
    'zoom_model_fov': 55,
    'ammo_count_style': 'normal',
    # 音效暂借默认枪包（tacz:m1911），后续可替换为自己的 ogg
    'sounds': {
        'draw': 'tacz:m1911/m1911_draw',
        'put_away': 'tacz:m1911/m1911_put_away',
        'shoot': 'tacz:m1911/m1911_shoot',
        'shoot_3p': 'tacz:m1911/m1911_shoot_3p',
        'silence': 'tacz:m1911/m1911_silence',
        'silence_3p': 'tacz:m1911/m1911_silence_3p',
        'dry_fire': 'tacz:dry_fire',
        'head_hit': 'tacz:head_hit',
        'flesh_hit': 'tacz:flesh_hit',
        'kill': 'tacz:kill',
    },
    'offhand_show': {
        'pos': [4, 13, -1],
        'rotate': [-90, -35, 90],
        'scale': [0.5, 0.5, 0.5],
    },
    'hotbar_show': {
        '0': {
            'pos': [-5, 13, -1],
            'rotate': [-90, -35, 90],
            'scale': [0.5, 0.5, 0.5],
        }
    },
}

# 数值以 rhino357（.357 左轮）为基准，按 COP 特点调整
DATA = {
    'ammo': 'tacz:357mag',
    'rpm': 120,
    'bullet': {
        'life': 0.85,
        'bullet_amount': 1,
        'damage': 10.5,
        'tracer_count_interval': 0,
        'extra_damage': {
            'armor_ignore': 0.3,
            'head_shot_multiplier': 1.75,
            'damage_adjust': [
                {'distance': 25, 'damage': 10.5},
                {'distance': 35, 'damage': 8.5},
                {'distance': 50, 'damage': 6},
                {'distance': 'infinite', 'damage': 6},
            ],
        },
        'speed': 190,
        'gravity': 0.15,
        'knockback': 0.075,
        'friction': 0.03,
        'ignite': False,
        'pierce': 1,
    },
    'ammo_amount': 4,
    'bolt': 'closed_bolt',
    'reload': {
        # manual = 手动供弹（开膛塞 4 发，最贴近德林格装填）
        'type': 'manual',
        'feed': {'empty': 2.6},
        'cooldown': {'empty': 3.1},
    },
    'draw_time': 0.4,
    'put_away_time': 0.3,
    'aim_time': 0.12,
    'sprint_time': 0.05,
    'weight': 0.8,
    'movement_speed': {
        'base': 0,
        'aim': 0,
        'reload': 0.1,
    },
    'crawl_recoil_multiplier': 0.5,
    'fire_mode': ['semi'],
    # 无配件槽：省略 allow_attachment_types（默认空）
    'recoil': {
        'pitch': [
            {'time': 0, 'value': [2.1, 2.1]},
            {'time': 0.08, 'value': [2.2, 2.2]},
            {'time': 0.16, 'value': [1.9, 1.9]},
            {'time': 0.35, 'value': [-0.125, -0.125]},
            {'time': 0.48, 'value': [0.225, 0.225]},
            {'time': 0.7, 'value': [0, 0]},
            {'time': 1.0, 'value': [0, 0]},
        ],
        'yaw': [
            {'time': 0, 'value': [-0.35, 0.05]},
            {'time': 0.08, 'value': [-0.8, -0.2]},
            {'time': 0.16, 'value': [-0.5, -0.1]},
            {'time': 0.5, 'value': [0, 0]},
        ],
    },
    'inaccuracy': {
        'stand': 4.0,
        'move': 5.5,
        'sneak': 2.5,
        'lie': 1.5,
        'aim': 0.12,
    },
}

INDEX = {
    'name': 'cop357.gun.cop357.name',
    'display': 'cop357:cop357_display',
    'data': 'cop357:cop357_data',
    'tooltip': 'cop357.gun.cop357.desc',
    'type': 'pistol',
    'sort': 6,
}

LANG_EN = {
    'cop357.gun.cop357.name': 'COP 357 Derringer',
    'cop357.gun.cop357.desc': 'Four barrels of .357 Magnum. Mind your wrist.',
}

LANG_ZH = {
    'cop357.gun.cop357.name': 'COP 357 四管德林格手枪',
    'cop357.gun.cop357.desc': '四管 .357 马格南，小心你的手腕。',
}

RECIPE = {
    'materials': [
        {'item': '#c:ingots/iron', 'count': 10},
    ],
    'result': {
        'type': 'gun',
        'id': 'cop357:cop357',
    },
    'type': 'tacz:gun_smith_table_crafting',
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def main():
    # 模型
    write_json(os.path.join(OUT_DIR, 'assets', NS, 'geo_models', 'gun',
                            'cop357_geo.json'), build_geo())
    # 贴图
    make_texture(os.path.join(OUT_DIR, 'assets', NS, 'textures', 'gun', 'uv',
                              'cop357.png'))
    make_slot(os.path.join(OUT_DIR, 'assets', NS, 'textures', 'gun', 'slot',
                           'cop357.png'))
    # 客户端配置 / 语言
    write_json(os.path.join(OUT_DIR, 'assets', NS, 'display', 'guns',
                            'cop357_display.json'), DISPLAY)
    write_json(os.path.join(OUT_DIR, 'assets', NS, 'lang', 'en_us.json'), LANG_EN)
    write_json(os.path.join(OUT_DIR, 'assets', NS, 'lang', 'zh_cn.json'), LANG_ZH)
    # 定义 / 数值 / 配方
    write_json(os.path.join(OUT_DIR, 'data', NS, 'index', 'guns',
                            'cop357.json'), INDEX)
    write_json(os.path.join(OUT_DIR, 'data', NS, 'data', 'guns',
                            'cop357_data.json'), DATA)
    write_json(os.path.join(OUT_DIR, 'data', NS, 'recipe', 'gun',
                            'cop357.json'), RECIPE)
    # 枪包元数据
    write_json(os.path.join(OUT_DIR, 'gunpack.meta.json'), {'namespace': NS})

    # 打包 zip（zip 根 = 枪包根）
    if os.path.exists(ZIP_PATH):
        os.remove(ZIP_PATH)
    with zipfile.ZipFile(ZIP_PATH, 'w', zipfile.ZIP_DEFLATED) as zf:
        for root, _, files in os.walk(OUT_DIR):
            for fn in files:
                full = os.path.join(root, fn)
                rel = os.path.relpath(full, OUT_DIR)
                zf.write(full, rel)
    print('枪包已生成：')
    for root, _, files in os.walk(OUT_DIR):
        for fn in sorted(files):
            print('  ', os.path.relpath(os.path.join(root, fn), OUT_DIR))
    print('ZIP:', ZIP_PATH)


if __name__ == '__main__':
    main()
