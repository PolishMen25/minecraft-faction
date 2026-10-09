# Construit le spawn complet par étapes (voir README). Ne pas lancer deux fois en même temps.
# Chaque étape forceload la zone de la suivante, puis libère les chunks dont elle n'a plus besoin.
say Spawn : début de la construction (106 étapes)
forceload add -96 -2272 -1 -2209
schedule function lifecraft:spawn/step_0 40t
