package org.telegram.ui.Stories;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;

/**
 * Downloads a story (photo or video) if needed and saves it to the gallery.
 * Shows the same loading dialog with a progress bar that Telegram uses when saving files.
 */
public class StoryDownloader implements NotificationCenter.NotificationCenterDelegate {

    private final BaseFragment fragment;
    private final int account;
    private final TL_stories.StoryItem story;

    private TLRPC.Document document;
    private TLRPC.Photo photo;
    private TLRPC.PhotoSize photoSize;
    private String fileKey;
    private boolean isVideo;

    private AlertDialog progressDialog;
    private boolean finished;

    public static void download(BaseFragment fragment, TL_stories.StoryItem story) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        new StoryDownloader(fragment, story).start();
    }

    private StoryDownloader(BaseFragment fragment, TL_stories.StoryItem story) {
        this.fragment = fragment;
        this.account = fragment.getCurrentAccount();
        this.story = story;
    }

    private void start() {
        final Activity activity = fragment.getParentActivity();
        if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            return;
        }
        if (story == null || story instanceof TL_stories.TL_storyItemSkipped || story.media == null) {
            showError();
            return;
        }
        document = story.media.getDocument();
        if (document != null) {
            isVideo = MessageObject.isVideoDocument(document);
            fileKey = FileLoader.getAttachFileName(document);
        } else if (story.media.photo != null) {
            photo = story.media.photo;
            photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
            if (photoSize == null) {
                showError();
                return;
            }
            isVideo = false;
            fileKey = FileLoader.getAttachFileName(photoSize);
        } else {
            showError();
            return;
        }

        final File existing = getFile();
        if (existing != null && existing.exists()) {
            saveToGallery(existing);
            return;
        }

        final NotificationCenter notificationCenter = NotificationCenter.getInstance(account);
        notificationCenter.addObserver(this, NotificationCenter.fileLoaded);
        notificationCenter.addObserver(this, NotificationCenter.fileLoadFailed);
        notificationCenter.addObserver(this, NotificationCenter.fileLoadProgressChanged);

        try {
            progressDialog = new AlertDialog(activity, AlertDialog.ALERT_TYPE_LOADING);
            progressDialog.setMessage(LocaleController.getString(R.string.Loading));
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setCancelable(true);
            progressDialog.setOnCancelListener(d -> {
                if (finished) {
                    return;
                }
                finish();
                cancelLoading();
            });
            progressDialog.show();
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (document != null) {
            FileLoader.getInstance(account).loadFile(document, story, FileLoader.PRIORITY_HIGH, 0);
        } else {
            FileLoader.getInstance(account).loadFile(ImageLocation.getForPhoto(photoSize, photo), story, "jpg", FileLoader.PRIORITY_HIGH, 0);
        }
    }

    private File getFile() {
        if (document != null) {
            return FileLoader.getInstance(account).getPathToAttach(document);
        }
        File file = FileLoader.getInstance(account).getPathToAttach(photoSize, true);
        if (!file.exists()) {
            file = FileLoader.getInstance(account).getPathToAttach(photoSize, false);
        }
        return file;
    }

    private void cancelLoading() {
        if (document != null) {
            FileLoader.getInstance(account).cancelLoadFile(document);
        } else if (photoSize != null) {
            FileLoader.getInstance(account).cancelLoadFile(photoSize);
        }
    }

    private void saveToGallery(File file) {
        final BaseFragment f = fragment;
        final boolean video = isVideo;
        MediaController.saveFile(file.toString(), f.getParentActivity(), video ? 1 : 0, null, null, uri -> {
            BulletinFactory.createSaveToGalleryBulletin(f, video, null).show();
        });
    }

    private void showError() {
        BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.ErrorOccurred)).show();
    }

    private void finish() {
        finished = true;
        final NotificationCenter notificationCenter = NotificationCenter.getInstance(account);
        notificationCenter.removeObserver(this, NotificationCenter.fileLoaded);
        notificationCenter.removeObserver(this, NotificationCenter.fileLoadFailed);
        notificationCenter.removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        if (progressDialog != null) {
            try {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            progressDialog = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (finished || fileKey == null || args == null || args.length == 0 || !(args[0] instanceof String) || !fileKey.equals(args[0])) {
            return;
        }
        if (id == NotificationCenter.fileLoadProgressChanged) {
            if (progressDialog != null && args.length >= 3 && args[1] instanceof Long && args[2] instanceof Long) {
                final long loaded = (Long) args[1];
                final long total = (Long) args[2];
                if (total > 0) {
                    try {
                        progressDialog.setProgress((int) (loaded * 100f / total));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        } else if (id == NotificationCenter.fileLoaded) {
            finish();
            final File file = getFile();
            if (file != null && file.exists()) {
                saveToGallery(file);
            } else {
                showError();
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            finish();
            showError();
        }
    }
}
