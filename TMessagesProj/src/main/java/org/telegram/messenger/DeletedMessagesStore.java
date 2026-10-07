package org.telegram.messenger;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    private final Object lock = new Object();
    private final DispatchQueue queue = new DispatchQueue("deletedMessagesQueue");
    private final HashMap<Long, HashSet<Integer>> deleted = new HashMap<>();
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
            cursor = database.queryFinalized("SELECT uid, mid FROM deleted_messages");
            while (cursor.next()) {
                putInMemory(cursor.longValue(0), cursor.intValue(1));
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private void putInMemory(long uid, int mid) {
        HashSet<Integer> set = deleted.get(uid);
        if (set == null) {
            set = new HashSet<>();
            deleted.put(uid, set);
        }
        set.add(mid);
    }

    public boolean isDeleted(long uid, int mid) {
        synchronized (lock) {
            ensureLoaded();
            HashSet<Integer> set = deleted.get(uid);
            return set != null && set.contains(mid);
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
                putInMemory(uid, copy.get(a));
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

    /** A copy of everything stored, dialog id to message ids. */
    public HashMap<Long, ArrayList<Integer>> snapshot() {
        HashMap<Long, ArrayList<Integer>> result = new HashMap<>();
        synchronized (lock) {
            ensureLoaded();
            for (Map.Entry<Long, HashSet<Integer>> entry : deleted.entrySet()) {
                result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
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
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }
}
