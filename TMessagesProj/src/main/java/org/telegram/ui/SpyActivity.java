package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;

import java.util.ArrayList;

/**
 * Spy settings.
 * Row 1: Keep Deleted Messages toggle
 * Row 2: Customization (translucent toggle, deleted mark icon, deleted mark color)
 * Row 3: Clear Saved Deleted Messages
 */
public class SpyActivity extends BaseFragment {

    private static final int ID_KEEP_DELETED = 1;
    private static final int ID_TRANSLUCENT = 2;
    private static final int ID_MARK = 3;
    private static final int ID_COLORS = 4;
    private static final int ID_CLEAR = 5;
    private static final int ID_PREVIEW = 6;

    private UniversalRecyclerView listView;
    private MarkRow markRow;
    private ColorRow colorRow;
    private ThemePreviewMessagesCell preview;
    private FrameLayout previewHolder;

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

        markRow = new MarkRow(context);
        markRow.setOnClickListener(v -> showMarkPicker());
        
        preview = new ThemePreviewMessagesCell(context, getParentLayout(), ThemePreviewMessagesCell.TYPE_DELETED_PREVIEW);
        preview.drawShadow = false;
        final FrameLayout card = new FrameLayout(context);
        card.addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
            }
        });
        previewHolder = new FrameLayout(context);
        previewHolder.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 12, 4, 12, 8));

        colorRow = new ColorRow(context);
        colorRow.setCallback(index -> {
            SharedConfig.setDeletedMarkColor(SharedConfig.DELETED_MARK_COLORS[index]);
            colorRow.invalidate();
            if (preview != null) preview.refreshDeletedPreview();
            
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
        // Row 1: keep deleted messages
        items.add(UItem.asHeader("Deleted Messages"));
        items.add(UItem.asCheck(ID_KEEP_DELETED, "Keep Deleted Messages").setChecked(SharedConfig.keepDeletedMessages));
        items.add(UItem.asShadow("Messages that other people delete stay in your chats, in the same place. Secret chats, service messages and self-destructing messages are never kept. Messages you delete yourself are removed as usual."));

        // Row 2: customization
        items.add(UItem.asHeader("Customization"));
        items.add(UItem.asCustom(ID_PREVIEW, previewHolder));
        items.add(UItem.asCheck(ID_TRANSLUCENT, "Translucent Deleted Messages").setChecked(SharedConfig.deletedTranslucent));
        items.add(UItem.asCustom(ID_MARK, markRow));
        items.add(UItem.asCustom(ID_COLORS, colorRow));
        items.add(UItem.asShadow(null));

        // Row 3: clear
        items.add(UItem.asButton(ID_CLEAR, R.drawable.msg_delete, "Clear Saved Deleted Messages"));
        items.add(UItem.asShadow(null));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_KEEP_DELETED) {
            SharedConfig.setKeepDeletedMessages(!SharedConfig.keepDeletedMessages);
            listView.adapter.update(true);
        } else if (item.id == ID_TRANSLUCENT) {
            SharedConfig.setDeletedTranslucent(!SharedConfig.deletedTranslucent);
            listView.adapter.update(true);
            if (preview != null) preview.refreshDeletedPreview();
        } else if (item.id == ID_CLEAR) {
            confirmClearDeletedMessages();
        }
    }

    private void showMarkPicker() {
        if (getParentActivity() == null) {
            return;
        }
        final Context context = getParentActivity();
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Deleted Mark");

        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(8), dp(4), dp(8), dp(12));

        final AlertDialog[] dialogRef = new AlertDialog[1];
        final int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
        final int iconColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);

        for (int i = 0; i < SharedConfig.DELETED_MARK_ICONS.length; i++) {
            final int index = i;
            final ImageView iv = new ImageView(context);
            iv.setScaleType(ImageView.ScaleType.CENTER);
            iv.setImageResource(SharedConfig.DELETED_MARK_ICONS[i]);
            iv.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
            if (i == SharedConfig.deletedMarkIcon) {
                final GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor((accent & 0x00FFFFFF) | 0x33000000);
                iv.setBackground(bg);
            }
            iv.setOnClickListener(v -> {
                SharedConfig.setDeletedMarkIcon(index);
                markRow.update();
                if (preview != null) preview.refreshDeletedPreview();
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
            });
            row.addView(iv, new LinearLayout.LayoutParams(dp(42), dp(42)));
        }

        builder.setView(row);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        dialogRef[0] = builder.create();
        showDialog(dialogRef[0]);
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

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // "Deleted Mark" row: title on the left, the currently selected icon on the right.
    // ---------------------------------------------------------------------------------------------
    private static class MarkRow extends FrameLayout {

        private final TextView textView;
        private final ImageView iconView;

        MarkRow(Context context) {
            super(context);
            setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setSingleLine();
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setText("Deleted Mark");
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 21, 0, 70, 0));

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
            addView(iconView, LayoutHelper.createFrame(28, 28, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 21, 0));

            update();
        }

        void update() {
            iconView.setImageResource(SharedConfig.getDeletedMarkIconRes());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Color row: 8 circles (first one = default / gray), selected one gets a ring.
    // ---------------------------------------------------------------------------------------------
    private static class ColorRow extends View {

        interface Callback {
            void onPick(int index);
        }

        private static final int DEFAULT_PREVIEW_COLOR = 0xFF9CA3AF;

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Callback callback;

        ColorRow(Context context) {
            super(context);
            ringPaint.setStyle(Paint.Style.STROKE);
        }

        void setCallback(Callback callback) {
            this.callback = callback;
        }

        private static int selectedIndex() {
            for (int i = 0; i < SharedConfig.DELETED_MARK_COLORS.length; i++) {
                if (SharedConfig.DELETED_MARK_COLORS[i] == SharedConfig.deletedMarkColor) {
                    return i;
                }
            }
            return 0;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(76));
        }

        private float slotWidth() {
            return (getMeasuredWidth() - dp(16) * 2) / (float) SharedConfig.DELETED_MARK_COLORS.length;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int n = SharedConfig.DELETED_MARK_COLORS.length;
            final float slot = slotWidth();
            final float radius = Math.min(dp(17), slot / 2f - dp(3));
            final float cy = getMeasuredHeight() / 2f;
            final int selected = selectedIndex();
            final int bg = Theme.getColor(Theme.key_windowBackgroundWhite);
            for (int i = 0; i < n; i++) {
                final float cx = dp(16) + slot * (i + 0.5f);
                final int color = i == 0 ? DEFAULT_PREVIEW_COLOR : SharedConfig.DELETED_MARK_COLORS[i];
                fillPaint.setColor(color);
                canvas.drawCircle(cx, cy, radius, fillPaint);
                if (i == selected) {
                    ringPaint.setColor(bg);
                    ringPaint.setStrokeWidth(dp(2.5f));
                    canvas.drawCircle(cx, cy, radius - dp(4), ringPaint);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                final int index = (int) ((event.getX() - dp(16)) / slotWidth());
                if (index >= 0 && index < SharedConfig.DELETED_MARK_COLORS.length && callback != null) {
                    callback.onPick(index);
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
