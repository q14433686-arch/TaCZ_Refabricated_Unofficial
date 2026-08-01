#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
measure_proportions.py — 测量 TACZ 默认枪包（或其他枪包）各枪模型的包围盒尺寸。

用途
----
自制枪包时，用这个脚本"量"默认枪包里的模型比例：
  1) 得到每把枪在模型网格(grid)里的全长/高/宽；
  2) 按官方标准比例 1000mm = 48 grid 换算成"等效毫米"；
  3) 拿你要做的枪的真实全长（维基百科/厂商官网）套公式 x = 48*L/1000，
     就能保证你的模型和默认包里的同类枪比例一致。

原理
----
TACZ 枪械模型是 Blockbench 基岩版实体模型（geo.json, format_version 1.12.0），
由骨骼(bone)+方块(cube)组成。脚本对每个方块求 8 个顶点，逐级套用骨骼变换
（pivot 平移 + 旋转），方块级旋转（pivot/rotation）也一并处理，最后取全局
AABB。手部模型骨骼（lefthand/righthand 等）不计入，以免污染尺寸。

用法
----
    python3 measure_proportions.py [geo目录] [--json out.json]

默认 geo 目录：
    src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/geo_models/gun

注意
----
* 测量结果是"包围盒"，带大角度旋转的部件（折叠枪托、倾斜握把、转轮弹巢等）
  会把结果撑大，个别枪（如 g36k 折叠托、m4a1/m16a4 的旋转细节件）会偏大，
  表里以 * 标注，此时请以 muzzle_pos 的 z 值为枪口参考。
* 枪口统一朝 -Z（Blockbench 里朝北），muzzle_pos 是配件定位组，其 pivot 的
  z 值就是"枪口位置"——做第一人称动画时它也决定枪口火光/曳光弹的位置。
"""
import json
import math
import os
import glob
import sys


def rot_matrix(rx, ry, rz):
    """欧拉角(度) -> 旋转矩阵，按 X、Y、Z 顺序（与 Blockbench 导出约定一致）。"""
    rx, ry, rz = math.radians(rx), math.radians(ry), math.radians(rz)
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]

    def mm(A, B):
        return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)]
                for i in range(3)]

    return mm(Rz, mm(Ry, Rx))


def apply(M, p):
    return [sum(M[i][k] * p[k] for k in range(3)) for i in range(3)]


def measure(path):
    """返回 (min_xyz, max_xyz, muzzle_pos_z)。"""
    with open(path) as f:
        data = json.load(f)
    geo = data['minecraft:geometry'][0]
    bones = {b['name']: b for b in geo['bones']}

    # 世界变换：每个骨头的仿射变换 (Rw, tw)，p_world = Rw*p_local + tw
    world = {}
    for name, b in bones.items():
        pivot = b.get('pivot', [0, 0, 0])
        R = rot_matrix(*b.get('rotation', [0, 0, 0]))
        if 'parent' in b:
            Rw, tw = world[b['parent']]
        else:
            Rw, tw = [[1, 0, 0], [0, 1, 0], [0, 0, 1]], [0, 0, 0]
        Rwc = [[sum(Rw[i][k] * R[k][j] for k in range(3)) for j in range(3)]
               for i in range(3)]
        t = [apply(Rw, pivot)[i] + tw[i] for i in range(3)]
        t = [t[i] - sum(Rwc[i][k] * pivot[k] for k in range(3)) for i in range(3)]
        world[name] = (Rwc, t)

    # 排除手部/视觉定位等非枪体内容
    skip = lambda n: any(k in n.lower() for k in ('hand', '_arm', 'refit')) \
        or n in ('views', 'view', 'positioning', 'camera',
                 'mag_and_left', 'right_and_gun', 'gun_and_righthand',
                 'mag_and_lefthand', 'mag_and_bullet')

    mins, maxs = [1e9] * 3, [-1e9] * 3
    for b in bones.values():
        if skip(b['name']):
            continue
        for cube in b.get('cubes', []):
            origin = cube['origin']
            size = cube['size']
            inflate = cube.get('inflate', 0)
            Rc, pc = None, None
            if cube.get('rotation') is not None and cube.get('pivot') is not None:
                Rc = rot_matrix(*cube['rotation'])
                pc = cube['pivot']
            Rw, tw = world[b['name']]
            for dx in (0, size[0]):
                for dy in (0, size[1]):
                    for dz in (0, size[2]):
                        p = [origin[0] + dx, origin[1] + dy, origin[2] + dz]
                        if Rc is not None:
                            p = [p[i] - pc[i] for i in range(3)]
                            p = apply(Rc, p)
                            p = [p[i] + pc[i] for i in range(3)]
                        q = apply(Rw, p)
                        q = [q[i] + tw[i] for i in range(3)]
                        for i in range(3):
                            mins[i] = min(mins[i], q[i] - abs(inflate))
                            maxs[i] = max(maxs[i], q[i] + abs(inflate))

    mz = None
    for b in bones.values():
        if b['name'] == 'muzzle_pos':
            mz = b.get('pivot', [0, 0, 0])[2]
    return mins, maxs, mz


def main():
    gun_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(__file__),
        '../../src/main/resources/assets/tacz/custom/tacz_default_gun'
        '/assets/tacz/geo_models/gun')
    out_json = None
    if '--json' in sys.argv:
        out_json = sys.argv[sys.argv.index('--json') + 1]

    rows = []
    for path in sorted(glob.glob(os.path.join(gun_dir, '*_geo.json'))):
        name = os.path.basename(path).replace('_geo.json', '')
        try:
            mn, mx, mz = measure(path)
            L, H, W = mx[2] - mn[2], mx[1] - mn[1], mx[0] - mn[0]
            rows.append({'gun': name, 'len_grid': round(L, 2),
                         'h_grid': round(H, 2), 'w_grid': round(W, 2),
                         'len_mm_est': round(L / 48 * 1000),
                         'muzzle_pos_z': round(mz, 2) if mz is not None else None})
        except Exception as e:  # noqa: BLE001
            rows.append({'gun': name, 'error': str(e)})

    print(f"{'gun':<20}{'len_grid':>9}{'h_grid':>8}{'w_grid':>8}"
          f"{'len_mm_est':>10}{'muzzle_z':>10}")
    for r in rows:
        if 'error' in r:
            print(f"{r['gun']:<20}ERR: {r['error']}")
        else:
            print(f"{r['gun']:<20}{r['len_grid']:>9.2f}{r['h_grid']:>8.2f}"
                  f"{r['w_grid']:>8.2f}{r['len_mm_est']:>10}"
                  f"{str(r['muzzle_pos_z']):>10}")

    if out_json:
        with open(out_json, 'w') as f:
            json.dump(rows, f, ensure_ascii=False, indent=1)
        print(f'\n已写入 {out_json}')


if __name__ == '__main__':
    main()
