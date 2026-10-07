package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.VoiceChanger;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

/** "Volume boost" row (0..+12 dB makeup gain for the changed voice in calls). */
public class VoiceChangerBoostCell extends FrameLayout {

    private final TextView titleView;
    private final TextView valueView;
    private final SeekBarView seekBar;

    public VoiceChangerBoostCell(Context context) {
        super(context);
        setWillNotDraw(false);

        titleView = new TextView(context);
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT);
        titleView.setGravity(Gravity.LEFT);
        titleView.setText("Volume boost");
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 12, 21, 0));

        valueView = new TextView(context);
        valueView.setTextSize(14);
        valueView.setGravity(Gravity.RIGHT);
        addView(valueView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 21, 14, 21, 0));

        seekBar = new SeekBarView(context);
        seekBar.setReportChanges(true);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                int gain = Math.round(VoiceChanger.MIN_GAIN_DB + progress * (VoiceChanger.MAX_GAIN_DB - VoiceChanger.MIN_GAIN_DB));
                SharedConfig.setVoiceChangerGainDb(gain);
                valueView.setText(VoiceChanger.describeGain(SharedConfig.voiceChangerGainDb));
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
            }

            @Override
            public int getStepsCount() {
                return VoiceChanger.MAX_GAIN_DB - VoiceChanger.MIN_GAIN_DB;
            }
        });
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 6, 40, 6, 0));

        setColors(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
    }

    public void setColors(int background, int titleColor, int valueColor) {
        setBackgroundColor(background);
        titleView.setTextColor(titleColor);
        valueView.setTextColor(valueColor);
    }

    public void bind() {
        boolean enabled = SharedConfig.voiceChangerEnabled;
        setAlpha(enabled ? 1f : 0.5f);
        seekBar.setEnabled(enabled);
        seekBar.setClickable(enabled);
        seekBar.setProgress((SharedConfig.voiceChangerGainDb - VoiceChanger.MIN_GAIN_DB) / (float) (VoiceChanger.MAX_GAIN_DB - VoiceChanger.MIN_GAIN_DB));
        valueView.setText(VoiceChanger.describeGain(SharedConfig.voiceChangerGainDb));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(84), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return !SharedConfig.voiceChangerEnabled || super.onInterceptTouchEvent(ev);
    }
}
