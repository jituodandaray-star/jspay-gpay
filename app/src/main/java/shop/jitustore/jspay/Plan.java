package shop.jitustore.jspay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Premium plan: server se expiry leke live countdown. Asli rok server par hai (notify.php), yeh sirf dikhata hai. */
final class Plan {
    static SharedPreferences sp(Context c) { return c.getSharedPreferences("c", Context.MODE_PRIVATE); }

    static long bootEpoch() { return System.currentTimeMillis() - SystemClock.elapsedRealtime(); }

    static void save(Context c, long expTs, long serverTs) {
        long left = (expTs - serverTs) * 1000L;
        sp(c).edit().putBoolean("p_has", true).putBoolean("p_dead", left <= 0).putLong("p_exp", expTs)
                .putLong("p_left", left).putLong("p_base", SystemClock.elapsedRealtime())
                .putLong("p_boot", bootEpoch()).apply();
    }

    static void clear(Context c) { sp(c).edit().putBoolean("p_has", false).putBoolean("p_dead", false).putBoolean("p_life", false).apply(); }

    static void markExpired(Context c) { sp(c).edit().putBoolean("p_dead", true).apply(); }

    static boolean life(Context c) { return sp(c).getBoolean("p_life", false); }

    static boolean has(Context c) { return sp(c).getBoolean("p_has", false); }

    /** Plan khatam ho chuka hai (server ne expiry di aur samay nikal gaya). */
    static boolean dead(Context c) { return has(c) && leftMs(c) <= 0; }

    /** Bache hue poore din (aaj ke liye 0). Lifetime/unknown ke liye -1. */
    static int daysLeft(Context c) {
        if (!has(c)) return -1;
        long l = leftMs(c);
        return l <= 0 ? 0 : (int) ((l + 86399999L) / 86400000L);
    }

    static long expTs(Context c) { return sp(c).getLong("p_exp", 0); }

    /** Bacha hua samay ms mein. Phone ki clock badalne se farak nahi padta (elapsedRealtime). */
    static long leftMs(Context c) {
        SharedPreferences p = sp(c);
        if (!p.getBoolean("p_has", false)) return Long.MAX_VALUE;
        if (p.getBoolean("p_dead", false)) return 0;
        long el = SystemClock.elapsedRealtime(), base = p.getLong("p_base", 0);
        boolean same = el >= base && Math.abs(bootEpoch() - p.getLong("p_boot", 0)) < 90000L;
        if (same) return p.getLong("p_left", 0) - (el - base);
        return p.getLong("p_exp", 0) * 1000L - System.currentTimeMillis();
    }

    interface Done { void run(); }

    static void refresh(final Context ctx, final Done done) {
        final Context c = ctx.getApplicationContext();
        final String secret = sp(c).getString("secret", "");
        if (secret.isEmpty()) return;
        new Thread(() -> {
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(Sx.NOTIFY).openConnection();
                h.setRequestMethod("POST");
                h.setConnectTimeout(12000);
                h.setReadTimeout(12000);
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                OutputStream o = h.getOutputStream();
                o.write(("secret=" + URLEncoder.encode(secret, "UTF-8") + "&ping=1").getBytes("UTF-8"));
                o.close();
                if (h.getResponseCode() == 200) {
                    InputStream in = h.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[512];
                    int n;
                    while ((n = in.read(buf)) > 0 && bo.size() < 4096) bo.write(buf, 0, n);
                    String b = bo.toString("UTF-8");
                    long exp = num(b, "expiry_ts"), now = num(b, "server_ts");
                    boolean expired = b.contains("\"plan\":\"expired\"");
                    if (exp > 0 && now > 0) {
                        save(c, exp, now);
                        if (expired) markExpired(c);
                    } else if (b.contains("\"plan\"")) {
                        clear(c);
                        sp(c).edit().putBoolean("p_life", true).apply();
                    }
                }
            } catch (Exception ignored) { }
            if (done != null) new Handler(Looper.getMainLooper()).post(done::run);
        }).start();
    }

    private static long num(String s, String k) {
        Matcher m = Pattern.compile("\"" + k + "\"\\s*:\\s*\"?([0-9]+)\"?").matcher(s);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }
}
