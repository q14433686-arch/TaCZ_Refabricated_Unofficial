# refab 1.21.11 · 给已暂存的 `docs/ci/build.yml` 追加两条运行期安全校验（2026-09-02）

> **目标分支**：`TaCZ_Refabricated_Unofficial` 的 `1.21.11`。
> **前置**：先打同目录的 [`verify-mixin-targets-portable.patch`](verify-mixin-targets-portable.patch)
> （把 `docs/verify_mixin_targets.py` 里两条沙箱硬编码路径改成 `JAVA_HOME` / `GRADLE_USER_HOME` 可移植版；
> 补丁已在该分支真实 worktree 上 `git apply --check` + 实落通过，打完的脚本 `compile()` 通过）。
> 不打这个补丁就挂 CI ⇒ **必然红**：脚本里写死了 `/usr/lib/jvm/java-21-openjdk-amd64/bin/javap`
> 与 `/home/user/.gradle/...`，Actions 上两个路径都不存在。

## 为什么要加这两条

AGENTS.md §3 的原话：**「编译通过不等于运行期安全」——该分支移植期间崩过 5 次，每次都能编译通过。**
两个脚本正是为这 5 次里的两类写的：

| 脚本 | 挡住的故障 | 编译期能发现吗 |
|---|---|---|
| `docs/verify_mixin_targets.py` | mixin `method=` 名字写错 / 描述符写错 / `@At(target=…)` 指向不存在的成员 / `@Inject` handler 前导参数与目标方法不符（混淆版只 warn 不 error） | ❌ 只在启动时炸 |
| `docs/verify_shader_imports.py` | shader 的 `#moj_import` 指向目标 MC 版本没有的 include（26.x 的 `sample_lightmap.glsl` 抄进 1.21.11 ⇒ 资源重载失败 ⇒ 黑屏） | ❌ GLSL 不参与 javac |

现状（2026-09-02 实拉核对）：**这两个脚本没有被任何 workflow 调用**——
该分支 `.github/workflows/` 只有 `compile-check.yml`，暂存的 `docs/ci/build.yml` 里也没有它们。
`verify_shader_imports.py` 本身已经是可移植的（用 `os.path.expanduser`），可直接挂；
`verify_mixin_targets.py` 需要先打上面那个补丁。

## 追加位置与内容

在该分支 `docs/ci/build.yml` 的 **`Full build` 之后、`Upload jars` 之前**插入下面两步
（必须在 gradle 之后：两个脚本都要读 Loom 生成的 `minecraft-merged` jar，
checkout 完就跑会直接 `sys.exit`）：

```yaml
      - name: Mixin targets against the real 1.21.11 jar (运行期安全，编译期查不出)
        run: python3 docs/verify_mixin_targets.py

      - name: Shader #moj_import targets exist in 1.21.11 (运行期安全，编译期查不出)
        run: python3 docs/verify_shader_imports.py
```

同时把 `Upload jars` 一步改成 **`if: always()`**：

```yaml
      - name: Upload jars
        if: always()          # ← 新增：上面两条校验红了也照样出 jar，方便复现
        uses: actions/upload-artifact@v4
        with:
          name: TACZ-Refabricated-1.21.11-${{ github.sha }}
          ...
```

理由：这两条校验红的含义是「这个 jar 一进游戏就崩」，此时**更需要**把 jar 拿到手复现，
而不是让 artifact 步骤被跳过。（refab 26.2 侧的 build.yml 没有这两步，所以那边不需要 `if: always()`。）

## 首跑预期

- `verify_shader_imports.py`：**绿**（该脚本是 1.21.11 移植第 5 号故障后写的，R3 定稿时本地跑过）。
- `verify_mixin_targets.py`：**未知**——它需要 `javap` 逐个反编译 vanilla 类，
  在 Actions 上首跑可能耗时数分钟（脚本内 `timeout=180`/类）。若超时，
  把该步改成 `continue-on-error: true` 先观察一轮，再决定要不要收窄检查范围。
  **不要因为首跑慢就直接删掉这一步**：它挡的是「编译全绿、启动即崩」这一类，
  本分支已经为此崩过 5 次。

## 顺带一条（同一分支，独立于本文件）

该分支 `docs/ci/README.md` 已列明三件待上线（`compile-check.yml` v4 / `build.yml` / `consistency.yml`）。
其中 `consistency.yml` 的暂存稿自带「本分支没有 `scripts/check_release_consistency.sh` 就从默认分支取」的回退，
可以直接上线；但更干净的做法是把脚本本体也镜像到该分支（26.1.2 线 2026-09-02 就是这么做的，
见其 `docs/ci/README.md` 最后一行）——脚本本身分支无关，三条分支都能查。
