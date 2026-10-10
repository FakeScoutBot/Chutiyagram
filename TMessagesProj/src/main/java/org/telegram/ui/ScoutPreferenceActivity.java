package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/**
 * ScoutGram Preferences: landing screen with the ScoutGram identity header,
 * feature categories and community links.
 * Category screens are not wired up yet, so no setting is reachable from here for now.
 */
public class ScoutPreferenceActivity extends BaseFragment {

    private static final String CHANNEL_USERNAME = "scoutgram";
    private static final String GROUP_USERNAME = "scoutgramchat";

    private static final int ID_GHOST_MODE = 1;
    private static final int ID_SPY = 2;
    private static final int ID_VOICE_CHANGER = 3;
    private static final int ID_CHANNEL = 10;
    private static final int ID_GROUP = 11;

    private FrameLayout contentView;
    private UniversalRecyclerView listView;
    private View actionBarBackground;

    private FrameLayout headerView;
    private FrameLayout logoView;
    private TextView titleView;
    private TextView versionView;

    private boolean actionBarVisible;
    private ValueAnimator actionBarVisibleAnimator;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setUseContainerForTitles();
        actionBar.setTitle(getTitle());
        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(true);
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setBackground(null);
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

        contentView = new FrameLayout(context);

        buildHeader(context);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.statusBarHeight + dp(12), 0, AndroidUtilities.navigationBarHeight);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateActionBarVisible(false, true);
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        actionBarBackground = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                final int height = actionBar.getHeight();
                paint.setColor(getThemedColor(Theme.key_actionBarDefault));
                canvas.drawRect(0, 0, getMeasuredWidth(), height, paint);
                if (getParentLayout() != null) {
                    getParentLayout().drawHeaderShadow(canvas, height);
                }
            }
        };
        contentView.addView(actionBarBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.TOP));
        contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        updateColors();
        updateActionBarVisible(true, false);
        listView.adapter.update(false);

        return fragmentView = contentView;
    }

    private void buildHeader(Context context) {
        headerView = new FrameLayout(context);

        // Logo: gradient disc with the Scout mark.
        logoView = new FrameLayout(context);
        final GradientDrawable disc = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF35B4F5, 0xFF2B6FE6, 0xFF5B47D6}
        );
        disc.setShape(GradientDrawable.OVAL);
        disc.setStroke(dp(2), 0x26FFFFFF);
        logoView.setBackground(disc);
        logoView.setElevation(dp(2));

        final ImageView mark = new ImageView(context);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setImageResource(R.drawable.settings_scout);
        logoView.addView(mark, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
        headerView.addView(logoView, LayoutHelper.createFrame(84, 84, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16, 0, 0));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setText("ScoutGram");
        headerView.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 16, 112, 16, 0));

        versionView = new TextView(context);
        versionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        versionView.setGravity(Gravity.CENTER);
        versionView.setSingleLine();
        versionView.setEllipsize(TextUtils.TruncateAt.END);
        versionView.setText(getVersionCaption());
        headerView.addView(versionView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 16, 144, 16, 0));
    }

    private String getVersionCaption() {
        final String version = BuildVars.BUILD_VERSION_STRING;
        return TextUtils.isEmpty(version) ? "" : version;
    }

    private String getTitle() {
        return "ScoutGram Preferences";
    }

    private void updateColors() {
        if (contentView == null) {
            return;
        }
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        versionView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        actionBarBackground.invalidate();
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustomShadow(headerView, 176));

        items.add(UItem.asHeader("Categories"));
        items.add(UItem.asButton(ID_GHOST_MODE, R.drawable.scout_ic_ghost, "Ghost Mode"));
        items.add(UItem.asButton(ID_SPY, R.drawable.scout_ic_spy, "Spy"));
        items.add(UItem.asButton(ID_VOICE_CHANGER, R.drawable.scout_ic_voice, "Voice Changer"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Links"));
        items.add(UItem.asButton(ID_CHANNEL, R.drawable.scout_ic_channel, "Channel", "@" + CHANNEL_USERNAME));
        items.add(UItem.asButton(ID_GROUP, R.drawable.outline_groups_24, "Group", "@" + GROUP_USERNAME));
        items.add(UItem.asShadow(null));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_CHANNEL:
                getMessagesController().openByUserName(CHANNEL_USERNAME, this, 1);
                break;
            case ID_GROUP:
                getMessagesController().openByUserName(GROUP_USERNAME, this, 1);
                break;
            case ID_GHOST_MODE:
                presentFragment(new GhostModeActivity());
                break;
            case ID_SPY:
                presentFragment(new SpyPreferencesActivity());
                break;
            case ID_VOICE_CHANGER:
                // Category screens come in the next step.
                break;
        }
    }

    private void updateActionBarVisible(boolean force, boolean animated) {
        final boolean visible;
        if (listView != null && listView.getChildCount() > 0) {
            final View firstChild = listView.getChildAt(0);
            visible = listView.getChildAdapterPosition(firstChild) > 0
                    || firstChild.getY() + firstChild.getHeight() < actionBar.getHeight();
        } else {
            visible = false;
        }
        if (actionBarVisible == visible && !force) {
            return;
        }
        actionBarVisible = visible;
        if (actionBarVisibleAnimator != null) {
            actionBarVisibleAnimator.cancel();
            actionBarVisibleAnimator = null;
        }
        if (!animated) {
            actionBar.getTitlesContainer().setAlpha(visible ? 1.0f : 0.0f);
            actionBarBackground.setAlpha(visible ? 1.0f : 0.0f);
        } else {
            actionBarVisibleAnimator = ValueAnimator.ofFloat(actionBar.getTitlesContainer().getAlpha(), visible ? 1.0f : 0.0f);
            actionBarVisibleAnimator.addUpdateListener(a -> {
                final float t = (float) a.getAnimatedValue();
                actionBar.getTitlesContainer().setAlpha(t);
                actionBarBackground.setAlpha(t);
            });
            actionBarVisibleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            actionBarVisibleAnimator.setDuration(420);
            actionBarVisibleAnimator.start();
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (listView != null) {
            listView.setPadding(0, top + dp(12), 0, bottom);
        }
    }

    @Override
    public ArrayList<org.telegram.ui.ActionBar.ThemeDescription> getThemeDescriptions() {
        final ArrayList<org.telegram.ui.ActionBar.ThemeDescription> descriptions = new ArrayList<>();
        descriptions.add(new org.telegram.ui.ActionBar.ThemeDescription(fragmentView, org.telegram.ui.ActionBar.ThemeDescription.FLAG_BACKGROUND, null, null, null, () -> updateColors(), Theme.key_windowBackgroundGray));
        return descriptions;
    }
}
