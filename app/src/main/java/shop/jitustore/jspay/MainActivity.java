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
    ScrollView sv2;
    FrameLayout frameRef;
    int page = 0;
    float dX, dY;
    View dot0, dot1;
    TextView vSw, vHi, vEn, vAuto, vAll, vWarn, vSample1, vSample2, vSample3, vToday1, vToday2, vToday3;
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

        sv = new ScrollView(this);
        sv.setBackgroundColor(Color.TRANSPARENT);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(36), dp(20), dp(64));
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
        TextView foot = text("Secured by JS PAY  •  v1.7", 12, Color.parseColor("#64748B"), false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        bg = new BgView(this);
        frame.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        frameRef = frame;
        sv2 = buildPage2();
        frame.addView(sv2, new FrameLayout.LayoutParams(-1, -1));
        sv2.setTranslationX(getResources().getDisplayMetrics().widthPixels);
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        dot0 = new View(this);
        dot1 = new View(this);
        LinearLayout.LayoutParams dp0 = new LinearLayout.LayoutParams(dp(22), dp(8));
        dp0.setMargins(dp(8), dp(12), dp(8), dp(12));
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(dp(8), dp(8));
        dp1.setMargins(dp(8), dp(12), dp(8), dp(12));
        dots.addView(dot0, dp0);
        dots.addView(dot1, dp1);
        dot0.setOnClickListener(v -> showPage(0));
        dot1.setOnClickListener(v -> showPage(1));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = dp(6);
        frame.addView(dots, dlp);
        paintDots();
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


    void paintDots() {
        dot0.setBackground(box(page == 0 ? Color.parseColor("#818CF8") : Color.parseColor("#3B4570"), 6, 0));
        dot1.setBackground(box(page == 1 ? Color.parseColor("#818CF8") : Color.parseColor("#3B4570"), 6, 0));
        LinearLayout.LayoutParams a = (LinearLayout.LayoutParams) dot0.getLayoutParams();
        LinearLayout.LayoutParams b = (LinearLayout.LayoutParams) dot1.getLayoutParams();
        a.width = dp(page == 0 ? 22 : 8);
        b.width = dp(page == 1 ? 22 : 8);
        dot0.setLayoutParams(a);
        dot1.setLayoutParams(b);
    }

    void showPage(int p) {
        if (p == page || sv == null) return;
        page = p;
        int w = getResources().getDisplayMetrics().widthPixels;
        sv.animate().translationX(p == 0 ? 0 : -w).setDuration(260).start();
        sv2.animate().translationX(p == 0 ? w : 0).setDuration(260).start();
        paintDots();
        sTap();
        if (p == 1) refreshVoiceUi();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) { dX = ev.getX(); dY = ev.getY(); }
            else if (ev.getAction() == MotionEvent.ACTION_UP) {
                float dx = ev.getX() - dX, dy = ev.getY() - dY;
                if (Math.abs(dx) > dp(90) && Math.abs(dx) > Math.abs(dy) * 1.6f) {
                    if (dx < 0) showPage(1); else showPage(0);
                }
            }
        } catch (Exception ignored) { }
        return super.dispatchTouchEvent(ev);
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
        r2.setPadding(dp(20), dp(36), dp(20), dp(64));
        s2.addView(r2);

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
        names.addView(text("Voice & Alerts", 22, TXT, true));
        names.addView(text("Advanced settings", 13, MUTED, false));
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
        c3.addView(text("AAJ", 11, MUTED, true));
        vToday1 = text("", 15, TXT, false);
        vToday2 = text("", 15, TXT, false);
        vToday3 = text("", 15, TXT, false);
        for (TextView t : new TextView[]{vToday1, vToday2, vToday3}) {
            t.setPadding(0, dp(8), 0, 0);
            c3.addView(t);
        }
        r2.addView(c3);
        return s2;
    }

    void setLang(String l) { sp.edit().putString("voice_lang", l).apply(); sTap(); refreshVoiceUi(); }
    void setMode(String m) { sp.edit().putString("voice_mode", m).apply(); sTap(); refreshVoiceUi(); }

    String curLang() {
        String pref = sp.getString("voice_lang", "auto");
        if (pref.equals("hi")) return "hi";
        if (pref.equals("en")) return "en";
        return "hi";
    }

    void refreshVoiceUi() {
        if (vSw == null) return;
        boolean on = sp.getBoolean("voice_on", true);
        setChip(vSw, on, "ON \u2713", "OFF");
        String l = sp.getString("voice_lang", "auto");
        paintSeg(vHi, l.equals("hi"));
        paintSeg(vEn, l.equals("en"));
        paintSeg(vAuto, l.equals("auto"));
        String m = sp.getString("voice_mode", "all");
        paintSeg(vAll, m.equals("all"));
        paintSeg(vWarn, m.equals("warn"));
        String lg = curLang();
        vSample1.setText("\u201C" + Voice.say("paid", lg, "26", "") + "\u201D");
        vSample2.setText("\u201C" + Voice.say("wrong", lg, "2", "500") + "\u201D");
        vSample3.setText("\u201C" + Voice.say("noorder", lg, "5", "") + "\u201D");
        renderToday();
    }

    void testVoice() {
        if (ttsT == null || !ttsTReady) {
            Toast.makeText(this, "Voice engine is not ready. Check Settings > Text-to-speech.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ttsT.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            String lang = Voice.pick(ttsT, sp.getString("voice_lang", "auto"));
            if (lang.equals("en") && !sp.getString("voice_lang", "auto").equals("en")) {
                Toast.makeText(this, "Hindi voice not found on this phone. Using English.", Toast.LENGTH_LONG).show();
            }
            ttsT.speak(Voice.say("paid", lang, "26", ""), TextToSpeech.QUEUE_FLUSH, null, "test1");
            ttsT.speak(Voice.say("wrong", lang, "2", "500"), TextToSpeech.QUEUE_ADD, null, "test2");
        } catch (Exception e) {
            Toast.makeText(this, "Could not play voice.", Toast.LENGTH_SHORT).show();
        }
    }

    void renderToday() {
        if (vToday1 == null) return;
        String h = sp.getString("hist", "");
        int cnt = 0, warn = 0;
        double tot = 0;
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        if (!h.isEmpty()) {
            for (String line : h.split("\n")) {
                String[] f = line.split("\\|", 6);
                if (f.length < 4) continue;
                try {
                    long ts = Long.parseLong(f[0]);
                    if (ts < start) continue;
                    int code = Integer.parseInt(f[1]);
                    String res = f.length > 4 ? f[4] : "";
                    cnt++;
                    if (code == 200 && (res.equals("wrong") || res.equals("no_order"))) warn++;
                    else if (code == 200) tot += Double.parseDouble(f[2].replace(",", ""));
                } catch (Exception ignored) { }
            }
        }
        String ts2 = (tot == Math.floor(tot)) ? String.valueOf((long) tot) : String.format(Locale.US, "%.2f", tot);
        vToday1.setText("Payments:  " + cnt);
        vToday2.setText("Total received:  \u20B9" + ts2);
        vToday3.setText("Warnings:  " + warn);
        vToday3.setTextColor(warn > 0 ? Color.parseColor("#F87171") : TXT);
    }

    @Override
    protected void onDestroy() {
        try { if (ttsT != null) { ttsT.stop(); ttsT.shutdown(); } } catch (Exception ignored) { }
        super.onDestroy();
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
        renderToday();
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
