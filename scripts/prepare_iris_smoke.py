#!/usr/bin/env python3
"""CI only: resolve exact reported Iris/Sodium release lines; verify downloaded hashes.
Never use latest or a different Minecraft/loader version when the pin is unavailable.
"""
import hashlib, json, pathlib, urllib.request, re, sys

def report_exception(kind, value, trace):
    print(f"::error::Iris runtime preparation: {kind.__name__}: {value}", flush=True)
    sys.__excepthook__(kind,value,trace)
sys.excepthook=report_exception
root=pathlib.Path('build/iris-smoke'); root.mkdir(parents=True,exist_ok=True)
manifest=[]
for project,prefix in [('iris','1.11.6'),('sodium','0.9.2')]:
    url=f'https://api.modrinth.com/v2/project/{project}/version?game_versions=%5B%2226.3%22%5D&loaders=%5B%22fabric%22%5D'
    req=urllib.request.Request(url,headers={'User-Agent':'TaCZ-Refabricated/iris-regression'})
    versions=json.load(urllib.request.urlopen(req,timeout=60))
    matches=[v for v in versions if re.search(r'(?<![0-9.])'+re.escape(prefix)+r'(?![0-9.])',v['version_number'])]
    if len(matches)!=1: raise RuntimeError(f'Expected one pinned {project} {prefix} for 26.3 Fabric, found {len(matches)}; available: {[v["version_number"] for v in versions[:15]]}')
    v=matches[0]; f=next(f for f in v['files'] if f['primary'])
    data=urllib.request.urlopen(f['url'],timeout=90).read()
    if hashlib.sha512(data).hexdigest()!=f['hashes']['sha512']: raise SystemExit('Hash mismatch')
    (root/f'{project}.jar').write_bytes(data)
    manifest.append({'project':project,'version':v['version_number'],'id':v['id'],'sha512':f['hashes']['sha512']})
(root/'manifest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps(manifest,indent=2))
