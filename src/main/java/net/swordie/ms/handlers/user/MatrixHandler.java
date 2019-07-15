package net.swordie.ms.handlers.user;

import net.swordie.ms.client.character.BroadcastMsg;
import net.swordie.ms.client.character.Char;
import net.swordie.ms.client.character.skills.MatrixInventory;
import net.swordie.ms.client.character.skills.MatrixSkill;
import net.swordie.ms.connection.InPacket;
import net.swordie.ms.connection.packet.WvsContext;
import net.swordie.ms.constants.MatrixConstants;
import net.swordie.ms.enums.MatrixUpdateType;
import net.swordie.ms.handlers.Handler;
import net.swordie.ms.handlers.header.InHeader;
import net.swordie.ms.loaders.VCore;
import net.swordie.ms.loaders.VCoreData;
import net.swordie.ms.util.FileTime;

import java.util.ArrayList;
import java.util.List;

public class MatrixHandler {

    @Handler(op = InHeader.UPDATE_MATRIX)
    public static void handleMatrixUpdate(Char chr, InPacket inPacket) {
        if (chr == null) {
            return;
        }
        int type = inPacket.decodeInt();
        MatrixUpdateType updateType = MatrixUpdateType.getUpdateTypeByVal(type);
        if (updateType == null) {
            chr.chatMessage(String.format("[VMatrix Update] Packet Data %s", inPacket));
            chr.chatMessage(String.format("[VMatrix Update] Unknown update type [%d]", type));
            return;
        }
        switch (updateType) {
            case ENABLE: {
                int slot = inPacket.decodeInt();
                inPacket.decodeInt();// -1
                inPacket.decodeInt();// -1
                int toSlot = inPacket.decodeInt();

                chr.write(WvsContext.updateVMatrix(chr, true, MatrixUpdateType.ENABLE, chr.getMatrixInventory().activateSkill(slot, toSlot)));
                MatrixInventory.reloadSkills(chr);
                break;
            }
            case DISABLE: {
                int slot = inPacket.decodeInt();
                inPacket.decodeInt();// -1
                chr.write(WvsContext.updateVMatrix(chr, true, MatrixUpdateType.DISABLE, chr.getMatrixInventory().deactivateSkill(slot)));
                MatrixInventory.reloadSkills(chr);
                break;
            }
            case MOVE: {
                int skillSlotID = inPacket.decodeInt();
                int replaceSkill = inPacket.decodeInt();
                int fromSlot = inPacket.decodeInt();// 0
                int toSlot = inPacket.decodeInt();
                chr.getMatrixInventory().moveSkill(skillSlotID, replaceSkill, fromSlot, toSlot);
                chr.write(WvsContext.updateVMatrix(chr, true, MatrixUpdateType.MOVE, 0));
                MatrixInventory.reloadSkills(chr);
                break;
            }
            case DISASSEMBLE_SINGLE: {
                int slot = inPacket.decodeInt();
                inPacket.decodeInt();// -1
                chr.getMatrixInventory().disassemble(chr, slot);
                MatrixInventory.reloadSkills(chr);
                break;
            }
            case DISASSEMBLE_MULTIPLE: {
                int count = inPacket.decodeInt();

                List<MatrixSkill> skills = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    MatrixSkill skill = chr.getMatrixInventory().getSkill(inPacket.decodeInt());
                    if (skill != null) {
                        skills.add(skill);
                    }
                }
                chr.getMatrixInventory().disassembleMultiple(chr, skills);
                MatrixInventory.reloadSkills(chr);
                break;
            }
            case ENHANCE: {
                int slot = inPacket.decodeInt();
                MatrixSkill toEnhance = chr.getMatrixInventory().getSkill(slot);
                if (toEnhance != null && toEnhance.getSkillLevel() < VCore.getMaxLevel(VCore.getCore(toEnhance.getCoreID()).getType())) {
                    int count = inPacket.decodeInt();
                    List<MatrixSkill> skills = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        MatrixSkill skill = chr.getMatrixInventory().getSkill(inPacket.decodeInt());
                        if (skill != null) {
                            skills.add(skill);
                        }
                    }
                    chr.getMatrixInventory().enhance(chr, toEnhance, skills);
                    MatrixInventory.reloadSkills(chr);
                }
                break;
            }
            case CRAFT_NODE: {
                int coreID = inPacket.decodeInt();
                VCoreData core = VCore.getCore(coreID);
                if (core != null) {
                    int price = 0;
                    if (VCore.isSkillNode(coreID)) {
                        price = MatrixConstants.CRAFT_SKILL_CORE_COST;
                    } else if (VCore.isBoostNode(coreID)) {
                        price = MatrixConstants.CRAFT_ENCHANT_CORE_COST;
                    } else if (VCore.isSpecialNode(coreID)) {
                        price = MatrixConstants.CRAFT_SPECIAL_CORE_COST;
                    } else if (VCore.isExpNode(coreID)) {
                        price = MatrixConstants.CRAFT_GEMSTONE_COST;
                    }
                    if (price > 0) {
                        int shardCount = chr.getShards();
                        if (shardCount >= price) {
                            chr.incShards(-price);

                            MatrixSkill skill = new MatrixSkill();
                            skill.setCoreID(coreID);
                            if (!VCore.isSpecialNode(coreID)) {
                                skill.setSkillID(core.getConnectSkills().get(0));
                                skill.setSkillLevel(1);
                                skill.setMasterLevel(core.getMaxLevel());
                            } else {
                                skill.setSkillID(0);
                                skill.setSkillLevel(1);
                                skill.setMasterLevel(1);
                                skill.setExpirationDate(FileTime.fromLong(System.currentTimeMillis() + (86400000 * core.getExpireAfter())));
                            }
                            if (VCore.isBoostNode(coreID)) {
                                List<VCoreData> boostNode = VCore.getBoostNodes();
                                boostNode.remove(core);

                                core = boostNode.get((int) (Math.random() % boostNode.size()));
                                while (!core.isJobSkill(chr.getJob())) {
                                    core = boostNode.get((int) (Math.random() % boostNode.size()));
                                }
                                boostNode.remove(core);
                                skill.setSkillID2(core.getConnectSkills().get(0));

                                core = boostNode.get((int) (Math.random() % boostNode.size()));
                                while (!core.isJobSkill(chr.getJob())) {
                                    core = boostNode.get((int) (Math.random() % boostNode.size()));
                                }
                                skill.setSkillID3(core.getConnectSkills().get(0));
                            }
                            chr.getMatrixInventory().addSkill(skill);
                            MatrixInventory.reloadSkills(chr);
                            chr.write(WvsContext.updateVMatrix(chr, true, MatrixUpdateType.CRAFT_NODE, 0));
                            chr.write(WvsContext.nodeCraftResult(coreID, skill.getSkillID(), skill.getSkillID2(), skill.getSkillID3()));
                        }
                    }
                }
                break;
            }
            default: {
                chr.write(WvsContext.broadcastMsg(BroadcastMsg.popUpMessage("[MapleEllinel]\r\nThis feature is under work.")));
                break;
            }
        }
    }

}
