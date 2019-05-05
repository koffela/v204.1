package net.swordie.ms.life;

import net.swordie.ms.client.character.Char;
import net.swordie.ms.connection.packet.DragonPool;

/**
 * Created by MechAviv on 12/22/2018.
 */
public class Dragon extends Life {

    private Char owner;

    public Dragon(int templateId, Char owner) {
        super(templateId);
        this.owner = owner;
    }

    public Char getOwner() {
        return owner;
    }

    public int getJobCode() {
        return this.owner.getJob();
    }

    public int getCharID() {
        return this.getOwner().getId();
    }

    @Override
    public void broadcastSpawnPacket(Char onlyChar) {
        getField().broadcastPacket(DragonPool.createDragon(this));
    }

    @Override
    public void broadcastLeavePacket() {
        getField().broadcastPacket(DragonPool.removeDragon(this));
    }

    public void resetToPlayer() {
        setPosition(owner.getPosition().deepCopy());
        setMoveAction((byte) 4); // default
    }
}
