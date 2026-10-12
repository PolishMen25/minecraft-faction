# Remet la box à neuf, puis se replanifie dans 10 minutes (schedule remplace l'ancien : pas de doublon)
function lifecraft:spawn/box_reset
# Enclumes de la salle d'enchantement : remplacées par des neuves (elles s'abîment à l'usage et
# personne ne peut les reposer en safezone). Seulement si leur chunk est chargé.
execute if loaded 5 0 -2102 run setblock 5 69 -2102 minecraft:anvil[facing=north]
execute if loaded 9 0 -2102 run setblock 9 69 -2102 minecraft:anvil[facing=north]
schedule function lifecraft:spawn/box_tick 12000t
