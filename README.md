# Theme swapper

`hyprland-theme-swapper` may coordinate automatic theme switching by picking a wallpaper (via `swww`), generating a new matugen scheme and updating all the individual components' themes. To make it work:

- make sure its dependencies are resolved by running `bb deps`.
- make sure its executable and on PATH, for example:
```sh
# File:  ~/.local/bin/hypr-swap-theme
#!/bin/sh
/bin/env bb ~/.local/lib/hypr-theme-swapper/theme_swapper.clj $@
```
