# ResourcePack

Send your resource pack to players.

## Concepts

The resource pack is sent to players when they join or enter the `/rp`
command or whenever the resource pack hash changes, based on
corresponding permissions.

The plugin reads the hash file regularly and updates accordingly.

The plugin works server-side and uses Redis (via the Connect plugin)
to keep track of players.

When the resource pack is declined, the player is educated how to
enable server resource packs.

## Dependencies

- Connect (for Redis)

## Commands

- `/rp` Send the resource pack (players)

## Permissions

- `/resourcepack.send` - Send the resource pack when the player joins
- `/resourcepack.resend` - Send the resource pack whenever the hash changes
- `/resourcepack.command` - Use the `/rp` command to send the pack

## API

The static method `ResourcePackPlugin::isLoaded(Player)` will check if
a player currently has the resource pack loaded.  A local cache is
used, so that Redis needs not be queried.  When false is returned, we
could consider sending an alt text in chat, instead of the intended
glyph, or a different item type in a container GUI:

```java
player.sendMessage(ResourcePackPlugin.isLoaded(player)
                   ? DefaultFont.YES_BUTTON
		   : Component.text("[Yes]", NamedTextColor.BLUE));
```