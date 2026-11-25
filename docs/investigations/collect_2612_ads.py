#!/usr/bin/env python3
"""Rebuild the receiver's small TSV receipts from a successful shader-optics run.

Run inside the checkout: python3 docs/investigations/collect_2612_ads.py RUN_ID --out /tmp/ads
Requires gh with read access. Doesn't download or redistribute game or shader-pack files.
"""
import argparse
import csv
import json
from pathlib import Path
import re
import subprocess

VIEWS = 'suite optic slot host magnification view_locator cx_px cy_px radius_px red_square_px no_division_red_px mark_delta coating_delta background_pixels measured_pixels dark_pixels off_centre_scale_pixels coloured_stroke_pixels iron_scene_control iron_post_control actions_job'.split()
HEAT = 'suite optic slot mode distance_blocks yaw_deg rendered_yaw_deg hot_face sampled_face hot_optical_body visible_optical_body wall_display_bright pre_overlay_wall_leak source_wall_mask_pixels radius_px actions_job'.split()
FRAME_ROW = re.compile(r'(\w+)_(\d+) ([\w:]+) ([\d.]+)x view=(\d+) centre=(\d+),(\d+) r=(\d+) red=(\d+)/(\d+) markDelta=(\d+) coatingDelta=(\d+) background=(\d+)/(\d+) body=(\d+) scaleInk=(\d+) colouredInk=(\d+) ironScene=(\d+) ironPost=(\d+) PASS')
HEAT_ROW = re.compile(r'(\w+)_(\d+)_(classic|pip|narrow)_(\d+) @(\d+) drawn=([\d.-]+) face=(\d+)/(\d+) opticalBody=(\d+)/(\d+) wallBright=(\d+) wallLeak=(\d+) wallMask=(\d+) radius=(\d+) PASS')
EXPECTED = {'scopes': 34, 'sights': 25, 'pip': 20, 'report-size': 4, 'report-marks': 5, 'thermal-reap': 24, 'thermal-xl50': 24, 'thermal-narrow': 6}


def api(path):
    return json.loads(subprocess.check_output(['gh', 'api', 'repos/{owner}/{repo}/' + path]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=int)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    path = f'actions/runs/{args.run}'
    run = api(path)
    if run['path'] != '.github/workflows/shader-optics.yml' or run['conclusion'] != 'success':
        raise SystemExit('Expected a successful receiver shader-optics run')
    frames, heat, completed = [], [], set()
    for job in api(path + '/jobs?per_page=100')['jobs']:
        suite = job['name'].removeprefix('ads (').removesuffix(')')
        if suite not in EXPECTED:
            continue  # readout and wide-world controls aren't new catalog slots
        receipts = api(f"check-runs/{job['id']}/annotations?per_page=100")
        if job['conclusion'] != 'success' or not any(a['title'] == 'PACK' and 'ComplementaryReimagined_r5.8.1.zip active' in a['message'] for a in receipts):
            raise SystemExit(f'{suite}: missing active-pack receipt')
        thermal = suite.startswith('thermal-')
        rows = []
        for a in receipts:
            if a['title'] == ('HEAT' if thermal else 'FRAME'):
                for measured in a['message'].split(' | '):
                    m = (HEAT_ROW if thermal else FRAME_ROW).fullmatch(measured.strip())
                    if not m:
                        raise SystemExit(f'{suite}: incomplete or failing receipt: {measured}')
                    rows.append((suite, *m.groups(), str(job['id'])))
        if len(rows) != EXPECTED[suite] or len({r[1:6 if thermal else 3] for r in rows}) != len(rows):
            raise SystemExit(f'{suite}: expected {EXPECTED[suite]} distinct rows, got {len(rows)}')
        (heat if thermal else frames).extend(rows)
        completed.add(suite)
    if completed != EXPECTED.keys():
        raise SystemExit(f'Missing suites: {EXPECTED.keys() - completed}')
    args.out.mkdir(parents=True, exist_ok=True)
    for name, fields, data in [('VIEWS', VIEWS, frames), ('HEAT', HEAT, heat)]:
        with (args.out / f'ADS_R581_26_1_2_{name}.tsv').open('w', newline='') as f:
            writer = csv.writer(f, delimiter='\t', lineterminator='\n')
            writer.writerow(fields)
            writer.writerows(sorted(data))
    print(f"{run['head_sha']} run={args.run} views={len(frames)} heat={len(heat)}")


if __name__ == '__main__':
    main()
