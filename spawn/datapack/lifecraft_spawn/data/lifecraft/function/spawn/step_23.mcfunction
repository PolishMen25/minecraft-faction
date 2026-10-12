# Étape 24/106 : plateau
forceload add -96 -2112 -1 -2049
fill 176 50 -2128 207 59 -2097 minecraft:stone replace #minecraft:replaceable
fill 176 60 -2128 207 67 -2097 minecraft:dirt replace #minecraft:replaceable
fill 176 68 -2128 207 68 -2097 minecraft:grass_block
fill 176 69 -2128 207 89 -2097 minecraft:air
fill 176 90 -2128 207 110 -2097 minecraft:air
fill 208 50 -2128 223 59 -2097 minecraft:stone replace #minecraft:replaceable
fill 208 60 -2128 223 67 -2097 minecraft:dirt replace #minecraft:replaceable
fill 208 68 -2128 223 68 -2097 minecraft:grass_block
fill 208 69 -2128 223 110 -2097 minecraft:air
forceload remove 160 -2144 239 -2081
schedule function lifecraft:spawn/step_24 10t
