package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.tgnet.tl.TL_stories;

/**
 * Requests after which Telegram's server marks the account as online (sending a message, joining or leaving a
 * group / video chat, deleting a group ...). In Stealth Mode we answer each of them with an immediate "offline".
 */
public class StealthActions {

    public static boolean marksUserOnline(TLObject request) {
        if (request == null) {
            return false;
        }
        return
            // messages
            request instanceof TLRPC.TL_messages_sendMessage ||
            request instanceof TLRPC.TL_messages_sendMedia ||
            request instanceof TLRPC.TL_messages_sendMultiMedia ||
            request instanceof TLRPC.TL_messages_forwardMessages ||
            request instanceof TLRPC.TL_messages_sendInlineBotResult ||
            request instanceof TLRPC.TL_messages_sendReaction ||
            request instanceof TLRPC.TL_messages_sendVote ||
            request instanceof TLRPC.TL_messages_editMessage ||
            request instanceof TLRPC.TL_messages_deleteMessages ||
            request instanceof TLRPC.TL_messages_deleteHistory ||
            request instanceof TLRPC.TL_messages_startBot ||
            request instanceof TLRPC.TL_messages_getBotCallbackAnswer ||
            // groups / channels
            request instanceof TLRPC.TL_channels_joinChannel ||
            request instanceof TLRPC.TL_channels_leaveChannel ||
            request instanceof TLRPC.TL_channels_deleteChannel ||
            request instanceof TLRPC.TL_channels_createChannel ||
            request instanceof TLRPC.TL_channels_inviteToChannel ||
            request instanceof TLRPC.TL_channels_editBanned ||
            request instanceof TLRPC.TL_channels_editAdmin ||
            request instanceof TLRPC.TL_messages_importChatInvite ||
            request instanceof TLRPC.TL_messages_createChat ||
            request instanceof TLRPC.TL_messages_addChatUser ||
            request instanceof TLRPC.TL_messages_deleteChatUser ||
            request instanceof TLRPC.TL_messages_deleteChat ||
            // video chats and calls
            request instanceof TL_phone.joinGroupCall ||
            request instanceof TL_phone.leaveGroupCall ||
            request instanceof TL_phone.createGroupCall ||
            request instanceof TL_phone.discardGroupCall ||
            request instanceof TL_phone.inviteToGroupCall ||
            request instanceof TL_phone.editGroupCallParticipant ||
            request instanceof TL_phone.joinGroupCallPresentation ||
            request instanceof TL_phone.leaveGroupCallPresentation ||
            request instanceof TL_phone.requestCall ||
            request instanceof TL_phone.acceptCall ||
            request instanceof TL_phone.discardCall ||
            // stories
            request instanceof TL_stories.TL_stories_sendStory ||
            request instanceof TL_stories.TL_stories_deleteStories;
    }
}
