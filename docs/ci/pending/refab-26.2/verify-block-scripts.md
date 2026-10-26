# 把两个方块掉落/持久化守门脚本挂到 26.2 的 CI（待维护者粘贴）

> 为什么需要人工：本会话的 GitHub 凭据没有 `workflow` 权限，推不动
> `.github/workflows/`（与 `docs/ci/README.md` 记录的是同一个限制）。
> 目标分支：**本分支 26.2(main)**（不是姊妹分支），所以这次没有走 `pending/<分支>/` 的分目录惯例。

## 要加的两个脚本

| 脚本 | 守住什么 | 首次落地的原因 |
|---|---|---|
| `scripts/verify_block_drops.py` | 多方块工作台的掉落只由战利品表负责；创造模式抑制挂在「不产出的那一半」 | 2026-10-26「挖另一半掉两个」 |
| `scripts/verify_block_entity_persistence.py` | 方块实体存档键对称、判据键真实存在、持有 `ItemStack` 者必须有移除钩子 | 2026-10-27「雕像枪丢失」「标靶主人丢失」 |

两个脚本都支持 `--strict`（有问题退出 1，可直接当门禁），不带参数时只报告、恒退出 0。
都是纯静态检查，不下载任何依赖，`python3` 即可跑，秒级完成。

## 插入位置

`.github/workflows/build.yml` 里已有三个同类静态检查（mixin 配置完整性 / en-zh 语言键齐平 /
版本一致性），在两个 job 里各有一处。请在 **两处** 的静态检查段落末尾追加同样的两步：

```yaml
      - name: Block drops wiring (静态检查)
        run: python3 scripts/verify_block_drops.py --strict

      - name: Block entity persistence (静态检查)
        run: python3 scripts/verify_block_entity_persistence.py --strict
```

`compile-check.yml` 是轻量流程，加与不加都可以；**build.yml 至少要有**，
否则 PR 上不红就不会有人看。

## 上线前的自检

```bash
python3 scripts/verify_block_drops.py --strict && \
python3 scripts/verify_block_entity_persistence.py --strict
```

两者在当前 26.2 工作树上均为 rc=0；把 `AbstractGunSmithTableBlock` 里塞回一条
`popResource(...)`、或把 `TargetBlockEntity` 的判据改回 `Owner`，两者会各自报错
（已实测）。

上线后请把 `.github/workflows/build.yml` 的副本同步回 `docs/ci/build.yml`，
保持 `docs/ci/README.md` 里「副本 = 正式件镜像」的约定。
