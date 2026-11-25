#!/usr/bin/env python3
"""CI only: resolve exact reported Iris/Sodium release lines; verify downloaded hashes.
Never use latest or a different Minecraft/loader version when the pin is unavailable.
"""
import hashlib, io, json, os, pathlib, tarfile, urllib.request, sys, zipfile

def report_exception(kind, value, trace):
    print(f"::error::Iris runtime preparation: {kind.__name__}: {value}", flush=True)
    sys.__excepthook__(kind,value,trace)
sys.excepthook=report_exception
root=pathlib.Path('build/iris-smoke'); root.mkdir(parents=True,exist_ok=True)
manifest=[]
# Iris and Sodium themselves are loaded by -PwithIris from the receiver's pinned Curse files.
optics = os.environ.get('TACZ_OPTIC_SUITE')
materials = os.environ.get('TACZ_IRIS_SUITE')
if bool(optics) == bool(materials): raise ValueError('Select exactly one of TACZ_OPTIC_SUITE and TACZ_IRIS_SUITE')
if materials and materials not in ('fixture', 'color-off', 'act'): raise ValueError('Unknown Iris material suite')
if materials == 'fixture':
    manifest.append({'shaderpack': 'tacz-regression', 'source': 'src/gametest/irisConfig', 'options': 'fixture defaults'})
else:
    if optics:
        commit = '08e1c2ada5eaf2fc36f08516c316b3d3c3677d8e'
        expected = 'ba80162db7752712ce7cb50b20b4fb101d3489254badc52ee2a5a79b7d790798'
        name = 'ComplementaryReimagined_r5.8.1.zip'
    else:
        commit = '54ae23a44dd21dcf1c8fe70b4f0683d973b34cd0'
        expected = '4800cabf00f9002eb91be02e5ba26c1c99710bf9c89ce79c8a9493d6b4e83e89'
        name = 'ComplementaryReimagined_r5.9.3.zip'
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
    if optics: options.unlink(missing_ok=True)
    else:
        color = '128' if materials == 'act' else '0'
        options.write_text(f'COLORED_LIGHTING={color}\nSHADOW_QUALITY=1\nshadowDistance=64.0\nENTITY_SHADOW=1\nCLOUD_QUALITY=0\n')
    manifest.append({'shaderpack': name, 'commit': commit, 'archiveSha256': digest,
                     'options': options.read_text() if options.exists() else 'pack defaults'})
manifest[-1].update(minecraft='26.1.2', irisCurseFile=8402621, sodiumCurseFile=8396480)
(root/'manifest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps(manifest,indent=2))
