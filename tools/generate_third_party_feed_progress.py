#!/usr/bin/env python3
"""Generate a source-pinned third-party feed compatibility progress report.

Author/CI utility only.  It reads an exported ``industry-feed-survey.json`` and
compares its current loaded identities against committed reference/feed data.
It never changes a gun profile and cannot enable feeds.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DATA_ROOT = ROOT / "src/main/resources/data"
OUTPUT_JSON = ROOT / "tools/industry/third_party_feed_progress.json"
OUTPUT_DOC = ROOT / "docs/THIRD_PARTY_FEED_PROGRESS.md"

PACK_GROUPS = [
    ("Default TACZ", ["tacz"]),
    ("Apocalypse", ["bf1"]),
    ("Cold War", ["rainforest"]),
    ("GunpowderRevolution", ["hamster"]),
    ("Enlisted", ["ww"]),
    ("CCRP / ClassicR", ["ccrp", "classicr"]),
    ("CIBR", ["cib", "cibs"]),
    ("KhanPowder", ["murasamet"]),
    ("Suffuse GunSmoke", ["suffuse"]),
    ("Delta Force: Storm Assault", ["wemql_r"]),
]


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def namespace_state(namespace: str, survey_ids: set[str]) -> dict[str, Any]:
    reference_dir = DATA_ROOT / namespace / "industry/reference/guns"
    feed_dir = DATA_ROOT / namespace / "industry/gun_feed"
    references = {path.stem for path in reference_dir.glob("*.json")} if reference_dir.exists() else set()
    feeds: dict[str, str] = {}
    if feed_dir.exists():
        for path in feed_dir.glob("*.json"):
            data = read_json(path)
            if isinstance(data, dict) and isinstance(data.get("mechanism"), str):
                feeds[path.stem] = data["mechanism"]
    matched_references = references & survey_ids
    matched_feeds = set(feeds) & survey_ids
    mechanism_counts: dict[str, int] = defaultdict(int)
    for gun_id in matched_feeds:
        mechanism_counts[feeds[gun_id]] += 1
    legacy_count = len(matched_references - matched_feeds)
    return {
        "namespace": namespace,
        "survey_total": len(survey_ids),
        "reference_count": len(matched_references),
        "active_feed_count": len(matched_feeds),
        "legacy_reference_count": legacy_count,
        "uncovered_count": len(survey_ids - matched_references),
        "active_mechanisms": dict(sorted(mechanism_counts.items())),
        "status": "complete" if len(matched_references) == len(survey_ids) else "in_progress",
    }


def report(survey_path: Path) -> dict[str, Any]:
    raw = survey_path.read_bytes()
    survey = json.loads(raw)
    by_namespace: dict[str, set[str]] = defaultdict(set)
    for entry in survey.get("entries", []):
        gun_id = entry.get("gun_id") if isinstance(entry, dict) else None
        if isinstance(gun_id, str) and ":" in gun_id:
            namespace, path = gun_id.split(":", 1)
            by_namespace[namespace].add(path)
    states = {namespace: namespace_state(namespace, ids) for namespace, ids in sorted(by_namespace.items())}
    groups = []
    covered_namespaces = set()
    for name, namespaces in PACK_GROUPS:
        present = [namespace for namespace in namespaces if namespace in states]
        if not present:
            continue
        covered_namespaces.update(present)
        group_states = [states[namespace] for namespace in present]
        total = sum(state["survey_total"] for state in group_states)
        references = sum(state["reference_count"] for state in group_states)
        active = sum(state["active_feed_count"] for state in group_states)
        legacy = sum(state["legacy_reference_count"] for state in group_states)
        groups.append(
            {
                "pack": name,
                "namespaces": present,
                "survey_total": total,
                "reference_count": references,
                "active_feed_count": active,
                "legacy_reference_count": legacy,
                "uncovered_count": total - references,
                "status": "complete" if references == total else "in_progress",
            }
        )
    for namespace, state in states.items():
        if namespace not in covered_namespaces:
            groups.append(
                {
                    "pack": namespace,
                    "namespaces": [namespace],
                    "survey_total": state["survey_total"],
                    "reference_count": state["reference_count"],
                    "active_feed_count": state["active_feed_count"],
                    "legacy_reference_count": state["legacy_reference_count"],
                    "uncovered_count": state["uncovered_count"],
                    "status": state["status"],
                }
            )
    return {
        "schema_version": 1,
        "generated_by": "tacz_third_party_feed_progress",
        "warning": "Author/CI audit only. This report is not a datapack and cannot enable feeds.",
        "survey": {
            "schema_version": survey.get("schema_version"),
            "sha256": hashlib.sha256(raw).hexdigest(),
            "summary": survey.get("summary", {}),
        },
        "pack_groups": groups,
        "namespace_details": states,
    }


def render(data: dict[str, Any]) -> str:
    lines = [
        "# 第三方枪包供弹兼容进度",
        "",
        "此文件由 `tools/generate_third_party_feed_progress.py` 从指定的 survey 导出生成，",
        "只供作者/CI追踪；普通玩家不需要运行 Python。它不启用任何供弹。",
        "",
        "## 当前 survey 来源",
        "",
        f"- schema：`{data['survey']['schema_version']}`",
        f"- SHA-256：`{data['survey']['sha256']}`",
        f"- 原始汇总：`{json.dumps(data['survey']['summary'], ensure_ascii=False, sort_keys=True)}`",
        "",
        "## 按枪包进度",
        "",
        "| 枪包 | Namespace | Survey 总数 | 已有事实 profile | Active 实体供弹 | Explicit legacy | 未覆盖 | 状态 |",
        "|---|---|---:|---:|---:|---:|---:|---|",
    ]
    for group in data["pack_groups"]:
        lines.append(
            f"| {group['pack']} | `{', '.join(group['namespaces'])}` | {group['survey_total']} | "
            f"{group['reference_count']} | {group['active_feed_count']} | {group['legacy_reference_count']} | "
            f"{group['uncovered_count']} | `{group['status']}` |"
        )
    lines.extend([
        "",
        "`complete` 的含义是该 survey 中每把枪都已有明确 reference 结论；它不意味着每把都被强行实体弹匣化。",
        "其中的 `Explicit legacy` 是有意保留原包行为、并记录原因的安全结论。",
        "",
        "## Namespace 机制明细",
        "",
        "| Namespace | Active mechanism 数量 |",
        "|---|---|",
    ])
    for namespace, state in data["namespace_details"].items():
        mechanisms = ", ".join(f"`{key}`={value}" for key, value in state["active_mechanisms"].items()) or "—"
        lines.append(f"| `{namespace}` | {mechanisms} |")
    lines.append("")
    return "\n".join(lines)


def write_or_check(path: Path, content: str, write: bool) -> bool:
    current = path.read_text(encoding="utf-8") if path.exists() else None
    if current == content:
        return False
    if write:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--survey", required=True, type=Path)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--write", action="store_true")
    group.add_argument("--check", action="store_true")
    args = parser.parse_args()
    data = report(args.survey)
    json_text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    markdown = render(data)
    stale = [
        path for path, content in ((OUTPUT_JSON, json_text), (OUTPUT_DOC, markdown))
        if write_or_check(path, content, args.write)
    ]
    if stale:
        mode = "wrote" if args.write else "checked"
        print(f"Third-party feed progress {mode} {len(stale)} stale output(s):")
        for path in stale:
            print(f"  {path.relative_to(ROOT)}")
        return 0 if args.write else 1
    print(f"Third-party feed progress checked: {len(data['pack_groups'])} pack group(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
