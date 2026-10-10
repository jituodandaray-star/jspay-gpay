package shop.jitustore.jspay;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
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
import android.widget.FrameLayout;
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

    // ---------- moving soft background ----------
    static class BgView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float[] fx = {0.15f, 0.80f, 0.45f, 0.90f, 0.10f, 0.60f};
        final float[] fy = {0.20f, 0.30f, 0.60f, 0.85f, 0.80f, 0.10f};
        final float[] sp = {0.00021f, 0.00017f, 0.00025f, 0.00014f, 0.00019f, 0.00023f};
        final int[] col = {0xFF6366F1, 0xFF8B5CF6, 0xFF22D3EE, 0xFF6366F1, 0xFF8B5CF6, 0xFF22D3EE};
        boolean running = false;

        BgView(Context c) { super(c); setClickable(false); setFocusable(false); }

        void go() { if (!running) { running = true; postInvalidateOnAnimation(); } }
        void stop() { running = false; }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            long t = android.os.SystemClock.uptimeMillis();
            for (int i = 0; i < fx.length; i++) {
                double a = t * sp[i] + i * 1.7;
                float cx = (float) (fx[i] * w + Math.sin(a) * w * 0.12);
                float cy = (float) (fy[i] * h + Math.cos(a * 0.8) * h * 0.06);
                float r = w * (0.38f + 0.05f * (float) Math.sin(a * 1.3));
                int c0 = (col[i] & 0x00FFFFFF) | 0x2E000000;
                p.setShader(new RadialGradient(cx, cy, r, c0, col[i] & 0x00FFFFFF, Shader.TileMode.CLAMP));
                cv.drawCircle(cx, cy, r, p);
            }
            if (running) postInvalidateOnAnimation();
        }

        @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }
    }

    // ---------- small synthesized sounds (no files needed) ----------
    void beep(final double[] freqs, final int noteMs, final double vol) {
        new Thread(() -> {
            try {
                int sr = 22050;
                int n = sr * noteMs / 1000;
                short[] buf = new short[n * freqs.length];
                for (int k = 0; k < freqs.length; k++) {
                    for (int i = 0; i < n; i++) {
                        double t = i / (double) sr;
                        double env = Math.min(1.0, i / (sr * 0.004)) * Math.exp(-4.5 * i / (double) n);
                        buf[k * n + i] = (short) (Math.sin(2 * Math.PI * freqs[k] * t) * env * vol * 32767);
                    }
                }
                AudioTrack at = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sr)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(buf.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC).build();
                at.write(buf, 0, buf.length);
                at.play();
                Thread.sleep((long) noteMs * freqs.length + 200);
                at.release();
            } catch (Exception ignored) { }
        }).start();
    }

    void sTap() { beep(new double[]{1480}, 40, 0.22); }
    void sOk() { beep(new double[]{880, 1175, 1568}, 110, 0.30); }
    void sErr() { beep(new double[]{330, 262}, 150, 0.30); }

    // tap animation + sound for any clickable view
    void press(View v) {
        v.setOnTouchListener((x, ev) -> {
            int a = ev.getAction();
            if (a == MotionEvent.ACTION_DOWN) {
                x.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.85f).setDuration(90).start();
                sTap();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                x.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start();
            }
            return false;
        });
    }

    BgView bg;

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
        press(b);
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
        if (l != null) press(r);
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
        sv.setBackgroundColor(Color.TRANSPARENT);
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
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        key.setFocusable(true);
        key.setFocusableInTouchMode(true);
        key.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                v.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
            return false;
        });
        key.setBackground(box(Color.parseColor("#0B1020"), 12, LINE));
        key.setPadding(dp(14), dp(12), dp(14), dp(12));
        lc.addView(key, new LinearLayout.LayoutParams(-1, -2));
        lc.addView(button("Paste from clipboard", false, v -> {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                    if (cs != null) { key.setText(cs.toString().trim()); key.setSelection(key.getText().length()); return; }
                }
                Toast.makeText(this, "Clipboard is empty. Copy the Login Key first.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not paste. Long-press the box to paste.", Toast.LENGTH_SHORT).show();
            }
        }));
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
        hc.addView(text("RECENT PAYMENTS", 11, MUTED, true));
        histTotal = text("", 13, MUTED, false);
        histTotal.setPadding(0, dp(6), 0, dp(4));
        hc.addView(histTotal);
        histBox = new LinearLayout(this);
        histBox.setOrientation(LinearLayout.VERTICAL);
        hc.addView(histBox);
        dashBox.addView(hc);

        LinearLayout ac = new LinearLayout(this);
        ac.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = dp(14);
        ac.setLayoutParams(alp);
        TextView bLogout = button("Logout", false, v -> {
            sp.edit().remove("secret").remove("verified").apply();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            refresh();
        });
        TextView bTest = button("Check connection", true, v -> verify(sp.getString("secret", ""), false));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1f);
        l1.rightMargin = dp(8);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, -2, 1f);
        l2.leftMargin = dp(8);
        bLogout.setLayoutParams(l1);
        bTest.setLayoutParams(l2);
        ac.addView(bLogout);
        ac.addView(bTest);
        dashBox.addView(ac);
        root.addView(dashBox);

        // ---- footer ----
        TextView panel = text("Open Panel  ›", 14, PRIMARY, true);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(0, dp(22), 0, dp(6));
        panel.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://jitustore.shop/pay/auth/gpay.php"))));
        press(panel);
        root.addView(panel);
        TextView foot = text("Secured by JS PAY  •  v1.6", 12, Color.parseColor("#64748B"), false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        bg = new BgView(this);
        frame.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
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
        if (bg != null) bg.go();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bg != null) bg.stop();
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
            sOk();
            if (!fromLogin) Toast.makeText(this, "Connection OK ✓", Toast.LENGTH_SHORT).show();
        } else if (rc == 401) {
            sErr();
            sp.edit().remove("secret").remove("verified").apply();
            if (fromLogin) loginMsg.setText("Invalid Login Key. Copy it again from the panel.");
            else Toast.makeText(this, "Login Key is invalid, please log in again", Toast.LENGTH_LONG).show();
        } else if (rc == -1) {
            sErr();
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
        int shown = 0;
        double total = 0;
        for (int i = 0; i < lines.length; i++) {
            String[] f = lines[i].split("\\|", 6);
            if (f.length < 4) continue;
            long ts = 0;
            int code = -1;
            try { ts = Long.parseLong(f[0]); code = Integer.parseInt(f[1]); } catch (Exception ignored) { }
            boolean ok = (code == 200);
            String res = f.length > 4 ? f[4] : "";
            String exp = f.length > 5 ? f[5] : "";
            boolean paid = ok && res.equals("paid");
            boolean wrong = ok && res.equals("wrong");
            boolean noord = ok && res.equals("no_order");
            boolean bad = wrong || noord;
            count++;
            if (paid || (ok && !bad)) {
                try { total += Double.parseDouble(f[2].replace(",", "")); } catch (Exception ignored) { }
            }
            if (shown >= 3) continue;
            shown++;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(12), 0, dp(2));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            String amt = f[2].isEmpty() ? "Payment" : "\u20B9" + f[2];
            left.addView(text(amt, 17, bad ? Color.parseColor("#F87171") : (ok ? Color.parseColor("#4ADE80") : TXT), true));
            java.util.regex.Matcher nm = java.util.regex.Pattern.compile("(?i)from\\s+(.+?)\\s+at\\s").matcher(f[3] + " ");
            if (nm.find()) {
                TextView who = text("from " + nm.group(1).trim(), 14, TXT, false);
                who.setPadding(0, dp(2), 0, 0);
                left.addView(who);
            }
            String when = new SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(new Date(ts));
            left.addView(text(when, 12, MUTED, false));
            r.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView c = chip();
            if (paid) { c.setText("\u2713 Order PAID"); c.setBackground(box(GOOD, 20, 0)); }
            else if (wrong) { c.setText("\u2715 Galat amount"); c.setBackground(box(BAD, 20, 0)); }
            else if (noord) { c.setText("\u2715 Bina order"); c.setBackground(box(BAD, 20, 0)); }
            else setChip(c, ok, "\u2713 Sent", "\u2717 Failed");
            r.addView(c);
            View item = r;
            if (paid || bad) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12), dp(2), dp(12), dp(10));
                int tint = bad ? Color.parseColor("#26DC2626") : Color.parseColor("#2616A34A");
                card.setBackground(box(tint, 14, bad ? BAD : GOOD));
                r.setPadding(0, dp(8), 0, dp(2));
                card.addView(r);
                String note = paid ? "Order mil gaya. Paisa credit ho gaya."
                        : wrong ? "Order \u20B9" + exp + " ka tha, \u20B9" + f[2] + " aaya. Credit nahi hoga."
                        : "Koi order nahi tha. Credit nahi hoga.";
                card.addView(text(note, 12, bad ? Color.parseColor("#FECACA") : Color.parseColor("#BBF7D0"), false));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, dp(8), 0, 0);
                card.setLayoutParams(clp);
                item = card;
            }
            item.setAlpha(0f);
            item.animate().alpha(1f).setDuration(350).setStartDelay(90L * shown).start();
            histBox.addView(item);
        }
        String tot = (total == Math.floor(total)) ? String.valueOf((long) total) : String.format(Locale.US, "%.2f", total);
        histTotal.setText(count + " payments  \u2022  Total \u20B9" + tot);
    }

    void refresh() {
        String k = sp.getString("secret", "");
        boolean verified = k.length() >= 5 && sp.getBoolean("verified", true);
        boolean access = notifOn();
        boolean batt = battOk();
        loginBox.setVisibility(verified ? View.GONE : View.VISIBLE);
        dashBox.setVisibility(verified ? View.VISIBLE : View.GONE);
        boolean net = NotifService.online(this);
        boolean live = verified && access && batt && net;
        pill.setText(live ? "LIVE" : (verified ? (net ? "SETUP PENDING" : "NO INTERNET") : "LOGIN REQUIRED"));
        pill.setBackground(box(live ? GOOD : BAD, 20, 0));
        pill.clearAnimation();
        if (live) {
            AlphaAnimation pulse = new AlphaAnimation(1f, 0.45f);
            pulse.setDuration(900);
            pulse.setRepeatMode(Animation.REVERSE);
            pulse.setRepeatCount(Animation.INFINITE);
            pill.startAnimation(pulse);
        }
        if (verified && !wasVerified) {
            dashBox.setAlpha(0f);
            dashBox.setTranslationY(dp(22));
            dashBox.animate().alpha(1f).translationY(0f).setDuration(450).start();
        }
        wasVerified = verified;
        renderHistory();

        String masked = k.length() > 8 ? k.substring(0, 3) + "••••" + k.substring(k.length() - 4) : "••••";
        setChip(chipKey, verified, "Logged in  " + masked, "Not logged in");
        setChip(chipAccess, access, "ON ✓", "OFF – tap to fix");
        setChip(chipBatt, batt, "ON ✓", "OFF – tap to fix");
        setChip(chipNet, net, "Online ✓", "OFFLINE – turn on net");

        long last = sp.getLong("last", 0);
        if (last > 0) {
            String when = new SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(new Date(last));
            rowLast.setText("Last payment signal: " + when + "  (server " + sp.getInt("code", 0) + ")");
        } else {
            rowLast.setText("No payment signal yet");
        }
    }
}
