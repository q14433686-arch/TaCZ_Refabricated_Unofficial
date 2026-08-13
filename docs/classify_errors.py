#!/usr/bin/env python3
"""Group a Gradle compileJava log into root-cause error families."""
import re, sys, collections

log = sys.argv[1] if len(sys.argv) > 1 else 'port-compile.log'
lines = open(log, encoding='utf-8', errors='replace').read().split('\n')

fams = collections.Counter()
files = collections.defaultdict(set)
examples = collections.defaultdict(list)

for i, l in enumerate(lines):
    m = re.match(r'/home/user/repo/src/main/java/([^:]+):(\d+): error: (.*)', l)
    if not m:
        continue
    f, ln, msg = m.groups()
    if 'package' in msg and 'does not exist' in msg:
        key = 'PKG ' + re.search(r'package ([\w.]+)', msg).group(1)
    elif msg == 'cannot find symbol':
        sym = kind = ''
        for j in range(i + 1, min(i + 7, len(lines))):
            s = re.search(r'symbol:\s+(\w+)\s+(\S+)', lines[j])
            if s:
                kind, sym = s.group(1), s.group(2)
                break
        key = f'SYM {kind} {sym}'
    elif 'incompatible types' in msg:
        key = 'TYPE ' + msg[:80]
    elif 'method does not override' in msg:
        key = 'OVERRIDE'
    elif 'is not abstract and does not override' in msg:
        key = 'ABSTRACT ' + msg[:70]
    else:
        key = 'OTHER ' + msg[:70]
    fams[key] += 1
    files[key].add(f)
    if len(examples[key]) < 2:
        examples[key].append(f.split('/')[-1] + ':' + ln)

total = sum(fams.values())
print(f'{total} errors, {len(fams)} families\n')
print(f'{"errs":>5} {"files":>5}  family')
for k, c in fams.most_common():
    print(f'{c:5d} {len(files[k]):5d}  {k}')
    print(f'{"":11}   e.g. {", ".join(examples[k])}')
