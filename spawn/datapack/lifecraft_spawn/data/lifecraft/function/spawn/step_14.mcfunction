# Étape 15/106 : plateau
forceload add -96 -2176 -1 -2113
fill 176 50 -2192 207 59 -2161 minecraft:stone replace #minecraft:replaceable
fill 176 60 -2192 207 67 -2161 minecraft:dirt replace #minecraft:replaceable
fill 176 68 -2192 207 68 -2161 minecraft:grass_block
fill 176 69 -2192 207 89 -2161 minecraft:air
fill 176 90 -2192 207 110 -2161 minecraft:air
fill 208 50 -2192 223 59 -2161 minecraft:stone replace #minecraft:replaceable
fill 208 60 -2192 223 67 -2161 minecraft:dirt replace #minecraft:replaceable
fill 208 68 -2192 223 68 -2161 minecraft:grass_block
fill 208 69 -2192 223 110 -2161 minecraft:air
forceload remove 160 -2208 239 -2145
schedule function lifecraft:spawn/step_15 10t
