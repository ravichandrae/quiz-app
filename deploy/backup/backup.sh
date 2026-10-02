#!/bin/sh
# Dumps the database to $BACKUP_DIR now, then every $BACKUP_INTERVAL_SECONDS, deleting dumps older
# than $BACKUP_KEEP_DAYS days. With BACKUP_ONCE=true it makes one dump and exits (for a cron job).
# Connection settings come from the standard PGHOST, PGPORT, PGUSER, PGPASSWORD and PGDATABASE.
set -eu

BACKUP_DIR="${BACKUP_DIR:-/backups}"
BACKUP_KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"
BACKUP_INTERVAL_SECONDS="${BACKUP_INTERVAL_SECONDS:-86400}"
BACKUP_ONCE="${BACKUP_ONCE:-false}"

mkdir -p "$BACKUP_DIR"

while true; do
  file="$BACKUP_DIR/quiz-$(date -u +%Y%m%dT%H%M%SZ).dump"
  # Write to a temporary name first so a half-written dump is never mistaken for a good one.
  if pg_dump --format=custom --file="$file.partial"; then
    mv "$file.partial" "$file"
    echo "$(date -u +%FT%TZ) backup written: $file ($(du -h "$file" | cut -f1))"
  else
    rm -f "$file.partial"
    echo "$(date -u +%FT%TZ) BACKUP FAILED" >&2
    [ "$BACKUP_ONCE" = true ] && exit 1
  fi
  find "$BACKUP_DIR" -name 'quiz-*.dump' -mtime +"$BACKUP_KEEP_DAYS" -delete
  [ "$BACKUP_ONCE" = true ] && exit 0
  sleep "$BACKUP_INTERVAL_SECONDS"
done
