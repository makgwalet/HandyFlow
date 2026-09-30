#!/usr/bin/env python3
"""
Finds JPA enum values that the database CHECK constraint would REJECT.

An @Enumerated(STRING) field can hold any constant of its Java enum, but the column may
have a CHECK (col IN (...)) that was written earlier and never widened when a constant
was added. The write then fails with a constraint violation, which the API reports as a
generic 500 ("An unexpected error occurred"). Example that motivated this script:
ShiftStatus.PULLED vs chk_shift_status.

For each (table, column) it takes the LAST CHECK ... IN (...) list defined for that column
across the versioned migrations (later migrations replace earlier ones) and reports enum
constants missing from it.

    python3 platform/scripts/audit_enum_checks.py        (exit 1 if anything is found)

Limits: static text analysis only. It does not evaluate Postgres enum types, ALTER TYPE,
CHECKs that use a different expression shape, or constraints dropped without replacement.
Treat a finding as "verify this", and an all-clear as "no obvious mismatch", not proof.
"""
import re, os, sys, glob

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
JAVA = os.path.join(ROOT, 'src/main/java')
MIG = os.path.join(ROOT, 'src/main/resources/db/migration')

def ver(p):
    m = re.search(r'V(\d+)__', os.path.basename(p)); return int(m.group(1)) if m else None

def strip_sql_comments(s): return re.sub(r'--[^\n]*', '', s)
def snake(n): return re.sub(r'(?<!^)(?=[A-Z])', '_', n).lower()

# ---- enums, keyed by fully-qualified name (package + outer classes + name)
def parse_consts(body):
    consts, depth, cur = [], 0, ''
    for ch in body:
        if ch == '(': depth += 1
        if ch == ')': depth -= 1
        if ch == ',' and depth == 0: consts.append(cur); cur = ''
        else: cur += ch
    consts.append(cur)
    out = []
    for c in consts:
        m = re.match(r'\s*(?:@\w+(?:\([^)]*\))?\s*)*([A-Z][A-Z0-9_]*)\b', c)
        if m: out.append(m.group(1))
    return out

enums = {}          # fqn -> [constants]
by_simple = {}      # simple name -> [fqn]
file_info = {}      # path -> (package, [ (outer-chain-aware) enum simple names declared here ], imports)
for f in glob.glob(JAVA + '/**/*.java', recursive=True):
    s = open(f, encoding='utf8', errors='ignore').read()
    pkg = (re.search(r'^\s*package\s+([\w.]+);', s, re.M) or [None, ''])[1]
    outer = (re.search(r'\b(?:class|interface|record)\s+(\w+)', s) or [None, ''])[1]
    local = []
    for m in re.finditer(r'\benum\s+(\w+)[^{]*\{', s):
        # walk to the matching closing brace; constants end at the first top-level ';' or that brace
        depth, k, body = 1, m.end(), ''
        while k < len(s) and depth > 0:
            ch = s[k]
            if ch == '{': depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0: break
            elif ch == ';' and depth == 1: break
            body += ch; k += 1
        names = parse_consts(body)
        if not names: continue
        # nested when declared inside a class body; top-level when the file is the enum itself
        is_nested = outer and outer != m.group(1)
        fqn = f"{pkg}.{outer}.{m.group(1)}" if is_nested else f"{pkg}.{m.group(1)}"
        enums[fqn] = names; by_simple.setdefault(m.group(1), []).append(fqn); local.append((m.group(1), fqn))
    imports = re.findall(r'^\s*import\s+(?:static\s+)?([\w.]+);', s, re.M)
    file_info[f] = (pkg, local, imports)

def resolve(simple, path):
    pkg, local, imports = file_info[path]
    for name, fqn in local:                       # 1. declared in this file
        if name == simple: return fqn
    for imp in imports:                           # 2. explicit import (incl. outer.Nested)
        if imp.endswith('.' + simple) and imp in enums: return imp
    same_pkg = f'{pkg}.{simple}'                  # 3. same package
    if same_pkg in enums: return same_pkg
    cands = by_simple.get(simple, [])             # 4. unique simple name anywhere
    return cands[0] if len(cands) == 1 else None

# ---- entity enum fields
fields = []
skipped_ambiguous = []
for f in glob.glob(JAVA + '/**/*.java', recursive=True):
    s = open(f, encoding='utf8', errors='ignore').read()
    if '@Entity' not in s: continue
    t = re.search(r'@Table\s*\(\s*(?:[^)]*?\bname\s*=\s*)"(?:\w+\.)?(\w+)"', s)
    if not t: continue
    for m in re.finditer(r'@Enumerated\(EnumType\.STRING\)(.{0,300}?)private\s+(\w+)\s+(\w+)\s*(?:=|;)', s, re.S):
        col = re.search(r'@Column\s*\([^)]*?\bname\s*=\s*"(\w+)"', m.group(1))
        fqn = resolve(m.group(2), f)
        if fqn is None: skipped_ambiguous.append((t.group(1), m.group(3), m.group(2))); continue
        fields.append((t.group(1), col.group(1) if col else snake(m.group(3)), fqn, os.path.relpath(f, JAVA)))

# ---- CHECK lists per (table, column), last migration wins
checks = {}
CHECK = re.compile(r'CHECK\s*\(\s*\(?\s*(\w+)\s+IN\s*\(([^)]*)\)', re.I | re.S)
for f in sorted((p for p in glob.glob(MIG + '/V*.sql') if ver(p) is not None), key=ver):
    s = strip_sql_comments(open(f, encoding='utf8', errors='ignore').read())
    for m in re.finditer(r'CREATE TABLE(?:\s+IF NOT EXISTS)?\s+(?:\w+\.)?(\w+)\s*\((.*?)\n\)\s*;', s, re.S):
        for c in CHECK.finditer(m.group(2)):
            checks[(m.group(1), c.group(1))] = (set(re.findall(r"'([^']*)'", c.group(2))), os.path.basename(f))
    for m in re.finditer(r'ALTER TABLE(?:\s+ONLY)?\s+(?:IF EXISTS\s+)?(?:\w+\.)?(\w+)\s+(.*?);', s, re.S | re.I):
        for c in CHECK.finditer(m.group(2)):
            checks[(m.group(1), c.group(1))] = (set(re.findall(r"'([^']*)'", c.group(2))), os.path.basename(f))

bad = []
for table, col, etype, src in fields:
    if (table, col) not in checks or etype not in enums: continue
    allowed, mig = checks[(table, col)]
    missing = [v for v in enums[etype] if v not in allowed]
    if missing: bad.append((table, col, etype, missing, mig, src))

for t,c,ty in skipped_ambiguous: print(f'  (skipped, cannot resolve type) {t}.{c}: {ty}')
print(f'{len(fields)} enum fields resolved, {len(skipped_ambiguous)} skipped (ambiguous type), {len(checks)} CHECK-constrained columns, {len(enums)} enums')
for t, c, e, miss, mig, src in sorted(bad):
    print(f'  {t}.{c}  ({e.split(".")[-1]}) rejects {miss}   [last CHECK in {mig}]  {src}')
print(f'\n{len(bad)} mismatch(es)')
sys.exit(1 if bad else 0)
