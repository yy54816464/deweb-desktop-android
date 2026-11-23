package com.deweb.shell;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import android.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/** Keystore-backed tamper evidence for the verified Tape cache. No user password is stored. */
final class TapeCacheHmac {
    private static final String ALIAS = "deweb-tape-cache-hmac-v1";
    private static final String VERSION = "1";

    private TapeCacheHmac() {}

    static String sign(JSONObject meta, byte[] page) {
        try {
            String pageSha = TapeVerifier.sha256(page);
            meta.put("officialSha256", pageSha);
            meta.put("cacheHmacVersion", 1);
            String value = hmac(canonical(pageSha, meta));
            if (value != null) meta.put("cacheHmac", value);
            return value;
        } catch (Throwable t) {
            return null;
        }
    }

    static String signMeta(JSONObject meta) {
        try {
            if (meta == null || !meta.has("officialSha256")) return null;
            meta.put("cacheHmacVersion", 1);
            String value = hmac(canonical(meta.optString("officialSha256", ""), meta));
            if (value != null) meta.put("cacheHmac", value);
            return value;
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean verify(Context context, byte[] page, JSONObject meta) {
        try {
            if (context == null || page == null || meta == null) return false;
            String saved = meta.optString("cacheHmac", "");
            if (saved.isEmpty()) return false;
            String pageSha = TapeVerifier.sha256(page);
            if (!pageSha.equalsIgnoreCase(meta.optString("officialSha256", ""))) return false;
            String expected = hmac(canonical(pageSha, meta));
            if (expected == null) return false;
            return MessageDigest.isEqual(saved.getBytes(StandardCharsets.UTF_8),
                    expected.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            return false;
        }
    }

    private static String canonical(String pageSha, JSONObject meta) {
        return VERSION + "\n" + pageSha + "\n"
                + meta.optString("chainHash", "") + "\n"
                + meta.optLong("chainSize", -1) + "\n"
                + meta.optLong("chunks", -1) + "\n"
                + meta.optLong("chainCheckedAt", 0);
    }

    private static String hmac(String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key());
            return Base64.encodeToString(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        } catch (Throwable t) {
            return null;
        }
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            KeyStore.Entry entry = ks.getEntry(ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        }
        KeyGenerator kg = KeyGenerator.getInstance("HmacSHA256", "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build());
        return kg.generateKey();
    }
}
