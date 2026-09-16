package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Lightweight programmatic modal used to keep every internal surface in Darbak identity. */
final class DarbakDialog {
    interface Choice { void onChoice(int which); }

    static void menu(Context c, String title, String[] labels, Choice choice) {
        LinearLayout box = panel(c, title);
        final AlertDialog[] holder = new AlertDialog[1];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView action = DarbakUi.action(c, labels[i]);
            action.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                choice.onChoice(index);
            });
            LinearLayout.LayoutParams p = row(c, 8);
            p.height = DarbakUi.dp(c, 50);
            box.addView(action, p);
        }
        addClose(c, box, holder);
        holder[0] = show(c, box);
    }

    static void infoActions(Context c, String title, String message, String[] labels, Choice choice) {
        LinearLayout box = panel(c, title);
        TextView body = text(c, message, 16, false);
        body.setBackground(DarbakUi.rounded(DarbakUi.CARD, DarbakUi.BORDER, 14, c));
        body.setPadding(DarbakUi.dp(c, 16), DarbakUi.dp(c, 12), DarbakUi.dp(c, 16), DarbakUi.dp(c, 12));
        box.addView(body, row(c, 0));
        final AlertDialog[] holder = new AlertDialog[1];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView action = DarbakUi.action(c, labels[i]);
            action.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                choice.onChoice(index);
            });
            LinearLayout.LayoutParams p = row(c, 8);
            p.height = DarbakUi.dp(c, 50);
            box.addView(action, p);
        }
        addClose(c, box, holder);
        holder[0] = show(c, box);
    }

    static LinearLayout panel(Context c, String titleValue) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = DarbakUi.dp(c, 18);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(DarbakUi.rounded(DarbakUi.BG, DarbakUi.ACCENT, 20, c));
        TextView title = text(c, titleValue, 21, true);
        title.setTextColor(DarbakUi.ACCENT);
        LinearLayout.LayoutParams p = row(c, 0);
        p.bottomMargin = DarbakUi.dp(c, 12);
        box.addView(title, p);
        return box;
    }

    static TextView text(Context c, String value, int sp, boolean bold) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextColor(DarbakUi.TEXT);
        v.setTextSize(sp);
        v.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        v.setLineSpacing(0f, 1.12f);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    static AlertDialog show(Context c, View content) {
        AlertDialog dialog = new AlertDialog.Builder(c).setView(content).create();
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.55f);
            }
        });
        dialog.show();
        return dialog;
    }

    static LinearLayout.LayoutParams row(Context c, int topMarginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = DarbakUi.dp(c, topMarginDp);
        return p;
    }

    private static void addClose(Context c, LinearLayout box, AlertDialog[] holder) {
        TextView close = DarbakUi.action(c, "إغلاق");
        close.setTextColor(DarbakUi.TEXT_SECONDARY);
        close.setBackground(DarbakUi.rounded(DarbakUi.CARD, DarbakUi.BORDER, 14, c));
        close.setOnClickListener(v -> { if (holder[0] != null) holder[0].dismiss(); });
        LinearLayout.LayoutParams p = row(c, 8);
        p.height = DarbakUi.dp(c, 46);
        box.addView(close, p);
    }

    private DarbakDialog() {}
}
