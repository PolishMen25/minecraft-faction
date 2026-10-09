# Étape 29/106 : plateau
forceload add -96 -2080 -1 -2017
fill 208 50 -2096 223 59 -2065 minecraft:stone replace #minecraft:replaceable
fill 208 60 -2096 223 67 -2065 minecraft:dirt replace #minecraft:replaceable
fill 208 68 -2096 223 68 -2065 minecraft:grass_block
fill 208 69 -2096 223 110 -2065 minecraft:air
forceload remove 192 -2112 239 -2049
schedule function lifecraft:spawn/step_29 10t
