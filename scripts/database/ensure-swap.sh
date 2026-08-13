#!/usr/bin/env bash
set -euo pipefail

swap_file="/swapfile"
sysctl_source="${1:-/tmp/90-orangevc-memory.conf}"
resolved_swap_file="$(readlink -m "$swap_file")"
if [[ "$resolved_swap_file" != "/swapfile" ]]; then
  echo "Unexpected swap path: $resolved_swap_file" >&2
  exit 1
fi

if [[ ! -f "$swap_file" ]]; then
  fallocate -l 2G "$swap_file"
  chmod 600 "$swap_file"
  mkswap "$swap_file"
fi

if ! swapon --show=NAME --noheadings | grep -Fxq "$swap_file"; then
  swapon "$swap_file"
fi

if ! grep -Eq '^/swapfile[[:space:]]+none[[:space:]]+swap[[:space:]]' /etc/fstab; then
  cp -a /etc/fstab "/etc/fstab.before-orangevc-swap-$(date +%Y%m%d-%H%M%S)"
  printf '%s\n' '/swapfile none swap sw 0 0' >> /etc/fstab
fi

test -f "$sysctl_source"
install -o root -g root -m 644 "$sysctl_source" /etc/sysctl.d/90-orangevc-memory.conf
sysctl --system >/dev/null
swapon --show
