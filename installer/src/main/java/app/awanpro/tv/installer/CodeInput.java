package app.awanpro.tv.installer;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Six boxes for the TV's pairing code, typed through one invisible field so paste and delete work as usual.
 * Always left to right in Latin digits, as the TV shows the code.
 */
final class CodeInput extends FrameLayout {
    interface Listener { void onCode(String code); }

    private final EditText field;
    private final TextView[] boxes = new TextView[6];

    CodeInput(Context c, Listener listener) {
        super(c);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        LinearLayout row = Ui.row(c);
        row.setLayoutDirection(LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < 6; i++) {
            TextView b = Ui.text(c, "", Ui.PLASTER, 24, Ui.semibold());
            b.setGravity(Gravity.CENTER);
            Ui.latin(b);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 56), 1f);
            lp.setMarginStart(Ui.dp(c, i == 0 ? 0 : i == 3 ? 14 : 6));
            row.addView(b, lp);
            boxes[i] = b;
        }
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        field = new EditText(c);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        field.setCursorVisible(false);
        field.setBackground(null);
        field.setTextColor(0);
        field.setAlpha(0.01f);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable e) {
                paint();
                listener.onCode(e.toString());
            }
        });
        field.setOnFocusChangeListener((v, has) -> paint());
        addView(field, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 56)));
        setOnClickListener(v -> focus());
        paint();
    }

    String code() { return field.getText().toString(); }

    void focus() {
        field.requestFocus();
        InputMethodManager im = getContext().getSystemService(InputMethodManager.class);
        if (im != null) im.showSoftInput(field, 0);
    }

    private void paint() {
        String s = field.getText().toString();
        boolean focused = field.hasFocus();
        for (int i = 0; i < 6; i++) {
            boxes[i].setText(i < s.length() ? String.valueOf(s.charAt(i)) : "");
            boolean current = focused && (i == s.length() || (s.length() == 6 && i == 5));
            GradientDrawable bg = Ui.round(Ui.LAPIS_800, 12, getContext());
            bg.setStroke(Ui.dp(getContext(), current ? 2 : 1),
                    current ? Ui.GOLD_400 : i < s.length() ? 0x99d8b46a : Ui.LINE_SOFT);
            boxes[i].setBackground(bg);
        }
    }

    @Override public void setVisibility(int v) {
        super.setVisibility(v);
        if (v == View.VISIBLE) paint();
    }
}
