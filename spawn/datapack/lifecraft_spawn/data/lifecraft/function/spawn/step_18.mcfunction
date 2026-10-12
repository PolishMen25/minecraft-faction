# Étape 19/106 : plateau
forceload add 224 -2176 239 -2113
fill 144 50 -2160 175 59 -2129 minecraft:stone replace #minecraft:replaceable
fill 144 60 -2160 175 67 -2129 minecraft:dirt replace #minecraft:replaceable
fill 144 68 -2160 175 68 -2129 minecraft:grass_block
fill 144 69 -2160 175 89 -2129 minecraft:air
fill 144 90 -2160 175 110 -2129 minecraft:air
fill 176 50 -2160 207 59 -2129 minecraft:stone replace #minecraft:replaceable
fill 176 60 -2160 207 67 -2129 minecraft:dirt replace #minecraft:replaceable
fill 176 68 -2160 207 68 -2129 minecraft:grass_block
fill 176 69 -2160 207 89 -2129 minecraft:air
fill 176 90 -2160 207 110 -2129 minecraft:air
forceload remove 128 -2176 191 -2113
schedule function lifecraft:spawn/step_19 10t
