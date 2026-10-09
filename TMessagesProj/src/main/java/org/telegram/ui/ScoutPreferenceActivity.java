package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.VoiceChangerPitchCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

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
    private int keepDeletedSectionRow;
    private int keepDeletedRow;
    private int clearDeletedRow;
    private int keepDeletedDetailRow;
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
        keepDeletedSectionRow = rowCount++;
        keepDeletedRow = rowCount++;
        clearDeletedRow = rowCount++;
        keepDeletedDetailRow = rowCount++;
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
            } else if (position == keepDeletedRow) {
                SharedConfig.setKeepDeletedMessages(!SharedConfig.keepDeletedMessages);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.keepDeletedMessages);
                }
            } else if (position == clearDeletedRow) {
                confirmClearDeletedMessages();
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

    private void confirmClearDeletedMessages() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Clear saved deleted messages?");
        builder.setMessage("Messages that other people deleted and that are kept on this device will be removed from your chats. This cannot be undone.");
        builder.setPositiveButton(LocaleController.getString(R.string.Clear), (dialog, which) -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    MessagesController.getInstance(a).clearSavedDeletedMessages();
                }
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, "Saved deleted messages cleared").show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == stealthModeRow || position == screenshotsRow || position == voiceChangerRow || position == keepDeletedRow || position == clearDeletedRow;
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
                    view = new VoiceChangerPitchCell(mContext);
                    break;
                case 5:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                        privacyCell.setText("When enabled, your online status, typing status, read receipts, and story views are hidden from other users as much as Telegram's protocol allows. Sending a message will still reveal that you're online. That's a server-side restriction, not something a client can hide :)");
                    } else if (position == voiceChangerDetailRow) {
                        privacyCell.setText("Changes your voice in voice messages. Lower values make your voice deeper, higher values make it higher.");
                    } else if (position == keepDeletedDetailRow) {
                        privacyCell.setText("Messages that other people delete stay in your chats, in the same place. Secret chats, service messages and self-destructing messages are never kept. Messages you delete yourself are removed as usual.");
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == stealthModeSectionRow) {
                        headerCell.setText("Stealth Mode");
                    } else if (position == voiceChangerSectionRow) {
                        headerCell.setText("Voice Changer");
                    } else if (position == keepDeletedSectionRow) {
                        headerCell.setText("Deleted Messages");
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
                    } else if (position == keepDeletedRow) {
                        textCheckCell.setTextAndCheck("Keep Deleted Messages", SharedConfig.keepDeletedMessages, true);
                    }
                    break;
                case 4:
                    ((VoiceChangerPitchCell) holder.itemView).bind();
                    break;
                case 5:
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    settingsCell.setText("Clear Saved Deleted Messages", false);
                    settingsCell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == stealthModeDetailRow || position == voiceChangerDetailRow || position == keepDeletedDetailRow) {
                return 1;
            } else if (position == stealthModeSectionRow || position == voiceChangerSectionRow || position == keepDeletedSectionRow) {
                return 2;
            } else if (position == voicePitchRow) {
                return 4;
            } else if (position == clearDeletedRow) {
                return 5;
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
