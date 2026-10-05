# CustomTrims (Minecraft 1.21.11, Paper)

Custom armor trims with a colored particle **trail** and **aura**, plus **8 brand-new trim
patterns** and **custom trim materials** that blend several colors together on the armor itself.

## Build with GitHub
1. Upload everything in this folder (including the hidden `.github` folder) to a GitHub repo.
2. Open the **Actions** tab, wait for the green check, open the run, download **CustomTrims** under *Artifacts*.
3. Put `CustomTrims-1.6.0.jar` in `plugins/` and start the server.
4. **Restart the server one more time.** The first start installs a data pack into your world
   that registers the new trims; Minecraft only loads it on startup.

## The resource pack (so players can see the new trims)
The plugin builds the pack and sends it to players when they join. By default it hosts the pack
itself on port **8164**. Friends joining from outside your network need port 8164 forwarded,
the same way you forwarded the Minecraft port.

If you can't open another port (some hosts and tunnels don't allow it): upload
`plugins/CustomTrims/CustomTrims-ResourcePack.zip` to a pack host like mc-packs.net, paste the
direct link into `resource-pack.url` in config.yml, and run `/ctrim reload`.

Players who decline the pack still get particles, but the new patterns/materials show as missing textures.

## Commands
```
/ctrim presets                     list ready-made trims
/ctrim preset inferno              put a preset on the armor you're wearing
/ctrim color red orange yellow     1 to 8 colors, blended together
/ctrim pattern blaze fire          trim design + trim material on the armor
/ctrim material galaxy             change just the material
/ctrim trail flame comet           trail particle + trail shape
/ctrim aura wings gradient         aura shape + aura particle
/ctrim style liquid                liquid, particles or both
/ctrim size 1.5 | density 2 | glint off | name Starfall
/ctrim info | remove | toggle | options | pack
/ctrim give <player> <preset> [netherite|diamond|...]        (ops)
/ctrim newmaterial <name> <color1> [color2] ...              (ops, then restart)
/ctrim reload                                                (ops)
```

## What's new in 1.1
- **Multiple colors**: `/ctrim color` takes up to 8 colors. Particles blend through all of them,
  and leather armor is dyed as a fade from helmet to boots.
- **8 new trim patterns**: nova, circuit, scales, blaze, rune, fracture, spiral, aurora.
- **Custom trim materials**: new trim colors on the armor. Multi-color materials fade from the
  top of the armor down to the boots. 16 included (fire, ice, galaxy, rainbow, sunset...),
  and you can make your own in config.yml or with `/ctrim newmaterial`.
- They work with all vanilla patterns too, and the new patterns work with vanilla materials.

## What's new in 1.2: liquid style
Trails and auras can now be **flowing liquid** instead of particles (the new default).
The resource pack adds smooth, glossy, animated liquid models in your trim colors:
- Auras: a liquid band that swirls around you (ring, double ring, halo, crown, tilted
  gyro rings for helix/double helix, an atom of three rings for orbit, a splash wave for
  pulse, stacked swirling rings for tornado), liquid wings, or floating liquid droplets (fireflies).
- Trails: liquid streaks that stretch behind you and dry up, splashing droplets, and
  splash puddles for footsteps.
- Multi-color trims fade top-to-bottom through your colors, and trails flow through them.

Players without the resource pack will see missing-texture models instead, so make sure the pack is set up.

## What's new in 1.3
- **28 new trim patterns** in total: nova, circuit, scales, blaze, rune, fracture, spiral, aurora,
  lightning, vines, constellation, honeycomb, chains, crystal, feathers, web, thorns, tiger,
  leopard, camo, heartbeat, matrix, sun, moon, skull, hearts, plaid, ripple.
- **32 custom materials** and **36 presets** out of the box.
- **New liquid ring**: a thick, perfectly round liquid cylinder (64 smooth segments) with a
  glossy top surface, fixed light reflections, sparkles, flowing swirls inside, and your colors
  slowly flowing around it like a current. Droplets are higher resolution and glassier too.
- Restart the server once after updating so the new patterns and materials load.

## What's new in 1.4: survival smithing + stacked trims
- **New Smithing Templates** for all 28 new patterns (craft: diamonds + a key item around any
  vanilla trim template, see config.yml), and **Trim Essences** for every custom material
  (8 key items around an amethyst shard). Use them in a smithing table like vanilla ones.
- **Stacked trims**: up to 3 trims on one piece (max-trim-layers). Smithing a new pattern onto
  trimmed armor adds it on top; the same pattern just changes its material. Vanilla templates
  keep the custom trims instead of breaking them. A grindstone removes all custom trims.
  Commands: `/ctrim layer add <pattern> <material>`, `/ctrim layer remove <n>`, `/ctrim layer clear`.
- **Prism Template**: smithing table + trimmed armor + a dye adds that color (blend up to 8),
  a sponge resets the colors, and special items change the aura/trail/style (`/ctrim prism`).
- **More options**: 3 new auras (saturn, shield, galaxy), 2 new trail shapes (zigzag, rings),
  30 more particles (firefly, copper_flame, tinted_leaves and swirl in your colors...),
  `/ctrim speed`, `/ctrim height`, `/ctrim trailshape`, `/ctrim auraparticle`, `/ctrim color add <c>`.
- Admin: `/ctrim item <template|essence|prism> [name] [player] [amount]`.

## What's new in 1.5
- **Cycle mode**: `/ctrim cycle presets|random|patterns|colors|auras|trails|off` keeps changing the
  armor forever; `/ctrim cyclespeed <seconds>` sets how fast. Works on armor stands too.
- **Armor stands**: trimmed armor on armor stands shows its aura, trail and liquid effects.
- **Inventory icons**: trimmed armor shows trim accents in its colors in the inventory, with an
  animated liquid ring around it.
- **New water ring**: a smooth, rounded water tube (rounded edges, 64 segments) with ripples,
  moving caustic light, sun glints, sky reflections, a glowing rim and tiny bubbles.

## What's new in 1.6
- The liquid is much less see-through.
- Auras no longer trail behind you when you walk: other players see it locked to you, and your
  own copy (if you turn it on) is placed slightly ahead to cancel out your ping.
- You don't see your own aura by default, so it never blocks your view. Everyone else sees it.
  `/ctrim selfview` turns your own view of it on/off (the server can't tell if you're in F5).
- Smooth changes: when cycle mode (or you) changes the trim, colors fade into each other and
  old auras shrink away while the new one grows in (`blend-seconds` in config.yml).
- `/ctrim cycleaura on|keep|off`: while cycling, change the aura too, keep yours, or have no aura.
