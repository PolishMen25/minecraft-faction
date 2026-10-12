# Étape 20/106 : plateau
forceload add -96 -2144 -1 -2081
fill 208 50 -2160 223 59 -2129 minecraft:stone replace #minecraft:replaceable
fill 208 60 -2160 223 67 -2129 minecraft:dirt replace #minecraft:replaceable
fill 208 68 -2160 223 68 -2129 minecraft:grass_block
fill 208 69 -2160 223 110 -2129 minecraft:air
forceload remove 192 -2176 239 -2113
say Spawn : 18 %
schedule function lifecraft:spawn/step_20 10t
