#!/usr/bin/env python3
"""
Mixin 注入点体检 —— 对照 26.2 字节码核验所有已注册 mixin 的注入目标是否存在。

背景
----
跨大版本移植时最难查的一类问题是「mixin 注入点静默失效」：
目标方法被改名/删除后，注入要么启动崩溃，要么（有 require=0 / 条件 plugin 时）
悄悄不生效——编译通过、不报错、只是某个功能再也不工作了。

本项目已经踩过一次：登出事件只 hook 了 `Minecraft#clearClientLevel`，
而玩家最常用的「退出到标题」走的是 `Minecraft#disconnect(Screen,ZZ)`，
于是 `onPlayerLoggedOut` 从未被调用，静态状态跨存档残留，
表现为「持枪重进同一存档后打不出子弹」。

83 个注入点靠人工核对不现实，故脚本化。建议每次大版本升级后重跑。

用法
----
    python3 tools/audit_mixin_targets.py

退出码：0 = 全部有效；1 = 存在可疑项。
"""
import glob
import json
import os
import re
import struct
import sys
import zipfile

JAR_GLOB = '.gradle/loom-cache/minecraftMaven/net/minecraft/*/*/*.jar'
SRC_ROOT = 'src/main/java'


def find_jar():
    jars = [p for p in glob.glob(JAR_GLOB) if 'sources' not in p]
    if not jars:
        sys.exit('找不到 minecraft-merged jar；请先让 loom 至少跑过一次。')
    return jars[0]


class ClassReader:
    """极简 class 文件解析：只取方法名与描述符，不依赖任何第三方库。"""

    def __init__(self, jar):
        self.z = zipfile.ZipFile(jar)
        self.cache = {}

    def members(self, fqcn):
        if fqcn in self.cache:
            return self.cache[fqcn]
        try:
            data = self.z.read(fqcn.replace('.', '/') + '.class')
        except KeyError:
            self.cache[fqcn] = None
            return None
        cp, off = {}, 10
        count = struct.unpack_from('>H', data, 8)[0]
        i = 1
        while i < count:
            tag = data[off]
            off += 1
            if tag == 1:
                ln = struct.unpack_from('>H', data, off)[0]
                off += 2
                cp[i] = data[off:off + ln].decode('utf-8', 'replace')
                off += ln
            elif tag in (7, 8, 16, 19, 20):
                off += 2
            elif tag == 15:
                off += 3
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                off += 4
            elif tag in (5, 6):
                off += 8
                i += 1
            else:
                raise ValueError('unknown cp tag %d' % tag)
            i += 1
        off += 6  # access, this, super
        ifc = struct.unpack_from('>H', data, off)[0]
        off += 2 + 2 * ifc
        found = set()
        for kind in range(2):  # 0 = fields, 1 = methods
            n = struct.unpack_from('>H', data, off)[0]
            off += 2
            for _ in range(n):
                _, nx, dx, ac = struct.unpack_from('>HHHH', data, off)
                off += 8
                if kind == 1:
                    name, desc = cp.get(nx) or '', cp.get(dx) or ''
                    found.add(name)
                    found.add(name + desc)
                for _ in range(ac):
                    _, ln = struct.unpack_from('>HI', data, off)
                    off += 6 + ln
        self.cache[fqcn] = found
        return found


def registered_mixins():
    out = []
    for cfg in glob.glob('src/main/resources/*.mixins.json'):
        data = json.load(open(cfg, encoding='utf-8'))
        pkg = data.get('package', '')
        for key in ('mixins', 'client', 'server'):
            for entry in data.get(key) or []:
                out.append((cfg, pkg + '.' + entry))
    return out


def main():
    reader = ClassReader(find_jar())
    mixins = registered_mixins()
    suspicious, checked = [], 0

    for cfg, cls in mixins:
        path = os.path.join(SRC_ROOT, cls.replace('.', '/') + '.java')
        if not os.path.exists(path):
            suspicious.append((cls, '-', '<mixin 源文件缺失>'))
            continue
        src = open(path, encoding='utf-8').read()
        m = re.search(r'@Mixin\(\s*(?:value\s*=\s*)?\{?\s*([A-Za-z_][\w.]*)\.class', src)
        if not m:
            continue
        simple = m.group(1)
        imp = re.search(r'import\s+([\w.]*\.' + re.escape(simple) + r');', src)
        if not imp:
            # 同包类或 mod 自有类，不在 vanilla jar 内，跳过
            continue
        target = imp.group(1)
        members = reader.members(target)
        if members is None:
            # 目标不在 vanilla jar：多为 mod 自有类 / 条件加载的兼容 mixin
            continue
        for meth in re.findall(r'method\s*=\s*"([^"]+)"', src):
            base = meth.split('(')[0]
            if base.startswith('<'):
                continue
            checked += 1
            if meth not in members and base not in members:
                suspicious.append((cls, target, meth))

    print(f'已注册 mixin：{len(mixins)}    核验注入目标：{checked}\n')
    if suspicious:
        print(f'!! {len(suspicious)} 个可疑注入目标（在当前 jar 中找不到）：\n')
        for cls, target, meth in suspicious:
            print(f'  {cls.split(".")[-1]:34s} -> {target.split(".")[-1]}#{meth}')
        print('\n注意：注入 mod 自有类或条件加载的兼容 mixin 可能是误报，请人工确认。')
        return 1
    print('所有注入目标均在当前 jar 中找到 ✓')
    return 0


if __name__ == '__main__':
    sys.exit(main())
