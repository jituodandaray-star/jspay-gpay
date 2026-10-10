package shop.jitustore.jspay;

import android.app.Notification;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.service.notification.NotificationListenerService;
import android.speech.tts.TextToSpeech;
import android.service.notification.StatusBarNotification;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotifService extends NotificationListenerService {
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final java.util.ArrayList<String[]> pendingSay = new java.util.ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            tts = new TextToSpeech(getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        tts.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    } catch (Exception ignored) { }
                    synchronized (pendingSay) {
                        ttsReady = true;
                        for (String[] p : pendingSay) speakNow(p[0], p[1], p[2], p[3]);
                        pendingSay.clear();
                    }
                }
            });
        } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() {
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Exception ignored) { }
        super.onDestroy();
    }

    private void speak(String kind, String amount, String exp, String gw) {
        SharedPreferences sp = getSharedPreferences("c", MODE_PRIVATE);
        if (!sp.getBoolean("voice_on", true)) return;
        boolean warn = kind.equals("wrong") || kind.equals("noorder") || kind.equals("failed");
        if (sp.getString("voice_mode", "all").equals("warn") && !warn) return;
        synchronized (pendingSay) {
            if (!ttsReady) { pendingSay.add(new String[]{kind, amount, exp, gw}); return; }
        }
        speakNow(kind, amount, exp, gw);
    }

    private void speakNow(String kind, String amount, String exp, String gw) {
        try {
            SharedPreferences sp = getSharedPreferences("c", MODE_PRIVATE);
            String lang = Voice.pick(tts, sp);
            tts.speak(Voice.say(kind, lang, amount, exp, gw), TextToSpeech.QUEUE_ADD, null, "jspay" + System.nanoTime());
        } catch (Exception ignored) { }
    }

    static final String URL_NOTIFY = Sx.NOTIFY;

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
            if (!pkg.startsWith("com.google.android.apps.nbu.paisa") && !pkg.toLowerCase().contains("bharatpe")) return;
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
            if (!all.contains("₹") && !Pattern.compile("(?i)\\bRs\\.?\\s?[0-9]").matcher(all).find()) return;
            Matcher m = Pattern.compile("(?:₹|Rs\\.?|INR)\\s?([0-9,]+(\\.[0-9]+)?)").matcher(all);
            String amount = m.find() ? m.group(1) : "";
            boolean isBp = Pattern.compile("(?is)received\\s+(?:rs|₹|inr).*from").matcher(all).find() || all.toLowerCase().contains("settled");
            String gw = isBp ? "bp" : "gp";
            String basis = title.toLowerCase().contains(" from ") ? title : (text.toLowerCase().contains(" from ") ? text : (title.isEmpty() ? text : title));
            String sum = basis.replace("\n", " ").replace("|", " ");
            if (sum.length() > 110) sum = sum.substring(0, 110);
            if (isBp) sum = "[BP] " + sum;
            String key = title + "#" + text;
            long ts = postTime > 0 ? postTime : System.currentTimeMillis();
            SharedPreferences sp0 = getSharedPreferences("c", MODE_PRIVATE);
            String lk = sp0.getString("lastkey", "");
            long lt = sp0.getLong("lastkeyt", 0);
            if (key.equals(lk) && Math.abs(ts - lt) < 120000L) return; // same notification dobara: list mein mat dikhao
            sp0.edit().putString("lastkey", key).putLong("lastkeyt", ts).apply();
            String who = Notifier.who(sum);
            if (code != 200) {
                speak("failed", amount, exp, gw);
                MainActivity.popup("failed", amount, gw, who, exp);
                Notifier.warn(this, 21, online(this) ? "Server tak nahi pahunchi" : "Internet band hai", "\u20B9" + amount + " ki payment verify nahi hui. Internet check karo.");
            }
            else if (res.equals("paid")) { speak("paid", amount, exp, gw); MainActivity.popup("paid", amount, gw, who, exp); Notifier.pay(this, amount, gw, who); }
            else if (res.equals("wrong")) { speak("wrong", amount, exp, gw); MainActivity.popup("wrong", amount, gw, who, exp); Notifier.warn(this, 22, "Galat amount \u00B7 " + Notifier.gwName(gw), "\u20B9" + amount + " aaye, order \u20B9" + exp + " ka tha"); }
            else if (res.equals("no_order")) { speak("noorder", amount, exp, gw); MainActivity.popup("noorder", amount, gw, who, exp); Notifier.warn(this, 23, "Bina order ka paisa \u00B7 " + Notifier.gwName(gw), "\u20B9" + amount + " aaye, par koi order nahi tha"); }
            else if (res.equals("expired")) { Plan.markExpired(this); speak("expired", amount, exp, gw); MainActivity.popup("expired", amount, gw, who, exp); Notifier.warn(this, 24, "Premium khatam", "Payment verify nahi hui. Renew karo."); }
            else if (!res.equals("duplicate") && !res.equals("ignored")) speak("plain", amount, exp, gw);
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
