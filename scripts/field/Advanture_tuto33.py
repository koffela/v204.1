# Created by MechAviv
# ID :: [4000035]
# Unknown : Unknown

sm.chatScript("Eliminate Mano.")
if not sm.hasMobsInField():
    sm.spawnMob(9300815, -152, 150, False) # Spawn Mano
else:
    sm.killMobs()
    sm.spawnMob(9300815, -152, 150, False) # Spawn Mano
