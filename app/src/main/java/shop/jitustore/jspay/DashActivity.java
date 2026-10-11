package shop.jitustore.jspay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Home + Profile (light purple theme), Attached Cashier (server code + Google sign-in). All text is English. */
public class DashActivity extends Activity {
    static final String START = "https://pay.google.com/g4b/";
    static final int BG = Color.parseColor("#FFFFFF");
    static final int HEAD = Color.parseColor("#ECE8FF");
    static final int TONAL = Color.parseColor("#DDD6FF");
    static final int PRIMARY = Color.parseColor("#6246EA");
    static final int TXT = Color.parseColor("#1A1740");
    static final int MUTED = Color.parseColor("#6B6890");
    static final int LINE = Color.parseColor("#D9D3F5");
    static final int GREEN = Color.parseColor("#16A34A");
    static final int RED = Color.parseColor("#DC2626");
    static final int NAVBG = Color.parseColor("#F3F0FF");

    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private ScrollView homeScroll, profileScroll;
    private LinearLayout dyn, navHome, navProfile;
    private HorizontalScrollView hs;
    private LinearLayout dotsRow;
    private WebView web;
    private int tab = 0, period = 1, curBanner = 0, sw = 0;
    private String sig = "";
    private boolean webMode = false, navigated = false, finishing2 = false;
    private long backAt = 0, webStart = 0, lastTouch = 0, lastKick = 0;

    private final Runnable listTick = new Runnable() {
        @Override public void run() {
            if (!webMode) { watchdog(); refresh(false); h.postDelayed(this, 3000); }
        }
    };
    private final Runnable autoSwipe = new Runnable() {
        @Override public void run() {
            try {
                if (!webMode && hs != null && tab == 0 && System.currentTimeMillis() - lastTouch > 5000) {
                    int next = (curBanner + 1) % 4;
                    hs.smoothScrollTo(next * sw, 0);
                }
            } catch (Exception ignored) { }
            h.postDelayed(this, 4000);
        }
    };

    /** Background service ko zinda rakho. Start request sirf tab jab service sach mein band lage. */
    void watchdog() {
        try {
            if (sp.getString("cashier_url", "").isEmpty()) return;
            long now = System.currentTimeMillis();
            if (now - sp.getLong("svc_ok", 0) > 120000L && now - lastKick > 150000L) {
                lastKick = now;
                DashService.stop(this);
                DashService.ensure(this, true);
                return;
            }
            DashService.ensure(this, false);
        } catch (Exception ignored) { }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!webMode) {
            watchdog();
            refresh(true);
            h.removeCallbacks(listTick);
            h.postDelayed(listTick, 3000);
            h.removeCallbacks(autoSwipe);
            h.postDelayed(autoSwipe, 4000);
        }
    }

    @Override protected void onPause() {
        h.removeCallbacks(autoSwipe);
        super.onPause();
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView tv(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
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

    static String inr(double v) {
        boolean whole = Math.abs(v - Math.rint(v)) < 0.005;
        String s = whole ? String.valueOf((long) Math.rint(v)) : String.format(Locale.US, "%.2f", v);
        String ip = s, fp = "";
        int d = s.indexOf('.');
        if (d >= 0) { ip = s.substring(0, d); fp = s.substring(d); }
        if (ip.length() > 3) {
            String last3 = ip.substring(ip.length() - 3);
            String rest = ip.substring(0, ip.length() - 3);
            StringBuilder sb = new StringBuilder();
            while (rest.length() > 2) {
                sb.insert(0, "," + rest.substring(rest.length() - 2));
                rest = rest.substring(0, rest.length() - 2);
            }
            sb.insert(0, rest);
            ip = sb.toString() + "," + last3;
        }
        return "₹" + ip + fp;
    }

    // ---------------------------------------------------------------- small drawn icons (home / profile)
    static class Glyph extends Drawable {
        final boolean home;
        final int col, px;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph(boolean home, int col, int px) { this.home = home; this.col = col; this.px = px; }
        @Override public int getIntrinsicWidth() { return px; }
        @Override public int getIntrinsicHeight() { return px; }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float s = Math.min(b.width(), b.height()) / 24f;
            c.save();
            c.translate(b.left, b.top);
            c.scale(s, s);
            p.setColor(col);
            p.setStyle(Paint.Style.FILL);
            if (home) {
                Path q = new Path();
                q.moveTo(12, 3);
                q.lineTo(21, 11);
                q.lineTo(18.5f, 11);
                q.lineTo(18.5f, 20);
                q.lineTo(14, 20);
                q.lineTo(14, 14.5f);
                q.lineTo(10, 14.5f);
                q.lineTo(10, 20);
                q.lineTo(5.5f, 20);
                q.lineTo(5.5f, 11);
                q.lineTo(3, 11);
                q.close();
                c.drawPath(q, p);
            } else {
                c.drawCircle(12, 8, 4.2f, p);
                c.drawArc(new RectF(4.5f, 13f, 19.5f, 27f), 180, 180, true, p);
            }
            c.restore();
        }
    }

    // ---------------------------------------------------------------- banner
    static final String[] B_TITLE = {"Verify payments instantly", "Fake alerts get blocked", "Voice alert for every payment", "Use BharatPe too"};

    class BannerView extends View {
        final int idx;
        final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint bt = new Paint(Paint.ANTI_ALIAS_FLAG);

        BannerView(Context c, int idx) { super(c); this.idx = idx; setClickable(true); }

        String label() {
            if (idx == 0) return sp.getString("cashier_url", "").isEmpty() ? "Add Cashier" : "View cashier";
            if (idx == 1) return "How it works";
            if (idx == 2) return "Voice alerts";
            return "Connect BharatPe";
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = View.MeasureSpec.getSize(wSpec);
            setMeasuredDimension(w, (int) (w * 290f / 900f));
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), hh = getHeight();
            cv.drawColor(HEAD);
            Bitmap bmp = Art.get(idx);
            if (bmp != null) cv.drawBitmap(bmp, null, new android.graphics.Rect(0, 0, w, hh), bp);
            tp.setColor(TXT);
            tp.setTextSize(w * 0.046f);
            tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            int tw = (int) (w * 0.42f);
            StaticLayout sl = new StaticLayout(B_TITLE[idx], tp, tw, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false);
            cv.save();
            cv.translate(w * 0.055f, hh * 0.20f);
            sl.draw(cv);
            cv.restore();
            String lb = label();
            bt.setColor(Color.WHITE);
            bt.setTextSize(w * 0.036f);
            bt.setTypeface(Typeface.DEFAULT_BOLD);
            float textW = bt.measureText(lb);
            float left = w * 0.055f, top = hh * 0.64f;
            float right = left + textW + w * 0.09f, bottom = top + hh * 0.24f;
            fill.setColor(PRIMARY);
            cv.drawRoundRect(new RectF(left, top, right, bottom), (bottom - top) / 2f, (bottom - top) / 2f, fill);
            Paint.FontMetrics fm = bt.getFontMetrics();
            cv.drawText(lb, left + w * 0.045f, (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f, bt);
        }
    }

    void bannerTap(int i) {
        if (i == 0) { if (sp.getString("cashier_url", "").isEmpty()) askCode(); else showCashier(); }
        else if (i == 1) showHelp();
        else if (i == 2) openPage("voice");
        else openPage("bharat");
    }

    void updateDots(int cur) {
        if (dotsRow == null) return;
        curBanner = cur;
        for (int i = 0; i < dotsRow.getChildCount(); i++) {
            View d = dotsRow.getChildAt(i);
            boolean on = i == cur;
            d.setBackground(box(on ? PRIMARY : Color.parseColor("#B9B0F0"), 4, 0));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
            lp.width = dp(on ? 18 : 6);
            d.setLayoutParams(lp);
        }
    }

    // ---------------------------------------------------------------- lifecycle
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("c", MODE_PRIVATE);
        sw = getResources().getDisplayMetrics().widthPixels;
        try {
            getWindow().setStatusBarColor(HEAD);
            getWindow().setNavigationBarColor(NAVBG);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        } catch (Throwable ignored) { }
        try { showMain(); }
        catch (Throwable e) {
            TextView t = new TextView(this);
            t.setBackgroundColor(BG);
            t.setTextColor(TXT);
            t.setPadding(dp(16), dp(40), dp(16), dp(16));
            t.setText("Could not open. Please send a screenshot of this screen:\n\n" + e);
            setContentView(t);
        }
    }

    // ---------------------------------------------------------------- main screen (Home + Profile)
    void showMain() {
        webMode = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            getWindow().setStatusBarColor(HEAD);
            getWindow().setNavigationBarColor(NAVBG);
        } catch (Throwable ignored) { }
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        homeScroll = buildHome();
        profileScroll = new ScrollView(this);
        profileScroll.setBackgroundColor(BG);
        profileScroll.setVisibility(View.GONE);
        root.addView(homeScroll, new FrameLayout.LayoutParams(-1, -1));
        root.addView(profileScroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(NAVBG);
        navHome = navItem(true, "Home", 0);
        navProfile = navItem(false, "Profile", 1);
        nav.addView(navHome, new LinearLayout.LayoutParams(0, -1, 1f));
        nav.addView(navProfile, new LinearLayout.LayoutParams(0, -1, 1f));
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, dp(72));
        nl.gravity = Gravity.BOTTOM;
        root.addView(nav, nl);
        setContentView(root);
        showTab(tab);
        sig = "";
        refresh(true);
        h.removeCallbacks(listTick);
        h.postDelayed(listTick, 3000);
        h.removeCallbacks(autoSwipe);
        h.postDelayed(autoSwipe, 4000);
    }

    LinearLayout navItem(boolean home, String label, final int which) {
        LinearLayout it = new LinearLayout(this);
        it.setOrientation(LinearLayout.VERTICAL);
        it.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setTag("icon");
        it.addView(iv, new LinearLayout.LayoutParams(dp(64), dp(32)));
        TextView t = tv(label, 12, MUTED, false);
        t.setTag("label");
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(3);
        it.addView(t, lp);
        it.setOnClickListener(v -> showTab(which));
        return it;
    }

    void paintNav(LinearLayout it, boolean home, boolean on) {
        ImageView iv = (ImageView) it.findViewWithTag("icon");
        TextView t = (TextView) it.findViewWithTag("label");
        iv.setImageDrawable(new Glyph(home, on ? PRIMARY : MUTED, dp(22)));
        iv.setBackground(on ? box(TONAL, 16, 0) : null);
        t.setTextColor(on ? TXT : MUTED);
        t.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
    }

    void showTab(int t) {
        tab = t;
        if (homeScroll == null || profileScroll == null) return;
        homeScroll.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        profileScroll.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        paintNav(navHome, true, t == 0);
        paintNav(navProfile, false, t == 1);
        try { getWindow().setStatusBarColor(HEAD); } catch (Throwable ignored) { }
        if (t == 1) renderProfile();
        else { sig = ""; refresh(true); }
    }

    // ---------------------------------------------------------------- HOME
    ScrollView buildHome() {
        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(BG);
        s.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        s.addView(col, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackgroundColor(HEAD);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(18), dp(18), dp(14));
        String biz = sp.getString("biz", "JS PAY");
        TextView name = tv(biz, 20, TXT, true);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(avatar(38, 15), new LinearLayout.LayoutParams(dp(38), dp(38)));
        head.addView(top);

        hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            final int bi = i;
            BannerView bv = new BannerView(this, i);
            bv.setOnClickListener(v -> bannerTap(bi));
            strip.addView(bv, new LinearLayout.LayoutParams(sw, -2));
        }
        hs.addView(strip, new FrameLayout.LayoutParams(-2, -2));
        hs.setOnTouchListener((v, ev) -> {
            lastTouch = System.currentTimeMillis();
            int a = ev.getAction();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                h.postDelayed(() -> {
                    try {
                        int page = Math.round(hs.getScrollX() / (float) sw);
                        page = Math.max(0, Math.min(3, page));
                        hs.smoothScrollTo(page * sw, 0);
                    } catch (Exception ignored) { }
                }, 60);
            }
            return false;
        });
        hs.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            int page = Math.round(x / (float) sw);
            if (page != curBanner) updateDots(Math.max(0, Math.min(3, page)));
        });
        head.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        dotsRow.setPadding(0, dp(10), 0, dp(14));
        for (int i = 0; i < 4; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(6));
            lp.leftMargin = dp(3);
            lp.rightMargin = dp(3);
            dotsRow.addView(d, lp);
        }
        head.addView(dotsRow);
        col.addView(head);
        updateDots(0);

        dyn = new LinearLayout(this);
        dyn.setOrientation(LinearLayout.VERTICAL);
        dyn.setPadding(dp(18), dp(16), dp(18), dp(100));
        col.addView(dyn, new LinearLayout.LayoutParams(-1, -2));
        return s;
    }

    TextView avatar(int size, int textSp) {
        TextView a = tv("JS", textSp, Color.WHITE, true);
        a.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(PRIMARY);
        a.setBackground(g);
        a.setOnClickListener(v -> showTab(1));
        return a;
    }

    /** Data: hist line = time|code|amount|summary|result|expected. */
    static class Pay { long ts; double amt; String name; }

    ArrayList<Pay> loadPays() {
        ArrayList<Pay> out = new ArrayList<>();
        String hs0 = sp.getString("hist", "");
        if (hs0.isEmpty()) return out;
        java.util.regex.Pattern nm = java.util.regex.Pattern.compile("(?i)from\\s+(.+?)(?:\\s+and\\s+settled|\\s+settled|\\s+at\\s)");
        for (String line : hs0.split("\n")) {
            String[] f = line.split("\\|", 6);
            if (f.length < 5) continue;
            String res = f[4];
            if (res.equals("duplicate") || res.equals("ignored")) continue;
            Pay p = new Pay();
            try { p.ts = Long.parseLong(f[0]); p.amt = Double.parseDouble(f[2].replace(",", "")); } catch (Exception e) { continue; }
            if (p.amt <= 0) continue;
            java.util.regex.Matcher m = nm.matcher(f[3] + " ");
            p.name = m.find() ? m.group(1).trim() : "Payment";
            if (p.name.isEmpty()) p.name = "Payment";
            out.add(p);
        }
        return out;
    }

    static long dayStart(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    String when(long ts, boolean full) {
        String tm = new SimpleDateFormat("h:mm a", Locale.US).format(new Date(ts)).toLowerCase(Locale.US);
        if (!full) return tm;
        long ds = dayStart(System.currentTimeMillis());
        if (ts >= ds) return "Today, " + tm;
        if (ts >= ds - 86400000L) return "Yesterday, " + tm;
        return new SimpleDateFormat("dd MMM", Locale.US).format(new Date(ts)) + ", " + tm;
    }

    void refresh(boolean force) {
        if (dyn == null || tab != 0) return;
        String url = sp.getString("cashier_url", "");
        long now = System.currentTimeMillis();
        boolean alive = now - sp.getLong("svc_ok", 0) < 180000L || now - sp.getLong("cashier_ok_ts", 0) < 60000L;
        boolean expired = sp.getString("cashier_state", "ok").equals("expired");
        String state = url.isEmpty() ? "none" : expired ? "expired" : alive ? "ok" : "connecting";
        String hist = sp.getString("hist", "");
        String ns = state + "|" + period + "|" + hist.length() + "|" + (hist.length() > 40 ? hist.substring(0, 40) : hist);
        if (!force && ns.equals(sig)) return;
        sig = ns;
        renderHome(state);
    }

    void renderHome(String state) {
        dyn.removeAllViews();
        // --- cashier card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(box(BG, 18, LINE));
        ImageView tile = new ImageView(this);
        tile.setScaleType(ImageView.ScaleType.CENTER);
        String ik = state.equals("ok") ? "check" : state.equals("connecting") ? "sync" : state.equals("expired") ? "warn" : "shield";
        int ic = state.equals("ok") ? GREEN : state.equals("expired") ? RED : PRIMARY;
        tile.setImageDrawable(new Ic(ik, ic, dp(28)));
        tile.setBackground(box(HEAD, 16, 0));
        card.addView(tile, new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), 0, 0, 0);
        String title, sub, link;
        if (state.equals("none")) { title = "Add your cashier"; sub = "Verify Google Pay payments automatically."; link = "Add Cashier"; }
        else if (state.equals("ok")) { title = "Cashier connected"; sub = "Google Pay Business is verifying your payments."; link = "View cashier"; }
        else if (state.equals("expired")) { title = "Cashier disconnected"; sub = "Sign in again to continue verification."; link = "Sign in again"; }
        else { title = "Connecting..."; sub = "Starting verification. Keep this phone online."; link = "View cashier"; }
        t.addView(tv(title, 17, state.equals("expired") ? RED : TXT, false));
        TextView st = tv(sub, 13, MUTED, false);
        st.setPadding(0, dp(3), 0, dp(6));
        t.addView(st);
        t.addView(tv(link, 14, PRIMARY, true));
        card.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        final String fstate = state;
        card.setOnClickListener(v -> {
            if (fstate.equals("none")) askCode();
            else if (fstate.equals("expired")) startLogin();
            else showCashier();
        });
        dyn.addView(card, new LinearLayout.LayoutParams(-1, -2));

        // --- amount + toggle
        ArrayList<Pay> all = loadPays();
        long now = System.currentTimeMillis();
        long ds = dayStart(now);
        long from = period == 1 ? ds : now - 30L * 86400000L;
        double total = 0;
        ArrayList<Pay> sel = new ArrayList<>();
        for (Pay p : all) { if (p.ts >= from) { total += p.amt; sel.add(p); } }
        LinearLayout ar = new LinearLayout(this);
        ar.setOrientation(LinearLayout.HORIZONTAL);
        ar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = dp(20);
        LinearLayout am = new LinearLayout(this);
        am.setOrientation(LinearLayout.VERTICAL);
        am.addView(tv(inr(total), 30, TXT, false));
        am.addView(tv(period == 1 ? "Received today" : "Received in last 30 days", 14, MUTED, false));
        ar.addView(am, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout tg = new LinearLayout(this);
        tg.setOrientation(LinearLayout.HORIZONTAL);
        tg.setBackground(box(BG, 22, LINE));
        tg.addView(seg("1D", period == 1, 1));
        tg.addView(seg("30D", period == 30, 30));
        ar.addView(tg, new LinearLayout.LayoutParams(-2, -2));
        dyn.addView(ar, alp);

        // --- payments
        int max = period == 1 ? 3 : 5;
        if (sel.isEmpty()) {
            TextView e = tv(period == 1 ? "No payments received today." : "No payments in the last 30 days.", 14, MUTED, false);
            e.setPadding(0, dp(22), 0, dp(10));
            dyn.addView(e);
        } else {
            int n = 0;
            for (Pay p : sel) {
                if (n++ >= max) break;
                LinearLayout r = new LinearLayout(this);
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(0, dp(14), 0, dp(14));
                LinearLayout l = new LinearLayout(this);
                l.setOrientation(LinearLayout.VERTICAL);
                l.addView(tv(p.name, 17, TXT, false));
                l.addView(tv(when(p.ts, period != 1), 13, MUTED, false));
                r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
                r.addView(tv("+ " + inr(p.amt), 17, TXT, false));
                dyn.addView(r, new LinearLayout.LayoutParams(-1, -2));
            }
        }
        TextView all2 = tv("Show all payments", 15, PRIMARY, true);
        all2.setGravity(Gravity.CENTER);
        all2.setPadding(dp(26), dp(12), dp(26), dp(12));
        all2.setBackground(box(TONAL, 24, 0));
        all2.setOnClickListener(v -> openPage("history"));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(-2, -2);
        sp2.gravity = Gravity.CENTER_HORIZONTAL;
        sp2.topMargin = dp(8);
        dyn.addView(all2, sp2);

        // --- quick links
        TextView ql = tv("Quick links", 20, TXT, false);
        ql.setPadding(0, dp(28), 0, dp(12));
        dyn.addView(ql);
        LinearLayout qr = new LinearLayout(this);
        qr.setOrientation(LinearLayout.HORIZONTAL);
        qr.addView(quick("hist", "All payments", () -> openPage("history")), new LinearLayout.LayoutParams(0, -2, 1f));
        qr.addView(quick("vol", "Voice alerts", () -> openPage("voice")), new LinearLayout.LayoutParams(0, -2, 1f));
        qr.addView(quick("rup", "BharatPe", () -> openPage("bharat")), new LinearLayout.LayoutParams(0, -2, 1f));
        qr.addView(quick("info", "Help", () -> openPage("about")), new LinearLayout.LayoutParams(0, -2, 1f));
        dyn.addView(qr, new LinearLayout.LayoutParams(-1, -2));
    }

    TextView seg(String label, boolean on, final int val) {
        TextView s = tv(label, 15, on ? PRIMARY : TXT, on);
        s.setGravity(Gravity.CENTER);
        s.setPadding(dp(20), dp(9), dp(20), dp(9));
        s.setBackground(on ? box(TONAL, 22, 0) : null);
        s.setOnClickListener(v -> { period = val; sig = ""; refresh(true); });
        return s;
    }

    LinearLayout quick(String icon, String label, final Runnable r) {
        LinearLayout q = new LinearLayout(this);
        q.setOrientation(LinearLayout.VERTICAL);
        q.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setImageDrawable(new Ic(icon, PRIMARY, dp(26)));
        iv.setBackground(box(BG, 18, LINE));
        q.addView(iv, new LinearLayout.LayoutParams(dp(62), dp(62)));
        TextView t = tv(label, 12, TXT, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, 0);
        q.addView(t);
        q.setOnClickListener(v -> r.run());
        return q;
    }

    void openPage(String page) {
        try {
            Intent in = new Intent(this, MainActivity.class);
            in.putExtra("details", true);
            in.putExtra("page", page);
            startActivity(in);
        } catch (Throwable e) { Toast.makeText(this, "Could not open this page", Toast.LENGTH_SHORT).show(); }
    }

    // ---------------------------------------------------------------- PROFILE
    void renderProfile() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(HEAD);
        head.setPadding(dp(18), dp(30), dp(18), dp(22));
        head.addView(avatar(64, 22), new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout nm = new LinearLayout(this);
        nm.setOrientation(LinearLayout.VERTICAL);
        nm.setPadding(dp(16), 0, 0, 0);
        nm.addView(tv(sp.getString("biz", "JS PAY"), 20, TXT, true));
        nm.addView(tv("GPay Auto Verify", 14, MUTED, false));
        head.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(head);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(18), dp(18), dp(100));
        String url = sp.getString("cashier_url", "");
        String cs = url.isEmpty() ? "Not added" : sp.getString("cashier_state", "ok").equals("expired") ? "Disconnected" : "Connected";
        list.addView(profRow("shield", "Cashier details", cs, () -> { if (sp.getString("cashier_url", "").isEmpty()) askCode(); else showCashier(); }));
        list.addView(profRow("vol", "Voice alerts", "Sound and language", () -> openPage("voice")));
        list.addView(profSwitch("vol", "Announce other apps", "PhonePe, Paytm and more. Voice only.", "voice_other"));
        list.addView(profRow("rup", "Connect BharatPe", "Same server code", () -> openPage("bharat")));
        list.addView(profRow("hist", "Payment history", "All payments", () -> openPage("history")));
        list.addView(profRow("batt", "Advanced settings", "Notification access, battery, pop-up", () -> openPage("setup")));
        list.addView(profRow("info", "Help & support", "About, panel and help", () -> openPage("about")));
        TextView foot = tv("JS PAY  •  v1.20", 12, MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(22), 0, 0);
        list.addView(foot);
        col.addView(list);
        profileScroll.removeAllViews();
        profileScroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
    }

    LinearLayout profBase(String icon, String title, String sub) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(14), dp(14), dp(14));
        r.setBackground(box(BG, 18, LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        r.setLayoutParams(lp);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setImageDrawable(new Ic(icon, PRIMARY, dp(22)));
        iv.setBackground(box(HEAD, 14, 0));
        r.addView(iv, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), 0, dp(8), 0);
        t.addView(tv(title, 16, TXT, false));
        t.addView(tv(sub, 13, MUTED, false));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    LinearLayout profRow(String icon, String title, String sub, final Runnable go) {
        LinearLayout r = profBase(icon, title, sub);
        r.addView(tv("›", 24, PRIMARY, true));
        r.setOnClickListener(v -> go.run());
        return r;
    }

    LinearLayout profSwitch(String icon, String title, String sub, final String key) {
        LinearLayout r = profBase(icon, title, sub);
        android.widget.Switch s = new android.widget.Switch(this);
        s.setChecked(sp.getBoolean(key, false));
        s.setOnCheckedChangeListener((bv, on) -> {
            sp.edit().putBoolean(key, on).apply();
            if (on && !notifOn()) {
                Toast.makeText(this, "Turn ON Notification Access for JS PAY", Toast.LENGTH_LONG).show();
                try { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); } catch (Exception ignored) { }
            }
        });
        r.addView(s);
        return r;
    }

    boolean notifOn() {
        try {
            String f = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return f != null && f.contains(getPackageName());
        } catch (Exception e) { return false; }
    }

    // ---------------------------------------------------------------- dialogs
    void showHelp() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("How it works")
                .setMessage("1. Open your panel and copy the Cashier server code.\n2. Tap Add Cashier and enter the code.\n3. Sign in with the Google account of your Google Pay for Business.\n\nAfter that every payment is read from your Google dashboard and verified automatically. A fake or repeated notification cannot approve an order.")
                .setPositiveButton("OK", null).show();
    }

    Dialog lightDialog(LinearLayout box) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        box.setBackground(box(BG, 22, LINE));
        d.setContentView(box);
        return d;
    }

    void sizeDialog(Dialog d) {
        try {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f), -2);
        } catch (Exception ignored) { }
    }

    TextView dlgBtn(String label, boolean filled, int color) {
        TextView b = tv(label, 15, filled ? Color.WHITE : color, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(13), dp(16), dp(13));
        b.setBackground(filled ? box(color, 14, 0) : box(Color.TRANSPARENT, 14, LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    void showCashier() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(20));
        final Dialog d = lightDialog(box);
        TextView title = tv("Cashier details", 20, TXT, true);
        title.setOnLongClickListener(v -> { showDiag(); return true; });
        box.addView(title);
        long now = System.currentTimeMillis();
        boolean alive = now - sp.getLong("svc_ok", 0) < 180000L || now - sp.getLong("cashier_ok_ts", 0) < 60000L;
        boolean expired = sp.getString("cashier_state", "ok").equals("expired");
        String st = expired ? "Disconnected" : alive ? "Connected" : "Connecting...";
        long ts = Math.max(sp.getLong("svc_ok", 0), sp.getLong("cashier_ok_ts", 0));
        box.addView(kv("Account", "Google Pay Business"));
        box.addView(kv("Status", st));
        box.addView(kv("Last checked", ts == 0 ? "-" : new SimpleDateFormat("dd MMM, h:mm a", Locale.US).format(new Date(ts))));
        TextView note = tv("Payments are verified from your Google Pay for Business dashboard. Keep this phone online.", 12, MUTED, false);
        note.setPadding(0, dp(10), 0, dp(4));
        box.addView(note);
        TextView again = dlgBtn("Sign in again", true, PRIMARY);
        again.setOnClickListener(v -> { d.dismiss(); startLogin(); });
        box.addView(again);
        TextView rm = dlgBtn("Remove cashier", false, RED);
        rm.setOnClickListener(v -> { d.dismiss(); askRemove(); });
        box.addView(rm);
        TextView close = dlgBtn("Close", false, TXT);
        close.setOnClickListener(v -> d.dismiss());
        box.addView(close);
        d.show();
        sizeDialog(d);
    }

    LinearLayout kv(String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(12), 0, 0);
        r.addView(tv(k, 15, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(tv(v, 15, TXT, true));
        return r;
    }

    /** Sirf support ke liye: "Cashier details" title ko long-press karo. */
    void showDiag() {
        SimpleDateFormat f = new SimpleDateFormat("hh:mm:ss a", Locale.US);
        long pg = sp.getLong("dash_page", 0), tr = sp.getLong("dash_try", 0);
        String s = "Last page load: " + (pg == 0 ? "never" : f.format(new Date(pg)))
                + "\nLast read: " + (tr == 0 ? "never" : f.format(new Date(tr)) + " (" + sp.getInt("dash_len", 0) + " chars, " + sp.getInt("dash_tx", 0) + " payments)")
                + "\nService: " + sp.getString("svc_stage", "not started") + " (ticks " + sp.getInt("svc_tick", 0) + ")"
                + (sp.getString("dash_err", "").isEmpty() ? "" : "\nError: " + sp.getString("dash_err", ""));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Diagnostics").setMessage(s).setPositiveButton("OK", null).show();
    }

    void askRemove() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Remove cashier?")
                .setMessage("Automatic verification from the Google dashboard will stop.")
                .setPositiveButton("Remove", (d, w) -> {
                    DashService.stop(this);
                    sp.edit().remove("cashier_url").remove("cashier_ok_ts").remove("cashier_seen").remove("cashier_base").remove("dash_ok").remove("cashier_state").apply();
                    try { CookieManager.getInstance().removeAllCookies(null); } catch (Exception ignored) { }
                    sig = "";
                    refresh(true);
                    if (tab == 1) renderProfile();
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ---------------------------------------------------------------- Add Cashier dialog
    void askCode() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(22));
        final Dialog d = lightDialog(box);
        TextView t = tv("Require", 20, TXT, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView l = tv("Enter server code", 15, TXT, false);
        l.setPadding(0, dp(22), 0, dp(8));
        box.addView(l);
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setHint("Enter 12-digit server code");
        in.setHintTextColor(Color.parseColor("#8A87A8"));
        in.setTextColor(TXT);
        in.setTextSize(17);
        in.setSingleLine(true);
        in.setPadding(dp(14), dp(12), dp(14), dp(12));
        in.setBackground(box(Color.parseColor("#F4F1FF"), 14, LINE));
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
        d.show();
        sizeDialog(d);
        try { d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE); } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- Google sign-in screen
    void startLogin() {
        h.removeCallbacks(listTick);
        h.removeCallbacks(autoSwipe);
        webMode = true;
        navigated = false;
        finishing2 = false;
        webStart = System.currentTimeMillis();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(BG);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(16), dp(18), dp(16), dp(14));
        top.setBackgroundColor(HEAD);
        top.addView(tv("Complete Process...", 20, TXT, true));
        top.addView(tv("Do not press back", 14, PRIMARY, false));
        r.addView(top);
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
        r.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(r);
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
                DashService.ensure(this, true);
                Toast.makeText(this, "Verification completed successfully", Toast.LENGTH_LONG).show();
                leaveWeb();
                return;
            }
        }
        if (navigated && System.currentTimeMillis() - webStart > 60000 && !url.contains("/g4b/transactions")) {
            Toast.makeText(this, "Open the menu and tap Transactions", Toast.LENGTH_LONG).show();
            webStart = System.currentTimeMillis();
        }
    }

    void leaveWeb() {
        webMode = false;
        h.removeCallbacks(webLoop);
        try { if (web != null) web.destroy(); } catch (Exception ignored) { }
        web = null;
        showMain();
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
        if (tab == 1) { showTab(0); return; }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        try { if (web != null) web.destroy(); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
