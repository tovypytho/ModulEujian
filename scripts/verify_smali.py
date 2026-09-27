import hashlib
import pathlib
import sys


def files(root):
    root = pathlib.Path(root)
    return {
        path.relative_to(root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for folder in root.glob("smali*")
        for path in folder.rglob("*.smali")
    }


baseline, candidate = map(files, sys.argv[1:3])
added = sorted(candidate.keys() - baseline.keys())
removed = sorted(baseline.keys() - candidate.keys())
changed = sorted(k for k in candidate.keys() & baseline.keys() if candidate[k] != baseline[k])
print(f"added={len(added)} removed={len(removed)} changed={len(changed)}")
for label, names in (("ADDED", added), ("REMOVED", removed), ("CHANGED", changed)):
    for name in names:
        print(f"{label}\t{name}")
expected = {"smali/id/exambro/cbt/MainActivity.smali"}
if removed or set(changed) != expected or any(not n.startswith("smali_classes3/") for n in added):
    sys.exit(2)
