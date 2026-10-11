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
import android.speech.tts.TextToSpeech;
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
    ScrollView sv;
    FrameLayout frameRef;
    String cur = "home";
    java.util.HashMap<String, ScrollView> pages = new java.util.HashMap<>();
    LinearLayout todayBox, histPageBox, setupBox, aboutBox;
    String histFilter = "all";
    String gwFilter = "all";
    TextView hgAll, hgGp, hgBp;
    TextView hfAll, hfPaid, hfWarn;
    TextView vSw, vHi, vEn, vBn, vOr, vAuto, vAll, vWarn, vSample1, vSample2, vSample3;
    TextToSpeech ttsT;
    boolean ttsTReady = false;
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
        String bi = label.equals("Logout") ? "out" : label.equals("Check connection") ? "wifi" : label.equals("Open Panel") ? "globe" : label.startsWith("Notification Access") ? "bell" : label.startsWith("Pop-up") ? "bell" : "";
        if (!bi.isEmpty()) {
            Ic d = new Ic(bi, filled ? Color.WHITE : TXT, dp(20));
            d.setBounds(0, 0, dp(20), dp(20));
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("\uFFFC");
            sb.setSpan(new Ic.Span(d), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("   ").append(label);
            b.setText(sb);
        }
        return b;
    }

    TextView chip() {
        TextView t = text("", 12, Color.WHITE, true);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        return t;
    }

    String iconFor(String label) {
        switch (label) {
            case "Login Key": return "key";
            case "Notification Access": return "bell";
            case "Background / Battery": return "batt";
            case "Internet": return "globe";
            case "Voice engine": return "vol";
            default: return "";
        }
    }

    String menuIcon(String target) {
        switch (target) {
            case "voice": return "vol";
            case "dash": return "globe";
            case "today": return "rup";
            case "history": return "hist";
            case "setup": return "shield";
            default: return "info";
        }
    }

    LinearLayout toggleRow(String icon, String title, String sub, String key) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(12), 0, dp(4));
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageDrawable(new Ic(icon, Color.parseColor("#6EE7B7"), dp(22)));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(22), dp(22));
        ilp.rightMargin = dp(12);
        r.addView(iv, ilp);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(text(title, 15, TXT, true));
        t.addView(text(sub, 12, MUTED, false));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(sp.getBoolean(key, true));
        sw.setOnCheckedChangeListener((bv, on) -> {
            sp.edit().putBoolean(key, on).apply();
            if (key.equals("daily_on") && on) Daily.schedule(this);
        });
        r.addView(sw);
        return r;
    }

    LinearLayout row(String label, TextView chipView, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(12), 0, dp(4));
        String ik = iconFor(label);
        if (!ik.isEmpty()) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageDrawable(new Ic(ik, Color.parseColor("#A5B4FC"), dp(20)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(20), dp(20));
            ilp.rightMargin = dp(10);
            r.addView(iv, ilp);
        }
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
        try {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 77);
            }
            Notifier.channels(this);
            Daily.schedule(this);
        } catch (Exception ignored) { }

        sv = new ScrollView(this);
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
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(box(Color.parseColor("#1E2748"), 14, LINE));
        for (int i = 0; i < 3; i++) {
            View bar = new View(this);
            bar.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(18), dp(2));
            bp.topMargin = (i == 0) ? 0 : dp(4);
            menu.addView(bar, bp);
        }
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(44), dp(44));
        mp.leftMargin = dp(10);
        head.addView(menu, mp);
        menu.setOnClickListener(v -> go("menu"));
        press(menu);
        root.addView(head);

        TextView tag = text("Payments verified instantly. Zero manual work.", 14, MUTED, false);
        tag.setPadding(0, dp(16), 0, 0);
        root.addView(tag);
        root.addView(buildPlanCard());

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
            Plan.clear(this);
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            refresh();
        });
        TextView bTest = button("Check connection", true, v -> verify(sp.getString("secret", ""), false));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(52), 1f);
        l1.rightMargin = dp(6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(52), 1f);
        l2.leftMargin = dp(6);
        for (TextView tb : new TextView[]{bLogout, bTest}) {
            tb.setTextSize(14);
            tb.setSingleLine(true);
            tb.setPadding(dp(8), 0, dp(8), 0);
        }
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
                Uri.parse(Sx.PANEL))));
        press(panel);
        root.addView(panel);
        TextView foot = text("Secured by JS PAY  •  v1.17", 12, Color.parseColor("#64748B"), false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        bg = new BgView(this);
        frame.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        frameRef = frame;
        int sw = getResources().getDisplayMetrics().widthPixels;
        pages.put("home", sv);
        pages.put("menu", buildMenu());
        pages.put("voice", buildPage2());
        pages.put("today", buildToday());
        pages.put("history", buildHistoryPage());
        pages.put("setup", buildSetupPage());
        pages.put("about", buildAboutPage());
        for (java.util.Map.Entry<String, ScrollView> e : pages.entrySet()) {
            if (e.getKey().equals("home")) continue;
            frame.addView(e.getValue(), new FrameLayout.LayoutParams(-1, -1));
            e.getValue().setTranslationX(sw);
        }
        if (!splashShown) {
            splashShown = true;
            splashRef = buildSplash();
            frame.addView(splashRef, new FrameLayout.LayoutParams(-1, -1));
        }
        frame.addView(buildLock(), new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        try {
            ttsT = new TextToSpeech(getApplicationContext(), st2 -> { ttsTReady = (st2 == TextToSpeech.SUCCESS); });
        } catch (Exception ignored) { }
        for (int i = 0; i < root.getChildCount(); i++) {
            View ch = root.getChildAt(i);
            ch.setAlpha(0f);
            ch.setTranslationY(dp(26));
            ch.animate().alpha(1f).translationY(0f).setDuration(480).setStartDelay(90L * i).start();
        }
    }


    String parentOf(String n) {
        if (n.equals("home")) return "home";
        if (n.equals("menu")) return "home";
        return "menu";
    }

    void go(String name) {
        ScrollView nw = pages.get(name), old = pages.get(cur);
        if (nw == null || old == null || name.equals(cur)) return;
        int w = getResources().getDisplayMetrics().widthPixels;
        nw.setTranslationX(w);
        nw.animate().translationX(0).setDuration(260).start();
        old.animate().translationX(-w).setDuration(260).start();
        cur = name;
        sTap();
        onShow(name);
    }

    void goBack() {
        if (cur.equals("home")) return;
        String p = parentOf(cur);
        ScrollView from = pages.get(cur), to = pages.get(p);
        if (from == null || to == null) return;
        int w = getResources().getDisplayMetrics().widthPixels;
        from.animate().translationX(w).setDuration(260).start();
        to.animate().translationX(0).setDuration(260).start();
        cur = p;
        sTap();
        onShow(p);
    }

    void jumpHome() {
        int w = getResources().getDisplayMetrics().widthPixels;
        for (java.util.Map.Entry<String, ScrollView> e : pages.entrySet()) {
            e.getValue().animate().cancel();
            e.getValue().setTranslationX(e.getKey().equals("home") ? 0 : w);
        }
        cur = "home";
    }

    void onShow(String name) {
        try {
            if (name.equals("voice")) refreshVoiceUi();
            else if (name.equals("today")) renderTodayPage();
            else if (name.equals("history")) renderHistoryPage();
            else if (name.equals("setup")) renderSetupPage();
            else if (name.equals("about")) renderAboutPage();
        } catch (Exception ignored) { }
    }

    @Override
    public void onBackPressed() {
        if (isLocked()) return;
        if (!cur.equals("home")) goBack(); else super.onBackPressed();
    }

    TextView seg(String label, View.OnClickListener l) {
        TextView t = text(label, 13, MUTED, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4), dp(11), dp(4), dp(11));
        t.setOnClickListener(l);
        press(t);
        return t;
    }

    void paintSeg(TextView t, boolean on) {
        t.setTextColor(on ? Color.WHITE : MUTED);
        t.setBackground(on ? box(PRIMARY, 12, 0) : box(Color.TRANSPARENT, 12, LINE));
    }

    ScrollView buildPage2() {
        ScrollView s2 = new ScrollView(this);
        s2.setFillViewport(true);
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.VERTICAL);
        r2.setPadding(dp(20), dp(36), dp(20), dp(28));
        s2.addView(r2);

        TextView back = text("\u2039  Back", 16, PRIMARY, true);
        back.setPadding(0, 0, dp(20), dp(14));
        back.setOnClickListener(v -> goBack());
        press(back);
        r2.addView(back);
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
        names.addView(text("Voice Alert", 22, TXT, true));
        names.addView(text("Awaaz settings", 13, MUTED, false));
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        r2.addView(head);

        LinearLayout c1 = card();
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.addView(text("Voice alert", 15, TXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        vSw = chip();
        vSw.setOnClickListener(v -> { sp.edit().putBoolean("voice_on", !sp.getBoolean("voice_on", true)).apply(); sTap(); refreshVoiceUi(); });
        press(vSw);
        row1.addView(vSw);
        c1.addView(row1);

        TextView l1 = text("LANGUAGE", 11, MUTED, true);
        l1.setPadding(0, dp(14), 0, dp(6));
        c1.addView(l1);
        LinearLayout s1 = new LinearLayout(this);
        s1.setOrientation(LinearLayout.HORIZONTAL);
        vHi = seg("\u0939\u093F\u0902\u0926\u0940", v -> setLang("hi"));
        vEn = seg("English", v -> setLang("en"));
        vAuto = seg("Auto", v -> setLang("auto"));
        for (TextView t : new TextView[]{vHi, vEn, vAuto}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.rightMargin = dp(6);
            s1.addView(t, p);
        }
        c1.addView(s1);
        LinearLayout s1b = new LinearLayout(this);
        s1b.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams s1bp = new LinearLayout.LayoutParams(-1, -2);
        s1bp.topMargin = dp(6);
        s1b.setLayoutParams(s1bp);
        vBn = seg("\u09AC\u09BE\u0982\u09B2\u09BE", v -> setLang("bn"));
        vOr = seg("\u0B13\u0B21\u0B3C\u0B3F\u0B06", v -> setLang("or"));
        for (TextView t : new TextView[]{vBn, vOr}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.rightMargin = dp(6);
            s1b.addView(t, p);
        }
        View sp3 = new View(this);
        s1b.addView(sp3, new LinearLayout.LayoutParams(0, 1, 1f));
        c1.addView(s1b);

        TextView l2 = text("KAB BOLE", 11, MUTED, true);
        l2.setPadding(0, dp(14), 0, dp(6));
        c1.addView(l2);
        LinearLayout s2r = new LinearLayout(this);
        s2r.setOrientation(LinearLayout.HORIZONTAL);
        vAll = seg("Har payment", v -> setMode("all"));
        vWarn = seg("Sirf warning", v -> setMode("warn"));
        for (TextView t : new TextView[]{vAll, vWarn}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.rightMargin = dp(6);
            s2r.addView(t, p);
        }
        c1.addView(s2r);

        TextView test = button("\u25B6  Test voice", true, v -> testVoice());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(14);
        test.setLayoutParams(tp);
        c1.addView(test);
        r2.addView(c1);

        LinearLayout c2 = card();
        c2.addView(text("AISA BOLEGA", 11, MUTED, true));
        vSample1 = text("", 13, Color.parseColor("#C7D2FE"), false);
        vSample2 = text("", 13, Color.parseColor("#FECACA"), false);
        vSample3 = text("", 13, Color.parseColor("#FECACA"), false);
        for (TextView t : new TextView[]{vSample1, vSample2, vSample3}) {
            t.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.topMargin = dp(8);
            c2.addView(t, p);
        }
        vSample1.setBackground(box(Color.parseColor("#226366F1"), 12, Color.parseColor("#6366F1")));
        vSample2.setBackground(box(Color.parseColor("#22DC2626"), 12, BAD));
        vSample3.setBackground(box(Color.parseColor("#22DC2626"), 12, BAD));
        r2.addView(c2);

        LinearLayout c3 = card();
        c3.addView(text("AWAAZ CHUNO", 11, MUTED, true));
        TextView vh = text("Hindi mein Hindi Awaaz 7 aur English mein English Awaaz 3 bolegi. Dabao to sunayi degi.", 12, MUTED, false);
        vh.setPadding(0, dp(6), 0, dp(4));
        c3.addView(vh);
        vListBox = new LinearLayout(this);
        vListBox.setOrientation(LinearLayout.VERTICAL);
        c3.addView(vListBox);
        c3.addView(button("Phone ki awaaz settings kholo", false, v -> {
            try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
            catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { } }
        }));
        r2.addView(c3);

        return s2;
    }

    LinearLayout vListBox;
    int voiceTries = 0;
    String vOpen = "";

    static final String[] VL = {"hi", "en", "bn", "or"};

    String vlName(String lg) {
        return lg.equals("hi") ? "Hindi" : lg.equals("en") ? "English" : lg.equals("bn") ? "Bangla" : "Odia";
    }

    java.util.ArrayList<android.speech.tts.Voice> voicesOf(String lg) {
        return Voice.sorted(ttsT, lg);
    }

    String voiceInfo(android.speech.tts.Voice v) {
        return (v.isNetworkConnectionRequired() ? "Internet chahiye" : "Offline") + " \u00B7 " + v.getLocale().getDisplayCountry();
    }

    void renderVoiceList() {
        if (vListBox == null) return;
        vListBox.removeAllViews();
        if (ttsT == null || !ttsTReady) {
            TextView w = text("Awaaz engine tayyar ho raha hai\u2026", 13, MUTED, false);
            w.setPadding(0, dp(10), 0, dp(6));
            vListBox.addView(w);
            if (voiceTries++ < 8) vListBox.postDelayed(() -> renderVoiceList(), 1200);
            return;
        }
        voiceTries = 0;
        // sirf 2 awaaz: Hindi = Awaaz 7, English = Awaaz 3 (hamesha yahi)
        try {
            java.util.ArrayList<android.speech.tts.Voice> hl = voicesOf("hi");
            java.util.ArrayList<android.speech.tts.Voice> el = voicesOf("en");
            SharedPreferences.Editor ed = sp.edit();
            ed.remove("voice_bn").remove("voice_or");
            if (!hl.isEmpty()) ed.putString("voice_hi", hl.get(Math.min(7, hl.size()) - 1).getName());
            if (!el.isEmpty()) ed.putString("voice_en", el.get(Math.min(3, el.size()) - 1).getName());
            ed.apply();
            String[] lgs = {"hi", "en"};
            java.util.ArrayList<java.util.ArrayList<android.speech.tts.Voice>> ls = new java.util.ArrayList<>();
            ls.add(hl); ls.add(el);
            for (int k = 0; k < 2; k++) {
                java.util.ArrayList<android.speech.tts.Voice> list = ls.get(k);
                String cur = sp.getString("voice_" + lgs[k], "");
                android.speech.tts.Voice sel = null;
                int idx = 0;
                for (int i = 0; i < list.size(); i++) if (list.get(i).getName().equals(cur)) { sel = list.get(i); idx = i + 1; }
                if (sel == null) continue;
                vListBox.addView(voiceRow(vlName(lgs[k]) + " \u00B7 Awaaz " + idx, voiceInfo(sel) + " \u00B7 sunne ke liye dabao", true, sel, lgs[k]));
            }
        } catch (Exception ignored) { }
    }

    void previewVoice(android.speech.tts.Voice v) {
        try {
            if (ttsT == null || !ttsTReady) return;
            ttsT.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            ttsT.setVoice(v);
            String lang = v.getLocale().getLanguage();
            if (!lang.equals("hi") && !lang.equals("bn") && !lang.equals("or")) lang = "en";
            ttsT.speak(Voice.say("paid", lang, "26", "", "bp"), TextToSpeech.QUEUE_FLUSH, null, "pv1");
            ttsT.speak(Voice.say("wrong", lang, "2", "500", "gp"), TextToSpeech.QUEUE_ADD, null, "pv2");
        } catch (Exception ignored) { }
    }

    View voiceRow(String title, String sub, boolean on, final android.speech.tts.Voice v, final String lg) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(9), dp(12), dp(9));
        r.setBackground(on ? box(Color.parseColor("#226366F1"), 12, PRIMARY) : box(Color.TRANSPARENT, 12, LINE));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(6);
        rp.leftMargin = dp(14);
        r.setLayoutParams(rp);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(text(title, 14, TXT, true));
        t.addView(text(sub, 11, MUTED, false));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        if (on) {
            android.widget.ImageView ck = new android.widget.ImageView(this);
            ck.setImageDrawable(new Ic("check", Color.parseColor("#4ADE80"), dp(22)));
            r.addView(ck, new LinearLayout.LayoutParams(dp(22), dp(22)));
        }
        r.setOnClickListener(x -> {
            if (v == null) sp.edit().remove("voice_" + lg).apply();
            else sp.edit().putString("voice_" + lg, v.getName()).apply();
            vOpen = "";
            sTap();
            refreshVoiceUi();
            if (v != null) previewVoice(v);
        });
        press(r);
        return r;
    }

    void setLang(String l) { sp.edit().putString("voice_lang", l).apply(); sTap(); refreshVoiceUi(); }
    void setMode(String m) { sp.edit().putString("voice_mode", m).apply(); sTap(); refreshVoiceUi(); }

    String curLang() {
        String pref = sp.getString("voice_lang", "auto");
        if (pref.equals("hi") || pref.equals("en") || pref.equals("bn") || pref.equals("or")) return pref;
        return "hi";
    }

    void refreshVoiceUi() {
        if (vSw == null) return;
        boolean on = sp.getBoolean("voice_on", true);
        setChip(vSw, on, "ON \u2713", "OFF");
        String l = sp.getString("voice_lang", "auto");
        paintSeg(vHi, l.equals("hi"));
        paintSeg(vBn, l.equals("bn"));
        paintSeg(vOr, l.equals("or"));
        paintSeg(vEn, l.equals("en"));
        paintSeg(vAuto, l.equals("auto"));
        String m = sp.getString("voice_mode", "all");
        paintSeg(vAll, m.equals("all"));
        paintSeg(vWarn, m.equals("warn"));
        String lg = curLang();
        vSample1.setText("\u201C" + Voice.say("paid", lg, "26", "") + "\u201D");
        vSample2.setText("\u201C" + Voice.say("wrong", lg, "2", "500") + "\u201D");
        vSample3.setText("\u201C" + Voice.say("noorder", lg, "5", "") + "\u201D");
        renderVoiceList();
    }

    void testVoice() {
        if (ttsT == null || !ttsTReady) {
            Toast.makeText(this, "Voice engine is not ready. Check Settings > Text-to-speech.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ttsT.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            String lang = Voice.pick(ttsT, sp);
            String pf = sp.getString("voice_lang", "auto");
            if (lang.equals("en") && !pf.equals("en")) {
                Toast.makeText(this, "Is bhasha ki awaaz phone mein nahi mili. English use ho rahi hai.", Toast.LENGTH_LONG).show();
            } else if (lang.equals("od")) {
                Toast.makeText(this, "Odia awaaz phone mein nahi hai. Hindi awaaz se Odia bolegi.", Toast.LENGTH_LONG).show();
            }
            ttsT.speak(Voice.say("paid", lang, "26", ""), TextToSpeech.QUEUE_FLUSH, null, "test1");
            ttsT.speak(Voice.say("wrong", lang, "2", "500"), TextToSpeech.QUEUE_ADD, null, "test2");
        } catch (Exception e) {
            Toast.makeText(this, "Could not play voice.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        try { if (ttsT != null) { ttsT.stop(); ttsT.shutdown(); } } catch (Exception ignored) { }
        super.onDestroy();
    }


    // ================= BEECH WALA SAFED POPUP =================
    static MainActivity front;
    FrameLayout popRoot;

    /** NotifService se: app khula ho to beech mein popup. */
    static void popup(final String kind, final String amount, final String gw, final String who, final String exp) {
        final MainActivity a = front;
        if (a == null) return;
        try { a.runOnUiThread(() -> a.showPopup(kind, amount, gw, who, exp)); } catch (Exception ignored) { }
    }

    void hidePopup() {
        try {
            if (popRoot != null && popRoot.getParent() != null) ((android.view.ViewGroup) popRoot.getParent()).removeView(popRoot);
        } catch (Exception ignored) { }
        popRoot = null;
    }

    void showPopup(String kind, String amount, String gw, String who, String exp) {
        try {
            hidePopup();
            boolean good = kind.equals("paid") || kind.equals("plain");
            int main = Color.parseColor(good ? "#16A34A" : "#DC2626");
            String label = kind.equals("wrong") ? "Galat Amount" : kind.equals("noorder") ? "Bina Order Ka Paisa" : kind.equals("expired") ? "Premium Khatam" : kind.equals("failed") ? "Server Tak Nahi Pahunchi" : "Payment Received";
            String sub = good ? (who == null ? "" : who) : kind.equals("wrong") ? "Order \u20B9" + exp + " ka tha" : kind.equals("noorder") ? "Koi order nahi tha" : kind.equals("expired") ? "Renew karo" : "Internet check karo";
            String amt = (amount == null || amount.isEmpty()) ? "" : "\u20B9" + Notifier.money(amount);
            final FrameLayout ov = new FrameLayout(this);
            ov.setBackgroundColor(Color.parseColor("#B8050814"));
            ov.setClickable(true);
            ov.setOnClickListener(v -> hidePopup());
            FrameLayout wrap = new FrameLayout(this);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dp(18), dp(46), dp(18), dp(20));
            card.setBackground(box(Color.WHITE, 26, 0));
            TextView t1 = text(label, 21, Color.parseColor(good ? "#1F2937" : "#B91C1C"), false);
            t1.setGravity(Gravity.CENTER);
            card.addView(t1);
            if (!amt.isEmpty()) {
                TextView t2 = text(amt, 44, Color.parseColor("#0F172A"), true);
                t2.setGravity(Gravity.CENTER);
                t2.setPadding(0, dp(4), 0, 0);
                card.addView(t2);
            }
            if (!sub.isEmpty()) {
                TextView t3 = text(sub, 15, Color.parseColor("#64748B"), false);
                t3.setGravity(Gravity.CENTER);
                t3.setPadding(0, dp(6), 0, 0);
                card.addView(t3);
            }
            TextView bd = text("bp".equals(gw) ? "BharatPe Business" : "Google Pay Business", 12, Color.WHITE, true);
            GradientDrawable bgd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    "bp".equals(gw) ? new int[]{Color.parseColor("#14B8A6"), Color.parseColor("#0284C7")} : new int[]{Color.parseColor("#4285F4"), Color.parseColor("#34A853")});
            bgd.setCornerRadius(dp(9));
            bd.setBackground(bgd);
            bd.setPadding(dp(11), dp(4), dp(11), dp(4));
            LinearLayout.LayoutParams bdp = new LinearLayout.LayoutParams(-2, -2);
            bdp.topMargin = dp(12);
            card.addView(bd, bdp);
            FrameLayout bar = new FrameLayout(this);
            bar.setBackground(box(Color.parseColor("#E5E7EB"), 3, 0));
            View fill = new View(this);
            fill.setBackground(box(main, 3, 0));
            bar.addView(fill, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(-1, dp(5));
            brp.topMargin = dp(18);
            card.addView(bar, brp);
            TextView sm = text("3 second mein band ho jayega", 11, Color.parseColor("#94A3B8"), false);
            sm.setGravity(Gravity.CENTER);
            sm.setPadding(0, dp(6), 0, 0);
            card.addView(sm);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2);
            cp.topMargin = dp(34);
            wrap.addView(card, cp);
            android.widget.ImageView ci = new android.widget.ImageView(this);
            ci.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            ci.setImageDrawable(new Ic(good ? "check" : "warn", Color.WHITE, dp(34)));
            GradientDrawable cg = new GradientDrawable();
            cg.setShape(GradientDrawable.OVAL);
            cg.setColor(main);
            cg.setStroke(dp(5), Color.WHITE);
            ci.setBackground(cg);
            wrap.addView(ci, new FrameLayout.LayoutParams(dp(68), dp(68), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            FrameLayout.LayoutParams wp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
            wp.leftMargin = dp(28);
            wp.rightMargin = dp(28);
            ov.addView(wrap, wp);
            addContentView(ov, new FrameLayout.LayoutParams(-1, -1));
            popRoot = ov;
            AlphaAnimation fa = new AlphaAnimation(0f, 1f);
            fa.setDuration(180);
            ov.startAnimation(fa);
            android.view.animation.ScaleAnimation sa = new android.view.animation.ScaleAnimation(1f, 0f, 1f, 1f,
                    Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0.5f);
            sa.setDuration(3000);
            sa.setFillAfter(true);
            fill.startAnimation(sa);
            ov.postDelayed(() -> { if (popRoot == ov) hidePopup(); }, 3200);
        } catch (Exception ignored) { }
    }

    // ================= MENU + PAGES =================
    ScrollView mkPage(String icon, String title, String sub, LinearLayout[] out) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(20), dp(36), dp(20), dp(28));
        s.addView(r);
        TextView back = text("‹  Back", 16, PRIMARY, true);
        back.setPadding(0, 0, dp(20), dp(14));
        back.setOnClickListener(v -> goBack());
        press(back);
        r.addView(back);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text(icon, 20, Color.WHITE, true);
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
        names.addView(text(title, 22, TXT, true));
        names.addView(text(sub, 13, MUTED, false));
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(head);
        out[0] = r;
        return s;
    }

    LinearLayout menuItem(String icon, String color, String title, String sub, String target) {
        LinearLayout it = new LinearLayout(this);
        it.setOrientation(LinearLayout.HORIZONTAL);
        it.setGravity(Gravity.CENTER_VERTICAL);
        it.setPadding(dp(14), dp(14), dp(14), dp(14));
        it.setBackground(box(CARD, 18, LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        it.setLayoutParams(lp);
        android.widget.ImageView ic = new android.widget.ImageView(this);
        ic.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        ic.setImageDrawable(new Ic(menuIcon(target), Color.WHITE, dp(24)));
        ic.setBackground(box(Color.parseColor(color), 12, 0));
        it.addView(ic, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), 0, 0, 0);
        t.addView(text(title, 16, TXT, true));
        t.addView(text(sub, 12, MUTED, false));
        it.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        it.addView(text("›", 24, PRIMARY, true));
        it.setOnClickListener(v -> { if (target.equals("dash")) startActivity(new Intent(this, DashActivity.class)); else go(target); });
        press(it);
        return it;
    }

    ScrollView buildMenu() {
        LinearLayout[] o = new LinearLayout[1];
        ScrollView s = mkPage("JS", "Menu", "Sab features yahan", o);
        o[0].addView(menuItem("V", "#312E81", "Voice Alert", "Awaaz, bhasha, kab bole", "voice"));
        o[0].addView(menuItem("₹", "#064E3B", "Aaj ka Hisaab", "Payments, total, warning", "today"));
        o[0].addView(menuItem("H", "#7C2D12", "Payment History", "Purani payments, hare/laal box", "history"));
        o[0].addView(menuItem("D", "#0F766E", "Dashboard Mode (Beta)", "Notification ke bina, Google dashboard se", "dash"));
        o[0].addView(menuItem("S", "#1E3A8A", "Setup Check", "Sab ON hai ya nahi, ek jagah", "setup"));
        o[0].addView(menuItem("i", "#4C1D95", "About & Help", "Version, Open Panel, madad", "about"));
        return s;
    }

    ScrollView buildToday() {
        LinearLayout[] o = new LinearLayout[1];
        ScrollView s = mkPage("₹", "Aaj ka Hisaab", "Aaj ki payments", o);
        todayBox = new LinearLayout(this);
        todayBox.setOrientation(LinearLayout.VERTICAL);
        o[0].addView(todayBox);
        return s;
    }

    ScrollView buildHistoryPage() {
        LinearLayout[] o = new LinearLayout[1];
        ScrollView s = mkPage("H", "Payment History", "Aakhri 50", o);
        LinearLayout fr = new LinearLayout(this);
        fr.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dp(14);
        fr.setLayoutParams(flp);
        hfAll = seg("Sab", v -> { histFilter = "all"; renderHistoryPage(); });
        hfPaid = seg("Paid", v -> { histFilter = "paid"; renderHistoryPage(); });
        hfWarn = seg("Warning", v -> { histFilter = "warn"; renderHistoryPage(); });
        for (TextView t : new TextView[]{hfAll, hfPaid, hfWarn}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.rightMargin = dp(6);
            fr.addView(t, p);
        }
        o[0].addView(fr);
        final TextView[] gws = new TextView[3];
        o[0].addView(gwRow(gws, () -> renderHistoryPage()));
        hgAll = gws[0]; hgGp = gws[1]; hgBp = gws[2];
        histPageBox = new LinearLayout(this);
        histPageBox.setOrientation(LinearLayout.VERTICAL);
        o[0].addView(histPageBox);
        return s;
    }

    ScrollView buildSetupPage() {
        LinearLayout[] o = new LinearLayout[1];
        ScrollView s = mkPage("S", "Setup Check", "Sab theek hai ya nahi", o);
        setupBox = new LinearLayout(this);
        setupBox.setOrientation(LinearLayout.VERTICAL);
        o[0].addView(setupBox);
        return s;
    }

    ScrollView buildAboutPage() {
        LinearLayout[] o = new LinearLayout[1];
        ScrollView s = mkPage("i", "About & Help", "JS PAY GPay Auto Verify", o);
        aboutBox = new LinearLayout(this);
        aboutBox.setOrientation(LinearLayout.VERTICAL);
        o[0].addView(aboutBox);
        return s;
    }

    // ----- history helpers -----
    java.util.ArrayList<String[]> loadHist() {
        java.util.ArrayList<String[]> out = new java.util.ArrayList<>();
        String h = sp.getString("hist", "");
        if (h.isEmpty()) return out;
        for (String line : h.split("\n")) {
            String[] f = line.split("\\|", 6);
            if (f.length < 4) continue;
            String[] g = new String[6];
            for (int i = 0; i < 6; i++) g[i] = i < f.length ? f[i] : "";
            out.add(g);
        }
        return out;
    }

    String kindOf(String[] f) {
        int code = -1;
        try { code = Integer.parseInt(f[1]); } catch (Exception ignored) { }
        if (code != 200) return "failed";
        if (f[4].equals("wrong")) return "wrong";
        if (f[4].equals("no_order")) return "noorder";
        if (f[4].equals("expired")) return "expired";
        if (f[4].equals("paid")) return "paid";
        return "plain";
    }

    double amtOf(String[] f) {
        try { return Double.parseDouble(f[2].replace(",", "")); } catch (Exception e) { return 0; }
    }

    String money(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format(Locale.US, "%.2f", v);
    }

    boolean gwOk(String[] f) {
        boolean bp = f[3].startsWith("[BP]");
        if (gwFilter.equals("bp")) return bp;
        if (gwFilter.equals("gp")) return !bp;
        return true;
    }

    /** Sab | GPay | BharatPe wali patti. out[0..2] mein buttons milte hain. */
    LinearLayout gwRow(TextView[] out, Runnable redraw) {
        LinearLayout fr = new LinearLayout(this);
        fr.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dp(14);
        fr.setLayoutParams(flp);
        out[0] = seg("Sab Gateway", v -> { gwFilter = "all"; redraw.run(); });
        out[1] = seg("GPay", v -> { gwFilter = "gp"; redraw.run(); });
        out[2] = seg("BharatPe", v -> { gwFilter = "bp"; redraw.run(); });
        for (TextView t : out) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.rightMargin = dp(6);
            fr.addView(t, p);
        }
        return fr;
    }

    void paintGw(TextView[] t) {
        paintSeg(t[0], gwFilter.equals("all"));
        paintSeg(t[1], gwFilter.equals("gp"));
        paintSeg(t[2], gwFilter.equals("bp"));
    }

    String whoOf(String[] f) {
        java.util.regex.Matcher nm = java.util.regex.Pattern.compile("(?i)from\\s+(.+?)(?:\\s+and\\s+settled|\\s+settled|\\s+at\\s)").matcher(f[3] + " ");
        String w = nm.find() ? nm.group(1).trim() : "";
        return f[3].startsWith("[BP]") ? "BharatPe \u00B7 " + (w.isEmpty() ? "payment" : "from " + w) : (w.isEmpty() ? "GPay" : "GPay \u00B7 from " + w);
    }

    View payItem(String[] f) {
        String kind = kindOf(f);
        boolean bad = kind.equals("wrong") || kind.equals("noorder") || kind.equals("failed") || kind.equals("expired");
        boolean paid = kind.equals("paid");
        long ts = 0;
        try { ts = Long.parseLong(f[0]); } catch (Exception ignored) { }
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        String a = f[2].isEmpty() ? "Payment" : "₹" + f[2];
        left.addView(text(a, 17, bad ? Color.parseColor("#F87171") : Color.parseColor("#4ADE80"), true));
        String w = whoOf(f);
        if (!w.isEmpty()) left.addView(text(w, 14, TXT, false));
        left.addView(text(new SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(new Date(ts)), 12, MUTED, false));
        r.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView c = chip();
        if (paid) { c.setText("✓ Order PAID"); c.setBackground(box(GOOD, 20, 0)); }
        else if (kind.equals("wrong")) { c.setText("✕ Galat amount"); c.setBackground(box(BAD, 20, 0)); }
        else if (kind.equals("noorder")) { c.setText("✕ Bina order"); c.setBackground(box(BAD, 20, 0)); }
        else if (kind.equals("expired")) { c.setText("✕ Premium khatam"); c.setBackground(box(BAD, 20, 0)); }
        else if (kind.equals("failed")) { c.setText("✕ Failed"); c.setBackground(box(BAD, 20, 0)); }
        else { c.setText("✓ Sent"); c.setBackground(box(GOOD, 20, 0)); }
        r.addView(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        if (!paid && !bad) {
            r.setPadding(0, dp(6), 0, dp(6));
            r.setLayoutParams(lp);
            return r;
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(8), dp(12), dp(10));
        card.setBackground(box(bad ? Color.parseColor("#26DC2626") : Color.parseColor("#2616A34A"), 14, bad ? BAD : GOOD));
        card.addView(r);
        String note = paid ? "Order mil gaya. Paisa credit ho gaya."
                : kind.equals("wrong") ? "Order ₹" + f[5] + " ka tha, ₹" + f[2] + " aaya. Credit nahi hoga."
                : kind.equals("noorder") ? "Koi order nahi tha. Credit nahi hoga."
                : kind.equals("expired") ? "Premium khatam. Renew karo, tab payment verify hogi."
                : "Server tak nahi pahunchi.";
        TextView nt = text(note, 12, bad ? Color.parseColor("#FECACA") : Color.parseColor("#BBF7D0"), false);
        nt.setPadding(0, dp(6), 0, 0);
        card.addView(nt);
        card.setLayoutParams(lp);
        return card;
    }

    LinearLayout statBox(String num, String label, int stroke, int tint) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(6), dp(12), dp(6), dp(12));
        b.setBackground(box(tint, 14, stroke));
        TextView n = text(num, 22, Color.WHITE, true);
        n.setGravity(Gravity.CENTER);
        b.addView(n);
        TextView l = text(label, 12, MUTED, false);
        l.setGravity(Gravity.CENTER);
        b.addView(l);
        return b;
    }

    void renderTodayPage() {
        if (todayBox == null) return;
        todayBox.removeAllViews();
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        java.util.ArrayList<String[]> today = new java.util.ArrayList<>();
        int paid = 0, warn = 0;
        double total = 0, gpTot = 0, bpTot = 0;
        long lastTs = 0;
        for (String[] f : loadHist()) {
            long ts = 0;
            try { ts = Long.parseLong(f[0]); } catch (Exception ignored) { }
            if (ts < start) continue;
            String k0 = kindOf(f);
            if (k0.equals("paid") || k0.equals("plain")) { if (f[3].startsWith("[BP]")) bpTot += amtOf(f); else gpTot += amtOf(f); }
            if (!gwOk(f)) continue;
            today.add(f);
            String k = kindOf(f);
            if (k.equals("paid") || k.equals("plain")) { paid++; total += amtOf(f); }
            else if (k.equals("wrong") || k.equals("noorder") || k.equals("expired")) warn++;
            if (ts > lastTs) lastTs = ts;
        }
        final TextView[] tg = new TextView[3];
        todayBox.addView(gwRow(tg, () -> renderTodayPage()));
        paintGw(tg);
        LinearLayout big = card();
        big.addView(text(gwFilter.equals("bp") ? "AAJ BHARATPE ME MILA" : gwFilter.equals("gp") ? "AAJ GPAY ME MILA" : "AAJ TOTAL MILA", 11, MUTED, true));
        TextView t = text("₹" + money(total), 38, Color.parseColor("#34D399"), true);
        t.setPadding(0, dp(6), 0, dp(2));
        big.addView(t);
        big.addView(text(lastTs > 0 ? "Aakhri payment: " + new SimpleDateFormat("hh:mm a", Locale.US).format(new Date(lastTs)) : "Aaj abhi koi payment nahi aayi", 13, MUTED, false));
        if (gwFilter.equals("all")) {
            TextView sp2 = text("GPay \u20B9" + money(gpTot) + "   \u00B7   BharatPe \u20B9" + money(bpTot), 14, TXT, true);
            sp2.setPadding(0, dp(8), 0, 0);
            big.addView(sp2);
        }
        todayBox.addView(big);
        LinearLayout st = new LinearLayout(this);
        st.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(14);
        st.setLayoutParams(slp);
        LinearLayout b1 = statBox(String.valueOf(paid), "Paid", GOOD, Color.parseColor("#2616A34A"));
        LinearLayout b2 = statBox(String.valueOf(warn), "Warning", BAD, Color.parseColor("#26DC2626"));
        LinearLayout b3 = statBox(String.valueOf(today.size()), "Total", LINE, CARD);
        for (LinearLayout b : new LinearLayout[]{b1, b2, b3}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.rightMargin = dp(8);
            st.addView(b, p);
        }
        todayBox.addView(st);
        LinearLayout lc = card();
        lc.addView(text("AAJ KI PAYMENTS", 11, MUTED, true));
        if (today.isEmpty()) {
            TextView e = text("Aaj koi payment nahi aayi.", 13, MUTED, false);
            e.setPadding(0, dp(10), 0, 0);
            lc.addView(e);
        } else {
            int n = 0;
            for (String[] f : today) { if (n++ >= 50) break; lc.addView(payItem(f)); }
        }
        todayBox.addView(lc);
    }

    void renderHistoryPage() {
        if (histPageBox == null) return;
        paintSeg(hfAll, histFilter.equals("all"));
        paintSeg(hfPaid, histFilter.equals("paid"));
        paintSeg(hfWarn, histFilter.equals("warn"));
        if (hgAll != null) paintGw(new TextView[]{hgAll, hgGp, hgBp});
        histPageBox.removeAllViews();
        int n = 0;
        for (String[] f : loadHist()) {
            String k = kindOf(f);
            boolean isPaid = k.equals("paid") || k.equals("plain");
            boolean isWarn = k.equals("wrong") || k.equals("noorder") || k.equals("failed") || k.equals("expired");
            if (histFilter.equals("paid") && !isPaid) continue;
            if (histFilter.equals("warn") && !isWarn) continue;
            if (!gwOk(f)) continue;
            if (n++ >= 50) break;
            histPageBox.addView(payItem(f));
        }
        if (n == 0) {
            TextView e = text("Koi payment nahi mili.", 14, MUTED, false);
            e.setPadding(0, dp(18), 0, 0);
            histPageBox.addView(e);
        }
    }

    void renderSetupPage() {
        if (setupBox == null) return;
        setupBox.removeAllViews();
        String k = sp.getString("secret", "");
        boolean verified = k.length() >= 5 && sp.getBoolean("verified", true);
        boolean access = notifOn();
        boolean batt = battOk();
        boolean net = NotifService.online(this);
        boolean ttsOk = false, hiOk = false;
        try {
            ttsOk = ttsT != null && ttsTReady;
            if (ttsOk) hiOk = ttsT.isLanguageAvailable(new Locale("hi", "IN")) >= TextToSpeech.LANG_AVAILABLE;
        } catch (Exception ignored) { }
        LinearLayout c = card();
        TextView c1 = chip(), c2 = chip(), c3 = chip(), c4 = chip(), c5 = chip();
        setChip(c1, verified, "Logged in", "Not logged in");
        setChip(c2, access, "ON ✓", "OFF - Fix");
        setChip(c3, batt, "ON ✓", "OFF - Fix");
        setChip(c4, net, "Online ✓", "OFFLINE");
        setChip(c5, ttsOk && hiOk, "Hindi ✓", ttsOk ? "English only" : "Not ready");
        c.addView(row("Login Key", c1, null));
        c.addView(row("Notification Access", c2, v -> { if (!notifOn()) startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); }));
        c.addView(row("Background / Battery", c3, v -> {
            if (!battOk()) startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }));
        c.addView(row("Internet", c4, v -> startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS))));
        c.addView(row("Voice engine", c5, null));
        setupBox.addView(c);
        LinearLayout tc = card();
        tc.addView(text("AUTO AUR ALERT", 11, MUTED, true));
        tc.addView(toggleRow("power", "Phone on hote hi chalu", "Restart ke baad app kholna nahi padega", "autostart"));
        tc.addView(toggleRow("bell", "Pop-up alert", "Payment aate hi phone ke upar pop-up", "pop_on"));
        tc.addView(toggleRow("moon", "Raat 10 baje summary", "Aaj ka total notification mein", "daily_on"));
        setupBox.addView(tc);
        setupBox.addView(button("Pop-up settings kholo", false, v -> {
            try {
                Intent ni = Build.VERSION.SDK_INT >= 26 ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(ni);
            } catch (Exception ignored) { }
        }));
        setupBox.addView(button("Popup dekho (test)", false, v -> showPopup("paid", "2", "bp", "Saumya Ranjan", "")));
        LinearLayout lc = card();
        lc.addView(text("AAKHRI SIGNAL", 11, MUTED, true));
        long last = sp.getLong("last", 0);
        TextView lt = text(last > 0 ? new SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(new Date(last)) + "  (server " + sp.getInt("code", 0) + ")" : "Abhi koi signal nahi", 14, TXT, false);
        lt.setPadding(0, dp(8), 0, 0);
        lc.addView(lt);
        setupBox.addView(lc);
        setupBox.addView(button("Check connection", true, v -> verify(sp.getString("secret", ""), false)));
        setupBox.addView(button("Notification Access kholo", false, v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))));
        String warn = "";
        if (!access) warn = "Notification Access OFF hai. Payment detect nahi hogi. Upar 'Fix' dabao.";
        else if (!batt) warn = "Battery / Background OFF hai. Phone band hone par payment miss ho sakti hai. 'Fix' dabao.";
        else if (!net) warn = "Internet band hai. Payment server tak nahi pahunchegi.";
        else if (!verified) warn = "Login Key daalo.";
        if (!warn.isEmpty()) {
            TextView w = text(warn, 13, Color.parseColor("#FECACA"), false);
            w.setPadding(dp(12), dp(10), dp(12), dp(10));
            w.setBackground(box(Color.parseColor("#26DC2626"), 12, BAD));
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
            wp.topMargin = dp(14);
            w.setLayoutParams(wp);
            setupBox.addView(w);
        }
    }

    LinearLayout kv(String key, String val) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(8), 0, dp(8));
        r.addView(text(key, 15, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(text(val, 15, TXT, true));
        return r;
    }

    void renderAboutPage() {
        if (aboutBox == null) return;
        aboutBox.removeAllViews();
        String k = sp.getString("secret", "");
        String masked = k.length() > 8 ? k.substring(0, 3) + "••••" + k.substring(k.length() - 4) : "••••";
        LinearLayout c = card();
        c.addView(kv("Version", "v1.17"));
        c.addView(kv("Login Key", masked));
        c.addView(kv("Server", "jitustore.shop"));
        aboutBox.addView(c);
        aboutBox.addView(button("Open Panel", true, v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Sx.PANEL)))));
        aboutBox.addView(button("Logout", false, v -> {
            sp.edit().remove("secret").remove("verified").apply();
            Plan.clear(this);
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            jumpHome();
            refresh();
        }));
        LinearLayout h = card();
        h.addView(text("MADAD", 11, MUTED, true));
        String[][] qa = {
            {"Payment nahi aa rahi?", "Setup Check kholo, sab ON hona chahiye."},
            {"Awaaz nahi aa rahi?", "Media volume badhao aur Voice Alert ON rakho."},
            {"Laal box kya hai?", "Galat amount ya bina order ki payment. Credit nahi hoga."}
        };
        for (String[] q : qa) {
            TextView a = text(q[0], 14, TXT, true);
            a.setPadding(0, dp(12), 0, 0);
            h.addView(a);
            h.addView(text(q[1], 13, MUTED, false));
        }
        aboutBox.addView(h);
    }


    // ================= SPLASH (opening screen) =================
    static boolean splashShown = false;
    java.util.ArrayList<android.animation.Animator> splashAnims = new java.util.ArrayList<>();

    void floatAnim(View v, int dyDp, long dur, float baseRot, float rotDelta, long delay) {
        android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(v, "translationY", -dp(dyDp), dp(dyDp));
        a.setDuration(dur);
        a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        a.setStartDelay(delay);
        a.start();
        splashAnims.add(a);
        v.setRotation(baseRot - rotDelta);
        android.animation.ObjectAnimator r = android.animation.ObjectAnimator.ofFloat(v, "rotation", baseRot - rotDelta, baseRot + rotDelta);
        r.setDuration(dur + 900);
        r.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        r.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        r.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        r.setStartDelay(delay);
        r.start();
        splashAnims.add(r);
    }

    TextView chipFloat(String s, int c1, int c2, int size) {
        TextView t = text(s, size, Color.WHITE, true);
        t.setPadding(dp(13), dp(9), dp(13), dp(9));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        g.setCornerRadius(dp(16));
        t.setBackground(g);
        return t;
    }

    LinearLayout payMini(String amt, String chipText, boolean good) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8), dp(7), dp(8), dp(7));
        r.setBackground(box(good ? Color.parseColor("#3316A34A") : Color.parseColor("#33DC2626"), 10, good ? GOOD : BAD));
        r.addView(text(amt, 12, good ? Color.parseColor("#34D399") : Color.parseColor("#F87171"), true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView c = text(chipText, 7, Color.WHITE, true);
        c.setPadding(dp(6), dp(3), dp(6), dp(3));
        c.setBackground(box(good ? GOOD : BAD, 8, 0));
        r.addView(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        r.setLayoutParams(lp);
        return r;
    }

    View buildPhoneView(TextView[] totalOut) {
        FrameLayout body = new FrameLayout(this);
        GradientDrawable pg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#8B8FFF"), Color.parseColor("#3B2F9C"), Color.parseColor("#1B1555")});
        pg.setCornerRadius(dp(36));
        body.setBackground(pg);
        LinearLayout scr = new LinearLayout(this);
        scr.setOrientation(LinearLayout.VERTICAL);
        scr.setPadding(dp(12), dp(22), dp(12), dp(12));
        scr.setBackground(box(Color.parseColor("#0B1128"), 31, 0));
        FrameLayout.LayoutParams sp1 = new FrameLayout.LayoutParams(-1, -1);
        sp1.setMargins(dp(5), dp(5), dp(5), dp(5));
        body.addView(scr, sp1);
        View notch = new View(this);
        notch.setBackground(box(Color.parseColor("#04060F"), 8, 0));
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(dp(54), dp(12), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nl.topMargin = dp(12);
        body.addView(notch, nl);

        LinearLayout hd = new LinearLayout(this);
        hd.setOrientation(LinearLayout.HORIZONTAL);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        TextView lg = text("JS", 12, Color.WHITE, true);
        lg.setGravity(Gravity.CENTER);
        GradientDrawable lgd = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#6366F1"), Color.parseColor("#8B5CF6")});
        lgd.setCornerRadius(dp(9));
        lg.setBackground(lgd);
        hd.addView(lg, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout nm = new LinearLayout(this);
        nm.setOrientation(LinearLayout.VERTICAL);
        nm.setPadding(dp(7), 0, 0, 0);
        nm.addView(text("JS PAY", 12, Color.WHITE, true));
        nm.addView(text("GPay Auto Verify", 7, MUTED, false));
        hd.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView lv = text("LIVE", 8, Color.WHITE, true);
        lv.setPadding(dp(7), dp(3), dp(7), dp(3));
        lv.setBackground(box(GOOD, 10, 0));
        hd.addView(lv);
        scr.addView(hd);

        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.setPadding(dp(9), dp(9), dp(9), dp(9));
        tc.setBackground(box(CARD, 12, LINE));
        tc.addView(text("AAJ TOTAL", 7, MUTED, true));
        TextView tot = text("₹0", 26, Color.parseColor("#34D399"), true);
        tc.addView(tot);
        tc.addView(text("5 payments", 8, MUTED, false));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(12);
        scr.addView(tc, tlp);
        totalOut[0] = tot;
        scr.addView(payMini("₹26", "✓ Order PAID", true));
        scr.addView(payMini("₹2", "✕ Galat amount", false));
        scr.addView(payMini("₹11", "✓ Order PAID", true));
        scr.addView(payMini("₹100", "✓ Order PAID", true));
        LinearLayout vb = new LinearLayout(this);
        vb.setGravity(Gravity.CENTER_VERTICAL);
        vb.setPadding(dp(10), dp(8), dp(10), dp(8));
        vb.setBackground(box(CARD, 10, LINE));
        vb.addView(text("Voice & Alerts", 10, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView von = text("ON ✓", 8, Color.WHITE, true);
        von.setPadding(dp(7), dp(3), dp(7), dp(3));
        von.setBackground(box(GOOD, 10, 0));
        vb.addView(von);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.topMargin = dp(10);
        scr.addView(vb, vlp);
        return body;
    }

    View buildSplash() {
        final FrameLayout sp0 = new FrameLayout(this);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bgd.setGradientCenter(0.5f, 0.38f);
        bgd.setGradientRadius(dp(520));
        bgd.setColors(new int[]{Color.parseColor("#3A2A9C"), Color.parseColor("#16124A"), Color.parseColor("#0B1020")});
        sp0.setBackground(bgd);
        sp0.setClickable(true);
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        int top = (int) (sh * 0.09f);

        // glow
        View glow = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(dp(210));
        gd.setColors(new int[]{Color.parseColor("#778B5CF6"), Color.parseColor("#008B5CF6")});
        glow.setBackground(gd);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(dp(420), dp(420), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        gl.topMargin = top - dp(10);
        sp0.addView(glow, gl);
        android.animation.ObjectAnimator ga = android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.55f, 1f);
        ga.setDuration(1500);
        ga.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        ga.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        ga.start();
        splashAnims.add(ga);

        // phone
        final TextView[] tot = new TextView[1];
        View phone = buildPhoneView(tot);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(dp(200), dp(400), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pl.topMargin = top;
        sp0.addView(phone, pl);
        floatAnim(phone, 9, 2100, -7f, 2.5f, 0);

        // floating chips
        TextView c1 = chipFloat("✓ ₹26 Order PAID", Color.parseColor("#16A34A"), Color.parseColor("#059669"), 13);
        FrameLayout.LayoutParams l1 = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        l1.leftMargin = dp(14);
        l1.topMargin = top + dp(262);
        sp0.addView(c1, l1);
        floatAnim(c1, 8, 1700, -6f, 3f, 200);
        TextView c2 = chipFloat("₹78 aaj mila", Color.parseColor("#6366F1"), Color.parseColor("#8B5CF6"), 13);
        FrameLayout.LayoutParams l2 = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        l2.rightMargin = dp(10);
        l2.topMargin = top + dp(200);
        sp0.addView(c2, l2);
        floatAnim(c2, 10, 1900, 5f, 3f, 450);
        TextView c3 = chipFloat("Auto verify · 24x7", Color.parseColor("#0EA5E9"), Color.parseColor("#6366F1"), 12);
        FrameLayout.LayoutParams l3 = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        l3.leftMargin = dp(26);
        l3.topMargin = top + dp(335);
        sp0.addView(c3, l3);
        floatAnim(c3, 7, 1500, 4f, 3f, 700);

        // sparkle dots
        int[][] dots = {{40, 60, 6}, {sw / 3 + 120, 90, 8}, {70, 520, 10}, {sw - 80, 500, 7}};
        for (int i = 0; i < dots.length; i++) {
            View d = new View(this);
            d.setBackground(box(i % 2 == 0 ? Color.parseColor("#A78BFA") : Color.parseColor("#34D399"), 8, 0));
            FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(dp(dots[i][2]), dp(dots[i][2]), Gravity.TOP | Gravity.LEFT);
            dl.leftMargin = dp(dots[i][0] > 600 ? 300 : dots[i][0] / 3);
            dl.topMargin = (int) (sh * 0.06f) + dp(dots[i][1] / 2);
            sp0.addView(d, dl);
            floatAnim(d, 6 + i * 2, 1400 + i * 300, 0f, 0f, i * 150);
        }

        // bottom: title + loading
        LinearLayout lw = new LinearLayout(this);
        lw.setOrientation(LinearLayout.VERTICAL);
        lw.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = text("JS PAY", 28, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        lw.addView(title);
        TextView sub = text("GPay Auto Verify", 13, Color.parseColor("#B4BCF0"), false);
        sub.setGravity(Gravity.CENTER);
        lw.addView(sub);
        final int trackW = sw - dp(60);
        FrameLayout track = new FrameLayout(this);
        track.setBackground(box(Color.parseColor("#1FFFFFFF"), 6, 0));
        final View fill = new View(this);
        GradientDrawable fg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#6366F1"), Color.parseColor("#A78BFA"), Color.parseColor("#34D399")});
        fg.setCornerRadius(dp(6));
        fill.setBackground(fg);
        track.addView(fill, new FrameLayout.LayoutParams(1, -1));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(trackW, dp(7));
        tl.topMargin = dp(18);
        lw.addView(track, tl);
        final TextView st = text("Connecting to server...", 13, Color.parseColor("#C7D2FE"), false);
        st.setGravity(Gravity.CENTER);
        st.setPadding(0, dp(10), 0, 0);
        lw.addView(st);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        wl.bottomMargin = dp(56);
        sp0.addView(lw, wl);

        final boolean[] done = {false};
        final Runnable finish = () -> {
            if (done[0]) return;
            done[0] = true;
            sp0.animate().alpha(0f).setDuration(380).withEndAction(() -> {
                for (android.animation.Animator a : splashAnims) a.cancel();
                splashAnims.clear();
                if (sp0.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) sp0.getParent()).removeView(sp0);
                planTick();
            }).start();
        };
        sp0.setOnClickListener(v -> finish.run());
        android.animation.ValueAnimator load = android.animation.ValueAnimator.ofFloat(0f, 1f);
        load.setDuration(3000);
        load.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        load.addUpdateListener(an -> {
            float f = (Float) an.getAnimatedValue();
            FrameLayout.LayoutParams fl = (FrameLayout.LayoutParams) fill.getLayoutParams();
            fl.width = Math.max(1, (int) (trackW * f));
            fill.setLayoutParams(fl);
            tot[0].setText("₹" + (int) (78 * f));
            st.setText(f < 0.35f ? "Connecting to server..." : f < 0.7f ? "Checking setup..." : f < 1f ? "Almost ready..." : "Ready");
            if (f >= 1f) finish.run();
        });
        load.start();
        splashAnims.add(load);
        return sp0;
    }


    // ================= PREMIUM PLAN (date + live countdown + lock) =================
    LinearLayout planBox;
    TextView planHead, planDate, planNote, planBtn;
    TextView[] planNum = new TextView[4];
    FrameLayout lockView;
    TextView lockDate;
    View splashRef;
    boolean planTicking = false;
    final android.os.Handler planH = new android.os.Handler(android.os.Looper.getMainLooper());

    LinearLayout buildPlanCard() {
        planBox = new LinearLayout(this);
        planBox.setOrientation(LinearLayout.VERTICAL);
        planBox.setPadding(dp(14), dp(12), dp(14), dp(14));
        planBox.setVisibility(View.GONE);
        planHead = text("PREMIUM PLAN", 11, MUTED, true);
        planBox.addView(planHead);
        LinearLayout r1 = new LinearLayout(this);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        r1.addView(text("Valid till", 14, TXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        planDate = text("-", 15, Color.WHITE, true);
        r1.addView(planDate);
        LinearLayout.LayoutParams r1p = new LinearLayout.LayoutParams(-1, -2);
        r1p.topMargin = dp(6);
        planBox.addView(r1, r1p);
        LinearLayout cd = new LinearLayout(this);
        String[] nm = {"DIN", "GHANTE", "MIN", "SEC"};
        for (int i = 0; i < 4; i++) {
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, dp(7), 0, dp(7));
            b.setBackground(box(Color.parseColor("#0B1128"), 10, Color.parseColor("#2B3A73")));
            planNum[i] = text("--", 22, Color.WHITE, true);
            planNum[i].setGravity(Gravity.CENTER);
            b.addView(planNum[i]);
            TextView l = text(nm[i], 9, MUTED, false);
            l.setGravity(Gravity.CENTER);
            b.addView(l);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) bp.leftMargin = dp(7);
            cd.addView(b, bp);
        }
        LinearLayout.LayoutParams cdp = new LinearLayout.LayoutParams(-1, -2);
        cdp.topMargin = dp(10);
        planBox.addView(cd, cdp);
        planNote = text("", 12, Color.parseColor("#FDE68A"), false);
        planNote.setPadding(0, dp(9), 0, 0);
        planNote.setVisibility(View.GONE);
        planBox.addView(planNote);
        planBtn = text("Renew karo", 14, Color.WHITE, true);
        planBtn.setGravity(Gravity.CENTER);
        planBtn.setPadding(0, dp(10), 0, dp(10));
        planBtn.setBackground(box(PRIMARY, 12, 0));
        planBtn.setVisibility(View.GONE);
        planBtn.setOnClickListener(v -> openPremium());
        press(planBtn);
        LinearLayout.LayoutParams pbp = new LinearLayout.LayoutParams(-1, -2);
        pbp.topMargin = dp(10);
        planBox.addView(planBtn, pbp);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = dp(16);
        planBox.setLayoutParams(pp);
        return planBox;
    }

    void openPremium() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Sx.PREMIUM))); } catch (Exception ignored) { }
    }

    FrameLayout buildLock() {
        lockView = new FrameLayout(this);
        lockView.setBackgroundColor(Color.parseColor("#E6020410"));
        lockView.setClickable(true);
        lockView.setFocusable(true);
        lockView.setOnTouchListener((v, e) -> true);
        lockView.setVisibility(View.GONE);
        LinearLayout pop = new LinearLayout(this);
        pop.setOrientation(LinearLayout.VERTICAL);
        pop.setGravity(Gravity.CENTER_HORIZONTAL);
        pop.setPadding(dp(18), dp(22), dp(18), dp(20));
        pop.setBackground(box(Color.parseColor("#14091C"), 24, Color.parseColor("#EF4444")));
        TextView ex = text("!", 34, Color.parseColor("#F87171"), true);
        ex.setGravity(Gravity.CENTER);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(3), Color.parseColor("#EF4444"));
        ex.setBackground(ring);
        pop.addView(ex, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView badge = text("PLAN EXPIRED", 12, Color.WHITE, true);
        badge.setPadding(dp(14), dp(4), dp(14), dp(4));
        badge.setBackground(box(BAD, 14, 0));
        LinearLayout.LayoutParams bgp = new LinearLayout.LayoutParams(-2, -2);
        bgp.topMargin = dp(12);
        pop.addView(badge, bgp);
        TextView t = text("Your plan is expired", 24, Color.parseColor("#F87171"), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, 0);
        pop.addView(t);
        lockDate = text("", 12, MUTED, false);
        lockDate.setGravity(Gravity.CENTER);
        pop.addView(lockDate);
        TextView msg = text("Plan jaldi renew kar lo.\nAapka payment ruka hua hai. Ab koi payment karega to auto verification kaam nahi karega aur order PAID nahi hoga.", 13, Color.parseColor("#FECACA"), false);
        msg.setPadding(dp(12), dp(11), dp(12), dp(11));
        msg.setBackground(box(Color.parseColor("#26DC2626"), 12, BAD));
        LinearLayout.LayoutParams mp2 = new LinearLayout.LayoutParams(-1, -2);
        mp2.topMargin = dp(14);
        pop.addView(msg, mp2);
        TextView go = text("Renew karo — ShopBot Builder kholo", 14, Color.WHITE, true);
        go.setGravity(Gravity.CENTER);
        go.setPadding(0, dp(13), 0, dp(13));
        go.setBackground(box(PRIMARY, 13, 0));
        go.setOnClickListener(v -> openPremium());
        press(go);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.topMargin = dp(14);
        pop.addView(go, gp);
        final TextView again = text("Renew kar liya? Phir se check karo", 12, Color.parseColor("#93C5FD"), true);
        again.setGravity(Gravity.CENTER);
        again.setPadding(0, dp(12), 0, dp(2));
        again.setOnClickListener(v -> {
            again.setText("Check ho raha hai...");
            Plan.refresh(this, () -> { again.setText("Renew kar liya? Phir se check karo"); planTick(); });
        });
        pop.addView(again);
        TextView sm = text("Jab tak renew nahi hoga, app ke saare button band rahenge.", 11, MUTED, false);
        sm.setGravity(Gravity.CENTER);
        sm.setPadding(0, dp(8), 0, 0);
        pop.addView(sm);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        lp.setMargins(dp(20), 0, dp(20), 0);
        lockView.addView(pop, lp);
        return lockView;
    }

    boolean splashDone() { return splashRef == null || splashRef.getParent() == null; }

    boolean isLocked() { return lockView != null && lockView.getVisibility() == View.VISIBLE; }

    void planTick() {
        if (planBox == null) return;
        if (!Plan.has(this)) {
            if (lockView != null) lockView.setVisibility(View.GONE);
            if (Plan.life(this)) {
                planBox.setVisibility(View.VISIBLE);
                planBox.setBackground(box(CARD, 16, LINE));
                planHead.setText("PREMIUM PLAN");
                planHead.setTextColor(MUTED);
                planDate.setText("Lifetime / No expiry");
                planDate.setTextColor(Color.parseColor("#4ADE80"));
                for (TextView n : planNum) { n.setText("\u221E"); n.setTextColor(Color.parseColor("#4ADE80")); }
                planNote.setVisibility(View.GONE);
                planBtn.setVisibility(View.GONE);
            } else planBox.setVisibility(View.GONE);
            return;
        }
        long left = Plan.leftMs(this);
        String d = new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date(Plan.expTs(this) * 1000L));
        planBox.setVisibility(View.VISIBLE);
        planDate.setText(d);
        if (left <= 0) {
            for (TextView n : planNum) n.setText("00");
            planBox.setBackground(box(Color.parseColor("#26DC2626"), 16, BAD));
            planHead.setText("PLAN EXPIRED");
            planHead.setTextColor(Color.parseColor("#F87171"));
            planDate.setTextColor(Color.parseColor("#F87171"));
            lockDate.setText("Khatam hui: " + d);
            if (splashDone() && lockView.getVisibility() != View.VISIBLE) {
                lockView.setAlpha(0f);
                lockView.setVisibility(View.VISIBLE);
                lockView.animate().alpha(1f).setDuration(300).start();
                if (!cur.equals("home")) jumpHome();
            }
            return;
        }
        if (lockView.getVisibility() == View.VISIBLE) lockView.setVisibility(View.GONE);
        long s = left / 1000L;
        planNum[0].setText(String.valueOf(s / 86400));
        planNum[1].setText(String.format(Locale.US, "%02d", (s % 86400) / 3600));
        planNum[2].setText(String.format(Locale.US, "%02d", (s % 3600) / 60));
        planNum[3].setText(String.format(Locale.US, "%02d", s % 60));
        boolean soon = s < 3 * 86400L;
        int col = soon ? Color.parseColor("#FBBF24") : Color.WHITE;
        for (TextView n : planNum) n.setTextColor(col);
        planDate.setTextColor(col);
        planBox.setBackground(soon ? box(Color.parseColor("#2A1D0A"), 16, Color.parseColor("#D97706")) : box(CARD, 16, LINE));
        planHead.setText(soon ? "⚠ PLAN JALDI KHATAM HOGA" : "PREMIUM PLAN");
        planHead.setTextColor(soon ? Color.parseColor("#FBBF24") : MUTED);
        planNote.setText("Renew kar lo, warna payment verify band ho jayegi.");
        planNote.setVisibility(soon ? View.VISIBLE : View.GONE);
        planBtn.setVisibility(soon ? View.VISIBLE : View.GONE);
    }

    void startPlanTicker() {
        if (planTicking) return;
        planTicking = true;
        planH.post(new Runnable() {
            int n = 0;
            @Override public void run() {
                if (!planTicking) return;
                planTick();
                if (isLocked() && ++n % 30 == 0) Plan.refresh(MainActivity.this, MainActivity.this::planTick);
                planH.postDelayed(this, 1000);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        front = this;
        if (bg != null) bg.go();
        planTick();
        startPlanTicker();
        Plan.refresh(this, this::planTick);
        refresh();
        onShow(cur);
    }

    @Override
    protected void onPause() {
        super.onPause();
        hidePopup();
        if (front == this) front = null;
        planTicking = false;
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
            if (!fromLogin) {
                if (!notifOn()) {
                    sErr();
                    Toast.makeText(this, "Notification Access is OFF. Payments will NOT be detected. Tap the red box to fix.", Toast.LENGTH_LONG).show();
                } else if (!battOk()) {
                    sErr();
                    Toast.makeText(this, "Background / Battery is OFF. Payments may be missed. Tap the red box to fix.", Toast.LENGTH_LONG).show();
                } else {
                    sOk();
                    Toast.makeText(this, "Connection OK \u2713 Everything is ON", Toast.LENGTH_SHORT).show();
                }
            } else sOk();
        } else if (rc == 401) {
            sErr();
            sp.edit().remove("secret").remove("verified").apply();
            Plan.clear(this);
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
            boolean expd = ok && res.equals("expired");
            boolean bad = wrong || noord || expd;
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
            String whoS = whoOf(f);
            if (!whoS.isEmpty()) {
                TextView who = text(whoS, 14, TXT, false);
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
            else if (expd) { c.setText("\u2715 Premium khatam"); c.setBackground(box(BAD, 20, 0)); }
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
                        : expd ? "Premium khatam. Renew karo, tab payment verify hogi."
                        : expd ? "Premium khatam. Renew karo, tab payment verify hogi."
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
