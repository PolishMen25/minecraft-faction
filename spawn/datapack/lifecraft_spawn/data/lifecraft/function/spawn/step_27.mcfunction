# Étape 28/106 : plateau
forceload add 224 -2112 239 -2049
fill 144 50 -2096 175 59 -2065 minecraft:stone replace #minecraft:replaceable
fill 144 60 -2096 175 67 -2065 minecraft:dirt replace #minecraft:replaceable
fill 144 68 -2096 175 68 -2065 minecraft:grass_block
fill 144 69 -2096 175 89 -2065 minecraft:air
fill 144 90 -2096 175 110 -2065 minecraft:air
fill 176 50 -2096 207 59 -2065 minecraft:stone replace #minecraft:replaceable
fill 176 60 -2096 207 67 -2065 minecraft:dirt replace #minecraft:replaceable
fill 176 68 -2096 207 68 -2065 minecraft:grass_block
fill 176 69 -2096 207 89 -2065 minecraft:air
fill 176 90 -2096 207 110 -2065 minecraft:air
forceload remove 128 -2112 191 -2049
schedule function lifecraft:spawn/step_28 10t
