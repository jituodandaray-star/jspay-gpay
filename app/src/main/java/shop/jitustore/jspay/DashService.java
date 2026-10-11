package shop.jitustore.jspay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Attached Cashier: Google Pay for Business dashboard ki Transactions page ko background mein dekhta rehta hai
 *  aur har nayi payment (asli UPI transaction ID ke saath) server ko bhejta hai. Notification ki zarurat nahi. */
public class DashService extends Service {
    static final String CH = "cashier_v1";
    static final Pattern TX_START = Pattern.compile("([0-9]{1,2}:[0-9]{2})\\s+([0-9]{1,2}\\s+[A-Za-z]{3,9}\\s+[0-9]{4})");
    static final Pattern TX_ID = Pattern.compile("(?i)UPI\\s+transaction\\s+ID\\s+([0-9]{6,30})");
    static final Pattern TX_AMT = Pattern.compile("(?i)Amount\\s+(?:₹|Rs\\.?)\\s?([0-9,]+(?:\\.[0-9]{1,2})?)");
    static final Pattern TX_NAME = Pattern.compile("(?i)Payer\\s+([^\\n\\r]+)");
    static final Pattern TX_STATUS = Pattern.compile("(?i)Status\\s+([^\\n\\r]+)");

    private WebView web;
    private Handler h;
    private SharedPreferences sp;
    private PowerManager.WakeLock wl;
    private boolean running = false;
    private long loadedAt = 0;
    private int tick = 0, signedOutTicks = 0;
    private boolean warned = false;

    static void ensure(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences("c", Context.MODE_PRIVATE);
            if (sp.getString("cashier_url", "").isEmpty()) return;
            Intent i = new Intent(c, DashService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) { }
    }

    static void stop(Context c) {
        try { c.stopService(new Intent(c, DashService.class)); } catch (Exception ignored) { }
    }

    /** Page text se saari transactions: {id, amount, name, hh:mm, status}. Nayi se purani. */
    static ArrayList<String[]> parseTx(String text) {
        ArrayList<String[]> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = TX_START.matcher(text);
        ArrayList<int[]> pos = new ArrayList<>();
        ArrayList<String> times = new ArrayList<>();
        while (m.find()) { pos.add(new int[]{m.start(), m.end()}); times.add(m.group(1)); }
        for (int i = 0; i < pos.size(); i++) {
            int a = pos.get(i)[1];
            int b = i + 1 < pos.size() ? pos.get(i + 1)[0] : text.length();
            if (b < a) continue;
            String blk = text.substring(a, b);
            Matcher mi = TX_ID.matcher(blk), ma = TX_AMT.matcher(blk);
            if (!mi.find() || !ma.find()) continue;
            Matcher mn = TX_NAME.matcher(blk), ms = TX_STATUS.matcher(blk);
            String name = mn.find() ? mn.group(1).trim() : "";
            String st = ms.find() ? ms.group(1).trim() : "";
            if (st.matches("(?is).*(fail|declin|pend|refund|revers|cancel|expire).*")) continue;
            out.add(new String[]{mi.group(1), ma.group(1).replace(",", ""), name, times.get(i), st});
        }
        return out;
    }

    static String time12(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            int hh = Integer.parseInt(p[0]);
            return (hh % 12 == 0 ? 12 : hh % 12) + ":" + p[1] + " " + (hh >= 12 ? "pm" : "am");
        } catch (Exception e) { return hhmm; }
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH, "Cashier connection", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Shows while payments are verified from your Google dashboard");
                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            }
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
            nb.setContentTitle("JS PAY Cashier connected")
              .setContentText("Verifying payments from your Google dashboard")
              .setSmallIcon(android.R.drawable.stat_notify_sync)
              .setOngoing(true);
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            nb.setContentIntent(pi);
            Notification n = nb.build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(77, n);
        } catch (Exception ignored) { }
        if (!running) start();
        return START_STICKY;
    }

    private void start() {
        sp = getSharedPreferences("c", MODE_PRIVATE);
        String url = sp.getString("cashier_url", "");
        if (url.isEmpty()) { stopSelf(); return; }
        running = true;
        h = new Handler(Looper.getMainLooper());
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jspay:cashier");
            wl.setReferenceCounted(false);
            wl.acquire();
        } catch (Exception ignored) { }
        try {
            web = new WebView(getApplicationContext());
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            try {
                String ua = ws.getUserAgentString();
                if (ua != null) ws.setUserAgentString(ua.replace("; wv", "").replace("Version/4.0 ", ""));
            } catch (Exception ignored) { }
            try {
                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                cm.setAcceptThirdPartyCookies(web, true);
            } catch (Exception ignored) { }
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String u) { loadedAt = System.currentTimeMillis(); }
            });
            web.loadUrl(url);
        } catch (Throwable t) { stopSelf(); return; }
        h.postDelayed(loop, 5000);
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            tick++;
            try {
                web.resumeTimers();
                if (tick % 5 == 0) { loadedAt = 0; web.reload(); }   // ~20 sec
                else if (loadedAt > 0 && System.currentTimeMillis() - loadedAt > 2500) {
                    web.evaluateJavascript("(function(){return document.body?document.body.innerText:''})()", val -> {
                        try { onText(DashActivity.unquote(val)); } catch (Exception ignored) { }
                    });
                }
            } catch (Exception ignored) { }
            h.postDelayed(this, 4000);
        }
    };

    private void onText(String text) {
        boolean signedOut = text.contains("Sign in") && !text.contains("Transactions");
        ArrayList<String[]> tx = parseTx(text);
        boolean loaded = !signedOut && text.length() > 100 && (!tx.isEmpty() || text.toLowerCase().contains("transactions"));
        if (signedOut) {
            if (++signedOutTicks >= 3) {
                sp.edit().putString("cashier_state", "expired").apply();
                if (!warned) {
                    warned = true;
                    Notifier.warn(this, 31, "Cashier disconnected", "Open JS PAY > Attached Cashier and sign in again.");
                }
            }
            return;
        }
        signedOutTicks = 0;
        if (!loaded) return;
        warned = false;
        SharedPreferences.Editor ed = sp.edit().putLong("dash_ok", System.currentTimeMillis()).putString("cashier_state", "ok");
        String seen = sp.getString("cashier_seen", "");
        boolean base = sp.getBoolean("cashier_base", false);
        java.util.HashSet<String> set = new java.util.HashSet<>();
        for (String x : seen.split("\n")) if (!x.isEmpty()) set.add(x);
        StringBuilder add = new StringBuilder();
        if (!base) {
            for (String[] t : tx) { set.add(t[0]); add.append(t[0]).append("\n"); }
            ed.putBoolean("cashier_base", true);
        } else {
            for (int i = tx.size() - 1; i >= 0; i--) {   // purani pehle
                String[] t = tx.get(i);
                if (set.contains(t[0])) continue;
                set.add(t[0]);
                add.append(t[0]).append("\n");
                String nm = t[2].isEmpty() ? "Customer" : t[2];
                NotifService.feed(this, "Received", "₹" + t[1] + " received from " + nm + " at " + time12(t[3]), t[0]);
            }
        }
        if (add.length() > 0) {
            String all = add + seen;
            String[] ps = all.split("\n");
            StringBuilder nb = new StringBuilder();
            for (int i = 0; i < ps.length && i < 400; i++) if (!ps[i].isEmpty()) nb.append(ps[i]).append("\n");
            ed.putString("cashier_seen", nb.toString());
        }
        ed.apply();
    }

    @Override
    public void onDestroy() {
        running = false;
        try { if (h != null) h.removeCallbacksAndMessages(null); } catch (Exception ignored) { }
        try { if (web != null) web.destroy(); } catch (Exception ignored) { }
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
