"""Compare APK content independently of ZIP layout and signing blocks.

Strictly checks every non-signature entry. The only permitted content differences
are CRLF versus LF in valid asset JSON and explicitly checked AGP VCS metadata.
Signature validity must be checked separately with Android SDK apksigner.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

VCS = 'META-INF/version-control-info.textproto'
SIGNATURE = re.compile(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))$', re.I)

def entries(path):
    with zipfile.ZipFile(path) as archive:
        files = [i for i in archive.infolist() if not i.is_dir()]
        if len({i.filename for i in files}) != len(files):
            raise ValueError('Duplicate ZIP entries')
        return {i.filename: archive.read(i) for i in files if not SIGNATURE.fullmatch(i.filename)}

def compare(signed, rebuilt, expected_baseline):
    if not re.fullmatch('[a-f0-9]{40}', expected_baseline):
        raise ValueError('Expected a full reviewed baseline commit')
    left, right = entries(signed), entries(rebuilt)
    added, missing = sorted(left.keys() - right.keys()), sorted(right.keys() - left.keys())
    changed, line_endings, same = [], [], []
    vcs = left.get(VCS, b'').decode('utf-8', errors='strict')
    revision = re.search(r'\brevision:\s*"([a-f0-9]{40})"', vcs)
    baseline_matches = bool(revision and revision.group(1) == expected_baseline)
    for name in sorted(left.keys() & right.keys()):
        a,b=left[name],right[name]
        if a == b:
            same.append(name)
        elif name == VCS:
            # AGP writes the current repository identity; exported source uses a
            # new history. Never treat this exception as proof of code equality.
            continue
        elif name.startswith('assets/') and name.endswith('.json') and a.replace(b'\r\n',b'\n') == b.replace(b'\r\n',b'\n'):
            if json.loads(a) != json.loads(b):
                changed.append(name)
            else:
                line_endings.append(name)
        else:
            changed.append(name)
    return {'match':not(added or missing or changed) and baseline_matches,
            'baseline_revision_matches':baseline_matches,
            'signed_sha256':hashlib.sha256(Path(signed).read_bytes()).hexdigest(),
            'rebuilt_sha256':hashlib.sha256(Path(rebuilt).read_bytes()).hexdigest(),
            'identical_entries':len(same),'entry_count':len(left),
            'line_ending_only_entries':line_endings,
            'vcs_metadata_differs':left.get(VCS)!=right.get(VCS),
            'only_signed':added,'only_rebuilt':missing,'changed_entries':changed}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--signed',type=Path,required=True)
    p.add_argument('--rebuilt',type=Path,required=True)
    p.add_argument('--expected-baseline',required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args(); result=compare(a.signed,a.rebuilt,a.expected_baseline)
    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result)); return 0 if result['match'] else 1

if __name__=='__main__':
    raise SystemExit(main())
