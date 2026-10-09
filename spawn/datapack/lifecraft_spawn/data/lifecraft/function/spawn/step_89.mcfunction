# Étape 90/106 : box
forceload add 48 -2208 111 -2145
# Box : structure complète et premier remplissage
function lifecraft:spawn/box_fill
forceload remove -80 -2048 -17 -1985
say Spawn : 84 %
schedule function lifecraft:spawn/step_90 10t
