package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.VoiceChanger;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

import java.util.ArrayList;

public class ScoutPreferenceActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int stealthModeSectionRow;
    private int stealthModeRow;
    private int screenshotsRow;
    private int stealthModeDetailRow;
    private int voiceChangerSectionRow;
    private int voiceChangerRow;
    private int voicePitchRow;
    private int voiceChangerDetailRow;
    private int rowCount;

    private void updateRows() {
        rowCount = 0;
        stealthModeSectionRow = rowCount++;
        stealthModeRow = rowCount++;
        screenshotsRow = rowCount++;
        stealthModeDetailRow = rowCount++;
        voiceChangerSectionRow = rowCount++;
        voiceChangerRow = rowCount++;
        voicePitchRow = rowCount++;
        voiceChangerDetailRow = rowCount++;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Scout Preference");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        if (parentLayout != null && parentLayout.isRightLayout()) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_close);
        }

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutAnimation(null);
        listView.setItemAnimator(null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == stealthModeRow) {
                SharedConfig.setStealthModeEnabled(!SharedConfig.stealthModeEnabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.stealthModeEnabled);
                }
            } else if (position == screenshotsRow) {
                SharedConfig.setForceAllowScreenshots(!SharedConfig.forceAllowScreenshots);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.forceAllowScreenshots);
                }
            } else if (position == voiceChangerRow) {
                SharedConfig.setVoiceChangerEnabled(!SharedConfig.voiceChangerEnabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.voiceChangerEnabled);
                }
                listAdapter.notifyItemChanged(voicePitchRow);
            }
        });

        return fragmentView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
    }

    private static String describeSemitones(int semitones) {
        if (semitones == 0) {
            return "Original";
        }
        return (semitones > 0 ? "+" : "") + semitones + (Math.abs(semitones) == 1 ? " semitone" : " semitones");
    }

    private class PitchCell extends FrameLayout {

        private final TextView titleView;
        private final TextView valueView;
        private final SeekBarView seekBar;

        public PitchCell(Context context) {
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
                    valueView.setText(describeSemitones(SharedConfig.voiceChangerSemitones));
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
        }

        public void bind() {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            boolean enabled = SharedConfig.voiceChangerEnabled;
            setAlpha(enabled ? 1f : 0.5f);
            seekBar.setEnabled(enabled);
            seekBar.setClickable(enabled);
            seekBar.setProgress((SharedConfig.voiceChangerSemitones - VoiceChanger.MIN_SEMITONES) / (float) (VoiceChanger.MAX_SEMITONES - VoiceChanger.MIN_SEMITONES));
            valueView.setText(describeSemitones(SharedConfig.voiceChangerSemitones));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            // Always take the full row width: the RecyclerView hands out an AT_MOST (wrap_content) spec,
            // which would shrink the cell to the width of the "Pitch" label and collapse the slider.
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(84), MeasureSpec.EXACTLY));
        }

        @Override
        public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
            return !SharedConfig.voiceChangerEnabled || super.onInterceptTouchEvent(ev);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == stealthModeRow || position == screenshotsRow || position == voiceChangerRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    break;
                case 4:
                    view = new PitchCell(mContext);
                    break;
                case 3:
                default:
                    view = new TextCheckCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == stealthModeDetailRow) {
                        privacyCell.setText("When enabled, your online status, typing status, read receipts, and story views are hidden from other users as much as Telegram's protocol allows. Sending a message will still reveal that you're online — that's a server-side restriction, not something a client can hide.\n\nForce Allow Screenshots overrides the screenshot-blocking that Telegram normally applies in secret chats and protected (no-forwards) content, so you can always take a screenshot regardless of the chat's restrictions.");
                    } else if (position == voiceChangerDetailRow) {
                        privacyCell.setText("When enabled, every voice message you record is pitch-shifted before it is encoded and sent, so the original voice is never uploaded. Negative values give a deeper voice, positive values a higher one. The slider is applied to the next recording; a recording already in progress keeps the value it started with. Round video messages are not affected.");
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == stealthModeSectionRow) {
                        headerCell.setText("Stealth Mode");
                    } else if (position == voiceChangerSectionRow) {
                        headerCell.setText("Voice Changer");
                    }
                    break;
                case 3:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position == stealthModeRow) {
                        textCheckCell.setTextAndCheck("Stealth Mode", SharedConfig.stealthModeEnabled, true);
                    } else if (position == screenshotsRow) {
                        textCheckCell.setTextAndCheck("Force Allow Screenshots", SharedConfig.forceAllowScreenshots, false);
                    } else if (position == voiceChangerRow) {
                        textCheckCell.setTextAndCheck("Voice Changer", SharedConfig.voiceChangerEnabled, true);
                    }
                    break;
                case 4:
                    ((PitchCell) holder.itemView).bind();
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == stealthModeDetailRow || position == voiceChangerDetailRow) {
                return 1;
            } else if (position == stealthModeSectionRow || position == voiceChangerSectionRow) {
                return 2;
            } else if (position == voicePitchRow) {
                return 4;
            } else if (position == stealthModeRow || position == screenshotsRow) {
                return 3;
            }
            return 3;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        return themeDescriptions;
    }
}
