package shop.jitustore.jspay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Attached Cashier: list screen + Add Cashier (server code) + Google sign-in screen. Sab kuch English mein. */
public class DashActivity extends Activity {
    static final String START = "https://pay.google.com/g4b/";
    static final int BG = Color.parseColor("#0B1020");
    static final int CARD = Color.parseColor("#141B33");
    static final int LINE = Color.parseColor("#26305A");
    static final int PRIMARY = Color.parseColor("#6366F1");
    static final int TXT = Color.parseColor("#F1F5F9");
    static final int MUTED = Color.parseColor("#94A3B8");
    static final int GREEN = Color.parseColor("#4ADE80");
    static final int RED = Color.parseColor("#F87171");
    static final int SOFT = Color.parseColor("#A5B4FC");

    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout listBox;
    private WebView web;
    private boolean webMode = false, navigated = false, finishing2 = false;
    private long backAt = 0, webStart = 0;
    private final Runnable listTick = new Runnable() {
        @Override public void run() { if (!webMode) { watchdog(); renderList(); h.postDelayed(this, 3000); } }
    };

    long lastKick = 0;
    /** Background service ko zinda rakho: band ho gaya ya heartbeat purana ho gaya to dobara shuru karo. */
    void watchdog() {
        try {
            if (sp.getString("cashier_url", "").isEmpty()) return;
            long now = System.currentTimeMillis();
            if (now - sp.getLong("svc_ok", 0) > 120000L && now - lastKick > 150000L) {
                lastKick = now;
                DashService.stop(this);
            }
            DashService.ensure(this);
        } catch (Exception ignored) { }
    }

    @Override protected void onResume() { super.onResume(); if (!webMode) watchdog(); }

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

    /** Server code: login key se nikla 12 ank (panel ke same formula se). */
    static String serverCode(String secret) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(("JSPAY-CASHIER|" + secret).getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : d) hex.append(String.format("%02x", b));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                int a = Character.digit(hex.charAt(i * 2), 16), b2 = Character.digit(hex.charAt(i * 2 + 1), 16);
                out.append((a + b2) % 10);
            }
            return out.toString();
        } catch (Exception e) { return ""; }
    }

    static String unquote(String v) {
        if (v == null) return "";
        try { Object o = new org.json.JSONTokener(v).nextValue(); return o == null ? "" : String.valueOf(o); }
        catch (Exception e) { return ""; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("c", MODE_PRIVATE);
        try { getWindow().setStatusBarColor(BG); } catch (Exception ignored) { }
        try { showList(); }
        catch (Throwable e) {
            TextView t = new TextView(this);
            t.setBackgroundColor(BG);
            t.setTextColor(Color.WHITE);
            t.setPadding(dp(16), dp(40), dp(16), dp(16));
            t.setText("Could not open. Please send a screenshot of this screen:\n\n" + e);
            setContentView(t);
        }
    }

    // ---------------------------------------------------------------- list screen
    void showList() {
        webMode = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        MainActivity.BgView bgv = new MainActivity.BgView(this);
        root.addView(bgv, new FrameLayout.LayoutParams(-1, -1));
        try { bgv.go(); } catch (Throwable ignored) { }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(36), dp(20), dp(8));
        TextView logo = tv("JS", 20, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable lg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#6366F1"), Color.parseColor("#8B5CF6")});
        lg.setCornerRadius(dp(16));
        logo.setBackground(lg);
        head.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(14), 0, 0, 0);
        names.addView(tv("JS PAY", 22, TXT, true));
        names.addView(tv("Attached Cashier", 13, MUTED, false));
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.VERTICAL);
        dots.setGravity(Gravity.CENTER);
        dots.setBackground(box(Color.parseColor("#1E2748"), 14, LINE));
        for (int i = 0; i < 3; i++) {
            View d = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(Color.WHITE);
            d.setBackground(dg);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(5), dp(5));
            dl.topMargin = i == 0 ? 0 : dp(4);
            dots.addView(d, dl);
        }
        dots.setOnClickListener(v -> openDetails());
        head.addView(dots, new LinearLayout.LayoutParams(dp(44), dp(44)));
        col.addView(head);
        ScrollView sv = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(dp(20), dp(8), dp(20), dp(120));
        sv.addView(listBox);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView add = tv("Add Cashier", 16, Color.WHITE, true);
        add.setGravity(Gravity.CENTER);
        GradientDrawable ag = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#6366F1"), Color.parseColor("#8B5CF6")});
        ag.setCornerRadius(dp(16));
        add.setBackground(ag);
        add.setOnClickListener(v -> askCode());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(56));
        lp.gravity = Gravity.BOTTOM;
        lp.setMargins(dp(20), 0, dp(20), dp(26));
        root.addView(add, lp);
        setContentView(root);
        renderList();
        h.removeCallbacks(listTick);
        h.postDelayed(listTick, 3000);
    }

    void openDetails() {
        try {
            Intent in = new Intent(this, MainActivity.class);
            in.putExtra("details", true);
            startActivity(in);
        } catch (Throwable e) { Toast.makeText(this, "Could not open details", Toast.LENGTH_SHORT).show(); }
    }

    void renderList() {
        if (listBox == null) return;
        listBox.removeAllViews();
        String url = sp.getString("cashier_url", "");
        if (url.isEmpty()) {
            TextView t = tv("No cashier added yet", 18, TXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(110), 0, dp(8));
            listBox.addView(t);
            TextView s = tv("Tap Add Cashier, enter the server code from your panel and sign in with Google. Payments are then verified automatically.", 13, MUTED, false);
            s.setGravity(Gravity.CENTER);
            s.setPadding(dp(24), 0, dp(24), 0);
            listBox.addView(s);
            return;
        }
        boolean alive = System.currentTimeMillis() - sp.getLong("svc_ok", 0) < 180000L || System.currentTimeMillis() - sp.getLong("cashier_ok_ts", 0) < 60000L;
        boolean expired = sp.getString("cashier_state", "ok").equals("expired");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(box(CARD, 18, LINE));
        ImageView tile = new ImageView(this);
        tile.setScaleType(ImageView.ScaleType.CENTER);
        tile.setImageDrawable(new Ic("shield", TXT, dp(26)));
        tile.setBackground(box(Color.parseColor("#1E2748"), 16, 0));
        card.addView(tile, new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), 0, 0, 0);
        t.addView(tv("Google Pay Business", 17, Color.WHITE, true));
        long ts = sp.getLong("cashier_ok_ts", 0);
        t.addView(tv(new SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US).format(new Date(ts == 0 ? System.currentTimeMillis() : ts)), 14, MUTED, false));
        String st; int col;
        if (expired) { st = "Disconnected. Tap to sign in again"; col = RED; }
        else if (alive) { st = "Verification completed successfully"; col = GREEN; }
        else { st = "Connecting..."; col = SOFT; }
        t.addView(tv(st, 14, col, false));
        card.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(14);
        card.setLayoutParams(cp);
        card.setOnClickListener(v -> { if (expired) startLogin(); else Toast.makeText(this, "Long press to remove this cashier", Toast.LENGTH_SHORT).show(); });
        card.setOnLongClickListener(v -> { askRemove(); return true; });
        listBox.addView(card);
        TextView note = tv("Payments are verified from your Google Pay for Business dashboard. Keep this phone online.", 12, MUTED, false);
        note.setPadding(dp(6), dp(14), dp(6), 0);
        listBox.addView(note);
        long pg = sp.getLong("dash_page", 0), tr = sp.getLong("dash_try", 0);
        TextView dbg = tv("Last page load: " + (pg == 0 ? "never" : new SimpleDateFormat("hh:mm:ss a", Locale.US).format(new Date(pg)))
                + "\nLast read: " + (tr == 0 ? "never" : new SimpleDateFormat("hh:mm:ss a", Locale.US).format(new Date(tr)) + "  (" + sp.getInt("dash_len", 0) + " chars, " + sp.getInt("dash_tx", 0) + " payments)")
                + "\nService: " + sp.getString("svc_stage", "not started") + (sp.getLong("svc_req", 0) > 0 ? " [start requested " + new SimpleDateFormat("hh:mm:ss a", Locale.US).format(new Date(sp.getLong("svc_req", 0))) + "]" : " [no start request]") + (sp.getLong("svc_ts", 0) == 0 ? "" : " at " + new SimpleDateFormat("hh:mm:ss a", Locale.US).format(new Date(sp.getLong("svc_ts", 0)))) + "  (ticks " + sp.getInt("svc_tick", 0) + ")"
                + (sp.getString("dash_err", "").isEmpty() ? "" : "\nError: " + sp.getString("dash_err", "")), 11, Color.parseColor("#64748B"), false);
        dbg.setPadding(dp(6), dp(10), dp(6), 0);
        listBox.addView(dbg);
    }

    void showHelp() {
        new AlertDialog.Builder(this).setTitle("How it works")
                .setMessage("1. Open your panel and copy the Cashier server code.\n2. Tap Add Cashier and enter the code.\n3. Sign in with the Google account of your Google Pay for Business.\n\nAfter that every payment is read from your Google dashboard and verified automatically. Nothing else to do.")
                .setPositiveButton("OK", null).show();
    }

    void askRemove() {
        new AlertDialog.Builder(this).setTitle("Remove cashier?")
                .setMessage("Automatic verification from the Google dashboard will stop.")
                .setPositiveButton("Remove", (d, w) -> {
                    DashService.stop(this);
                    sp.edit().remove("cashier_url").remove("cashier_ok_ts").remove("cashier_seen").remove("cashier_base").remove("dash_ok").remove("cashier_state").apply();
                    try { CookieManager.getInstance().removeAllCookies(null); } catch (Exception ignored) { }
                    renderList();
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ---------------------------------------------------------------- Add Cashier dialog
    void askCode() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(22));
        box.setBackground(box(CARD, 22, LINE));
        TextView t = tv("Require", 20, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView l = tv("Enter server code", 15, TXT, false);
        l.setPadding(0, dp(22), 0, dp(8));
        box.addView(l);
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setHint("Enter 12-digit server code");
        in.setHintTextColor(MUTED);
        in.setTextColor(Color.WHITE);
        in.setTextSize(17);
        in.setSingleLine(true);
        in.setPadding(dp(14), dp(12), dp(14), dp(12));
        in.setBackground(box(Color.parseColor("#0B1020"), 14, LINE));
        box.addView(in, new LinearLayout.LayoutParams(-1, -2));
        final TextView check = tv("Check", 17, Color.WHITE, true);
        check.setGravity(Gravity.CENTER);
        check.setBackground(box(PRIMARY, 16, 0));
        check.setAlpha(0.5f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(150), dp(52));
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        cp.topMargin = dp(20);
        box.addView(check, cp);
        in.addTextChangedListener(new TextWatcher() {
            boolean busy = false;
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) {
                if (busy) return;
                busy = true;
                String digits = e.toString().replaceAll("[^0-9]", "");
                if (digits.length() > 12) digits = digits.substring(0, 12);
                StringBuilder f = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) { if (i > 0 && i % 3 == 0) f.append(' '); f.append(digits.charAt(i)); }
                e.replace(0, e.length(), f.toString());
                check.setAlpha(digits.length() == 12 ? 1f : 0.5f);
                busy = false;
            }
        });
        check.setOnClickListener(v -> {
            final String code = in.getText().toString().replaceAll("[^0-9]", "");
            if (code.length() != 12) return;
            check.setText("Checking...");
            check.setEnabled(false);
            new Thread(() -> {
                String secret = ""; String msg = "";
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(NotifService.URL_NOTIFY.replace("notify.php", "cashier.php")).openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(12000);
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    java.io.OutputStream o = c.getOutputStream();
                    o.write(("code=" + code).getBytes("UTF-8"));
                    o.close();
                    int rc = c.getResponseCode();
                    java.io.InputStream is = rc >= 400 ? c.getErrorStream() : c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[1024]; int n;
                    while (is != null && (n = is.read(buf)) > 0) bo.write(buf, 0, n);
                    org.json.JSONObject j = new org.json.JSONObject(bo.toString("UTF-8"));
                    if (rc == 200 && "ok".equals(j.optString("status"))) secret = j.optString("secret");
                    else msg = rc == 429 ? "Too many attempts. Try again later." : "Invalid server code";
                } catch (Exception e) { msg = "No internet. Please try again."; }
                final String fs = secret, fm = msg;
                runOnUiThread(() -> {
                    check.setText("Check");
                    check.setEnabled(true);
                    if (fs.length() < 5) { Toast.makeText(this, fm.isEmpty() ? "Invalid server code" : fm, Toast.LENGTH_LONG).show(); return; }
                    sp.edit().putString("secret", fs).putBoolean("verified", true).apply();
                    d.dismiss();
                    startLogin();
                });
            }).start();
        });
        d.setContentView(box);
        d.show();
        try {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f), -2);
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- Google sign-in screen
    void startLogin() {
        h.removeCallbacks(listTick);
        webMode = true;
        navigated = false;
        finishing2 = false;
        webStart = System.currentTimeMillis();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(16), dp(16), dp(16), dp(14));
        top.setBackgroundColor(Color.parseColor("#141B33"));
        top.addView(tv("Complete Process...", 20, Color.WHITE, true));
        top.addView(tv("Do not press back", 14, SOFT, false));
        root.addView(top);
        web = new WebView(this);
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
        web.setWebViewClient(new WebViewClient());
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        web.loadUrl(START);
        h.postDelayed(webLoop, 2000);
    }

    private final Runnable webLoop = new Runnable() {
        @Override public void run() {
            if (!webMode || web == null) return;
            try {
                web.evaluateJavascript("(function(){return document.body?document.body.innerText:''})()", val -> {
                    try { onWebText(unquote(val)); } catch (Exception ignored) { }
                });
            } catch (Exception ignored) { }
            h.postDelayed(this, 1500);
        }
    };

    void onWebText(String text) {
        if (!webMode || finishing2) return;
        String url = "";
        try { url = web.getUrl() == null ? "" : web.getUrl(); } catch (Exception ignored) { }
        boolean signedIn = !text.contains("Sign in") && text.length() > 100;
        if (url.contains("/g4b/activity") && signedIn && !navigated) {
            navigated = true;
            web.loadUrl(url.replace("/g4b/activity", "/g4b/transactions"));   // Step 2: Transactions tab (automatic)
            return;
        }
        if (url.contains("/g4b/transactions") && signedIn) {
            ArrayList<String[]> tx = DashService.parseTx(text);
            if (!tx.isEmpty() || text.toLowerCase().contains("no transactions")) {
                finishing2 = true;
                StringBuilder seen = new StringBuilder();
                for (String[] t : tx) seen.append(t[0]).append("\n");
                sp.edit().putString("cashier_url", url).putLong("cashier_ok_ts", System.currentTimeMillis())
                        .putString("cashier_seen", seen.toString()).putBoolean("cashier_base", true)
                        .putLong("dash_ok", System.currentTimeMillis()).putString("cashier_state", "ok").apply();
                DashService.stop(this);
                DashService.ensure(this);
                Toast.makeText(this, "Verification completed successfully", Toast.LENGTH_LONG).show();
                leaveWeb();
                return;
            }
        }
        if (navigated && System.currentTimeMillis() - webStart > 60000 && !url.contains("/g4b/transactions")) {
            // Transactions page khul nahi raha: menu se kholne ko bolo
            Toast.makeText(this, "Open the menu and tap Transactions", Toast.LENGTH_LONG).show();
            webStart = System.currentTimeMillis();
        }
    }

    void leaveWeb() {
        webMode = false;
        h.removeCallbacks(webLoop);
        try { if (web != null) web.destroy(); } catch (Exception ignored) { }
        web = null;
        showList();
    }

    @Override
    public void onBackPressed() {
        if (webMode) {
            long now = System.currentTimeMillis();
            if (now - backAt < 1500) { leaveWeb(); return; }
            backAt = now;
            Toast.makeText(this, "Do not press back. Press again to cancel.", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        try { if (web != null) web.destroy(); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
