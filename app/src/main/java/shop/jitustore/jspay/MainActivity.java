package shop.jitustore.jspay;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int BG = Color.parseColor("#0B1020");
    static final int CARD = Color.parseColor("#141B33");
    static final int LINE = Color.parseColor("#26305A");
    static final int PRIMARY = Color.parseColor("#6366F1");
    static final int GOOD = Color.parseColor("#16A34A");
    static final int BAD = Color.parseColor("#DC2626");
    static final int TXT = Color.parseColor("#F1F5F9");
    static final int MUTED = Color.parseColor("#94A3B8");

    SharedPreferences sp;
    LinearLayout loginBox, dashBox;
    EditText key;
    LinearLayout histBox;
    ScrollView histScroll;
    TextView histTotal;
    boolean wasVerified = false;
    TextView pill, loginMsg, loginBtn, chipKey, chipAccess, chipBatt, chipNet, rowLast;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable box(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(box(CARD, 18, LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    TextView button(String label, boolean filled, View.OnClickListener l) {
        TextView b = text(label, 15, filled ? Color.WHITE : TXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(filled ? box(PRIMARY, 14, 0) : box(Color.TRANSPARENT, 14, LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        b.setOnTouchListener((v, ev) -> {
            int a = ev.getAction();
            if (a == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            return false;
        });
        return b;
    }

    TextView chip() {
        TextView t = text("", 12, Color.WHITE, true);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        return t;
    }

    LinearLayout row(String label, TextView chipView, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(12), 0, dp(4));
        TextView t = text(label, 15, TXT, false);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(chipView);
        r.setOnClickListener(l);
        return r;
    }

    void setChip(TextView t, boolean ok, String onText, String offText) {
        t.setText(ok ? onText : offText);
        t.setBackground(box(ok ? GOOD : BAD, 20, 0));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("c", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(36), dp(20), dp(28));
        sv.addView(root);

        // ---- header ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("JS", 20, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable lg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#6366F1"), Color.parseColor("#8B5CF6")});
        lg.setCornerRadius(dp(16));
        logo.setBackground(lg);
        logo.setLayoutParams(new LinearLayout.LayoutParams(dp(54), dp(54)));
        head.addView(logo);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(14), 0, 0, 0);
        names.addView(text("JS PAY", 22, TXT, true));
        names.addView(text("GPay Auto Verify", 13, MUTED, false));
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        pill = chip();
        head.addView(pill);
        root.addView(head);

        TextView tag = text("Payments verified instantly. Zero manual work.", 14, MUTED, false);
        tag.setPadding(0, dp(16), 0, 0);
        root.addView(tag);

        // ---- LOGIN BOX ----
        loginBox = new LinearLayout(this);
        loginBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout lc = card();
        lc.addView(text("LOGIN", 11, MUTED, true));
        TextView lt = text("Enter your Login Key", 20, TXT, true);
        lt.setPadding(0, dp(8), 0, 0);
        lc.addView(lt);
        TextView hint = text("Find your Login Key in the panel under GPay > Phone Setup. Every account has its own unique key.", 13, MUTED, false);
        hint.setPadding(0, dp(6), 0, dp(12));
        lc.addView(hint);
        key = new EditText(this);
        key.setHint("GN_xxxxxxxxxxxxxxxx");
        key.setHintTextColor(Color.parseColor("#64748B"));
        key.setTextColor(TXT);
        key.setTextSize(14);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setBackground(box(Color.parseColor("#0B1020"), 12, LINE));
        key.setPadding(dp(14), dp(12), dp(14), dp(12));
        lc.addView(key, new LinearLayout.LayoutParams(-1, -2));
        loginMsg = text("", 13, Color.parseColor("#F87171"), false);
        loginMsg.setPadding(0, dp(8), 0, 0);
        lc.addView(loginMsg);
        loginBtn = button("Login", true, v -> {
            String k = key.getText().toString().trim();
            if (k.length() < 5) { loginMsg.setText("Please enter your Login Key"); return; }
            loginMsg.setText("");
            verify(k, true);
        });
        lc.addView(loginBtn);
        loginBox.addView(lc);
        root.addView(loginBox);

        // ---- DASHBOARD BOX ----
        dashBox = new LinearLayout(this);
        dashBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout st = card();
        st.addView(text("STATUS", 11, MUTED, true));
        chipKey = chip();
        chipAccess = chip();
        chipBatt = chip();
        chipNet = chip();
        st.addView(row("Login Key", chipKey, null));
        st.addView(row("Notification Access", chipAccess, v -> {
            if (!notifOn()) startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        }));
        st.addView(row("Background / Battery", chipBatt, v -> {
            if (!battOk()) fixBattery();
        }));
        st.addView(row("Internet", chipNet, v -> startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS))));
        rowLast = text("", 13, MUTED, false);
        rowLast.setPadding(0, dp(14), 0, 0);
        st.addView(rowLast);
        TextView tapHint = text("Tap anything in red to turn it on.", 12, Color.parseColor("#64748B"), false);
        tapHint.setPadding(0, dp(8), 0, 0);
        st.addView(tapHint);
        dashBox.addView(st);

        LinearLayout hc = card();
        hc.addView(text("PAYMENT HISTORY", 11, MUTED, true));
        histTotal = text("", 13, MUTED, false);
        histTotal.setPadding(0, dp(6), 0, dp(4));
        hc.addView(histTotal);
        histScroll = new ScrollView(this);
        histScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        histBox = new LinearLayout(this);
        histBox.setOrientation(LinearLayout.VERTICAL);
        histScroll.addView(histBox);
        histScroll.setOnTouchListener((v, ev) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        hc.addView(histScroll, new LinearLayout.LayoutParams(-1, dp(380)));
        dashBox.addView(hc);

        LinearLayout ac = card();
        ac.addView(button("Connection test", false, v -> verify(sp.getString("secret", ""), false)));
        ac.addView(button("Logout", false, v -> {
            sp.edit().remove("secret").remove("verified").apply();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            refresh();
        }));
        dashBox.addView(ac);
        root.addView(dashBox);

        // ---- footer ----
        TextView panel = text("Open Panel  ›", 14, PRIMARY, true);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(0, dp(22), 0, dp(6));
        panel.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://jitustore.shop/pay/auth/gpay.php"))));
        root.addView(panel);
        TextView foot = text("Secured by JS PAY  •  v1.3", 12, Color.parseColor("#64748B"), false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot);

        setContentView(sv);
        for (int i = 0; i < root.getChildCount(); i++) {
            View ch = root.getChildAt(i);
            ch.setAlpha(0f);
            ch.setTranslationY(dp(26));
            ch.animate().alpha(1f).translationY(0f).setDuration(480).setStartDelay(90L * i).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7); } catch (Exception ignored) { }
        }
        refresh();
    }

    boolean notifOn() {
        String en = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return !TextUtils.isEmpty(en) && en.contains(getPackageName());
    }

    boolean battOk() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean ignoring = pm.isIgnoringBatteryOptimizations(getPackageName());
            boolean restricted = false;
            if (Build.VERSION.SDK_INT >= 28) {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                restricted = am.isBackgroundRestricted();
            }
            return ignoring && !restricted;
        } catch (Exception e) {
            return false;
        }
    }

    void fixBattery() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    void verify(final String k, final boolean fromLogin) {
        if (fromLogin) loginBtn.setText("Checking...");
        new Thread(() -> {
            int code = -1;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(NotifService.URL_NOTIFY).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                byte[] body = ("secret=" + URLEncoder.encode(k, "UTF-8")).getBytes("UTF-8");
                OutputStream o = c.getOutputStream();
                o.write(body);
                o.close();
                code = c.getResponseCode();
            } catch (Exception e) {
                code = -1;
            }
            final int rc = code;
            runOnUiThread(() -> onVerified(k, rc, fromLogin));
        }).start();
    }

    void onVerified(String k, int rc, boolean fromLogin) {
        loginBtn.setText("Login");
        boolean valid = (rc == 400 || rc == 200);
        if (valid) {
            sp.edit().putString("secret", k).putBoolean("verified", true).apply();
            if (!fromLogin) Toast.makeText(this, "Connection OK ✓", Toast.LENGTH_SHORT).show();
        } else if (rc == 401) {
            sp.edit().remove("secret").remove("verified").apply();
            if (fromLogin) loginMsg.setText("Invalid Login Key. Copy it again from the panel.");
            else Toast.makeText(this, "Login Key is invalid, please log in again", Toast.LENGTH_LONG).show();
        } else if (rc == -1) {
            if (fromLogin) loginMsg.setText("Check your internet and try again.");
            else Toast.makeText(this, "No internet", Toast.LENGTH_SHORT).show();
        } else {
            if (fromLogin) loginMsg.setText("Server error (" + rc + "). Try again later.");
            else Toast.makeText(this, "Server error (" + rc + ")", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    void renderHistory() {
        histBox.removeAllViews();
        String h = sp.getString("hist", "");
        if (h.isEmpty()) {
            TextView e = text("No payments yet.", 13, MUTED, false);
            e.setPadding(0, dp(10), 0, 0);
            histBox.addView(e);
            histTotal.setText("");
            return;
        }
        String[] lines = h.split("\n");
        int count = 0;
        double total = 0;
        for (int i = 0; i < lines.length; i++) {
            String[] f = lines[i].split("\\|", 4);
            if (f.length < 4) continue;
            long ts = 0;
            int code = -1;
            try { ts = Long.parseLong(f[0]); code = Integer.parseInt(f[1]); } catch (Exception ignored) { }
            boolean ok = (code == 200);
            count++;
            if (ok) {
                try { total += Double.parseDouble(f[2].replace(",", "")); } catch (Exception ignored) { }
            }
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(12), 0, dp(2));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            String amt = f[2].isEmpty() ? "Payment" : "\u20B9" + f[2];
            left.addView(text(amt, 17, ok ? Color.parseC
