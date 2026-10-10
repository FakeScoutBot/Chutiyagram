package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
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

    public static boolean marksUserOnline(TLObject request) {
        return request != null && !(request instanceof TL_account.updateStatus);
    }
}
