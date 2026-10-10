package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

/**
 * Decides which requests are answered with an immediate "offline" while Hide Online Presence is on.
 *
 * Telegram does not document which methods make the server mark the account as online (sending, reading,
 * joining, editing, opening a mini app ...), and a whitelist or a "passive" list always gets one wrong. So
 * nothing is skipped: every request is followed by an offline. A request that really was passive only costs
 * one extra account.updateStatus. The one exception is account.updateStatus itself, otherwise the offline
 * request would trigger another offline forever.
 */
public class StealthActions {

    /** Telegram sends a scheduled message right away when schedule_date is less than 10 s ahead, so keep a margin. */
    public static final int SCHEDULE_DELAY_SECONDS = 12;

    /**
     * Schedule Messages: a message is scheduled when it is created, but its request can go out much later
     * (media is uploaded first, a bad network retries). If the date is by then closer than the margin the
     * server would deliver it immediately (and we would show online), so it is moved to now + margin.
     * Dates further ahead (messages the user scheduled) are left alone.
     */
    public static void refreshScheduleDate(TLObject request, int now) {
        if (!SharedConfig.ghostScheduleMessages || request == null) {
            return;
        }
        if (request instanceof TLRPC.TL_messages_sendMessage) {
            final TLRPC.TL_messages_sendMessage req = (TLRPC.TL_messages_sendMessage) request;
            req.schedule_date = refreshedDate(req.schedule_date, now);
        } else if (request instanceof TLRPC.TL_messages_sendMedia) {
            final TLRPC.TL_messages_sendMedia req = (TLRPC.TL_messages_sendMedia) request;
            req.schedule_date = refreshedDate(req.schedule_date, now);
        } else if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
            final TLRPC.TL_messages_sendMultiMedia req = (TLRPC.TL_messages_sendMultiMedia) request;
            req.schedule_date = refreshedDate(req.schedule_date, now);
        } else if (request instanceof TLRPC.TL_messages_forwardMessages) {
            final TLRPC.TL_messages_forwardMessages req = (TLRPC.TL_messages_forwardMessages) request;
            req.schedule_date = refreshedDate(req.schedule_date, now);
        } else if (request instanceof TLRPC.TL_messages_sendInlineBotResult) {
            final TLRPC.TL_messages_sendInlineBotResult req = (TLRPC.TL_messages_sendInlineBotResult) request;
            req.schedule_date = refreshedDate(req.schedule_date, now);
        }
    }

    private static int refreshedDate(int scheduleDate, int now) {
        if (scheduleDate == 0 || scheduleDate == 0x7FFFFFFE) { // not scheduled / "when online"
            return scheduleDate;
        }
        return scheduleDate < now + SCHEDULE_DELAY_SECONDS ? now + SCHEDULE_DELAY_SECONDS : scheduleDate;
    }

    public static boolean marksUserOnline(TLObject request) {
        return request != null && !(request instanceof TL_account.updateStatus);
    }
}
