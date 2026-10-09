package org.telegram.messenger;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Remembers which messages were deleted by someone else but are kept in the local history.
 *
 * The messages themselves stay in the regular message database, in their original place. This store only holds
 * the (dialog id, message id) pairs, in its own small database so Telegram's message storage schema is untouched.
 */
public class DeletedMessagesStore extends BaseController {

    private static final DeletedMessagesStore[] instances = new DeletedMessagesStore[UserConfig.MAX_ACCOUNT_COUNT];

    public static DeletedMessagesStore getInstance(int num) {
        DeletedMessagesStore local = instances[num];
        if (local == null) {
            synchronized (DeletedMessagesStore.class) {
                local = instances[num];
                if (local == null) {
                    instances[num] = local = new DeletedMessagesStore(num);
                }
            }
        }
        return local;
    }

    // copies of kept channel messages are limited per channel, the oldest ones go first
    private static final int MAX_SAVED_PER_CHANNEL = 1000;

    private final Object lock = new Object();
    private final DispatchQueue queue = new DispatchQueue("deletedMessagesQueue");
    // dialog id -> (message id -> unix time at which this client saw the deletion)
    private final HashMap<Long, HashMap<Integer, Integer>> deleted = new HashMap<>();
    private SQLiteDatabase database;
    private boolean loaded;

    private DeletedMessagesStore(int num) {
        super(num);
    }

    // must be called with the lock held
    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        SQLiteCursor cursor = null;
        try {
            File file = new File(ApplicationLoader.getFilesDirFixed(), "deleted_messages_" + currentAccount + ".db");
            database = new SQLiteDatabase(file.getPath());
            database.executeFast("CREATE TABLE IF NOT EXISTS deleted_messages(uid INTEGER, mid INTEGER, date INTEGER, PRIMARY KEY(uid, mid))").stepThis().dispose();
            database.executeFast("CREATE TABLE IF NOT EXISTS deleted_message_data(uid INTEGER, mid INTEGER, data BLOB, PRIMARY KEY(uid, mid))").stepThis().dispose();
            cursor = database.queryFinalized("SELECT uid, mid, date FROM deleted_messages");
            while (cursor.next()) {
                putInMemory(cursor.longValue(0), cursor.intValue(1), cursor.intValue(2));
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private void putInMemory(long uid, int mid, int date) {
        HashMap<Integer, Integer> map = deleted.get(uid);
        if (map == null) {
            map = new HashMap<>();
            deleted.put(uid, map);
        }
        map.put(mid, date);
    }

    public boolean isDeleted(long uid, int mid) {
        synchronized (lock) {
            ensureLoaded();
            HashMap<Integer, Integer> map = deleted.get(uid);
            return map != null && map.containsKey(mid);
        }
    }

    /** Unix time at which this client noticed the deletion, or 0 when the message is not stored. */
    public int getDeletedDate(long uid, int mid) {
        synchronized (lock) {
            ensureLoaded();
            HashMap<Integer, Integer> map = deleted.get(uid);
            Integer date = map != null ? map.get(mid) : null;
            return date != null ? date : 0;
        }
    }

    public void add(long uid, ArrayList<Integer> mids) {
        if (mids == null || mids.isEmpty()) {
            return;
        }
        final ArrayList<Integer> copy = new ArrayList<>(mids);
        final int date = (int) (System.currentTimeMillis() / 1000);
        synchronized (lock) {
            ensureLoaded();
            for (int a = 0, N = copy.size(); a < N; a++) {
                putInMemory(uid, copy.get(a), date);
            }
        }
        queue.postRunnable(() -> {
            synchronized (lock) {
                if (database == null) {
                    return;
                }
                SQLitePreparedStatement state = null;
                try {
                    database.beginTransaction();
                    state = database.executeFast("REPLACE INTO deleted_messages VALUES(?, ?, ?)");
                    for (int a = 0, N = copy.size(); a < N; a++) {
                        state.requery();
                        state.bindLong(1, uid);
                        state.bindInteger(2, copy.get(a));
                        state.bindInteger(3, date);
                        state.step();
                    }
                    state.dispose();
                    state = null;
                    database.commitTransaction();
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (state != null) {
                        state.dispose();
                    }
                }
            }
        });
    }

    /**
     * Keeps a serialized copy of a kept channel message. A "too long" channel difference wipes the whole channel history
     * from the message database, the copies are what puts the kept messages back (see getSavedMessages).
     */
    public void saveMessageData(long uid, int mid, TLRPC.Message message) {
        if (message == null) {
            return;
        }
        final NativeByteBuffer buffer;
        try {
            buffer = new NativeByteBuffer(message.getObjectSize());
            message.serializeToStream(buffer);
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        queue.postRunnable(() -> {
            synchronized (lock) {
                ensureLoaded();
                SQLitePreparedStatement state = null;
                try {
                    if (database == null) {
                        return;
                    }
                    state = database.executeFast("REPLACE INTO deleted_message_data VALUES(?, ?, ?)");
                    state.requery();
                    state.bindLong(1, uid);
                    state.bindInteger(2, mid);
                    state.bindByteBuffer(3, buffer);
                    state.step();
                    state.dispose();
                    state = null;
                    database.executeFast("DELETE FROM deleted_message_data WHERE uid = " + uid + " AND mid NOT IN (SELECT mid FROM deleted_message_data WHERE uid = " + uid + " ORDER BY mid DESC LIMIT " + MAX_SAVED_PER_CHANNEL + ")").stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (state != null) {
                        state.dispose();
                    }
                    buffer.reuse();
                }
            }
        });
    }

    /** The saved copies of the kept messages of a dialog, newest first. Only messages that are still marked as deleted. */
    public ArrayList<TLRPC.Message> getSavedMessages(long uid) {
        return getSavedMessages(uid, 1, Integer.MAX_VALUE);
    }

    /** Same as getSavedMessages(uid), limited to message ids from minId to maxId (both included). */
    public ArrayList<TLRPC.Message> getSavedMessages(long uid, int minId, int maxId) {
        ArrayList<TLRPC.Message> result = new ArrayList<>();
        synchronized (lock) {
            ensureLoaded();
            if (database == null) {
                return result;
            }
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized("SELECT mid, data FROM deleted_message_data WHERE uid = " + uid + " AND mid >= " + minId + " AND mid <= " + maxId + " ORDER BY mid DESC");
                while (cursor.next()) {
                    int mid = cursor.intValue(0);
                    HashMap<Integer, Integer> map = deleted.get(uid);
                    NativeByteBuffer data = cursor.byteBufferValue(1);
                    if (data == null) {
                        continue;
                    }
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (message == null || message instanceof TLRPC.TL_messageEmpty || map == null || !map.containsKey(mid)) {
                        continue;
                    }
                    message.id = mid;
                    message.dialog_id = uid;
                    result.add(message);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        }
        return result;
    }

    /** A copy of everything stored, dialog id to message ids. */
    public HashMap<Long, ArrayList<Integer>> snapshot() {
        HashMap<Long, ArrayList<Integer>> result = new HashMap<>();
        synchronized (lock) {
            ensureLoaded();
            for (Map.Entry<Long, HashMap<Integer, Integer>> entry : deleted.entrySet()) {
                result.put(entry.getKey(), new ArrayList<>(entry.getValue().keySet()));
            }
        }
        return result;
    }

    public void clearAll() {
        synchronized (lock) {
            ensureLoaded();
            deleted.clear();
        }
        queue.postRunnable(() -> {
            synchronized (lock) {
                if (database == null) {
                    return;
                }
                try {
                    database.executeFast("DELETE FROM deleted_messages").stepThis().dispose();
                    database.executeFast("DELETE FROM deleted_message_data").stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }
}
