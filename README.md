<p align="center">
	<img src="logo.png" width="376" height="128" alt="Winlator Logo" />
</p>

# Winlator-RC

## Use under your own risk, not warranty of use for this app. is completed open source, you can modify for your own need.

## Most Code is IA Edited (Claude Code Opus 5.5) review by me but I'm not a pro, just an enthusiast trying to add what I want from winlator.

This is a clone from https://github.com/moze30/winlator-zmod repository
- External storage support was restored (SD CARD/HDD/SSD)
- Export/Import Container Configuretion
- Export/Import Shortcut Configuration
- By default external mouse/keyboard input


These programs are separate works, shipped next to ("mere aggregation" in GPL terms) and started as
separate processes. Each keeps its own license; they are redistributed unmodified.

| Component | Location | License | Link |
|---|---|---|---|
| otvdm / winevdm (otya128), includes Wine code | `opt/apps/otvdm` | GPL-2.0 (Wine parts LGPL-2.1-or-later) | https://github.com/otya128/winevdm |
| 7-Zip (Igor Pavlov) | `opt/apps/7-Zip` | GNU LGPL-2.1-or-later, BSD-3-Clause parts, unRAR restriction | https://www.7-zip.org/license.txt |
| AkelPad | `opt/apps/AkelPad.exe`, `opt/apps/AkelFiles` | BSD | https://akelpad.sourceforge.net/ |
| cnc-ddraw (FunkyFr3sh) | `opt/apps/d7d8tod9/cncddraw.7z`, `ra2.7z` | MIT | https://github.com/FunkyFr3sh/cnc-ddraw |
| libretro glsl-shaders (in cnc-ddraw `Shaders`) | `opt/apps/d7d8tod9/cncddraw.7z`, `ra2.7z` | per shader, see the shader headers | https://github.com/libretro/glsl-shaders |
| CnCNet `wsock32.dll` (Toni Spets) | `opt/apps/d7d8tod9/ra2.7z` | see the CnCNet project | https://github.com/CnCNet |
| dxwrapper (Elisha Riedlinger), includes DDrawCompat and zlib | `opt/apps/d7d8tod9/dxwrapper.7z` | zlib | https://github.com/elishacloud/dxwrapper |
| dgVoodoo 2 v2.81.3 (Dege) | `opt/apps/d7d8tod9/dgvoodoo.7z` | freeware, © 2013-2023 Dege | http://dege.freeweb.hu/ |
| Mesa `opengl32.dll` (Gallium WGL) | `opt/apps/d7d8tod9/opengl32.7z` | MIT | https://www.mesa3d.org/ |
| Mozilla CA certificate bundle (`cacert.pem`, from curl) | `etc/ca-certificates` | MPL-2.0 | https://curl.se/docs/caextract.html |
| DMZ cursor theme | `usr/share/icons/dmz` | CC-BY-SA-3.0 | https://packages.debian.org/sid/dmz-cursor-theme |

When redistributing, keep each component's license file next to it; for the GPL/LGPL components
(otvdm, 7-Zip) the source of the shipped version is available at the links above.

# Acknowledgements

### Credits for some of the component used on this project

- Original Winlator ([Winlator](https://github.com/brunodev85/winlator))
- Nightly Component Build ([Xnick417x](https://github.com/Xnick417x/winlator-nightly-wcp))
- Fex Component [FEX-Emu](https://github.com/FEX-Emu)
- Box64/Box32/Box86 [ptitSeb](https://github.com/ptitSeb)
- Sarek ([pythonlover02](https://github.com/pythonlover02))
- GPLAsync Patch ([Ph42oN](https://gitlab.com/Ph42oN))
- Turnip Driver ([Mesa](https://gitlab.freedesktop.org/mesa/mesa))
- Pre Regression ([Arihany](https://github.com/Arihany/WinlatorWCPHub))
- crypto - Box64 Fix ([Pipetto-crypto](https://github.com/Pipetto-crypto/box64))
- WineHQ ([WineHQ](https://www.winehq.org/))
- Nightly Turnip ([The412Banner](https://github.com/The412Banner/Banners-Turnip))
- turnip_mrpurple_T29 ([MrPurple666](https://github.com/MrPurple666/purple-turnip/releases))
- GLIBC Patches by [Termux Pacman](https://github.com/termux-pacman/glibc-packages)
- Wine ([winehq.org](https://www.winehq.org/))
- Box86/Box64 by [ptitseb](https://github.com/ptitSeb)
- Mesa (Turnip/Zink/VirGL) ([mesa3d.org](https://www.mesa3d.org))
- DXVK ([github.com/doitsujin/dxvk](https://github.com/doitsujin/dxvk))
- VKD3D ([gitlab.winehq.org/wine/vkd3d](https://gitlab.winehq.org/wine/vkd3d))
- CNC DDraw ([github.com/FunkyFr3sh/cnc-ddraw](https://github.com/FunkyFr3sh/cnc-ddraw))
- Icon Search ([Icon Search](https://www.flaticon.com/free-icons/search))


Special thanks to all the developers involved in these projects.
