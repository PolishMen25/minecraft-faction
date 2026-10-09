# Étape 47/106 : plateau
forceload add -96 -2272 -1 -2177
fill 176 50 -1968 207 59 -1953 minecraft:stone replace #minecraft:replaceable
fill 176 60 -1968 207 67 -1953 minecraft:dirt replace #minecraft:replaceable
fill 176 68 -1968 207 68 -1953 minecraft:grass_block
fill 176 69 -1968 207 110 -1953 minecraft:air
fill 208 50 -1968 223 59 -1953 minecraft:stone replace #minecraft:replaceable
fill 208 60 -1968 223 67 -1953 minecraft:dirt replace #minecraft:replaceable
fill 208 68 -1968 223 68 -1953 minecraft:grass_block
fill 208 69 -1968 223 110 -1953 minecraft:air
say Spawn : plateau nivelé, nettoyage de l'eau...
forceload remove 160 -1984 239 -1937
schedule function lifecraft:spawn/step_47 10t
