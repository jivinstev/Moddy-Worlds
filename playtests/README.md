# Playtests across worlds

Real-client playtests that need SEVERAL worlds in one game, run with `tools/play.sh <world>,<world> --build` (or
`--jars DIR`): every listed world's jar is installed beside the one core they all pin, and the playtests here make and
switch worlds themselves (no world is switched on by properties). One world's own playtests live in
`<world>/playtests/`.

The first one is coexistence (with the arena): both jars installed; an Isle world, an arena world and a Default world,
each keeping its mode after a reopen, and each world's wish charge answering only in its own world.
