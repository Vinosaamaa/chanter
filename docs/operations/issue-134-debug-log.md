# Issue #134 - debug log

## Symptom

During browser verification, the Resources request returned `502 Bad Gateway` after Docker infrastructure had been restarted.

## Investigation

1. Confirmed the frontend, gateway, auth, community, and media processes were expected to be running.
2. Called the Community Service access endpoint directly; it returned successfully.
3. Observed that the Media Service request to Community Service hung and eventually surfaced through the gateway as `502`.
4. Compared service lifecycle with the Docker restart and found `make product-up` had reused JVM processes started before the infrastructure restart.

## Root cause

The reused local JVM processes retained stale service/network state across the Docker restart. The failure was operational process state, not a Resources UI or authorization-contract defect.

## Fix and proof

Ran a clean product lifecycle:

```text
make product-down
make product-up
make product-health
```

After the restart, Course Resource access, list, upload, download, and Study Assistant installation all succeeded through the browser and gateway.

## Future diagnostic shortcut

For a local `502`, verify service listeners and process age before changing application code. If Docker dependencies restarted while backend JVMs remained alive, restart the complete product stack so every service reconnects from a clean lifecycle.
