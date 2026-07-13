# Issue #128 - browser debug log

## Symptom

During owner-flow browser verification, the sidebar stayed on `Loading courses...` and Community Events did not show the owner-only Create Event/Edit controls. Two gateway health checks reset the connection even though the auth route remained healthy.

## Investigation

1. Confirmed the UI route and fallback layout rendered without browser console errors.
2. Ran `make product-health`; the gateway actuator failed while auth, realtime, and LiveKit remained healthy.
3. Read `.product/logs/gateway-service.log` and checked the listener PID.
4. Found that `product-supervise` had reused a gateway process started on July 11. Its Netty workers were failing with `NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy`.

## Resolution

```bash
make product-down
make product-supervise
make product-health
```

After the clean restart, all health checks passed. A fresh demo-owner sign-in loaded navigation data, exposed Create Event/Edit and Create Course, and allowed both modals to be verified. No application code change was needed for the stale process failure.

## Separate visual fix found

The browser pass also found dark Teaching text caused by the global legacy demo `.app-shell` color winning through inheritance. The v2 shell now sets its own foreground color on `.v2-app-shell .app-shell`; this fix is recorded with issue #125.
