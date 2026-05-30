---
name: wsl-hermes-gateway-autostart
description: Configure Hermes gateway to auto-start on Windows/WSL login using tmux and the Windows Startup folder, with a fallback when Task Scheduler creation is denied.
---

Use this when Hermes is running inside WSL and the user wants Telegram/gateway services to come up automatically when the computer starts or when they log into Windows.

When to use
- User is on WSL and wants Hermes gateway to start automatically
- Telegram or other gateway adapters are already configured and working manually
- Task Scheduler may not be available due to Windows permission limits

Prerequisites
1. Confirm WSL distro name with `wsl.exe -l -q` (or `/mnt/c/Windows/System32/wsl.exe -l -q`).
2. Confirm systemd status in WSL via `/etc/wsl.conf` and `ps -p 1 -o comm=`.
3. Confirm `tmux` is installed with `command -v tmux`.
4. Confirm gateway already works manually before automating it.

Recommended approach
1. Keep Hermes gateway running inside a named tmux session.
2. Create an idempotent WSL-side launcher script that exits if the tmux session already exists.
3. Try Windows Task Scheduler only if appropriate, but expect `Acceso denegado` / Access denied on some systems without elevation.
4. If Task Scheduler fails, use the per-user Windows Startup folder as the reliable no-admin fallback.

WSL-side launcher script
Create a script like `~/.hermes/start-telegram-gateway.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd /home/USER/.hermes/hermes-agent
SESSION="hermes-telegram"
if tmux has-session -t "$SESSION" 2>/dev/null; then
  exit 0
fi
exec tmux new-session -d -s "$SESSION" 'cd /home/USER/.hermes/hermes-agent && hermes gateway run'
```

Then `chmod +x ~/.hermes/start-telegram-gateway.sh`.

Why tmux
- Safer for WSL than relying on a terminal staying open
- Easy to inspect with `tmux attach -t hermes-telegram`
- Prevents duplicate launches when wrapped with `tmux has-session`

Windows Startup-folder fallback
If `schtasks.exe /Create ...` fails with Access denied, create a `.cmd` file in:

`C:\Users\<windows-user>\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\`

Example contents:

```bat
@echo off
REM Inicia Hermes Telegram dentro de WSL al iniciar sesión en Windows
start "Hermes Telegram" /min C:\Windows\System32\wsl.exe -d Ubuntu -u usuario -- bash -lc "~/.hermes/start-telegram-gateway.sh"
```

Important notes
- This starts Hermes automatically on Windows login, not before login.
- For true boot-time startup before login, Task Scheduler or a Windows service is needed, usually with administrator rights.
- In WSL, foreground/manual mode is often the recommended gateway mode.

Verification steps
1. `hermes gateway status`
2. `tmux ls`
3. Ensure the expected session exists, e.g. `hermes-telegram`.
4. Optionally attach with `tmux attach -t hermes-telegram`.
5. Send a Telegram test message to verify the bot responds.

Troubleshooting
- If a background `hermes gateway run` process exits during restart but tmux session is alive, the final state may still be healthy. Re-check with `hermes gateway status` and `tmux ls` before assuming failure.
- `schtasks.exe` from WSL may fail with `Acceso denegado`; use the Startup-folder fallback.
- If importing a Task Scheduler XML with `schtasks /Create /XML ...` fails with `ERROR: no se pudo cambiar la codificación`, rewrite the XML as UTF-16LE with BOM and retry. Windows Task Scheduler is picky about XML encoding; UTF-8 may parse in editors but still be rejected by `schtasks`.
- `cmd.exe` from a WSL UNC working directory can warn that UNC paths are unsupported. Use a Windows path as the working directory when calling Windows commands from WSL if needed.
- If the user wants files under a custom Windows folder (for example `C:\Users\<user>\Documents\CronJobs\...`), place the `.cmd` and optional task XML there and point `schtasks /Create /XML` at that Windows path. Keep the WSL-side launcher in `~/.hermes/` unless the user asks otherwise.
- If importing the XML still fails after converting the file to UTF-16LE with BOM, check the XML declaration too: the header must say `encoding="UTF-16"`. A UTF-16 file that still declares `UTF-8` is rejected by Task Scheduler with the same encoding error.
- A useful hardening step is to add a Windows-side watchdog task (for example every 5 minutes) that runs a PowerShell script, checks `tmux has-session -t hermes-telegram` through WSL, starts `~/.hermes/start-telegram-gateway.sh` if missing, and appends status lines to a log file such as `HermesGatewayWatchdog.log` in the chosen Windows maintenance folder.
- If the Startup item launches but Hermes does not appear, manually run the `.cmd` once from Windows and inspect `tmux ls` plus `hermes gateway status`.

Useful commands
```bash
hermes gateway status
hermes gateway stop
tmux ls
tmux attach -t hermes-telegram
rm "/mnt/c/Users/<windows-user>/AppData/Roaming/Microsoft/Windows/Start Menu/Programs/Startup/Hermes Telegram Autostart.cmd"
```

What was learned from experience
- In this environment, `schtasks.exe /Create` returned Access denied, so Windows Startup-folder autostart was the practical solution.
- A dedicated idempotent launcher script is cleaner and safer than embedding all startup logic directly in the Windows command.
- Restart-related warnings from an older background process can be noise if the replacement tmux-managed gateway is already healthy.
