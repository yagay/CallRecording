package io.github.vvb2060.callrecording.settings;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import io.github.vvb2060.callrecording.R;

/** Minimal settings UI, matching the original module's native Android theme. */
public final class SettingsActivity extends Activity {
    private SharedPreferences preferences;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.auto_title);
        preferences = getSharedPreferences(SettingsProvider.PREFS, Context.MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(content);

        content.addView(label(R.string.auto_title, 24, true));
        add(content, label(R.string.auto_subtitle, 14, false), 8);
        add(content, label(R.string.auto_section, 16, true), 24);

        setting(content, R.string.auto_all, R.string.auto_all_description,
                SettingsProvider.AUTO, true);
        setting(content, R.string.auto_debug, R.string.auto_debug_description,
                SettingsProvider.DEBUG, false);

        add(content, label(R.string.auto_status_heading, 16, true), 22);
        status = label(R.string.auto_status_unknown, 14, false);
        add(content, status, 9);

        Button refresh = new Button(this);
        refresh.setText(R.string.auto_refresh);
        refresh.setOnClickListener(view -> refreshStatus());
        add(content, refresh, 10);
        add(content, label(R.string.auto_notice, 13, false), 20);

        setContentView(scroll);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) refreshStatus();
    }

    private void setting(LinearLayout parent, int title, int description,
                         String key, boolean initialValue) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(90));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(label(title, 16, true));
        TextView subtitle = label(description, 13, false);
        subtitle.setPadding(0, dp(5), dp(12), 0);
        labels.addView(subtitle);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch toggle = new Switch(this);
        toggle.setContentDescription(getString(title));
        toggle.setChecked(preferences.getBoolean(key, initialValue));
        toggle.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(key, checked).apply());
        row.addView(toggle);
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));

        add(parent, row, 5);
        View separator = new View(this);
        separator.setBackgroundColor(0x44888888);
        parent.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void refreshStatus() {
        String state = preferences.getString(SettingsProvider.LAST_STATUS, "");
        int text;
        switch (state == null ? "" : state) {
            case "installed": text = R.string.auto_status_installed; break;
            case "not_found": text = R.string.auto_status_not_found; break;
            case "factory_missing": text = R.string.auto_status_factory; break;
            case "error": text = R.string.auto_status_error; break;
            default: text = R.string.auto_status_unknown;
        }
        StringBuilder message = new StringBuilder(getString(text));
        long check = preferences.getLong(SettingsProvider.LAST_CHECK, 0);
        long hit = preferences.getLong(SettingsProvider.LAST_HIT, 0);
        if (check > 0) message.append("\n")
                .append(getString(R.string.auto_last_check)).append(": ")
                .append(DateFormat.format("yyyy-MM-dd HH:mm", check));
        if (hit > 0) message.append("\n")
                .append(getString(R.string.auto_last_hit)).append(": ")
                .append(DateFormat.format("yyyy-MM-dd HH:mm", hit));
        status.setText(message.toString());
    }

    private TextView label(int resource, int sizeSp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(resource);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private void add(LinearLayout parent, View child, int marginTop) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginTop);
        parent.addView(child, params);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
