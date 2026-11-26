"""Find the named game JAR, not a sources JAR or a cached copy for another MC line."""
import json
import os
from pathlib import Path
from zipfile import ZipFile


def named_jar():
    home = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
    root = home / 'caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged'
    for jar in sorted(root.glob('1.21.11-*/*.jar')):
        with ZipFile(jar) as z:
            if ('net/minecraft/client/Minecraft.class' in z.namelist() and 'version.json' in z.namelist()
                    and json.loads(z.read('version.json'))['id'] == '1.21.11'):
                return str(jar)
    raise SystemExit(f'No named Minecraft 1.21.11 merged JAR in {root}; run ./gradlew help first')
