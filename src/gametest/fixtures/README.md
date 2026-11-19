
The overlay ZIP intentionally includes sparse legacy `data/tacz/tacz_tags/attachments/scope*.json`
arrays. Its filename sorts before the exported default pack: these must **accumulate**, not shadow
new default optics. OpticInstallSmoke drives C2S installs for every visible non-builtin default
scope; the old composite-resource winner-only behavior fails that test.
