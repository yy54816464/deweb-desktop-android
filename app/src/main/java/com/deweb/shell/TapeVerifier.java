package com.deweb.shell;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;

/** Independent official-gateway vs chain content check for the test build. */
public final class TapeVerifier {
    private static final String[] XL_RPCS = new String[]{
            "https://xlayerrpc.okx.com",
            "https://xlayer.drpc.org",
            "https://rpc.xlayer.tech",
            "https://196.rpc.thirdweb.com/"
    };
    private static final String REGISTRY = "0xd6EFb7adCc9c83dC4924Ad56f6a8E4e969b9ADB6";
    private static final String FILEINFO_SELECTOR = "0x6c609107";
    private static final String CONTAINER = "0x685f01613654070D8974c1786F090Cee9343bEfF";
    private static final String PATH = "index.html";

    private TapeVerifier() {}

    public static final class Result {
        public byte[] bytes;
        public boolean ok;
        public JSONObject meta;
    }

    public static JSONObject verifyOfficial(String url) {
        Result r = fetchAndVerify(url);
        return r.meta;
    }

    public static Result fetchAndVerify(String url) {
        return fetchAndVerify(url, "", null);
    }

    public static Result fetchAndVerify(final String url, String etag, final byte[] cachedBytes) {
        final Result result = new Result();
        result.meta = new JSONObject();
        result.ok = false;
        try {
            final AtomicReference<FetchOutcome> fetched = new AtomicReference<>();
            final AtomicReference<Throwable> fetchError = new AtomicReference<>();
            Thread pageThread = new Thread(() -> {
                try {
                    fetched.set(httpBytes(url, etag, cachedBytes));
                } catch (Throwable t) {
                    fetchError.set(t);
                }
            }, "tape-page-fetch");
            pageThread.start();

            JSONObject chain = chainInfo();
            pageThread.join(20000);
            if (fetchError.get() != null) throw fetchError.get();
            FetchOutcome fo = fetched.get();
            if (fo == null || fo.bytes == null) throw new Exception("empty official page");
            byte[] bytes = fo.bytes;
            result.bytes = bytes;
            JSONObject out = result.meta;
            out.put("officialBytes", bytes.length);
            out.put("officialSha256", sha256(bytes));
            out.put("officialUrl", url);
            if (fo.etag != null && !fo.etag.isEmpty()) out.put("etag", fo.etag);
            if (fo.notModified) out.put("notModified", true);

            long chainSize = chain.optLong("chainSize", 0);
            String chainHash = chain.optString("chainHash", "");
            long chunks = chain.optLong("chunks", 0);
            out.put("chainSize", chainSize);
            out.put("chainHash", chainHash);
            out.put("chunks", chunks);
            String officialSha = out.optString("officialSha256", "");
            out.put("hashMatch", chainHash.equalsIgnoreCase(officialSha));
            out.put("sizeMatch", chainSize == bytes.length);
            out.put("ok", chainHash.equalsIgnoreCase(officialSha) && chainSize == bytes.length);
            result.ok = out.optBoolean("ok", false);
        } catch (Throwable t) {
            try {
                result.meta.put("ok", false);
                result.meta.put("error", t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
            } catch (Throwable ignored) {}
        }
        return result;
    }

    private static final long CHAIN_INFO_TTL_MS = 3L * 60L * 1000L;
    private static final long CHAIN_INFO_TIMEOUT_MS = 3500L;

    private static JSONObject chainInfo() throws Exception {
        final String callData = FILEINFO_SELECTOR + padAddress(CONTAINER) + padUint(0x40)
                + padUint(PATH.getBytes(StandardCharsets.UTF_8).length)
                + padRight(PATH.getBytes(StandardCharsets.UTF_8));
        final JSONObject call = new JSONObject();
        call.put("to", REGISTRY);
        call.put("data", callData);
        final JSONArray params = new JSONArray();
        params.put(call);
        params.put("latest");

        ExecutorService pool = Executors.newFixedThreadPool(XL_RPCS.length);
        CompletionService<JSONObject> cs = new ExecutorCompletionService<>(pool);
        try {
            for (final String rpc : XL_RPCS) {
                cs.submit(() -> {
                    JSONObject callRes = rpcAt(rpc, "eth_call", params);
                    String hex = callRes.optString("result", "");
                    if (hex.startsWith("0x")) hex = hex.substring(2);
                    JSONObject out = new JSONObject();
                    out.put("chainSize", Long.parseLong(word(hex, 0), 16));
                    out.put("chainHash", word(hex, 2).toLowerCase(Locale.US));
                    out.put("chunks", Long.parseLong(word(hex, 4), 16));
                    return out;
                });
            }
            Map<String, Integer> counts = new HashMap<>();
            Map<String, JSONObject> examples = new HashMap<>();
            long deadline = System.currentTimeMillis() + CHAIN_INFO_TIMEOUT_MS;
            for (int i = 0; i < XL_RPCS.length; i++) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) break;
                Future<JSONObject> f = cs.poll(remain, TimeUnit.MILLISECONDS);
                if (f == null) break;
                try {
                    JSONObject r = f.get();
                    String key = r.optLong("chainSize", -1) + "|" + r.optString("chainHash", "") + "|" + r.optLong("chunks", -1);
                    int n = counts.containsKey(key) ? counts.get(key) + 1 : 1;
                    counts.put(key, n);
                    examples.put(key, r);
                    if (n >= 2) {
                        JSONObject agreed = examples.get(key);
                        agreed.put("chainCheckedAt", System.currentTimeMillis());
                        return agreed;
                    }
                } catch (Throwable ignored) {}
            }
        } finally {
            pool.shutdownNow();
        }
        throw new Exception("链上信息 RPC 共识不足（需要 2/4）");
    }

    private static JSONObject rpcAt(String endpoint, String method, JSONArray params) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            JSONObject req = new JSONObject();
            req.put("jsonrpc", "2.0");
            req.put("id", 1);
            req.put("method", method);
            req.put("params", params);
            OutputStream os = c.getOutputStream();
            os.write(req.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            if (c.getResponseCode() != 200) throw new Exception("rpc HTTP " + c.getResponseCode());
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject r = new JSONObject(sb.toString());
            if (r.has("error")) throw new Exception(r.optJSONObject("error").optString("message", "rpc error"));
            return r;
        } finally {
            c.disconnect();
        }
    }

    private static final class FetchOutcome {
        byte[] bytes;
        String etag;
        boolean notModified;
    }

    public static Result verifyCached(byte[] bytes, JSONObject cachedMeta) {
        Result result = new Result();
        result.meta = new JSONObject();
        result.bytes = bytes;
        try {
            String sha = sha256(bytes);
            JSONObject out = result.meta;
            out.put("officialBytes", bytes.length);
            out.put("officialSha256", sha);
            out.put("fromCache", true);

            long now = System.currentTimeMillis();
            long checkedAt = cachedMeta == null ? 0 : cachedMeta.optLong("chainCheckedAt", 0);
            boolean fresh = checkedAt > 0 && now - checkedAt >= 0 && now - checkedAt < CHAIN_INFO_TTL_MS;
            long chainSize = cachedMeta == null ? -1 : cachedMeta.optLong("chainSize", -1);
            String chainHash = cachedMeta == null ? "" : cachedMeta.optString("chainHash", "");
            long chunks = cachedMeta == null ? 0 : cachedMeta.optLong("chunks", 0);
            if (fresh && chainSize == bytes.length && chainHash.equalsIgnoreCase(sha)) {
                out.put("chainSize", chainSize);
                out.put("chainHash", chainHash);
                out.put("chunks", chunks);
                out.put("chainCheckedAt", checkedAt);
                out.put("cacheHit", true);
                out.put("hashMatch", true);
                out.put("sizeMatch", true);
                out.put("ok", true);
                result.ok = true;
                return result;
            }

            JSONObject chain = chainInfo();
            chainSize = chain.optLong("chainSize", 0);
            chainHash = chain.optString("chainHash", "");
            chunks = chain.optLong("chunks", 0);
            out.put("chainSize", chainSize);
            out.put("chainHash", chainHash);
            out.put("chunks", chunks);
            out.put("cacheHit", false);
            out.put("chainCheckedAt", chain.optLong("chainCheckedAt", now));
            out.put("hashMatch", chainHash.equalsIgnoreCase(sha));
            out.put("sizeMatch", chainSize == bytes.length);
            out.put("ok", chainHash.equalsIgnoreCase(sha) && chainSize == bytes.length);
            result.ok = out.optBoolean("ok", false);
        } catch (Throwable t) {
            try {
                result.meta.put("ok", false);
                result.meta.put("error", t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
            } catch (Throwable ignored) {}
        }
        return result;
    }

    private static FetchOutcome httpBytes(String url, String etag, byte[] cachedBytes) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("User-Agent", "DeWEB-LocalKernel/1.0");
        if (etag != null && !etag.isEmpty()) c.setRequestProperty("If-None-Match", etag);
        int code = c.getResponseCode();
        FetchOutcome out = new FetchOutcome();
        if (code == 304) {
            if (cachedBytes == null) throw new Exception("official 304 without cache");
            out.bytes = cachedBytes;
            out.etag = etag;
            out.notModified = true;
            c.disconnect();
            return out;
        }
        if (code != 200) throw new Exception("official HTTP " + code);
        out.etag = c.getHeaderField("ETag");
        InputStream in = c.getInputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        c.disconnect();
        out.bytes = b.toByteArray();
        return out;
    }

    public static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(data);
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format(Locale.US, "%02x", x & 0xff));
        return b.toString();
    }

    private static String padAddress(String a) {
        String h = a.startsWith("0x") ? a.substring(2) : a;
        StringBuilder b = new StringBuilder(h.toLowerCase(Locale.US));
        while (b.length() < 64) b.insert(0, '0');
        return b.toString();
    }

    private static String padUint(long n) {
        return String.format(Locale.US, "%064x", n);
    }

    private static String padRight(byte[] data) {
        StringBuilder b = new StringBuilder();
        for (byte x : data) b.append(String.format(Locale.US, "%02x", x & 0xff));
        while (b.length() % 64 != 0) b.append('0');
        return b.toString();
    }

    private static String word(String hex, int index) {
        int start = index * 64;
        if (hex.length() < start + 64) return "0";
        return hex.substring(start, start + 64);
    }
}
