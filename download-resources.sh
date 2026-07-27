#!/bin/bash
# TACZ-Refabricated-26.2 资源下载脚本
# 在 tacz-type-a-prototype 目录下运行
# 从 Sh1roCu/TACZ-Refabricated (1.21.1分支) 和 tacz_default_gun.zip 下载枪械/配件资源

set -e
cd "$(dirname "$0")"

GUNS="aa12 ai_awp ak47 aug b93r cz75 db_long db_short deagle deagle_golden fn_evolys fn_fal g36k glock_17 hk416a5 hk416d hk_g3 hk_mk23 hk_mp5a5 kar98 lonetrail m1014 m107 m16a1 m16a4 m1911 m249 m320 m4a1 m700 m870 m95 m9a4 minigun mk14 p320 p90 qbz_191 qbz_95 rhino357 rpg7 rpk scar_h scar_l sks_tactical spas_12 spr15hb springfield1873 taurus500 taurus943 timeless50 type_81 ump45 uzi vector45"
REPO="https://raw.githubusercontent.com/Sh1roCu/TACZ-Refabricated/1.21.1/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz"
BASE="src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz"

mkdir -p "$BASE/geo_models/gun" "$BASE/geo_models/attachment" "$BASE/textures/gun/uv" "$BASE/animations" "$BASE/scripts"
mkdir -p "$BASE/geo_models/attachment/lod" "$BASE/textures/attachment/uv"

echo ">>> 下载枪械高模 + UV贴图 + 动画 + 脚本..."
for gun in $GUNS; do
    curl -sL "$REPO/geo_models/gun/${gun}_geo.json" -o "$BASE/geo_models/gun/${gun}_geo.json" &
    curl -sL "$REPO/textures/gun/uv/${gun}.png" -o "$BASE/textures/gun/uv/${gun}.png" &
    curl -sL "$REPO/animations/${gun}.animation.json" -o "$BASE/animations/${gun}.animation.json" &
    curl -sL "$REPO/scripts/${gun}_state_machine.lua" -o "$BASE/scripts/${gun}_state_machine.lua" &
done
wait
echo "  枪械高模: $(ls $BASE/geo_models/gun/*.json 2>/dev/null | wc -l)"
echo "  枪械UV: $(ls $BASE/textures/gun/uv/*.png 2>/dev/null | wc -l)"

echo ">>> 下载配件高模 + UV贴图..."
for f in $(find src/main/resources -path "*/display/attachments/*" -name "*.json" -exec basename {} _display.json \;); do
    curl -sL "$REPO/geo_models/attachment/${f}_geo.json" -o "$BASE/geo_models/attachment/${f}_geo.json" &
    curl -sL "$REPO/textures/attachment/uv/${f}.png" -o "$BASE/textures/attachment/uv/${f}.png" &
done
wait
echo "  配件高模: $(ls $BASE/geo_models/attachment/*.json 2>/dev/null | wc -l)"
echo "  配件UV: $(ls $BASE/textures/attachment/uv/*.png 2>/dev/null | wc -l)"

echo ">>> 从 tacz_default_gun.zip 提取 LOD/slot/HUD/flash/shell/ammo..."
ZIP="tacz_default_gun.zip"
if [ -f "$ZIP" ]; then
    TMP="/tmp/tacz_extract_$$"
    mkdir -p "$TMP"
    unzip -o "$ZIP" "tacz_default_gun/assets/tacz/textures/*" "tacz_default_gun/assets/tacz/geo_models/*/lod/*" -d "$TMP" 2>/dev/null
    cp -r "$TMP/tacz_default_gun/assets/tacz/textures/"* "$BASE/textures/" 2>/dev/null
    mkdir -p "$BASE/geo_models/gun/lod" "$BASE/geo_models/attachment/lod"
    cp "$TMP/tacz_default_gun/assets/tacz/geo_models/gun/lod/"*.json "$BASE/geo_models/gun/lod/" 2>/dev/null
    cp "$TMP/tacz_default_gun/assets/tacz/geo_models/attachment/lod/"*.json "$BASE/geo_models/attachment/lod/" 2>/dev/null
    rm -rf "$TMP"
    echo "  完成"
else
    echo "  警告: tacz_default_gun.zip 不存在，跳过LOD/slot/HUD资源"
fi

echo ">>> 全部完成！现在可以运行 ./gradlew jar 构建"
