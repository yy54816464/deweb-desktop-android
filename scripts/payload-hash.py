#!/usr/bin/env python3
"""Canonical APK payload hash, ignoring v2/v3 signature blocks.

APK signatures use randomized ECDSA, so two builds from the same source have
different APK file hashes but identical payloads. Compare this hash to prove
the app content is reproducible.
"""
import hashlib
import sys
import zipfile

SIG = ('.SF', '.RSA', '.DSA', '.EC')


def payload_hash(path):
    h = hashlib.sha256()
    count = 0
    with zipfile.ZipFile(path) as z:
        for name in sorted(z.namelist()):
            if name.startswith('META-INF/') and (
                name.endswith(SIG) or name.endswith('MANIFEST.MF')
            ):
                continue
            h.update(name.encode('utf-8'))
            h.update(b'\0')
            h.update(hashlib.sha256(z.read(name)).digest())
            count += 1
    return h.hexdigest(), count


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print('usage: payload-hash.py <apk>')
        sys.exit(2)
    digest, count = payload_hash(sys.argv[1])
    print('entries', count)
    print('payload_sha256', digest)
