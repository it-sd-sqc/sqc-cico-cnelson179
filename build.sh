#!/usr/bin/env bash
set -x

pushd src >/dev/null || exit 1

javac edu/cvtc/itsd/Main.java
rc=$?
echo "DEBUG: javac rc=$rc"

popd >/dev/null || exit 1
echo "DEBUG: exiting rc=$rc"
exit "$rc"
