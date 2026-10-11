package shop.jitustore.jspay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
    static final int BG = Color.parseColor("#0B1220");
    static final int CARD = Color.parseColor("#172033");
    static final int LINE = Color.parseColor("#2B3A55");
    static final int TXT = Color.parseColor("#E6EAF3");
    static final int MUTED = Color.parseColor("#93A0B8");
    static final int GREEN = Color.parseColor("#34D399");
    static final int RED = Color.parseColor("#F87171");

    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout listBox;
    private WebView web;
    private boolean webMode = false, navigated = false, finishing2 = false;
    private long backAt = 0, webStart = 0;
    private final Runnable listTick = new Runnable() {
        @Override public void run() { if (!webMode) { renderList(); h.postDelayed(this, 3000); } }
    };

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
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(14), dp(16), dp(14));
        head.setBackgroundColor(Color.parseColor("#111A2E"));
        ImageView back = new ImageView(this);
        back.setImageDrawable(new Ic("globe", TXT, dp(26)));
        head.addView(back, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView title = tv("Attached Cashier Logs", 20, Color.WHITE, true);
        title.setPadding(dp(14), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView help = new ImageView(this);
        help.setImageDrawable(new Ic("info", TXT, dp(24)));
        help.setOnClickListener(v -> showHelp());
        head.addView(help, new LinearLayout.LayoutParams(dp(28), dp(28)));
        col.addView(head);
        ScrollView sv = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(dp(14), dp(14), dp(14), dp(110));
        sv.addView(listBox);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout add = new LinearLayout(this);
        add.setOrientation(LinearLayout.HORIZONTAL);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(22), 0, dp(26), 0);
        add.setBackground(box(CARD, 18, LINE));
        ImageView ai = new ImageView(this);
        ai.setImageDrawable(new Ic("globe", TXT, dp(22)));
        add.addView(ai, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView at = tv("Add Cashier", 17, TXT, true);
        at.setPadding(dp(14), 0, 0, 0);
        add.addView(at);
        add.setOnClickListener(v -> askCode());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, dp(64));
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.setMargins(0, 0, dp(18), dp(26));
        root.addView(add, lp);
        setContentView(root);
        renderList();
        h.removeCallbacks(listTick);
        h.postDelayed(listTick, 3000);
    }

    void renderList() {
        if (listBox == null) return;
        listBox.removeAllViews();
        String url = sp.getString("cashier_url", "");
        if (url.isEmpty()) {
            TextView t = tv("No cashier attached yet.", 16, TXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(120), 0, dp(6));
            listBox.addView(t);
            TextView s = tv("Tap Add Cashier, enter the server code from your panel and sign in with Google.", 13, MUTED, false);
            s.setGravity(Gravity.CENTER);
            s.setPadding(dp(24), 0, dp(24), 0);
            listBox.addView(s);
            return;
        }
        boolean alive = System.currentTimeMillis() - sp.getLong("dash_ok", 0) < 90000L;
        boolean expired = sp.getString("cashier_state", "ok").equals("expired");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(box(CARD, 18, LINE));
        ImageView tile = new ImageView(this);
        tile.setScaleType(ImageView.ScaleType.CENTER);
        tile.setImageDrawable(new Ic("globe", TXT, dp(26)));
        tile.setBackground(box(Color.parseColor("#0F1626"), 16, 0));
        card.addView(tile, new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), 0, 0, 0);
        t.addView(tv("GooglePay Business", 17, Color.WHITE, true));
        long ts = sp.getLong("cashier_ok_ts", 0);
        t.addView(tv(new SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US).format(new Date(ts == 0 ? System.currentTimeMillis() : ts)), 14, MUTED, false));
        String st; int col;
        if (expired) { st = "Disconnected. Tap to sign in again"; col = RED; }
        else if (alive) { st = "Verification completed successfully"; col = GREEN; }
        else { st = "Connecting…"; col = Color.parseColor("#FBBF24"); }
        t.addView(tv(st, 14, col, false));
        card.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        card.setLayoutParams(cp);
        card.setOnClickListener(v -> { if (expired) startLogin(); else Toast.makeText(this, "Long press to remove this cashier", Toast.LENGTH_SHORT).show(); });
        card.setOnLongClickListener(v -> { askRemove(); return true; });
        listBox.addView(card);
        TextView note = tv("Payments are verified from your Google Pay for Business dashboard. Keep this phone online.", 12, MUTED, false);
        note.setPadding(dp(6), dp(14), dp(6), 0);
        listBox.addView(note);
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
        final String secret = sp.getString("secret", "");
        if (secret.length() < 5) {
            Toast.makeText(this, "Please log in with your Login Key first", Toast.LENGTH_LONG).show();
            return;
        }
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
        in.setBackground(box(Color.parseColor("#0F1626"), 14, LINE));
        box.addView(in, new LinearLayout.LayoutParams(-1, -2));
        final TextView check = tv("Check", 17, Color.WHITE, true);
        check.setGravity(Gravity.CENTER);
        check.setBackground(box(Color.parseColor("#1D4ED8"), 16, 0));
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
            String code = in.getText().toString().replaceAll("[^0-9]", "");
            if (code.length() != 12) return;
            if (!code.equals(serverCode(secret))) {
                Toast.makeText(this, "Invalid server code", Toast.LENGTH_LONG).show();
                return;
            }
            d.dismiss();
            startLogin();
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
        top.setBackgroundColor(Color.parseColor("#111A2E"));
        top.addView(tv("Complete Process...", 20, Color.parseColor("#CBD5E1"), true));
        top.addView(tv("Do not back press", 15, Color.parseColor("#B6C2D6"), false));
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
