# Sensitive Squaroid (2155009) | Haven (310070000)

diffusionLine = 4001842
lotusExtraordinary = 4001843
absoCoin = 4310156

if sm.hasItem(diffusionLine, 50) and sm.hasItem(lotusExtraordinary):
    #How many coins can the user exchange up to?
    diffusionQuantity = sm.getQuantityOfItem(diffusionLine)
    diffusionQuotient = diffusionQuantity//50
    lotusQuantity = sm.getQuantityOfItem(lotusExtraordinary)
    if diffusionQuotient > lotusQuantity:
        purchaseCap = lotusQuantity
    else:
        purchaseCap = diffusionQuotient
    
    sm.sendNext("I think you have what I need...")
    sm.sendNext("Give me 50 #i" + str(diffusionLine) + "##z" + str(diffusionLine)
    + "# items and 1 #i" + str(lotusExtraordinary) + "##z" + str(lotusExtraordinary) + "#,\r\n"
    + "and I'll give you 1 #i" + str(absoCoin) + "##z" + str(absoCoin) + "#..."
    + "Do we have a deal?\r\n"
    + "#L0# #i" + str(absoCoin) + "##z" + str(absoCoin) + "##l")
    quantity = sm.sendAskNumber("You can get up to " + str(purchaseCap) + " #b#z" + str(absoCoin) + "#(s)#k... How many do you want to trade? \r\n"
    + "(#t" + str(diffusionLine) + "# in your possession: " + str(diffusionQuantity) + ")\r\n"
    + "(#t" + str(lotusExtraordinary) + "# in your possession: " + str(lotusQuantity) + ")\r\n", 1, 1, 100)

    if not sm.canHold(absoCoin):
        sm.sendSayOkay("Please make room in your Etc. inventory.")
    elif purchaseCap < quantity:
        sm.sendSayOkay("You don't have enough Energy Cores.")
    else:
        sm.consumeItem(diffusionLine, quantity*50)
        sm.consumeItem(lotusExtraordinary, quantity)
        sm.giveItem(absoCoin, quantity)
else:
    sm.sendSayOkay("Come back when you have 50 #i" + str(diffusionLine) + "##z" + str(diffusionLine) +
    "# and a #i" + str(lotusExtraordinary) + "##z" + str(lotusExtraordinary) + "#.")