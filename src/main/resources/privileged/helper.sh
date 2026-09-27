#!/bin/sh
# DumpPorts privileged helper.
#
# This script is executed as root (through pkexec) by the DumpPorts
# application. It only exposes two read-only operations and never runs
# arbitrary commands supplied by the caller.
#
# Protocol (one command per line on stdin):
#   DUMP  -> prints the raw "ss" output, then an EXE marker, then one
#            "pid<TAB>executable-path" line per referenced process, and
#            finally an END marker.
#   QUIT  -> exits the helper.
#
# Only the whitelisted commands below are honoured.

RS=$(printf '\036')

while IFS= read -r cmd; do
    case "$cmd" in
        DUMP)
            out=$(ss -tunapH 2>/dev/null)
            printf '%s\n' "$out"

            printf '%sEXE%s\n' "$RS" "$RS"

            # Resolve the executable path of every PID referenced by ss.
            printf '%s\n' "$out" \
                | grep -oE 'pid=[0-9]+' \
                | cut -d= -f2 \
                | sort -u \
                | while IFS= read -r pid; do
                    [ -n "$pid" ] || continue
                    exe=$(readlink "/proc/$pid/exe" 2>/dev/null)
                    if [ -n "$exe" ]; then
                        printf '%s\t%s\n' "$pid" "$exe"
                    fi
                done

            printf '%sEND%s\n' "$RS" "$RS"
            ;;
        QUIT)
            exit 0
            ;;
    esac
done

exit 0
