#!/usr/bin/env bash
# End-to-end check of auto-capture on an emulator: real SMS in, captured spends out.
#
# Runs against the emulator only (never a phone with real data). Before the first run, open the app on the
# emulator, go to Settings > Auto-capture spends, turn on auto-capture and "Read new texts directly", and allow
# the permission. Then:
#
#   scripts/e2e-capture.sh            # uses emulator-5554
#   SERIAL=emulator-5556 scripts/e2e-capture.sh
set -euo pipefail
SERIAL="${SERIAL:-emulator-5554}"
ADB=(adb -s "$SERIAL")
case "$SERIAL" in emulator-*) ;; *) echo "Refusing to run on $SERIAL: emulators only." >&2; exit 1 ;; esac

query() {
  local dir; dir="$(mktemp -d)"
  "${ADB[@]}" exec-out run-as com.ledgerline.app tar cf - databases > "$dir/db.tar"
  (cd "$dir" && tar xf db.tar && sqlite3 databases/ledgerline.db "$1")
  rm -rf "$dir"
}

before=$(query "select count(*) from captured_spends")
send() { "${ADB[@]}" emu sms send "$1" "$2" >/dev/null; sleep 5; }

# Fresh cents each run: the same amount inside 20 minutes is treated as the same payment, by design.
c=$(printf '%02d' $((RANDOM % 100)))
# Three bank alerts that must be captured...
send 33789 "Chase: You made a \$45.$c transaction with STARBUCKS on your card ending in 1234."
send 22000 "Rs.5$c.00 debited from A/c XX4321 on 02-11-26 to VPA swiggy.store@axisbank (UPI Ref No 512345678901)."
send 33790 "Chase: Your direct deposit of \$2,5$c.00 from ACME INC is available."
# ...and three texts that must not be.
send 5551234567 "Hey are we still on for dinner? I paid \$12 for lunch btw"
send 73981 "Your Chase verification code is 482913. Never share it."
send 33791 "Your credit card bill of \$1,240.00 is due on Nov 15."

after=$(query "select count(*) from captured_spends")
echo "New captures: $((after - before)) (expected 3)"
query "select merchant, amountMinor, currency, direction, status from captured_spends order by capturedAtEpochMillis desc limit 3"
[ "$((after - before))" -eq 3 ]
