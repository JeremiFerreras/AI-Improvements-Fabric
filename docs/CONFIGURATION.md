# Experimental configuration

The port reads `config/aiimprovements.json` at startup. Stop Minecraft before editing this file. There is currently no config screen or live reload.

The configuration uses nested JSON objects. Missing keys fall back to the original upstream defaults.

## Global

- `entity.general.allow_remove_calls`: enables or disables goal-removal calls. Default: `true`.
- `entity.general.enable_call_bubbling`: enables adaptive ordering of goal modifiers. Default: `true`.

## Generic mobs

The three generic mob entries each contain:

- `enabled`: enable the selected feature.
- `is_allowlist`: when true, only IDs in the list are affected; when false, IDs in the list are excluded.
- `filter_list`: entity type IDs such as `minecraft:zombie`.

Entries:

- `entity.mob.remove_look_goal`
- `entity.mob.remove_look_random`
- `entity.mob.replace_look_controller` (default enabled)

## Fish

- `entity.fish.remove_swim`
- `entity.fish.remove_panic`
- `entity.fish.remove_avoid_player`
- `entity.fish.remove_follow_leader`
- `entity.fish.remove_puff`

All default to `false`.

## Squid

- `entity.squid.remove_flee`
- `entity.squid.remove_random_move`

Both default to `false`.

## Farm animals

Each of `entity.cow`, `entity.chicken`, `entity.pig` and `entity.sheep` supports:

- `remove_float`
- `remove_panic`
- `remove_breed`
- `remove_tempt`
- `remove_follow_parent`
- `remove_stroll`

All default to `false`. Sheep also has `entity.sheep.remove_eat_block`, default `false`.

## Example

```json
{
  "entity": {
    "general": {
      "allow_remove_calls": true,
      "enable_call_bubbling": true
    },
    "mob": {
      "remove_look_goal": {
        "enabled": true,
        "is_allowlist": true,
        "filter_list": ["minecraft:zombie"]
      }
    }
  }
}
```

Any missing entries are read using defaults. This branch is experimental: incorrect settings can remove AI goals and change mob behavior.
