# Field script for entering Ellinel Fairy Academy
sm.lockUI()
sm.hideUser(True)
sm.moveCamera(False, 100, -600, 0)

sm.sendDelay(1000)

sm.chatScript("The forest of fairies seems to materialize from nowhere as you exit the passage.")
sm.invokeAfterDelay(5000, "warp", MIDSUMMER_NIGHTS_FOREST_ELLINEL_LAKE_SHORE, 0)

sm.showFieldEffect("temaD/enter/fairyAcademy")
sm.sendDelay(2800)

sm.hideUser(False)
sm.unlockUI()
sm.warp(101070000, 0)