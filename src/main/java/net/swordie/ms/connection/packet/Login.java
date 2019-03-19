package net.swordie.ms.connection.packet;

import net.swordie.ms.client.Account;
import net.swordie.ms.client.User;
import net.swordie.ms.client.character.Char;
import net.swordie.ms.connection.OutPacket;
import net.swordie.ms.constants.JobConstants;
import net.swordie.ms.ServerConstants;
import net.swordie.ms.enums.LoginType;
import net.swordie.ms.ServerStatus;
import net.swordie.ms.handlers.header.InHeader;
import net.swordie.ms.handlers.header.OutHeader;
import net.swordie.ms.util.Position;
import net.swordie.ms.util.container.Tuple;
import net.swordie.ms.world.Channel;
import net.swordie.ms.Server;
import net.swordie.ms.world.World;
import net.swordie.ms.util.FileTime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Created by Tim on 2/28/2017.
 */
public class Login {

    public static OutPacket sendConnect(int siv, int riv, boolean login) {
        OutPacket oPacket = new OutPacket();
        int length = 2 + (2 + ServerConstants.MINOR_VERSION.length()) + 4 + 4 + 1 + (login ? 4 : 1);
        //int length = 14 + ServerConstants.MINOR_VERSION.length();
        oPacket.encodeShort((short) length);
        oPacket.encodeShort(ServerConstants.VERSION);
        oPacket.encodeString(ServerConstants.MINOR_VERSION);
        oPacket.encodeInt(siv);
        oPacket.encodeInt(riv);
        oPacket.encodeByte(ServerConstants.LOCALE);
        // 13
        if (login) {
            oPacket.encodeInt(0);
        } else {
            oPacket.encodeByte(false);// bLoadSingleThread
        }
        return oPacket;
    }

    public static OutPacket sendAliveReq() {
        return new OutPacket(OutHeader.ALIVE_REQ.getValue());
    }

    public static OutPacket sendAuthServer(boolean useAuthServer) {
        OutPacket outPacket = new OutPacket(OutHeader.AUTH_SERVER.getValue());
        outPacket.encodeByte(useAuthServer);
        return outPacket;
    }

    public static OutPacket sendStart() {
        OutPacket outPacket = new OutPacket(OutHeader.CLIENT_START.getValue());

        outPacket.encodeByte(true);

        return outPacket;
    }

    public static OutPacket sendLoginTime() {
        OutPacket outPacket = new OutPacket(OutHeader.LOGIN_TIME.getValue());

        outPacket.encodeFT(FileTime.currentTime());

        return outPacket;
    }

    public static OutPacket checkPasswordResult(boolean success, LoginType msg, User user) {
        OutPacket outPacket = new OutPacket(OutHeader.CHECK_PASSWORD_RESULT.getValue());

        if (success) {
            outPacket.encodeByte(LoginType.Success.getValue());
            outPacket.encodeByte(0);
            outPacket.encodeInt(0);
            outPacket.encodeString(user.getName());
            outPacket.encodeInt(user.getId());
            //outPacket.encodeByte(account.getGender());
            outPacket.encodeByte(user.getMsg2());// nGradeCode
            outPacket.encodeInt(user.getPrivateStatusIDFlag().getFlag());
            outPacket.encodeInt(user.getAge());// nVIPGrade
            outPacket.encodeByte(!user.hasCensoredNxLoginID());
            if(user.hasCensoredNxLoginID()) {
                outPacket.encodeString(user.getCensoredNxLoginID());
            }
            outPacket.encodeString(user.getName());
            outPacket.encodeByte(user.getpBlockReason());
            outPacket.encodeByte(0); // idk
            outPacket.encodeLong(user.getChatUnblockDate());
            outPacket.encodeLong(user.getChatUnblockDate());
            outPacket.encodeInt(user.getCharacterSlots() + 3);
            JobConstants.encode(outPacket, user.isManagerAccount());
            outPacket.encodeByte(user.getGradeCode());
            outPacket.encodeInt(-1);
            outPacket.encodeByte(0); // idk
            outPacket.encodeByte(0); // ^
            outPacket.encodeFT(user.getCreationDate());
        } else if (msg == LoginType.Blocked) {
            outPacket.encodeByte(msg.getValue());
            outPacket.encodeByte(0);
            outPacket.encodeInt(0);
            outPacket.encodeByte(0); // nReason
            outPacket.encodeFT(user.getBanExpireDate());
        } else {
            outPacket.encodeByte(msg.getValue());
            outPacket.encodeByte(0); // these two aren't in ida, wtf
            outPacket.encodeInt(0);
        }

        return outPacket;
    }

    public static OutPacket checkPasswordResultForBan(User user) {
        OutPacket outPacket = new OutPacket(OutHeader.CHECK_PASSWORD_RESULT);

        outPacket.encodeByte(LoginType.BlockedNexonID.getValue());
        outPacket.encodeByte(0);
        outPacket.encodeInt(0);
        outPacket.encodeByte(0);
        outPacket.encodeFT(user.getBanExpireDate());

        return outPacket;
    }

    public static OutPacket sendWorldInformation(World world, Set<Tuple<Position, String>> stringInfos) {
        // CLogin::OnWorldInformation
        OutPacket outPacket = new OutPacket(OutHeader.WORLD_INFORMATION.getValue());

        outPacket.encodeByte(world.getWorldId());
        outPacket.encodeString(world.getName());
        outPacket.encodeByte(world.getWorldState());
        outPacket.encodeString(world.getWorldEventDescription());
        outPacket.encodeByte(world.isCharCreateBlock());
        outPacket.encodeByte(world.getChannels().size());
        for (Channel c : world.getChannels()) {
            outPacket.encodeString(c.getName());
            outPacket.encodeInt(c.getGaugePx());
            outPacket.encodeByte(c.getWorldId());
            outPacket.encodeByte(c.getChannelId());
            outPacket.encodeByte(c.isAdultChannel());
        }
        if (stringInfos == null) {
            outPacket.encodeShort(0);
        } else {
            outPacket.encodeShort(stringInfos.size());
            for (Tuple<Position, String> stringInfo : stringInfos) {
                outPacket.encodePosition(stringInfo.getLeft());
                outPacket.encodeString(stringInfo.getRight());
            }
        }
        outPacket.encodeInt(0); // some offset
        outPacket.encodeByte(false); // connect with star planet stuff, not interested
        return outPacket;
    }

    public static OutPacket sendWorldInformationEnd() {
        OutPacket outPacket = new OutPacket(OutHeader.WORLD_INFORMATION);

        outPacket.encodeByte(-1);
        outPacket.encodeByte(0);
        outPacket.encodeByte(0);
        outPacket.encodeByte(0);

        return outPacket;
    }

    public static OutPacket sendAccountInfo(User user) {
        OutPacket outPacket = new OutPacket(OutHeader.ACCOUNT_INFO_RESULT);

        outPacket.encodeByte(0); // succeed
        outPacket.encodeInt(user.getId());
        outPacket.encodeByte(user.getGradeCode());
        outPacket.encodeInt(user.getPrivateStatusIDFlag().getFlag());
        outPacket.encodeInt(user.getVipGrade());
        outPacket.encodeByte(user.getGender());
        outPacket.encodeString(user.getName());
        outPacket.encodeByte(user.getPurchaseExp());
        outPacket.encodeByte(user.getnBlockReason());
        outPacket.encodeLong(user.getChatUnblockDate());
        outPacket.encodeString(user.getCensoredNxLoginID());
        outPacket.encodeLong(0);
        outPacket.encodeInt(28);
        outPacket.encodeLong(0);
        outPacket.encodeString(""); //v25 = CInPacket::DecodeStr(iPacket_1, &nAge);
        JobConstants.encode(outPacket, user.isManagerAccount());
        outPacket.encodeByte(0);
        outPacket.encodeInt(-1);

        return outPacket;
    }

    public static OutPacket sendServerStatus(byte worldId) {
        OutPacket outPacket = new OutPacket(OutHeader.SERVER_STATUS.getValue());
        World world = null;
        for (World w : Server.getInstance().getWorlds()) {
            if (w.getWorldId() == worldId) {
                world = w;
            }
        }
        if (world != null && !world.isFull()) {
            outPacket.encodeByte(world.getStatus().getValue());
        } else {
            outPacket.encodeByte(ServerStatus.BUSY.getValue());
        }
        outPacket.encodeByte(0); // ?

        return outPacket;
    }

    public static OutPacket sendSelectWorld(int worldId, byte unk) {
        OutPacket outPacket = new OutPacket(OutHeader.SELECT_WORLD_BUTTON);

        outPacket.encodeByte(unk);
        outPacket.encodeInt(worldId);
        return outPacket;
    }

    public static OutPacket selectWorldResult(User user, Account account, byte code, String specialServer, boolean burningEventBlock) {
        OutPacket outPacket = new OutPacket(OutHeader.SELECT_WORLD_RESULT);

        outPacket.encodeByte(code);
        outPacket.encodeByte(0);
        outPacket.encodeString(specialServer);
        outPacket.encodeString(specialServer);
        outPacket.encodeInt(account.getTrunk().getSlotCount());
        outPacket.encodeByte(burningEventBlock); // bBurningEventBlock
        int reserved = 0;
        outPacket.encodeInt(reserved); // Reserved size
        outPacket.encodeFT(FileTime.fromType(FileTime.Type.ZERO_TIME)); //Reserved timestamp
        for (int i = 0; i < reserved; i++) {
            // not really interested in this
            FileTime ft = FileTime.fromType(FileTime.Type.ZERO_TIME);
            outPacket.encodeInt(ft.getLowDateTime());
            ft.encode(outPacket);
        }
        boolean isEdited = false;
        outPacket.encodeByte(isEdited); // edited characters
        List<Char> chars = new ArrayList<>(account.getCharacters());
        chars.sort(Comparator.comparingInt(Char::getId));
        int orderSize = chars.size();
        outPacket.encodeInt(orderSize);
        for (Char chr : chars) {
            // order of chars
            outPacket.encodeInt(chr.getId());
        }

        outPacket.encodeByte(chars.size());
        for (Char chr : chars) {
            chr.getAvatarData().encode(outPacket);
            outPacket.encodeByte(false); // family stuff, deprecated (v61 = &v2->m_abOnFamily.a[v59];)
            /*boolean hasRanking = chr.getRanking() != null && !JobConstants.isGmJob(chr.getJob());
            outPacket.encodeByte(hasRanking);
            if (hasRanking) {
                chr.getRanking().encode(outPacket);
            }*/
        }
        outPacket.encodeByte(user.getPicStatus().getVal()); // bLoginOpt
        outPacket.encodeByte(false); // bQuerySSNOnCreateNewCharacter
        outPacket.encodeInt(user.getCharacterSlots());
        outPacket.encodeInt(0); // buying char slots
        outPacket.encodeInt(-1); // nEventNewCharJob
        outPacket.encodeFT(FileTime.fromType(FileTime.Type.ZERO_TIME));
        outPacket.encodeByte(0); // nRenameCount
        outPacket.encodeByte(0);
        outPacket.encodeByte(0);
        outPacket.encodeByte(0);
        outPacket.encodeInt(0);
        outPacket.encodeInt(0);
        outPacket.encodeInt(0);
        return outPacket;
    }

    public static OutPacket checkDuplicatedIDResult(String name, byte code) {
        OutPacket outPacket = new OutPacket(OutHeader.CHECK_DUPLICATED_ID_RESULT);

        outPacket.encodeString(name);
        outPacket.encodeByte(code);

        return outPacket;
    }

    public static OutPacket createNewCharacterResult(LoginType type, Char c) {
        OutPacket outPacket = new OutPacket(OutHeader.CREATE_NEW_CHARACTER_RESULT);

        outPacket.encodeByte(type.getValue());
        if (type == LoginType.Success) {
            c.getAvatarData().encode(outPacket);
        }
        outPacket.encodeByte(0);
        return outPacket;
    }

    public static OutPacket sendAuthResponse(int response) {
        OutPacket outPacket = new OutPacket(OutHeader.PRIVATE_SERVER_PACKET);

        outPacket.encodeInt(response);

        return outPacket;
    }

    public static OutPacket selectCharacterResult(LoginType loginType, byte errorCode, int port, int characterId) {
        OutPacket outPacket = new OutPacket(OutHeader.SELECT_CHARACTER_RESULT);

        outPacket.encodeByte(loginType.getValue());
        outPacket.encodeByte(errorCode);

        if (loginType == LoginType.Success) {
            byte[] server = new byte[]{8, 31, 99, ((byte) 141)};
            outPacket.encodeArr(server);
            outPacket.encodeShort(port);

            byte[] chatServer = new byte[]{0, 0, 0, 0};
            // chat stuff
            outPacket.encodeArr(chatServer);
            outPacket.encodeShort(0);// chat port

            outPacket.encodeInt(0);
            outPacket.encodeInt(characterId);

            outPacket.encodeByte(0);
            outPacket.encodeInt(0); // ulArgument
            outPacket.encodeByte(0);
            outPacket.encodeInt(0);
            outPacket.encodeInt(0);
            outPacket.encodeByte(0);
            outPacket.encodeArr(new byte[17]);
        }
        return outPacket;
    }

    public static OutPacket sendDeleteCharacterResult(int charId, LoginType loginType) {
        OutPacket outPacket = new OutPacket(OutHeader.DELETE_CHARACTER_RESULT);

        outPacket.encodeInt(charId);
        outPacket.encodeByte(loginType.getValue());
        outPacket.encodeLong(0);
        outPacket.encodeLong(0);
        return outPacket;
    }

    public static OutPacket sendRecommendWorldMessage(int nWorldID, String nMsg) {
        OutPacket oPacket = new OutPacket(OutHeader.RECOMMENDED_WORLD_MESSAGE);
        oPacket.encodeByte(1);
        oPacket.encodeInt(nWorldID);
        oPacket.encodeString(nMsg);
        return oPacket;
    }

    public static OutPacket initOpcodeEncryption(int nBlockSize, byte[] aBuffer) {
        OutPacket outPacket = new OutPacket(OutHeader.INIT_OPCODE_ENCRYPTION);
        outPacket.encodeInt(nBlockSize);
        outPacket.encodeInt(aBuffer.length);
        outPacket.encodeArr(aBuffer);
        return outPacket;
    }

    public static OutPacket setOpcodes() {
        OutPacket outPacket = new OutPacket(OutHeader.SET_OPS);

        // Out Packets headers
        outPacket.encodeShort(OutHeader.CHECK_PASSWORD_RESULT.getValue());
        outPacket.encodeShort(OutHeader.MIGRATE_COMMAND.getValue());
        outPacket.encodeShort(OutHeader.SELECT_CHARACTER_RESULT.getValue());
        outPacket.encodeShort(OutHeader.REQUEST_WZ.getValue());
        outPacket.encodeShort(OutHeader.OPEN_WEBSITE.getValue());
        outPacket.encodeShort(OutHeader.CHECK_CLIENT.getValue());
        outPacket.encodeShort(0);// 30581
        //outPacket.encodeShort(OutHeader.INIT_OPCODE_ENCRYPTION.getValue());
        // In Packets headers
        outPacket.encodeShort(InHeader.MIGRATE_IN.getValue());
        outPacket.encodeShort(InHeader.CLIENT_ERROR.getValue());
        outPacket.encodeShort(InHeader.CUSTOM_ERROR.getValue());
        outPacket.encodeShort(InHeader.WZ_CHECK_RESPONSE.getValue());
        outPacket.encodeShort(InHeader.SHOW_CLIENT.getValue());
        outPacket.encodeShort(InHeader.CLIENT_REQUEST.getValue());
        outPacket.encodeShort(InHeader.SELECT_WORLD.getValue());
        return outPacket;
    }

}
