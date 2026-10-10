package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.VoiceChangerPitchCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Voice Changer settings: on/off switch and the pitch slider. */
public class VoiceChangerActivity extends BaseFragment {

    private static final int ID_VOICE_CHANGER = 1;
    private static final int PITCH_CELL_HEIGHT_DP = 84;

    private UniversalRecyclerView listView;
    private VoiceChangerPitchCell pitchCell;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Voice Changer");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        pitchCell = new VoiceChangerPitchCell(context);
        // The settings card draws the white background (and its rounded corners) itself.
        pitchCell.setColors(Color.TRANSPARENT, getThemedColor(Theme.key_windowBackgroundWhiteBlackText), getThemedColor(Theme.key_windowBackgroundWhiteValueText));
        pitchCell.bind();

        final FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Voice Changer"));
        items.add(UItem.asCheck(ID_VOICE_CHANGER, "Voice Changer").setChecked(SharedConfig.voiceChangerEnabled));
        items.add(UItem.asCustom(pitchCell, PITCH_CELL_HEIGHT_DP));
        items.add(UItem.asShadow("Changes your voice in voice messages. Lower values make your voice deeper, higher values make it higher."));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_VOICE_CHANGER) {
            SharedConfig.setVoiceChangerEnabled(!SharedConfig.voiceChangerEnabled);
            pitchCell.bind();
            listView.adapter.update(true);
        }
    }
}
