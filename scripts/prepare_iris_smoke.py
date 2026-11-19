#!/usr/bin/env python3
"""CI only: resolve exact reported Iris/Sodium release lines; verify downloaded hashes.
Never use latest or a different Minecraft/loader version when the pin is unavailable.
"""
import hashlib, io, json, os, pathlib, tarfile, urllib.request, re, sys, zipfile

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
# Optional screenshot regression. Fetch the pinned original; neither the shader source nor
# the generated pack/options belong in the mod, the repository or the uploaded artifacts.
color = os.environ.get('TACZ_IRIS_COLOR')
if color:
    if color not in ('0','128'): raise ValueError('COLORED_LIGHTING must be 0 or 128')
    commit = '54ae23a44dd21dcf1c8fe70b4f0683d973b34cd0'
    name = 'ComplementaryReimagined_r5.9.3.zip'
    req = urllib.request.Request('https://codeload.github.com/ComplementaryDevelopment/ComplementaryReimagined/tar.gz/'+commit)
    data = urllib.request.urlopen(req, timeout=90).read()
    digest = hashlib.sha256(data).hexdigest()
    if digest != '4800cabf00f9002eb91be02e5ba26c1c99710bf9c89ce79c8a9493d6b4e83e89': raise ValueError('Wrong Reimagined source')
    run = root/'complementary'
    (run/'shaderpacks').mkdir(parents=True,exist_ok=True); (run/'config').mkdir(exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(data),mode='r:gz') as tar, zipfile.ZipFile(run/'shaderpacks'/name,'w',zipfile.ZIP_DEFLATED) as pack:
        for entry in tar:
            if entry.isfile(): pack.writestr(entry.name.split('/',1)[1],tar.extractfile(entry).read())
    (run/'config/iris.properties').write_text('enableShaders=true\nshaderPack='+name+'\n')
    (run/'shaderpacks'/(name+'.txt')).write_text('COLORED_LIGHTING='+color+'\nSHADOW_QUALITY=1\nshadowDistance=64.0\nENTITY_SHADOW=1\nCLOUD_QUALITY=0\n')
    manifest.append({'shaderpack':name,'commit':commit,'archiveSha256':digest,'COLORED_LIGHTING':int(color)})
(root/'manifest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps(manifest,indent=2))
