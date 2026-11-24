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
# Iris and Sodium themselves are loaded by -PwithIris from the receiver's pinned Curse files.
if os.environ.get('TACZ_OPTIC_SUITE'):
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
    (run/'shaderpacks'/(name+'.txt')).unlink(missing_ok=True)
    manifest.append({'shaderpack': name, 'commit': commit, 'archiveSha256': digest, 'options': 'pack defaults',
                     'minecraft': '26.1.2', 'irisCurseFile': 8402621, 'sodiumCurseFile': 8396480})
(root/'manifest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps(manifest,indent=2))
