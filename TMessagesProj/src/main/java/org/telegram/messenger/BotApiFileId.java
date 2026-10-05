package org.telegram.messenger;

import android.util.Base64;

import java.io.ByteArrayOutputStream;

/**
 * Builds Bot API / Pyrogram style file_id and file_unique_id strings from MTProto media objects.
 * file_id is account-scoped (it carries access_hash and file_reference), file_unique_id is global.
 */
public class BotApiFileId {

    // File types
    public static final int TYPE_PHOTO = 2;
    public static final int TYPE_VOICE = 3;
    public static final int TYPE_VIDEO = 4;
    public static final int TYPE_DOCUMENT = 5;
    public static final int TYPE_STICKER = 8;
    public static final int TYPE_AUDIO = 9;
    public static final int TYPE_ANIMATION = 10;
    public static final int TYPE_VIDEO_NOTE = 13;

    private static final int FILE_REFERENCE_FLAG = 1 << 25;
    private static final int MAJOR = 4;
    private static final int MINOR = 30;
    private static final int UNIQUE_TYPE_DOCUMENT = 2;
    private static final int THUMBNAIL_SOURCE_THUMBNAIL = 1;

    public static String forDocument(int fileType, int dcId, byte[] fileReference, long mediaId, long accessHash) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final boolean hasRef = fileReference != null && fileReference.length > 0;
        writeInt(out, hasRef ? (fileType | FILE_REFERENCE_FLAG) : fileType);
        writeInt(out, dcId);
        if (hasRef) {
            writeTlBytes(out, fileReference);
        }
        writeLong(out, mediaId);
        writeLong(out, accessHash);
        out.write(MINOR);
        out.write(MAJOR);
        return encode(out.toByteArray());
    }

    public static String forPhoto(int dcId, byte[] fileReference, long mediaId, long accessHash, String sizeType) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final boolean hasRef = fileReference != null && fileReference.length > 0;
        writeInt(out, hasRef ? (TYPE_PHOTO | FILE_REFERENCE_FLAG) : TYPE_PHOTO);
        writeInt(out, dcId);
        if (hasRef) {
            writeTlBytes(out, fileReference);
        }
        writeLong(out, mediaId);
        writeLong(out, accessHash);
        writeLong(out, 0L); // volume_id
        writeInt(out, THUMBNAIL_SOURCE_THUMBNAIL);
        writeInt(out, TYPE_PHOTO); // thumbnail_file_type
        writeInt(out, sizeType != null && sizeType.length() > 0 ? sizeType.charAt(0) : 0);
        writeInt(out, 0); // local_id
        out.write(MINOR);
        out.write(MAJOR);
        return encode(out.toByteArray());
    }

    public static String uniqueId(long mediaId) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, UNIQUE_TYPE_DOCUMENT);
        writeLong(out, mediaId);
        return encode(out.toByteArray());
    }

    private static String encode(byte[] raw) {
        final ByteArrayOutputStream rle = new ByteArrayOutputStream();
        int zeros = 0;
        for (byte b : raw) {
            if (b == 0) {
                zeros++;
            } else {
                if (zeros > 0) {
                    rle.write(0);
                    rle.write(zeros);
                    zeros = 0;
                }
                rle.write(b);
            }
        }
        if (zeros > 0) {
            rle.write(0);
            rle.write(zeros);
        }
        return Base64.encodeToString(rle.toByteArray(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
        out.write((v >> 16) & 0xff);
        out.write((v >> 24) & 0xff);
    }

    private static void writeLong(ByteArrayOutputStream out, long v) {
        for (int i = 0; i < 8; i++) {
            out.write((int) ((v >> (8 * i)) & 0xff));
        }
    }

    private static void writeTlBytes(ByteArrayOutputStream out, byte[] data) {
        int len = data.length;
        int pad;
        if (len < 254) {
            out.write(len);
            pad = (4 - ((len + 1) % 4)) % 4;
        } else {
            out.write(254);
            out.write(len & 0xff);
            out.write((len >> 8) & 0xff);
            out.write((len >> 16) & 0xff);
            pad = (4 - (len % 4)) % 4;
        }
        out.write(data, 0, len);
        for (int i = 0; i < pad; i++) {
            out.write(0);
        }
    }
}
