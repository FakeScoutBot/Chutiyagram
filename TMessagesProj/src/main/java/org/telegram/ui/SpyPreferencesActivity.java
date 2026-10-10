package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/**
 * Spy settings: keeping messages that other people delete, how those messages look
 * (with a live preview) and clearing what has been saved.
 */
public class SpyPreferencesActivity extends BaseFragment {

    private static final int ID_KEEP_DELETED = 1;
    private static final int ID_TRANSLUCENT = 2;
    private static final int ID_CLEAR_DELETED = 3;
    private static final int ID_FORCE_SCREENSHOTS = 4;

    /** Swatches of the deleted mark color picker. 0 means "follow the message time color". */
    private static final int[] MARK_COLORS = {
        0,
        0xFFFF0000,
        0xFFDC2626,
        0xFFDB2777,
        0xFFC026D3,
        0xFF9333EA,
        0xFF4F46E5,
        0xFF2563EB
    };
    private static final int DEFAULT_SWATCH_COLOR = 0xFFA3ABB5;

    private UniversalRecyclerView listView;
    private PreviewContainer previewContainer;
    private ColorRow colorRow;
    private MarkRow markRow;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Spy");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        previewContainer = new PreviewContainer(context);
        markRow = new MarkRow(context);
        colorRow = new ColorRow(context);
        colorRow.setSelectedColor(SharedConfig.deletedMarkColor, false);
        colorRow.setOnColorSelected(color -> {
            SharedConfig.setDeletedMarkColor(color);
            previewContainer.updateDeletedStyle();
        });

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
        items.add(UItem.asHeader("Spy essentials"));
        items.add(UItem.asCheck(ID_KEEP_DELETED, "Keep Deleted Messages").setChecked(SharedConfig.keepDeletedMessages));
        items.add(UItem.asCheck(ID_FORCE_SCREENSHOTS, "Force Allow Screenshots").setChecked(SharedConfig.forceAllowScreenshots));
        items.add(UItem.asShadow("Keeps messages deleted by others in your chats. Secret chats, service messages and self-destructing messages are excluded."));

        items.add(UItem.asHeader("Customization"));
        items.add(UItem.asCustom(previewContainer, LayoutHelper.WRAP_CONTENT));
        items.add(UItem.asCheck(ID_TRANSLUCENT, "Translucent Deleted Messages").setChecked(SharedConfig.deletedMessagesTranslucent));
        items.add(UItem.asCustom(markRow, 50));
        items.add(UItem.asCustom(colorRow, 76));
        items.add(UItem.asShadow(null));

        items.add(UItem.asButton(ID_CLEAR_DELETED, "Clear Deleted Messages").red());
        items.add(UItem.asShadow(null));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_KEEP_DELETED) {
            SharedConfig.setKeepDeletedMessages(!SharedConfig.keepDeletedMessages);
            listView.adapter.update(true);
        } else if (item.id == ID_FORCE_SCREENSHOTS) {
            SharedConfig.setForceAllowScreenshots(!SharedConfig.forceAllowScreenshots);
            listView.adapter.update(true);
        } else if (item.id == ID_TRANSLUCENT) {
            SharedConfig.setDeletedMessagesTranslucent(!SharedConfig.deletedMessagesTranslucent);
            previewContainer.updateDeletedStyle();
            listView.adapter.update(true);
        } else if (item.id == ID_CLEAR_DELETED) {
            confirmClearDeletedMessages();
        }
    }

    private void confirmClearDeletedMessages() {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
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

    /** Live preview: a real chat bubble on the chat wallpaper, rounded at the top like the card it sits in. */
    private class PreviewContainer extends FrameLayout {
        private final ThemePreviewMessagesCell previewCell;

        public PreviewContainer(Context context) {
            super(context);
            previewCell = new ThemePreviewMessagesCell(context, parentLayout, ThemePreviewMessagesCell.TYPE_DELETED_MESSAGE);
            addView(previewCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // Extend the rounded rect below the view so only the top corners are rounded.
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + dp(16), dp(16));
                }
            });
            setClipToOutline(true);
        }

        public void updateDeletedStyle() {
            previewCell.updateDeletedStyle();
        }
    }

    /** "Deleted Mark" row: shows the mark that is currently used. */
    private static class MarkRow extends FrameLayout {
        private final TextView titleView;
        private final ImageView iconView;

        public MarkRow(Context context) {
            super(context);
            setWillNotDraw(false);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setSingleLine();
            titleView.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            titleView.setText("Deleted Mark");
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 21, 0, 70, 0));

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            iconView.setImageResource(R.drawable.scout_ic_trash);
            iconView.setColorFilter(new PorterDuffColorFilter(DEFAULT_SWATCH_COLOR, PorterDuff.Mode.SRC_IN));
            addView(iconView, LayoutHelper.createFrame(32, 32, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final int y = getMeasuredHeight() - 1;
            canvas.drawLine(LocaleController.isRTL ? 0 : dp(21), y, getMeasuredWidth() - (LocaleController.isRTL ? dp(21) : 0), y, Theme.dividerPaint);
        }
    }

    /** Row of color swatches: first one is the default (follows the time color), then the accent colors. */
    private static class ColorRow extends View {
        interface OnColorSelected {
            void onSelected(int color);
        }

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedFloat[] selection = new AnimatedFloat[MARK_COLORS.length];
        private int selectedIndex;
        private int pressedIndex = -1;
        private OnColorSelected onColorSelected;

        public ColorRow(Context context) {
            super(context);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(2.5f));
            for (int i = 0; i < selection.length; i++) {
                selection[i] = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
            }
        }

        public void setOnColorSelected(OnColorSelected listener) {
            onColorSelected = listener;
        }

        public void setSelectedColor(int color, boolean animated) {
            int index = 0;
            for (int i = 0; i < MARK_COLORS.length; i++) {
                if (MARK_COLORS[i] == color) {
                    index = i;
                    break;
                }
            }
            selectedIndex = index;
            if (!animated) {
                for (int i = 0; i < selection.length; i++) {
                    selection[i].force(i == index ? 1f : 0f);
                }
            }
            invalidate();
        }

        private float diameter() {
            final float pad = dp(17);
            return Math.min(dp(35), (getMeasuredWidth() - 2 * pad - (MARK_COLORS.length - 1) * dp(6)) / MARK_COLORS.length);
        }

        private float centerX(int index) {
            final float pad = dp(17);
            final float d = diameter();
            final float gap = (getMeasuredWidth() - 2 * pad - MARK_COLORS.length * d) / (MARK_COLORS.length - 1);
            return pad + d / 2f + index * (d + gap);
        }

        private int indexAt(float x) {
            final float half = diameter() / 2f + dp(3);
            for (int i = 0; i < MARK_COLORS.length; i++) {
                if (Math.abs(x - centerX(i)) <= half) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final float cy = getMeasuredHeight() / 2f;
            final float r = diameter() / 2f;
            for (int i = 0; i < MARK_COLORS.length; i++) {
                final float cx = centerX(i);
                final int color = MARK_COLORS[i] == 0 ? DEFAULT_SWATCH_COLOR : MARK_COLORS[i];
                fillPaint.setColor(color);
                canvas.drawCircle(cx, cy, r, fillPaint);
                final float progress = selection[i].set(i == selectedIndex ? 1f : 0f);
                if (progress > 0f) {
                    ringPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, (int) (255 * progress)));
                    canvas.drawCircle(cx, cy, r - dp(5) * (0.6f + 0.4f * progress), ringPaint);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    pressedIndex = indexAt(event.getX());
                    return pressedIndex >= 0;
                case MotionEvent.ACTION_UP:
                    final int index = indexAt(event.getX());
                    if (index >= 0 && index == pressedIndex && index != selectedIndex) {
                        selectedIndex = index;
                        invalidate();
                        if (onColorSelected != null) {
                            onColorSelected.onSelected(MARK_COLORS[index]);
                        }
                    }
                    pressedIndex = -1;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    pressedIndex = -1;
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
