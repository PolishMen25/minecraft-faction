# Étape 72/106 : eau
forceload add -96 -2272 191 -2241
fill 176 69 -2000 223 74 -1953 minecraft:air replace minecraft:water
fill 176 69 -2000 223 74 -1953 minecraft:air replace minecraft:lava
say Spawn : plateau terminé, construction du rempart...
forceload remove 160 -2016 239 -1937
schedule function lifecraft:spawn/step_72 10t
