package net.swordie.ms.handlers.social;

import net.swordie.ms.client.Client;
import net.swordie.ms.client.character.Char;
import net.swordie.ms.client.party.Party;
import net.swordie.ms.client.party.PartyMember;
import net.swordie.ms.client.party.PartyResult;
import net.swordie.ms.client.party.PartyType;
import net.swordie.ms.connection.InPacket;
import net.swordie.ms.connection.packet.UserRemote;
import net.swordie.ms.connection.packet.WvsContext;
import net.swordie.ms.handlers.Handler;
import net.swordie.ms.handlers.header.InHeader;
import net.swordie.ms.world.field.Field;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static net.swordie.ms.enums.ChatType.SystemNotice;

public class PartyHandler {

    private static final Logger log = Logger.getLogger(PartyHandler.class);

    @Handler(op = InHeader.PARTY_INVITABLE_SET)
    public static void handlePartyInvitableSet(Client c, InPacket inPacket) {
        c.getChr().setPartyInvitable(inPacket.decodeByte() != 0);
    }

    @Handler(op = InHeader.PARTY_REQUEST)
    public static void handlePartyRequest(Client c, InPacket inPacket) {
        Char chr = c.getChr();
        byte type = inPacket.decodeByte();
        PartyType prt = PartyType.getByVal(type);
        Party party = chr.getParty();
        if (prt == null) {
            log.error(String.format("Unknown party request type %d", type));
            return;
        }
        switch (prt) {
            case PartyReq_CreateNewParty:
                PartyHandler.handleCreateNewParty(chr, inPacket);
                break;
            case PartyReq_WithdrawParty:
                PartyHandler.handleLeaveParty(chr);
                break;
            case PartyReq_InviteParty:
                PartyHandler.handlePartyLeaderInvitingUser(chr, inPacket);
                break;
            case PartyReq_KickParty:
                int expelID = inPacket.decodeInt();
                party.expel(expelID);
                break;
            case PartyReq_ChangePartyBoss:
                int newLeaderID = inPacket.decodeInt();
                PartyMember leader = new PartyMember(chr.getWorld().getCharByID(newLeaderID));
                party.setPartyLeaderID(newLeaderID);
                party.broadcast(WvsContext.partyResult(PartyResult.changePartyBoss(party, 0, leader)));
                break;
            case PartyReq_ApplyParty:
                PartyHandler.handleUserAppliedToParty(chr, inPacket);
                break;
            default:
                log.error(String.format("Unknown party request type %d", type));
                break;
        }
    }

    private static void handlePartyLeaderInvitingUser(final Char leader, final InPacket inPacket) {
        final String invitedName = inPacket.decodeString();
        final Char userToBeInvited = leader.getWorld().getCharByName(invitedName);
        if (userToBeInvited == null) {
            leader.chatMessage("Could not find that player.");
            return;
        }

        final Party party;
        if (leader.hasParty()) {
            party = leader.getParty();
        } else {
            party = Party.createNewParty(true, "Party with me!", leader.getClient().getWorld());
            party.addPartyMember(leader);
            leader.write(WvsContext.partyResult(PartyResult.createNewParty(party)));
        }

        if (!userToBeInvited.isPartyInvitable()) {
            leader.chatMessage(SystemNotice, String.format("%s is currently not accepting party invites.", invitedName));
            leader.dispose();
            return;
        }

        if (userToBeInvited.hasParty()) {
            leader.chatMessage(SystemNotice, String.format("%s is already in a party.", invitedName));
            leader.dispose();
            return;
        }

        userToBeInvited.write(WvsContext.partyResult(PartyResult.applyParty(party, party.getPartyLeader())));
        leader.chatMessage(SystemNotice, String.format("You invited %s to your party.", invitedName));
    }

    private static void handleUserAppliedToParty(final Char applyee, final InPacket inPacket) {
        final int partyId = inPacket.decodeInt();
        final Party party = applyee.getWorld().getPartybyId(partyId);
        if (party == null) {
            applyee.write(WvsContext.partyResult(PartyResult.msg(PartyType.PartyRes_ApplyParty_UnknownParty)));
            return;
        }

        if (party.isFull()) {
            applyee.write(WvsContext.partyResult(PartyResult.msg(PartyType.PartyRes_ApplyParty_AlreadyFull)));
            return;
        }

        if (party.getApplyingChar() != null) {
            // applyee.chatMessage(SystemNotice, "That party already has an applier. Please wait until the applier is accepted or denied.");
            applyee.write(WvsContext.partyResult(PartyResult.msg(PartyType.PartyRes_ApplyParty_AlreadyAppliedByApplier)));
            return;
        }

        party.setApplyingChar(applyee);
        party.getPartyLeader().getChr().write(WvsContext.partyResult(PartyResult.inviteIntrusion(party, applyee)));
    }

    private static void handleCreateNewParty(final Char leader, final InPacket inPacket) {
        if (leader.hasParty()) {
            leader.chatMessage("You are already in a party.");
            return;
        }

        final boolean appliable = inPacket.decodeByte() != 0;
        final String partyName = inPacket.decodeString();
        final Party party = Party.createNewParty(appliable, partyName, leader.getWorld());
        if (party.addPartyMember(leader)) {
            party.broadcast(WvsContext.partyResult(PartyResult.createNewParty(party)));
        }
    }

    private static void handleLeaveParty(final Char chr) {
        final Party party = chr.getParty();
        if (party != null) {
            if (party.hasCharAsLeader(chr)) {
                party.disband();
            } else {
                final PartyMember leaver = party.getPartyMemberByID(chr.getId());
                party.broadcast(WvsContext.partyResult(PartyResult.withdrawParty(party, leaver, true, false)));
                party.removePartyMember(leaver);
                party.updateFull();
            }
        }
    }

    @Handler(op = InHeader.PARTY_RESULT)
    public static void handlePartyResult(Client c, InPacket inPacket) {
        final Char chr = c.getChr();
        final byte type = inPacket.decodeByte();
        final int partyId = inPacket.decodeInt();
        final PartyType pt = PartyType.getByVal(type);
        if (pt == null) {
            log.error(String.format("Unknown party request result type %d", type));
            return;
        }
        switch (pt) {
            case PartyRes_ApplyParty_Accepted:
                PartyHandler.handleInviteeAcceptedParty(chr, partyId);
                break;
            case PartyRes_ApplyParty_Rejected:
                PartyHandler.handleInviteeRejectedParty(chr, partyId);
                break;
            case PartyRes_InviteIntrusion_Accepted:
                PartyHandler.handlePartyLeaderAcceptedApplyingUser(chr);
                break;
            case PartyRes_InviteIntrusion_Rejected:
                PartyHandler.handlePartyLeaderRejectedApplyingUser(chr);
                break;
            default:
                log.error(String.format("Unknown party request result type %d", type));
                break;
        }
    }

    private static void handleInviteeAcceptedParty(final Char invitee, final int partyId) {
        final Party party = invitee.getWorld().getPartybyId(partyId);
        if (party == null) {
            invitee.chatMessage(SystemNotice, "The party has already been disbanded.");
            return;
        }

        if (party.isFull()) {
            invitee.write(WvsContext.partyResult(PartyResult.msg(PartyType.PartyRes_JoinParty_AlreadyFull)));
            return;
        }

        if (party.addPartyMember(invitee)) {
            final String charName = invitee.getName();
            for (Char member : party.getOnlineMembers().stream().map(PartyMember::getChr).collect(Collectors.toList())) {
                member.write(WvsContext.partyResult(PartyResult.joinParty(party, charName)));

                if (!member.equals(invitee)) {
                    member.write(UserRemote.receiveHP(invitee));
                    invitee.write(UserRemote.receiveHP(member));
                }
            }
        }
    }

    private static void handleInviteeRejectedParty(final Char invitee, final int partyId) {
        final Party party = invitee.getWorld().getPartybyId(partyId);
        if (party != null) {
            final PartyMember leader = party.getPartyLeader();
            leader.getChr().chatMessage(SystemNotice, String.format("%s has declined your invite.", invitee.getName()));
        }
    }

    private static void handlePartyLeaderAcceptedApplyingUser(final Char leader) {
        final Party party = leader.getParty();

        final Char applyingChar = party.getApplyingChar();
        party.setApplyingChar(null);

        if (applyingChar.hasParty()) {
            leader.chatMessage(SystemNotice, String.format("%s is already in a party.", applyingChar.getName()));
            return;
        }

        if (party.isFull()) {
            applyingChar.write(WvsContext.partyResult(PartyResult.msg(PartyType.PartyRes_JoinParty_AlreadyFull)));
            return;
        }

        if (party.addPartyMember(applyingChar)) {
            for (Char member : party.getOnlineMembers().stream().map(PartyMember::getChr).collect(Collectors.toList())) {
                member.write(WvsContext.partyResult(PartyResult.joinParty(party, applyingChar.getName())));

                if (!member.equals(applyingChar)) {
                    member.write(UserRemote.receiveHP(applyingChar));
                    applyingChar.write(UserRemote.receiveHP(member));
                }
            }
        }
    }

    private static void handlePartyLeaderRejectedApplyingUser(final Char leader) {
        final Party party = leader.getParty();
        final Char applyingChar = party.getApplyingChar();
        if (applyingChar != null) {
            applyingChar.chatMessage(SystemNotice, "Your party apply request has been denied.");
            party.setApplyingChar(null);
        }
    }

    @Handler(op = InHeader.PARTY_MEMBER_CANDIDATE_REQUEST)
    public static void handlePartyMemberCandidateRequest(Client c, InPacket inPacket) {
        Char chr = c.getChr();
        Field field = chr.getField();
        chr.write(WvsContext.partyMemberCandidateResult(field.getChars().stream().filter(ch -> ch.isPartyInvitable() && !ch.equals(chr) && ch.getParty() == null).collect(Collectors.toSet())));
    }

    @Handler(op = InHeader.PARTY_CANDIDATE_REQUEST)
    public static void handlePartyCandidateRequest(Client c, InPacket inPacket) {
        Char chr = c.getChr();
        if (chr.getParty() != null) {
            chr.write(WvsContext.partyCandidateResult(new HashSet<>()));
            return;
        }
        Field field = chr.getField();
        Set<Party> parties = new HashSet<>();
        for (Char ch : field.getChars()) {
            if (ch.getParty() != null && ch.getParty().hasCharAsLeader(ch)) {
                parties.add(ch.getParty());
            }
        }
        chr.write(WvsContext.partyCandidateResult(parties));
    }

}
