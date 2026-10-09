package shop.jitustore.jspay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    TextView status;
    EditText key;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final SharedPreferences sp = getSharedPreferences("c", MODE_PRIVATE);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (22 * getResources().getDisplayMetrics().density);
        l.setPadding(p, p * 2, p, p);
        l.setBackgroundColor(Color.parseColor("#F4F6FB"));

        TextView h = new TextView(this);
        h.setText("JS PAY — GPay Auto Verify"); h.setTextSize(22); h.setTextColor(Color.parseColor("#1b2a4a"));
        h.setGravity(Gravity.CENTER); l.addView(h);

        TextView d = new TextView(this);
        d.setText("\n1) Panel se apni GPay Secret copy karke neeche paste karo\n2) Save dabao\n3) Notification Access ON karo\n\nBas! Ab payment apne aap verify hogi.\n");
        d.setTextSize(15); l.addView(d);

        key = new EditText(this);
        key.setHint("GN_xxxxxxxx (Secret)"); key.setSingleLine(true);
        key.setText(sp.getString("secret", ""));
        l.addView(key);

        Button s = new Button(this); s.setText("Save");
        s.setOnClickListener(v -> {
            sp.edit().putString("secret", key.getText().toString().trim()).apply();
            Toast.makeText(this, "Save ho gaya", Toast.LENGTH_SHORT).show(); refresh();
        });
        l.addView(s);

        Button a = new Button(this); a.setText("Notification Access ON karo");
        a.setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        l.addView(a);

        Button bt = new Button(this); bt.setText("Battery: Unrestricted karo");
        bt.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + getPackageName()))));
        l.addView(bt);

        status = new TextView(this); status.setTextSize(15); status.setPadding(0, p, 0, 0);
        l.addView(status);
        setContentView(l);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    void refresh() {
        SharedPreferences sp = getSharedPreferences("c", MODE_PRIVATE);
        String en = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean access = !TextUtils.isEmpty(en) && en.contains(getPackageName());
        boolean hasKey = sp.getString("secret", "").length() >= 5;
        long last = sp.getLong("last", 0);
        status.setText((hasKey ? "✅ Secret saved\n" : "❌ Secret nahi daala\n")
                + (access ? "✅ Notification Access ON\n" : "❌ Notification Access OFF\n")
                + (last > 0 ? "Last server reply: " + sp.getInt("code", 0) + " (" + new java.util.Date(last) + ")" : "Abhi tak koi payment nahi aayi"));
    }
  }
