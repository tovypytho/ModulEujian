import hashlib
import sys
import zipfile


def entries(path):
    with zipfile.ZipFile(path) as archive:
        return {
            info.filename: hashlib.sha256(archive.read(info.filename)).hexdigest()
            for info in archive.infolist()
            if not info.is_dir()
        }


baseline, candidate = map(entries, sys.argv[1:3])
added = sorted(candidate.keys() - baseline.keys())
removed = sorted(baseline.keys() - candidate.keys())
changed = sorted(k for k in baseline.keys() & candidate.keys() if baseline[k] != candidate[k])
print(f"added={len(added)} removed={len(removed)} changed={len(changed)}")
for label, names in (("ADDED", added), ("REMOVED", removed), ("CHANGED", changed)):
    for name in names:
        print(f"{label}\t{name}")
expected_added = {"classes3.dex"}
expected_changed = {
    "classes.dex",
    "META-INF/ANDROIDD.RSA",
    "META-INF/ANDROIDD.SF",
    "META-INF/MANIFEST.MF",
}
if set(added) != expected_added or removed or set(changed) != expected_changed:
    print("UNEXPECTED ENTRY DIFFERENCE")
    sys.exit(2)
