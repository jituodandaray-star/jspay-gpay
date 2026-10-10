package shop.jitustore.jspay;

import android.app.Notification;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotifService extends NotificationListenerService {
    static final String URL_NOTIFY = "https://jitustore.shop/pay/gpay/notify.php";

    static boolean online(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) { return true; }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if (!pkg.startsWith("com.google.android.apps.nbu.paisa")) return;
            SharedPreferences sp = getSharedPreferences("c", MODE_PRIVATE);
            final String secret = sp.getString("secret", "");
            if (secret.length() < 5) return;
            Notification n = sbn.getNotification();
            CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence x = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (x == null) x = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            final String title = t == null ? "" : t.toString();
            final String text = x == null ? "" : x.toString();
            if (title.isEmpty() && text.isEmpty()) return;
            final long pt = sbn.getPostTime();
            new Thread(() -> send(secret, title, text, pt)).start();
        } catch (Exception ignored) { }
    }

    private void send(String secret, String title, String text, long postTime) {
        int code = -1;
        String res = "";
        String exp = "";
        for (int i = 0; i < 4; i++) {
            try {
                String body = "secret=" + URLEncoder.encode(secret, "UTF-8")
                        + "&title=" + URLEncoder.encode(title, "UTF-8")
                        + "&text=" + URLEncoder.encode(text, "UTF-8");
                HttpURLConnection c = (HttpURLConnection) new URL(URL_NOTIFY).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                OutputStream o = c.getOutputStream();
                o.write(body.getBytes("UTF-8"));
                o.close();
                code = c.getResponseCode();
                if (code == 200) {
                    try {
                        InputStream in = c.getInputStream();
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[512]; int nr;
                        while ((nr = in.read(buf)) > 0 && bo.size() < 4096) bo.write(buf, 0, nr);
                        String body2 = bo.toString("UTF-8");
                        Matcher rm = Pattern.compile("\"result\"\\s*:\\s*\"([a-z_]+)\"").matcher(body2);
                        if (rm.find()) res = rm.group(1);
                        Matcher em = Pattern.compile("\"expected\"\\s*:\\s*\"?([0-9.]+)\"?").matcher(body2);
                        if (em.find()) exp = em.group(1);
                    } catch (Exception ignored) { }
                }
                getSharedPreferences("c", MODE_PRIVATE).edit()
                        .putLong("last", System.currentTimeMillis()).putInt("code", code).apply();
                if (code == 200 || code == 401) break;
            } catch (Exception ignored) { }
            try { Thread.sleep(5000L * (i + 1)); } catch (InterruptedException e) { break; }
        }
        addHistory(title, text, code, postTime, res, exp);
    }

    private synchronized void addHistory(String title, String text, int code, long postTime, String res, String exp) {
        try {
            String all = title + " " + text;
            if (!all.contains("₹")) return;
            Matcher m = Pattern.compile("₹\\s?([0-9,]+(\\.[0-9]+)?)").matcher(all);
            String amount = m.find() ? m.group(1) : "";
            String sum = (title.isEmpty() ? text : title).replace("\n", " ").replace("|", " ");
            if (sum.length() > 70) sum = sum.substring(0, 70);
            String key = title + "#" + text;
            long ts = postTime > 0 ? postTime : System.currentTimeMillis();
            SharedPreferences sp0 = getSharedPreferences("c", MODE_PRIVATE);
            String lk = sp0.getString("lastkey", "");
            long lt = sp0.getLong("lastkeyt", 0);
            if (key.equals(lk) && Math.abs(ts - lt) < 120000L) return; // same notification dobara: list mein mat dikhao
            sp0.edit().putString("lastkey", key).putLong("lastkeyt", ts).apply();
            String line = ts + "|" + code + "|" + amount + "|" + sum + "|" + res + "|" + exp;
            SharedPreferences sp = getSharedPreferences("c", MODE_PRIVATE);
            String old = sp.getString("hist", "");
            String[] parts = old.isEmpty() ? new String[0] : old.split("\n");
            StringBuilder sb = new StringBuilder(line);
            for (int i = 0; i < parts.length && i < 499; i++) sb.append("\n").append(parts[i]);
            sp.edit().putString("hist", sb.toString()).apply();
        } catch (Exception ignored) { }
    }
}
