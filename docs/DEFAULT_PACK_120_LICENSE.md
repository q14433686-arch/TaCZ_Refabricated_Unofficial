# 默认包来源及署名

内置资源来自 [Sh1roCu/TACZ-Refabricated `356bdf9`](https://github.com/Sh1roCu/TACZ-Refabricated/tree/356bdf9d48f2225bac6ceb56adc5576a418517a8)，由本仓库移植至 Fabric 1.21.11。包内 `assets/tacz/gunpack_info.json` 保留 **TACZ Dev Team** 署名和 **CC BY-NC-ND 4.0** 许可证声明。仓库的代码许可不改变这些资源的许可；这次移植也不授予额外的再分发权。

[`DEFAULT_PACK_120.tsv`](../DEFAULT_PACK_120.tsv) 逐文件记录原始 Git blob、路径、采用的适配和发行字节 SHA-256。`source` 表示原样；历史版本前缀表示适配的来源，而非当前运行环境。元数据、包说明用本版；未改的模型、贴图和音频不重新编码。包外增量在 [`RESOURCE_IMPORT_120.tsv`](../RESOURCE_IMPORT_120.tsv)。构建仍从不可变的旧 bundle 取回未覆写文件；退役表不删除用户文件。可用 `python3 scripts/verify_12111_pack.py --jar <remapJar>` 复核。
