package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Ghost Mode settings. Every option can be switched on its own; the master switch
 * turns all of them on or off at once and the counter shows how many are active.
 */
public class GhostModeActivity extends BaseFragment {

    private static final int ID_MASTER = 1;
    private static final int ID_OPTION_BASE = 10; // + SharedConfig.GHOST_* option index
    private static final int ID_READ_ON_INTERACT = 30;
    private static final int ID_SHOW_STATUS_ICON = 31;

    private static final String[] OPTION_TITLES = {
        "Don't Read Messages",
        "Don't Read Stories",
        "Don't Send Online",
        "Don't Send Typing"
    };

    private UniversalRecyclerView listView;
    private boolean expanded = true;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Ghost Mode");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Ghost essentials"));
        items.add(
            UItem.asExpandableSwitch(ID_MASTER, "Ghost Mode",
                    String.format(Locale.US, "%d/%d", SharedConfig.ghostOptionsEnabledCount(), SharedConfig.GHOST_OPTIONS_COUNT))
                .setChecked(SharedConfig.isGhostModeActive())
                .setCollapsed(!expanded)
                .setClickCallback(v -> {
                    // Right-hand switch: everything on, or everything off.
                    SharedConfig.setAllGhostOptions(!SharedConfig.isGhostModeActive());
                    listView.adapter.update(true);
                })
        );
        if (expanded) {
            for (int option = 0; option < SharedConfig.GHOST_OPTIONS_COUNT; option++) {
                items.add(
                    UItem.asRoundCheckbox(ID_OPTION_BASE + option, OPTION_TITLES[option])
                        .setChecked(SharedConfig.getGhostOption(option))
                        .setPad(1)
                );
            }
        }
        items.add(UItem.asShadow("Choose exactly what stays hidden. The switch turns every option on or off at once."));

        items.add(UItem.asCheck(ID_READ_ON_INTERACT, "Read on Interact").setChecked(SharedConfig.ghostReadOnInteract));
        items.add(UItem.asShadow("While \"Don't Read Messages\" is on, sending a message or reacting to one marks that chat as read."));

        items.add(UItem.asCheck(ID_SHOW_STATUS_ICON, "Display Ghost Mode Status").setChecked(SharedConfig.ghostShowStatusIcon));
        items.add(UItem.asShadow("Shows a ghost icon next to the logo on the chats screen while Ghost Mode is on."));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MASTER) {
            expanded = !expanded;
            listView.adapter.update(true);
        } else if (item.id >= ID_OPTION_BASE && item.id < ID_OPTION_BASE + SharedConfig.GHOST_OPTIONS_COUNT) {
            final int option = item.id - ID_OPTION_BASE;
            SharedConfig.setGhostOption(option, !SharedConfig.getGhostOption(option));
            listView.adapter.update(true);
        } else if (item.id == ID_READ_ON_INTERACT) {
            SharedConfig.setGhostReadOnInteract(!SharedConfig.ghostReadOnInteract);
            listView.adapter.update(true);
        } else if (item.id == ID_SHOW_STATUS_ICON) {
            SharedConfig.setGhostShowStatusIcon(!SharedConfig.ghostShowStatusIcon);
            listView.adapter.update(true);
        }
    }
}
