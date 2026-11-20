#!/usr/bin/env python3
"""CI only: resolve exact reported Iris/Sodium release lines for 26.1.2; verify downloaded hashes.
Falls back to the branch's pinned CurseMaven file IDs when Modrinth omits the 26.1.2 hotfix build.
"""
import hashlib, io, json, os, pathlib, tarfile, urllib.request, re, sys, zipfile

def report_exception(kind, value, trace):
    print(f"::error::Iris runtime preparation: {kind.__name__}: {value}", flush=True)
    sys.__excepthook__(kind,value,trace)
sys.excepthook=report_exception
root=pathlib.Path('build/iris-smoke'); root.mkdir(parents=True,exist_ok=True)
manifest=[]
SPECS = [
    ('iris', '1.11.2', 'irisshaders-455508', '8402621'),
    ('sodium', '0.9.1', 'sodium-394468', '8396480'),
]
for project, prefix, curse_slug, curse_id in SPECS:
    matches = []
    for gv in ('26.1.2', '26.1'):
        url = f'https://api.modrinth.com/v2/project/{project}/version?game_versions=%5B%22{gv}%22%5D&loaders=%5B%22fabric%22%5D'
        req = urllib.request.Request(url, headers={'User-Agent': 'TaCZ-Refabricated/iris-regression'})
        versions = json.load(urllib.request.urlopen(req, timeout=60))
        matches = [
            v for v in versions
            if v["version_type"] == "release"
            and re.search(r'(?<![0-9.])' + re.escape(prefix) + r'(?![0-9.])', v['version_number'])
        ]
        if matches:
            break
    if len(matches) == 1:
        v = matches[0]
        f = next(f for f in v['files'] if f['primary'])
        data = urllib.request.urlopen(f['url'], timeout=90).read()
        if hashlib.sha512(data).hexdigest() != f['hashes']['sha512']:
            raise SystemExit('Hash mismatch')
        (root / f'{project}.jar').write_bytes(data)
        manifest.append({'project': project, 'version': v['version_number'], 'id': v['id'], 'sha512': f['hashes']['sha512']})
    else:
        # Use the exact 26.1.2 CurseForge build pinned in gradle.properties.
        curse_url = f'https://cursemaven.com/curse/maven/{curse_slug}/{curse_id}/{curse_slug}-{curse_id}.jar'
        req = urllib.request.Request(curse_url, headers={'User-Agent': 'TaCZ-Refabricated/iris-regression'})
        data = urllib.request.urlopen(req, timeout=90).read()
        sha512 = hashlib.sha512(data).hexdigest()
        (root / f'{project}.jar').write_bytes(data)
        manifest.append({'project': project, 'version': f'{prefix}+curse.{curse_id}', 'id': curse_id, 'sha512': sha512})

color = os.environ.get('TACZ_IRIS_COLOR')
optic = os.environ.get('TACZ_OPTIC_SUITE')
if color or optic:
    if optic and color: raise ValueError('Choose one shader-pack suite')
    if color and color not in ('0','128'): raise ValueError('COLORED_LIGHTING must be 0 or 128')
    commit = '54ae23a44dd21dcf1c8fe70b4f0683d973b34cd0'
    expected = '4800cabf00f9002eb91be02e5ba26c1c99710bf9c89ce79c8a9493d6b4e83e89'
    name = 'ComplementaryReimagined_r5.9.3.zip'
    if optic:
        commit = '08e1c2ada5eaf2fc36f08516c316b3d3c3677d8e'
        expected = 'ba80162db7752712ce7cb50b20b4fb101d3489254badc52ee2a5a79b7d790798'
        name = 'ComplementaryReimagined_r5.8.1.zip'
    req = urllib.request.Request('https://codeload.github.com/ComplementaryDevelopment/ComplementaryReimagined/tar.gz/'+commit)
    data = urllib.request.urlopen(req, timeout=90).read()
    digest = hashlib.sha256(data).hexdigest()
    if digest != expected: raise ValueError('Wrong Reimagined source')
    run = root/'complementary'
    (run/'shaderpacks').mkdir(parents=True,exist_ok=True); (run/'config').mkdir(exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(data),mode='r:gz') as tar, zipfile.ZipFile(run/'shaderpacks'/name,'w',zipfile.ZIP_DEFLATED) as pack:
        for entry in tar:
            if entry.isfile(): pack.writestr(entry.name.split('/',1)[1],tar.extractfile(entry).read())
    (run/'config/iris.properties').write_text('enableShaders=true\nshaderPack='+name+'\n')
    options = run/'shaderpacks'/(name+'.txt')
    if optic: options.unlink(missing_ok=True)  # use the reported release's own defaults
    else: options.write_text('COLORED_LIGHTING='+color+'\nSHADOW_QUALITY=1\nshadowDistance=64.0\nENTITY_SHADOW=1\nCLOUD_QUALITY=0\n')
    manifest.append({'shaderpack':name,'commit':commit,'archiveSha256':digest,'options':options.read_text() if options.exists() else 'pack defaults'})
(root/'manifest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps(manifest,indent=2))
