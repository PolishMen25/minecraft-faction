# Étape 26/106 : plateau
forceload add 64 -2112 159 -2049
fill -16 50 -2096 15 59 -2065 minecraft:stone replace #minecraft:replaceable
fill -16 60 -2096 15 67 -2065 minecraft:dirt replace #minecraft:replaceable
fill -16 68 -2096 15 68 -2065 minecraft:grass_block
fill -16 69 -2096 15 89 -2065 minecraft:air
fill -16 90 -2096 15 110 -2065 minecraft:air
fill 16 50 -2080 47 59 -2065 minecraft:stone replace #minecraft:replaceable
fill 16 60 -2080 47 67 -2065 minecraft:dirt replace #minecraft:replaceable
fill 16 68 -2080 47 68 -2065 minecraft:grass_block
fill 16 69 -2080 47 110 -2065 minecraft:air
fill 16 50 -2096 44 59 -2081 minecraft:stone replace #minecraft:replaceable
fill 16 60 -2096 44 67 -2081 minecraft:dirt replace #minecraft:replaceable
fill 16 68 -2096 44 68 -2081 minecraft:grass_block
fill 16 69 -2096 44 110 -2081 minecraft:air
forceload remove -32 -2112 31 -2049
schedule function lifecraft:spawn/step_26 10t
