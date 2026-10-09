package shop.jitustore.jspay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int BG = Color.parseColor("#0B1020");
    static final int CARD = Color.parseColor("#141B33");
    static final int LINE = Color.parseColor("#26305A");
    static final int PRIMARY = Color.parseColor("#6366F1");
    static final int GOOD = Color.parseColor("#22C55E");
    static final int BAD = Color.parseColor("#F87171");
    static final int TXT = Color.parseColor("#F1F5F9");
    static final int MUTED = Color.parseColor("#94A3B8");

    EditText key;
    TextView pill;
    TextView rowSecret, rowAccess, rowLast;
    SharedPreferences sp;

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
        return b;
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
        pill = text("", 12, Color.WHITE, true);
        pill.setPadding(dp(12), dp(6), dp(12), dp(6));
        head.addView(pill);
        root.addView(head);

        TextView tag = text("Payment aate hi turant verify. Koi manual kaam nahi.", 14, MUTED, false);
        tag.setPadding(0, dp(16), 0, 0);
        root.addView(tag);

        // ---- status card ----
        LinearLayout st = card();
        st.addView(text("STATUS", 11, MUTED, true));
        rowSecret = text("", 15, TXT, false);
        rowAccess = text("", 15, TXT, false);
        rowLast = text("", 13, MUTED, false);
        rowSecret.setPadding(0, dp(10), 0, 0);
        rowAccess.setPadding(0, dp(8), 0, 0);
        rowLast.setPadding(0, dp(10), 0, 0);
        st.addView(rowSecret);
        st.addView(rowAccess);
        st.addView(rowLast);
        root.addView(st);

        // ---- secret card ----
        LinearLayout sc = card();
        sc.addView(text("1. APNI SECRET DAALO", 11, MUTED, true));
        TextView hint = text("Panel > GPay > Phone Setup se secret copy karke yahan paste karo.", 13, MUTED, false);
        hint.setPadding(0, dp(6), 0, dp(10));
        sc.addView(hint);
        key = new EditText(this);
        key.setHint("GN_xxxxxxxxxxxxxxxx");
        key.setHintTextColor(Color.parseColor("#64748B"));
        key.setTextColor(TXT);
        key.setTextSize(14);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setBackground(box(Color.parseColor("#0B1020"), 12, LINE));
        key.setPadding(dp(14), dp(12), dp(14), dp(12));
        key.setText(sp.getString("secret", ""));
        sc.addView(key, new LinearLayout.LayoutParams(-1, -2));
        sc.addView(button("Save karo", true, new View.OnClickListener() {
            public void onClick(View v) {
                sp.edit().putString("secret", key.getText().toString().trim()).apply();
                Toast.makeText(MainActivity.this, "Save ho gaya", Toast.LENGTH_SHORT).show();
                refresh();
            }
        }));
        root.addView(sc);

        // ---- permissions card ----
        LinearLayout pc = card();
        pc.addView(text("2. PERMISSION DO", 11, MUTED, true));
        pc.addView(button("Notification Access ON karo", false, new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            }
        }));
        pc.addView(button("Battery: Unrestricted karo", false, new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }));
        root.addView(pc);

        // ---- footer ----
        TextView panel = text("Panel kholo  ›  jitustore.shop/pay", 14, PRIMARY, true);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(0, dp(22), 0, dp(6));
        panel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://jitustore.shop/pay")));
            }
        });
        root.addView(panel);
        TextView foot = text("Secured by JS PAY  •  v1.0", 12, Color.parseColor("#64748B"), false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot);

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    void refresh() {
        String en = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean access = !TextUtils.isEmpty(en) && en.contains(getPackageName());
        boolean hasKey = sp.getString("secret", "").length() >= 5;
        long last = sp.getLong("last", 0);
        boolean live = access && hasKey;

        pill.setText(live ? "LIVE" : "SETUP BAAKI");
        pill.setBackground(box(live ? GOOD : BAD, 20, 0));

        rowSecret.setText((hasKey ? "\u25CF  Secret saved" : "\u25CF  Secret nahi daala"));
        rowSecret.setTextColor(hasKey ? GOOD : BAD);
        rowAccess.setText((access ? "\u25CF  Notification Access ON" : "\u25CF  Notification Access OFF"));
        rowAccess.setTextColor(access ? GOOD : BAD);
        if (last > 0) {
            String when = new SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(new Date(last));
            rowLast.setText("Aakhri payment signal: " + when + "  (server " + sp.getInt("code", 0) + ")");
        } else {
            rowLast.setText("Abhi tak koi payment signal nahi aaya");
        }
    }
}
