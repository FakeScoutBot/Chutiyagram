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

/**
 * "Pitch" row with a semitone slider. Reads and writes the shared Voice Changer settings in SharedConfig,
 * so it can be used both in Scout preferences and inside the group call menu.
 */
public class VoiceChangerPitchCell extends FrameLayout {

    private final TextView titleView;
    private final TextView valueView;
    private final SeekBarView seekBar;

    public VoiceChangerPitchCell(Context context) {
        super(context);
        setWillNotDraw(false);

        titleView = new TextView(context);
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT);
        titleView.setGravity(Gravity.LEFT);
        titleView.setText("Pitch");
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
                int semitones = Math.round(VoiceChanger.MIN_SEMITONES + progress * (VoiceChanger.MAX_SEMITONES - VoiceChanger.MIN_SEMITONES));
                SharedConfig.setVoiceChangerSemitones(semitones);
                valueView.setText(VoiceChanger.describe(SharedConfig.voiceChangerSemitones));
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
            }

            @Override
            public int getStepsCount() {
                return VoiceChanger.MAX_SEMITONES - VoiceChanger.MIN_SEMITONES;
            }
        });
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 6, 40, 6, 0));

        applyDefaultColors();
    }

    private void applyDefaultColors() {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
    }

    /** For dark surfaces such as the group call. Pass 0 as background to keep it transparent. */
    public void setColors(int background, int titleColor, int valueColor) {
        setBackgroundColor(background);
        titleView.setTextColor(titleColor);
        valueView.setTextColor(valueColor);
    }

    /** Re-reads the shared settings. Call after the toggle changes. */
    public void bind() {
        boolean enabled = SharedConfig.voiceChangerEnabled;
        setAlpha(enabled ? 1f : 0.5f);
        seekBar.setEnabled(enabled);
        seekBar.setClickable(enabled);
        seekBar.setProgress((SharedConfig.voiceChangerSemitones - VoiceChanger.MIN_SEMITONES) / (float) (VoiceChanger.MAX_SEMITONES - VoiceChanger.MIN_SEMITONES));
        valueView.setText(VoiceChanger.describe(SharedConfig.voiceChangerSemitones));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Always take the full row width: a RecyclerView hands out an AT_MOST (wrap_content) spec,
        // which would shrink the cell to the width of the "Pitch" label and collapse the slider.
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(84), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return !SharedConfig.voiceChangerEnabled || super.onInterceptTouchEvent(ev);
    }
}
