# ResourcePack

Offer players to use your resource pack(s).

## Concepts

Available packs are presented as a list when the command is issued or the player joins.  Each pack may be selected for use right there, as well as saved for later sessions.  Every message can be configured and said buttons can be disabled in the configuration file.

Player choices (via the `always` command) are kept in a file.  They may delete their option by choosing (via the `use` command) any available resource pack.

Each pack requires a URL.  The plugin will not host the resource pack file itself; that must be done separately.  Forthermore, the `SHA1` sum of each pack must be entered manually to make sure the player re-downloads the pack if, and only if, it was updated.

Individual packs can be locked behind a permission node in the configuration file.

## Commands

- `/resourcepack` *or* `/rp` - List all resource packs.
- `/rp use <pack> *or* `/rp <pack>` - Use a pack.
- `/rp always <pack>` - Use this pack and save for later.

## Permissions

- `resourcepack.resourcepack` - Use `/rp`. Default: **true**.
- `resourcepack.admin` - Use `/rp reload`. Default: **op**.

## Configuration

The plugin is highly configurable, down to every shown message.
Here is the default `config.yml`:

```yaml
# Show more debug info on console?
debug: true
# Show packs when player joins? Permission still required.
showOnJoin: true
header: |-
  
  &6&lResource Packs:
footer: ' '
default:
  # This is the default for all resource packs.
  # All settings can be overridden by each individual pack.
  displayName: 'NoName'
  url: 'NoURL'
  hash: 'NoHash'
  permission: ''
  messages:
    # Placeholders:
    # {key} Resource pack section name.
    # {name} Resource pack display name.
    # {url} Resource pack url.
    # Make empty string to skip.
    description: '&e- &6{name} '
    use: '&6[Use]'
    always: '&6[Always]'
    download: '&9[&nDownload&9]'
    confirm: '&6Using {name}'

resourcePacks:
  # Edit as needed. Add one section per pack.
  myResourcePack:
    displayName: "&dEpic Pack"
    url: 'https://www.your.url/resourcepack.zip'
    hash: '4d1e0539ffd9dbbc8618616114685de1c71eae84'
    # Keep empty to skip check.
    permission: ''
```

## Links

- [github.com/StarTux/ResourcePack](https://github.com/StarTux/ResourcePack) The official repository.