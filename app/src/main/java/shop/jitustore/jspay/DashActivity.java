package shop.jitustore.jspay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dashboard Mode (Beta): Google Pay for Business ka web dashboard app ke andar kholta hai,
 *  aaj ki payments padhta hai, naya paisa aate hi server ko bhejta hai. Notification ki zarurat nahi. */
public class DashActivity extends Activity {
    static final String START = "https://pay.google.com/g4b/";
    static final Pattern ROW = Pattern.compile("₹\\s?([0-9,]+(?:\\.[0-9]{1,2})?)\\s+(Today|Yesterday|[0-9]{1,2}\\s+[A-Za-z]{3,9})\\s+([0-9]{1,2}:[0-9]{2})");
    static final Pattern CARD = Pattern.compile("Received\\s+₹\\s?([0-9,]+(?:\\.[0-9]{1,2})?)[\\s\\S]{0,80}?From\\s+([^\\n\\r]+)");

    private WebView web;
    private TextView status, info, btnSend;
    private final Handler h = new Handler(Looper.getMainLooper());
    private SharedPreferences sp;
    private boolean baselineDone = false;
    private final Map<String, Integer> seen = new LinkedHashMap<>();
    private int sentCount = 0, newCount = 0, tick = 0;
    private String lastText = "";
    private boolean running = true;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView tv(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    GradientDrawable box(int color, int r, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    TextView btn(String s, int bg, View.OnClickListener l) {
        TextView b = tv(s, 14, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(box(bg, 12, 0));
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { build(); }
        catch (Throwable e) {
            TextView t = new TextView(this);
            t.setBackgroundColor(Color.parseColor("#0B1020"));
            t.setTextColor(Color.WHITE);
            t.setPadding(dp(16), dp(40), dp(16), dp(16));
            t.setText("Dashboard khul nahi paya. Is screen ka screenshot bhej do:\n\n" + e);
            setContentView(t);
        }
    }

    void build() {
        sp = getSharedPreferences("c", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.parseColor("#0B1020"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B1020"));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(10), dp(14), dp(6));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.addView(tv("Dashboard Mode (Beta)", 16, Color.WHITE, true));
        status = tv("Page khul raha hai…", 11, Color.parseColor("#94A3B8"), false);
        status.setSingleLine(true);
        tt.addView(status);
        top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = btn("Band karo", Color.parseColor("#1E293B"), v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(96), dp(40)));
        root.addView(top);

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        try {
            String ua = ws.getUserAgentString();
            if (ua != null) ws.setUserAgentString(ua.replace("; wv", "").replace("Version/4.0 ", ""));
        } catch (Exception ignored) { }
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(web, true);
        } catch (Exception ignored) { }
        web.setWebViewClient(new WebViewClient());
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bot = new LinearLayout(this);
        bot.setOrientation(LinearLayout.VERTICAL);
        bot.setPadding(dp(14), dp(8), dp(14), dp(10));
        bot.setBackgroundColor(Color.parseColor("#0F172A"));
        info = tv("", 11, Color.parseColor("#CBD5E1"), false);
        info.setMaxLines(5);
        bot.addView(info);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(48));
        rp.topMargin = dp(8);
        row.setLayoutParams(rp);
        btnSend = btn("", Color.parseColor("#475569"), v -> toggleSend());
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -1, 2f);
        a.rightMargin = dp(6);
        row.addView(btnSend, a);
        TextView copy = btn("Page text copy", Color.parseColor("#1E293B"), v -> copyText());
        LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(0, -1, 1.2f);
        c2.leftMargin = dp(6);
        row.addView(copy, c2);
        bot.addView(row);
        root.addView(bot);
        setContentView(root);
        paintSend();

        String u = sp.getString("dash_url", START);
        web.loadUrl(u.startsWith("https://pay.google.com") ? u : START);
        h.postDelayed(loop, 3000);
    }

    void paintSend() {
        boolean on = sp.getBoolean("dash_send", false);
        btnSend.setText(on ? "Bhejna CHALU (dashboard se)" : "Bhejna BAND (sirf dekhna)");
        btnSend.setBackground(box(Color.parseColor(on ? "#16A34A" : "#475569"), 12, 0));
    }

    void toggleSend() {
        boolean on = !sp.getBoolean("dash_send", false);
        sp.edit().putBoolean("dash_send", on).apply();
        if (on) {
            Toast.makeText(this, "Ab payment dashboard se verify hogi, Notification se nahi", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Wapas Notification se verify hoga", Toast.LENGTH_LONG).show();
        }
        paintSend();
    }

    void copyText() {
        try {
            String t = lastText.length() > 2500 ? lastText.substring(0, 2500) : lastText;
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("page", t));
            Toast.makeText(this, "Copy ho gaya, chat mein paste kar do", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) { }
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            tick++;
            try {
                web.evaluateJavascript("(function(){return document.body?document.body.innerText:''})()", val -> {
                    try { onText(unquote(val)); } catch (Exception ignored) { }
                });
                if (tick % 12 == 0) web.reload(); // ~40 sec: dashboard ko taza rakho
            } catch (Exception ignored) { }
            h.postDelayed(this, 3500);
        }
    };

    static String unquote(String v) {
        if (v == null) return "";
        try { Object o = new org.json.JSONTokener(v).nextValue(); return o == null ? "" : String.valueOf(o); }
        catch (Exception e) { return ""; }
    }

    /** Page text se aaj ki rows: key "amount|HH:mm" -> kitni baar. */
    static Map<String, Integer> parseToday(String text) {
        Map<String, Integer> cur = new LinkedHashMap<>();
        Matcher m = ROW.matcher(text);
        while (m.find()) {
            if (!m.group(2).equals("Today")) continue;
            String amt = m.group(1).replace(",", "");
            String k = amt + "|" + m.group(3);
            cur.put(k, cur.containsKey(k) ? cur.get(k) + 1 : 1);
        }
        return cur;
    }

    static String time12(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            int hh = Integer.parseInt(p[0]);
            String ap = hh >= 12 ? "pm" : "am";
            int h12 = hh % 12 == 0 ? 12 : hh % 12;
            return h12 + ":" + p[1] + " " + ap;
        } catch (Exception e) { return hhmm; }
    }

    void onText(String text) {
        lastText = text;
        String url = "";
        try { url = web.getUrl() == null ? "" : web.getUrl(); } catch (Exception ignored) { }
        String shortUrl = url.replace("https://", "");
        if (shortUrl.length() > 60) shortUrl = shortUrl.substring(0, 60);
        boolean loaded = text.contains("Activity") && !text.contains("Sign in") && text.length() > 150;
        if (!loaded) {
            status.setText("Google se sign in karo, phir Activity page tak jao · " + shortUrl);
            info.setText("Dashboard abhi khula nahi. Menu (≡) se Activity ya Transactions kholo.");
            return;
        }
        if (url.startsWith("https://pay.google.com")) sp.edit().putString("dash_url", url).apply();
        Map<String, Integer> cur = parseToday(text);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        if (!baselineDone) {
            // pehli baar / naya din: jo payments pehle se dikh rahi hain unhe naya mat samjho
            seen.clear();
            String saved = sp.getString("dash_seen", "");
            if (saved.startsWith(today + ";")) {
                for (String part : saved.substring(today.length() + 1).split(";")) {
                    int e = part.lastIndexOf('=');
                    if (e > 0) { try { seen.put(part.substring(0, e), Integer.parseInt(part.substring(e + 1))); } catch (Exception ignored) { } }
                }
            }
            for (Map.Entry<String, Integer> e : cur.entrySet()) {
                Integer o = seen.get(e.getKey());
                if (o == null || o < e.getValue()) seen.put(e.getKey(), e.getValue());
            }
            baselineDone = true;
            save(today);
        } else {
            String sender = "Dashboard";
            Matcher cm = CARD.matcher(text);
            String cardAmt = "", cardName = "";
            if (cm.find()) { cardAmt = cm.group(1).replace(",", ""); cardName = cm.group(2).trim(); }
            java.util.ArrayList<String[]> fresh = new java.util.ArrayList<>();
            for (Map.Entry<String, Integer> e : cur.entrySet()) {
                int old = seen.containsKey(e.getKey()) ? seen.get(e.getKey()) : 0;
                for (int i = old; i < e.getValue(); i++) fresh.add(e.getKey().split("\\|"));
                if (e.getValue() > old) seen.put(e.getKey(), e.getValue());
            }
            if (!fresh.isEmpty()) {
                boolean send = sp.getBoolean("dash_send", false);
                for (int i = fresh.size() - 1; i >= 0; i--) {
                    String[] f = fresh.get(i);
                    newCount++;
                    String nm = f[0].equals(cardAmt) && !cardName.isEmpty() ? cardName : sender;
                    if (send) {
                        NotifService.feed(this, "Received", "₹" + f[0] + " received from " + nm + " at " + time12(f[1]));
                        sentCount++;
                    }
                }
                save(today);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Aaj ki rows: ").append(cur.size()).append("  ·  Naye mile: ").append(newCount).append("  ·  Bheje: ").append(sentCount).append("\n");
        int n = 0;
        for (String k : cur.keySet()) {
            if (n++ >= 4) break;
            String[] p = k.split("\\|");
            sb.append("₹").append(p[0]).append("  ").append(p[1]).append("   ");
        }
        info.setText(sb.toString());
        status.setText("Dashboard chal raha hai · " + shortUrl);
    }

    void save(String today) {
        StringBuilder sb = new StringBuilder(today).append(";");
        for (Map.Entry<String, Integer> e : seen.entrySet()) sb.append(e.getKey()).append("=").append(e.getValue()).append(";");
        sp.edit().putString("dash_seen", sb.toString()).apply();
    }

    @Override
    protected void onDestroy() {
        running = false;
        h.removeCallbacksAndMessages(null);
        try { web.destroy(); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
