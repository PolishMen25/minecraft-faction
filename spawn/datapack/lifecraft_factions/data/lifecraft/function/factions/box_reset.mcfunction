# Remise à neuf de la box (X 93..124, Z -2255..-2224). Si ses 4 chunks sont déjà chargés,
# remplissage immédiat ; sinon on les force le temps du remplissage (1 s) puis on les libère.
scoreboard objectives add lifecraft_box dummy
scoreboard players set #charge lifecraft_box 0
execute if loaded 93 0 -2255 if loaded 93 0 -2224 if loaded 124 0 -2255 if loaded 124 0 -2224 run scoreboard players set #charge lifecraft_box 1
execute if score #charge lifecraft_box matches 1 run function lifecraft:factions/box_fill
execute if score #charge lifecraft_box matches 0 run forceload add 93 -2255 124 -2224
execute if score #charge lifecraft_box matches 0 run schedule function lifecraft:factions/box_reset_forced 20t
