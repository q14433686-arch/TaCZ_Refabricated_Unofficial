# compile-check-2612.yml 建议追加步骤（待维护者上线；2026-09-01）

来源：1211 复核 `SYNC_REVIEW_2612_TML_PORT_20260901.md` §0/§1/§2/§6 的建议
（「把 mixin 注册检查做成 CI 第 6 步」「lang 只许增不许减」「引用键必须存在」
「把 parity 脚本挂进 compile-check」）。`.github/workflows/**` 推送被 token
权限拒绝（AGENTS.md §1 同款），故按惯例暂存在此目录，由维护者并进
`compile-check-2612.yml`。

在现有 `compile` 步骤之后追加（顺序不敏感，三条都是快脚本）：

```yaml
      - name: mixin registration guard
        run: python3 docs/check_mixin_registration.py

      - name: lang key guard
        run: python3 docs/check_lang_keys.py

      - name: mesh config parity
        run: python3 docs/check_mesh_config_parity.py
```

三条脚本全部零依赖（只有 python3 + stdlib + 本仓工作树），无 jar/无网络。
`check_lang_keys.py` 的超集检查以 `git show HEAD:<path>` 为基线，在 CI 的
checkout（浅克隆也可，`git show HEAD:` 不需要历史）下工作。

失败样例与判定口径见各脚本 docstring；误报处置（白名单）也写在脚本里，
改白名单请连同理由一起改。
