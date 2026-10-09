package shop.jitustore.jspay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotifService extends NotificationListenerService {
    static final String URL_NOTIFY = "https://jitustore.shop/pay/gpay/notify.php";

    ConnectivityManager.NetworkCallback cb;
    static boolean online(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) { return true; }
    }

    void warn(boolean off) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (!off) { nm.cancel(4242); return; }
            if (getSharedPreferences("c", MODE_PRIVATE).getString("secret", "").length() < 5) return;
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel("net", "Internet warning", NotificationManager.IMPORTANCE_HIGH));
                b = new Notification.Builder(this, "net");
            } else {
                b = new Notification.Builder(this);
            }
            b.setSmallIcon(R.drawable.ic_launcher)
             .setContentTitle("Internet is OFF")
             .setContentText("Payments may be delayed. Turn on mobile data or Wi-Fi.")
             .setOngoing(true);
            nm.notify(4242, b.build());
        } catch (Exception ignored) { }
    }

    @Override
    public void onListenerConnected() {
        try {
            warn(!online(this));
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cb != null) cm.unregisterNetworkCallback(cb);
            cb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { warn(false); }
                @Override public void onLost(Network n) { warn(!online(NotifService.this)); }
            };
            cm.registerDefaultNetworkCallback(cb);
        } catch (Exception ignored) { }
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
            new Thread(() -> send(secret, title, text)).start();
        } catch (Exception ignored) { }
    }

    private void send(String secret, String title, String text) {
        int code = -1;
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
                getSharedPreferences("c", MODE_PRIVATE).edit()
                        .putLong("last", System.currentTimeMillis()).putInt("code", code).apply();
                if (code == 200 || code == 401) break;
            } catch (Exception ignored) { }
            try { Thread.sleep(5000L * (i + 1)); } catch (InterruptedException e) { break; }
        }
        addHistory(title, text, code);
    }

    private synchronized void addHistory(String title, String text, int code) {
        try {
            String all = title + " " + text;
            if (!all.contains("₹")) return;
            Matcher m = Pattern.compile("₹\\s?([0-9,]+(\\.[0-9]+)?)").matcher(all);
            String amount = m.find() ? m.group(1) : "";
            String sum = (title.isEmpty() ? text : title).replace("\n", " ").replace("|", " ");
            if (sum.length() > 70) sum = sum.substring(0, 70);
            String line = System.currentTimeMillis() + "|" + code + "|" + amount + "|" + sum;
            SharedPreferences sp = getSharedPreferences("c", MODE_PRIVATE);
            String old = sp.getString("hist", "");
            String[] parts = old.isEmpty() ? new String[0] : old.split("\n");
            StringBuilder sb = new StringBuilder(line);
            for (int i = 0; i < parts.length && i < 499; i++) sb.append("\n").append(parts[i]);
            sp.edit().putString("hist", sb.toString()).apply();
        } catch (Exception ignored) { }
    }
                    }
