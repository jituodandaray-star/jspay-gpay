package shop.jitustore.jspay;

import android.app.Notification;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class NotifService extends NotificationListenerService {
    static final String URL_NOTIFY = "https://jitustore.shop/pay/gpay/notify.php";

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
        for (int i = 0; i < 4; i++) {
            try {
                String body = "secret=" + URLEncoder.encode(secret, "UTF-8")
                        + "&title=" + URLEncoder.encode(title, "UTF-8")
                        + "&text=" + URLEncoder.encode(text, "UTF-8");
                HttpURLConnection c = (HttpURLConnection) new URL(URL_NOTIFY).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000); c.setReadTimeout(15000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                OutputStream o = c.getOutputStream(); o.write(body.getBytes("UTF-8")); o.close();
                int code = c.getResponseCode();
                getSharedPreferences("c", MODE_PRIVATE).edit()
                        .putLong("last", System.currentTimeMillis()).putInt("code", code).apply();
                if (code == 200 || code == 401) return;
            } catch (Exception ignored) { }
            try { Thread.sleep(5000L * (i + 1)); } catch (InterruptedException e) { return; }
        }
    }
              }
