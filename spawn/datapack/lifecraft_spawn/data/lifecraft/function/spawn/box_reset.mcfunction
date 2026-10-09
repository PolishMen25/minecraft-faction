# Remise à neuf de la box (X -64..-33, Z -2032..-2001). Si ses 4 chunks sont déjà chargés,
# remplissage immédiat ; sinon on les force le temps du remplissage (1 s) puis on les libère.
scoreboard objectives add lifecraft_box dummy
scoreboard players set #charge lifecraft_box 0
execute if loaded -64 0 -2032 if loaded -64 0 -2001 if loaded -33 0 -2032 if loaded -33 0 -2001 run scoreboard players set #charge lifecraft_box 1
execute if score #charge lifecraft_box matches 1 run function lifecraft:spawn/box_fill
execute if score #charge lifecraft_box matches 0 run forceload add -64 -2032 -33 -2001
execute if score #charge lifecraft_box matches 0 run schedule function lifecraft:spawn/box_reset_forced 20t
