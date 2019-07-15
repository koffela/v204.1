package net.swordie.ms.handlers.life;

import net.swordie.ms.client.Client;
import net.swordie.ms.client.character.Char;
import net.swordie.ms.client.character.items.Item;
import net.swordie.ms.client.character.quest.Quest;
import net.swordie.ms.client.jobs.cygnus.DawnWarrior;
import net.swordie.ms.client.jobs.nova.AngelicBuster;
import net.swordie.ms.client.jobs.resistance.Xenon;
import net.swordie.ms.connection.InPacket;
import net.swordie.ms.connection.OutPacket;
import net.swordie.ms.connection.packet.*;
import net.swordie.ms.constants.GameConstants;
import net.swordie.ms.constants.JobConstants;
import net.swordie.ms.constants.QuestConstants;
import net.swordie.ms.enums.ChatType;
import net.swordie.ms.enums.MessageType;
import net.swordie.ms.enums.QuestStatus;
import net.swordie.ms.handlers.Handler;
import net.swordie.ms.handlers.header.InHeader;
import net.swordie.ms.handlers.header.OutHeader;
import net.swordie.ms.life.DeathType;
import net.swordie.ms.life.Life;
import net.swordie.ms.life.mob.EscortDest;
import net.swordie.ms.life.mob.Mob;
import net.swordie.ms.life.mob.MobStat;
import net.swordie.ms.life.mob.MobTemporaryStat;
import net.swordie.ms.life.mob.skill.MobSkill;
import net.swordie.ms.life.mob.skill.MobSkillID;
import net.swordie.ms.life.mob.skill.MobSkillStat;
import net.swordie.ms.life.movement.MovementInfo;
import net.swordie.ms.loaders.ItemData;
import net.swordie.ms.loaders.SkillData;
import net.swordie.ms.loaders.containerclasses.MobSkillInfo;
import net.swordie.ms.util.Position;
import net.swordie.ms.util.Randomizer;
import net.swordie.ms.util.Util;
import net.swordie.ms.util.container.Tuple;
import net.swordie.ms.world.field.Field;
import net.swordie.ms.world.field.Portal;
import net.swordie.ms.world.field.fieldeffect.FieldEffect;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MobHandler {

    private static final Logger log = Logger.getLogger(MobHandler.class);


    @Handler(op = InHeader.MOB_APPLY_CTRL)
    public static void handleMobApplyCtrl(Client c, InPacket inPacket) {
        Char chr = c.getChr();
        Field field = chr.getField();
        int mobID = inPacket.decodeInt();
        Mob mob = (Mob) field.getLifeByObjectID(mobID);
        c.write(MobPool.changeController(mob, true, true));
    }

    @Handler(op = InHeader.MOB_MOVE)
    public static void handleMobMove(Client c, InPacket inPacket) {
        // CMob::GenerateMovePath (line 918 onwards)
        Field field = c.getChr().getField();
        int objectID = inPacket.decodeInt();
        Life life = field.getLifeByObjectID(objectID);
        if (!(life instanceof Mob)) {
            return;
        }
        MobSkillAttackInfo msai = new MobSkillAttackInfo();
        Mob mob = (Mob) life;
        Char controller = field.getLifeToControllers().get(mob);
        //byte idk0 = inPacket.decodeByte(); // check if the templateID / 10000 == 250 or 251. No idea for what it's used
        short moveID = inPacket.decodeShort();
        msai.actionAndDirMask = inPacket.decodeByte();
        byte action = inPacket.decodeByte();
        msai.action = (byte) (action >> 1);
        life.setMoveAction(action);
        int skillID = msai.action - 30; // thanks yuuroido :D
        int skillSN = skillID;
        int slv = 0;
        msai.targetInfo = inPacket.decodeLong();
        int afterAttack = -1;
        //c.getChr().chatMessage("" + msai.action);
        boolean didSkill = action != -1;
        if (didSkill && mob.hasSkillDelayExpired() && !mob.isInAttack()) {
            List<MobSkill> skillList = mob.getSkills();
            if (Util.succeedProp(GameConstants.MOB_SKILL_CHANCE)) {
                MobSkill mobSkill;
                mobSkill = skillList.stream()
                        .filter(ms -> ms.getSkillSN() == skillSN
                                /*&& mob.hasSkillOffCooldown(ms.getSkillID(), ms.getLevel())*/)
                        .findFirst()
                        .orElse(null);
                if (mobSkill == null) {
                    skillList = skillList.stream()
                            .filter(ms -> mob.hasSkillOffCooldown(ms.getSkillID(), ms.getLevel()))
                            .collect(Collectors.toList());
                    if (skillList.size() > 0) {
                        mobSkill = skillList.get(Randomizer.nextInt(skillList.size()));
                    }
                }
                didSkill = mobSkill != null;
                if (didSkill) {
                    didSkill = true;
                    skillID = mobSkill.getSkillID();
                    slv = mobSkill.getLevel();
                    MobSkillInfo msi = SkillData.getMobSkillInfoByIdAndLevel(skillID, slv);
                    long curTime = System.currentTimeMillis();
                    long interval = msi.getSkillStatIntValue(MobSkillStat.interval) * 1000;
                    long nextUseableTime = curTime + interval;
                    c.getChr().chatMessage(ChatType.Mob, String.format("Mob" + mob + " did skill with ID %d (%s), level = %d",
                            mobSkill.getSkillID(), MobSkillID.getMobSkillIDByVal(mobSkill.getSkillID()), mobSkill.getLevel()));
                    mob.putSkillCooldown(skillID, slv, nextUseableTime);
                    if (mobSkill.getSkillAfter() > 0) {
                        mob.getSkillDelays().add(mobSkill);
                        mob.setSkillDelay(mobSkill.getSkillAfter());
                        c.write(MobPool.setSkillDelay(mob.getObjectId(), mobSkill.getSkillAfter(), skillID, slv, 0, null));
                    } else {
                        mobSkill.applyEffect(mob);
                    }
                }
            }
        }
        if (!didSkill) {
            // didn't do a skill, so ensure that the attack gets properly formed
            int attackIdx = skillID + 17;
            if (mob.hasAttackOffCooldown(attackIdx)) {
                MobSkill ms = mob.getAttackById(attackIdx);
                if (ms != null && ms.getAfterAttack() >= 0) {
                    afterAttack = ms.getAfterAttack();
                }
            }
        }
        byte multiTargetForBallSize = inPacket.decodeByte();
        for (int i = 0; i < multiTargetForBallSize; i++) {
            Position pos = inPacket.decodePosition(); // list of ball positions
            msai.multiTargetForBalls.add(pos);
        }

        byte randTimeForAreaAttackSize = inPacket.decodeByte();
        for (int i = 0; i < randTimeForAreaAttackSize; i++) {
            short randTimeForAreaAttack = inPacket.decodeShort(); // could be used for cheat detection, but meh
            msai.randTimeForAreaAttacks.add(randTimeForAreaAttack);
        }

        byte unkSize = inPacket.decodeByte();
        for (int i = 0; i < unkSize; i++) {
            int unk = inPacket.decodeInt();
            msai.unks.add(unk);
        }

        byte mask = inPacket.decodeByte();
        boolean targetUserIDFromSvr = (mask & 1) != 0;
        boolean isCheatMobMoveRand = ((mask >> 4) & 1) != 0;
        int hackedCode = inPacket.decodeInt();
        int oneTimeActionCS = inPacket.decodeInt();
        int moveActionCS = inPacket.decodeInt();
        int hitExpire = inPacket.decodeInt();
        byte idk = inPacket.decodeByte();
        MovementInfo movementInfo = new MovementInfo(inPacket);
        c.write(MobPool.ctrlAck(mob, true, moveID, skillID, (byte) slv, -1));
        movementInfo.applyTo(mob);
        mob.setInAttack(afterAttack >= 0);
        if (afterAttack >= 0) {
            c.write(MobPool.setAfterAttack(mob.getObjectId(), (short) afterAttack, msai.action, action % 2 != 0));
        }
        field.checkMobInAffectedAreas(mob);
        field.broadcastPacket(MobPool.move(mob, msai, movementInfo), controller);
    }

    @Handler(op = InHeader.MOB_SKILL_DELAY_END)
    public static void handleMobSkillDelayEnd(Char chr, InPacket inPacket) {
        Life life = chr.getField().getLifeByObjectID(inPacket.decodeInt());
        if (!(life instanceof Mob)) {
            return;
        }
        Mob mob = (Mob) life;
        int skillID = inPacket.decodeInt();
        int slv = inPacket.decodeInt();
        int remainCount = 0; // only set in MobDelaySkill::UpdateSequenceMode
        if (inPacket.decodeByte() != 0) {
            remainCount = inPacket.decodeInt();
        }
        List<MobSkill> delays = mob.getSkillDelays();
        MobSkill ms = Util.findWithPred(delays, skill -> skill.getSkillID() == skillID && skill.getLevel() == slv);
        if (ms != null) {
            ms.applyEffect(mob);
        }

    }


    @Handler(op = InHeader.USER_BAN_MAP_BY_MOB)
    public static void handleBanMapByMob(Client c, InPacket inPacket) {
        Field field = c.getChr().getField();
        int mobID = inPacket.decodeInt();
        Life life = field.getLifeByTemplateId(mobID);
        if (!(life instanceof Mob)) {
            return;
        }
        Mob mob = (Mob) life;
        Char chr = c.getChr();
        if (mob.isBanMap()) {
            if (mob.getBanType() == 1) {
                if (mob.getBanMsgType() == 1) { // found 2 types (1(most of ban types), 2).
                    String banMsg = mob.getBanMsg();
                    if (banMsg != null && !banMsg.equals("")) {
                        chr.write(WvsContext.message(MessageType.SYSTEM_MESSAGE, 0, banMsg, (byte) 0));
                    }
                }
                Tuple<Integer, String> banMapField = mob.getBanMapFields().get(0);
                if (banMapField != null) {
                    Field toField = chr.getOrCreateFieldByCurrentInstanceType(banMapField.getLeft());
                    if (toField == null) {
                        return;
                    }
                    Portal toPortal = toField.getPortalByName(banMapField.getRight());
                    if (toPortal == null) {
                        toPortal = toField.getPortalByName("sp");
                    }

                    chr.warp(toField, toPortal);
                }
            }
        }
    }

    @Handler(op = InHeader.MOB_EXPLOSION_START)
    public static void handleMobExplosionStart(Client c, InPacket inPacket) {
        int mobID = inPacket.decodeInt();
        int charID = inPacket.decodeInt();
        int skillID = inPacket.decodeInt(); //tick
        Char chr = c.getChr();
        if (JobConstants.isXenon(chr.getJob()) && chr.hasSkill(Xenon.TRIANGULATION)) {
            skillID = Xenon.TRIANGULATION;
        } else if (JobConstants.isDawnWarrior(chr.getJob()) && chr.hasSkill(DawnWarrior.IMPALING_RAYS)) {
            skillID = DawnWarrior.IMPALING_RAYS_EXPLOSION;
        } else if (JobConstants.isAngelicBuster(chr.getId()) && chr.hasSkill(AngelicBuster.LOVELY_STING)) {
            skillID = AngelicBuster.LOVELY_STING_EXPLOSION;
        } else {
            chr.chatMessage("Unhandled mob explosion for your job.");
            return;
        }
        Mob mob = (Mob) c.getChr().getField().getLifeByObjectID(mobID);
        if (mob != null) {
            MobTemporaryStat mts = mob.getTemporaryStat();
            if ((mts.hasCurrentMobStat(MobStat.Explosion) && mts.getCurrentOptionsByMobStat(MobStat.Explosion).wOption == chr.getId()) ||
                    (mts.hasCurrentMobStat(MobStat.SoulExplosion) && mts.getCurrentOptionsByMobStat(MobStat.SoulExplosion).wOption == chr.getId())) {
                c.write(UserLocal.explosionAttack(skillID, mob.getPosition(), mobID, 1));
                mts.removeMobStat(MobStat.Explosion, true);
                mts.removeMobStat(MobStat.SoulExplosion, true);
            }
        }
    }

    @Handler(op = InHeader.USER_REQUEST_CHANGE_MOB_ZONE_STATE)
    public static void handleUserRequestChangeMobZoneState(Client c, InPacket inPacket) {
        Char chr = c.getChr();
        int mobID = inPacket.decodeInt();
        Position pos = inPacket.decodePositionInt();
        Life life = chr.getField().getLifeByObjectID(mobID);
        if (life instanceof Mob) {
            Mob mob = (Mob) life;
            // Should this be handled like this? I doubt it, but it works :D
            int dataType = 0;
            switch (life.getTemplateId()) {
                case 8880002: // Normal magnus
                    double perc = mob.getHp() / (double) mob.getMaxHp();
                    if (perc <= 0.25) {
                        dataType = 4;
                    } else if (perc <= 0.5) {
                        dataType = 3;
                    } else if (perc <= 0.75) {
                        dataType = 2;
                    } else {
                        dataType = 1;
                    }
                    break;
                default:
                    log.error("Unhandled mob zone stat for mob template id " + life.getTemplateId());
            }
            chr.getField().broadcastPacket(FieldPacket.changeMobZone(mobID, dataType));
        }
        OutPacket outPacket = new OutPacket(OutHeader.SERVER_ACK_MOB_ZONE_STATE_CHANGE);
        outPacket.encodeByte(true);
        c.write(outPacket);
    }

    @Handler(ops = {InHeader.MOB_SELF_DESTRUCT, InHeader.MOB_SELF_DESTRUCT_COLLISION_GROUP})
    public static void handleMobSelfDestruct(Char chr, InPacket inPacket) {
        int mobID = inPacket.decodeInt();
        Field field = chr.getField();
        Mob mob = (Mob) field.getLifeByObjectID(mobID);
        if (mob != null && mob.isSelfDestruction()) {
            field.removeLife(mobID);
            field.broadcastPacket(MobPool.leaveField(mobID, DeathType.ANIMATION_DEATH));
        }
    }

    @Handler(op = InHeader.MOB_AREA_ATTACK_DISEASE)
    public static void handleMobAreaAttackDisease(Char chr, InPacket inPacket) {
        int mobID = inPacket.decodeInt();
        int attackIdx = inPacket.decodeInt();
        Position areaPos = inPacket.decodePositionInt();
        int nextTickPossible = inPacket.decodeInt();
    }

    @Handler(op = InHeader.MOB_REQUEST_ESCORT_INFO)
    public static void handleMobRequestEscortInfo(Char chr, InPacket inPacket) {
        Field field = chr.getField();
        int objectID = inPacket.decodeInt();
        Life life = field.getLifeByObjectID(objectID);
        if (!(life instanceof Mob)) {
            return;
        }
        Mob mob = (Mob) life;
        if (mob.isEscortMob()) {

            // [Grand Athenaeum] Ariant : Escort Hatsar's Servant
            if (mob.getTemplateId() == 8230000) {
                mob.addEscortDest(-1616, 233, -1);
                mob.addEscortDest(1898, 233, 0);
                mob.escortFullPath(-1);
                //      chr.write(CField.removeBlowWeather());
                //      chr.write(CField.blowWeather(5120118, "I'm glad you're here, " + chr.getName() + "! Please get rid of these pesky things."));
            }
        }
    }

    @Handler(op = InHeader.MOB_ESCORT_COLLISION)
    public static void handleMobEscortCollision(Char chr, InPacket inPacket) {
        short tab = inPacket.decodeShort();
        HashMap<Item, Integer> itemMap = new HashMap<>();

        if (chr.getScriptManager().getQRValue(QuestConstants.SILENT_CRUSADE_WANTED_TAB_1 + tab).contains("r=1")) {
            chr.getOffenseManager().addOffense(String.format("Character %d tried to complete Silent Crusade Chapter %d, whilst already having claimed the reward.", chr.getId(), tab + 1));
            chr.dispose();
            return;
        }

        switch (tab) {
            case 0: // Chapter 1
                itemMap.put(ItemData.getItemDeepCopy(3700031), 1);  // Apprentice Hunter
                itemMap.put(ItemData.getItemDeepCopy(4310029), 10); // Crusader Coins  x10
                break;
            case 1: // Chapter 2
                itemMap.put(ItemData.getItemDeepCopy(3700032), 1);  // Capable Hunter
                itemMap.put(ItemData.getItemDeepCopy(4001832), 100);// Spell Traces  x100
                itemMap.put(ItemData.getItemDeepCopy(4310029), 15); // Crusader Coins  x15
                break;
            case 2: // Chapter 3
                itemMap.put(ItemData.getItemDeepCopy(3700033), 1);  // Veteran Hunter
                itemMap.put(ItemData.getItemDeepCopy(2430668), 1);  // Silent Crusade Mastery Book
                itemMap.put(ItemData.getItemDeepCopy(4310029), 20); // Crusader Coins  x20
                break;
            case 3: // Chapter 4
                itemMap.put(ItemData.getItemDeepCopy(3700034), 1);  // Superior Hunter
                itemMap.put(ItemData.getItemDeepCopy(4001832), 500);// Spell Traces  x500
                itemMap.put(ItemData.getItemDeepCopy(4310029), 30); // Crusader Coins  x30
                break;
        }

        if (!chr.canHold(new ArrayList<>(itemMap.keySet()))) {
            chr.chatMessage("Please make some space in your inventory.");
            chr.dispose();
            return;
        }

        chr.getScriptManager().setQRValue(QuestConstants.SILENT_CRUSADE_WANTED_TAB_1 + tab, "r=1");
        for (Map.Entry<Item, Integer> entry : itemMap.entrySet()) {
            Item item = entry.getKey();
            item.setQuantity(entry.getValue());
            chr.addItemToInventory(item);
        }
        chr.dispose();
    }
}
