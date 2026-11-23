#!/usr/bin/env node
/**
 * Sign the Android update manifest. The private key stays outside the repo.
 *
 * Manifest canonical bytes (must match MainActivity.verifyUpdateSignature):
 *   versionCode + "\n" + versionName + "\n" + apkUrl + "\n" + sha256
 *
 * Usage:
 *   node scripts/sign-update-manifest.mjs \
 *     --version-code 405 --version-name 0.9.32-beta \
 *     --apk <apk> --url <https-apk-url> --notes <text> \
 *     --key <workspace>\release-keys\update-signing-private.pem \
 *     --public <workspace>\release-keys\update-signing-public.txt \
 *     --out <manifest.json>
 */
import crypto from 'node:crypto';
import fs from 'node:fs';

function arg(name, required = true) {
  const i = process.argv.indexOf('--' + name);
  if (i === -1 || i + 1 >= process.argv.length) {
    if (required) throw new Error('missing --' + name);
    return '';
  }
  return process.argv[i + 1];
}

const versionCode = Number(arg('version-code'));
if (!Number.isSafeInteger(versionCode) || versionCode < 1) throw new Error('invalid versionCode');
const versionName = arg('version-name');
const apk = arg('apk');
const apkUrl = arg('url');
const notes = arg('notes', false);
const keyPath = arg('key');
const publicPath = arg('public', false);
const outPath = arg('out');

if (!/^https:\/\//.test(apkUrl)) throw new Error('apk URL must be https');
const sha256 = crypto.createHash('sha256').update(fs.readFileSync(apk)).digest('hex');
const canonical = String(versionCode) + '\n' + versionName + '\n' + apkUrl + '\n' + sha256;

const privateKey = crypto.createPrivateKey(fs.readFileSync(keyPath, 'utf8'));
const signature = crypto.sign('sha256', Buffer.from(canonical, 'utf8'), privateKey).toString('base64');

if (publicPath) {
  const publicKey = crypto.createPublicKey({
    key: Buffer.from(fs.readFileSync(publicPath, 'utf8').trim(), 'base64'),
    format: 'der',
    type: 'spki'
  });
  const ok = crypto.verify('sha256', Buffer.from(canonical, 'utf8'), publicKey, Buffer.from(signature, 'base64'));
  if (!ok) throw new Error('signature does not verify with the supplied public key');
}

const manifest = { versionCode, versionName, apk: apkUrl, sha256, signature, notes };
fs.writeFileSync(outPath, JSON.stringify(manifest, null, 2) + '\n');
console.log('WROTE ' + outPath);
console.log('sha256 ' + sha256);
console.log('signature_len ' + signature.length);
