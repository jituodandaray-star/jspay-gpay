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

    static long lastEnsure = 0;

    static void ensure(Context c) { ensure(c, false); }

    /** force=true: seedha shuru karo (login ke baad / boot). Warna sirf tab jab service sach mein band lage. */
    static void ensure(Context c, boolean force) {
        SharedPreferences sp = null;
        try {
            sp = c.getSharedPreferences("c", Context.MODE_PRIVATE);
            if (sp.getString("cashier_url", "").isEmpty()) return;
            long now = System.currentTimeMillis();
            if (!force) {
                if (now - sp.getLong("svc_ok", 0) < 60000L) return;   // chal rahi hai: dobara start request nahi
                if (now - lastEnsure < 20000L) return;
            }
            lastEnsure = now;
            Intent i = new Intent(c, DashService.class);
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
                sp.edit().putLong("svc_req", System.currentTimeMillis()).apply();
            } catch (Throwable e1) {
                sp.edit().putString("svc_stage", "start refused: " + e1).putLong("svc_ts", System.currentTimeMillis()).apply();
                try { c.startService(i); } catch (Throwable e2) { sp.edit().putString("svc_stage", "start refused: " + e2).apply(); }
            }
        } catch (Throwable e) {
            try { if (sp != null) sp.edit().putString("svc_stage", "ensure error: " + e).apply(); } catch (Throwable ignored) { }
        }
    }

    void stage(String s) {
        try { getSharedPreferences("c", MODE_PRIVATE).edit().putString("svc_stage", s).putLong("svc_ts", System.currentTimeMillis()).apply(); } catch (Exception ignored) { }
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
        stage("command received");
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
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, DashActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            nb.setContentIntent(pi);
            Notification n = nb.build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(77, n);
            stage("foreground ok");
        } catch (Throwable e) { stage("foreground failed: " + e); }
        if (!running) start();
        return START_STICKY;
    }

    private void start() {
        sp = getSharedPreferences("c", MODE_PRIVATE);
        String url = sp.getString("cashier_url", "");
        if (url.isEmpty()) { stopSelf(); return; }
        running = true;
        stage("starting webview");
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
                public void onPageFinished(WebView v, String u) {
                    loadedAt = System.currentTimeMillis();
                    try {
                        String uu = u == null ? "" : u;
                        SharedPreferences.Editor e = sp.edit().putLong("dash_page", loadedAt).putString("dash_url", uu.length() > 60 ? uu.substring(0, 60) : uu);
                        boolean out = uu.contains("accounts.google.com") || uu.contains("/g4b/signup");
                        if (uu.contains("pay.google.com/g4b") && !out) { e.putLong("dash_ok", loadedAt).putLong("svc_ok", loadedAt); }
                        e.apply();
                    } catch (Exception ignored) { }
                }
                @Override
                public void onReceivedError(WebView v, int code, String d, String u) { sp.edit().putString("dash_err", code + " " + d).apply(); }
            });
            try {
                int w = getResources().getDisplayMetrics().widthPixels, hh = getResources().getDisplayMetrics().heightPixels;
                web.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(hh, android.view.View.MeasureSpec.EXACTLY));
                web.layout(0, 0, w, hh);
                web.onResume();
                web.resumeTimers();
            } catch (Throwable ignored) { }
            ws.setUseWideViewPort(true);
            web.setWebChromeClient(new android.webkit.WebChromeClient());
            web.loadUrl(url);
            stage("page requested");
        } catch (Throwable t) { stage("webview failed: " + t); stopSelf(); return; }
        h.postDelayed(loop, 5000);
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            tick++;
            sp.edit().putInt("svc_tick", tick).apply();
            try {
                web.onResume();
                web.resumeTimers();
                if (tick % 3 == 0) { loadedAt = 0; web.reload(); }   // ~6 sec
                else if (loadedAt > 0 && System.currentTimeMillis() - loadedAt > 1500) {
                    web.evaluateJavascript("(function(){return document.body?document.body.innerText:''})()", val -> {
                        try { onText(DashActivity.unquote(val)); } catch (Exception ignored) { }
                    });
                }
            } catch (Exception ignored) { }
            h.postDelayed(this, 2000);
        }
    };

    private void onText(String text) {
        boolean signedOut = web != null && web.getUrl() != null && (web.getUrl().contains("accounts.google.com") || web.getUrl().contains("/g4b/signup")) || (text.contains("Sign in") || text.contains("Sign-in")) && !text.contains("Transactions") && !text.contains("UPI transaction ID");
        ArrayList<String[]> tx = parseTx(text);
        boolean loaded = !signedOut && text.length() > 100;
        sp.edit().putLong("dash_try", System.currentTimeMillis()).putInt("dash_len", text.length()).putInt("dash_tx", tx.size()).apply();
        if (signedOut) {
            if (++signedOutTicks >= 3) {
                sp.edit().putString("cashier_state", "expired").apply();
                if (!warned) {
                    warned = true;
                    Notifier.warn(this, 31, "Cashier disconnected", "Open JS PAY and sign in again.");
                }
            }
            return;
        }
        signedOutTicks = 0;
        if (!loaded) return;
        warned = false;
        SharedPreferences.Editor ed = sp.edit().putLong("dash_ok", System.currentTimeMillis()).putLong("svc_ok", System.currentTimeMillis()).putString("cashier_state", "ok");
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
                NotifService.feed(this, "Google Pay", "₹" + t[1] + " received from " + nm + " at " + time12(t[3]), t[0]);
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
