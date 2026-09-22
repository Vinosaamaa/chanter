// Apply only the official Caddy CEL compatibility backport without changing the
// shared module cache. Go's vendored copy is local to this build.
package main

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// Upstream: caddyserver/caddy@b2693fb63a30e6d7be0972c3645e9a2c0a500e93.
const originalSHA = "e0fb48fde80ea23f7810e0e7f3fb2998032a6bea052deedaaeb02881d6c8cc6b"
const patchedSHA = "e4a3578e3307cb0d97b3d140aff2a8d14fb2147d0fd5122c1d94e523c3ed89bb"

func backport(source []byte) ([]byte, error) {
	if fmt.Sprintf("%x", sha256.Sum256(source)) != originalSHA {
		return nil, fmt.Errorf("Caddy CEL source identity differs from reviewed 2.11.4 source")
	}
	patched := []byte(strings.ReplaceAll(string(source), "[]interpreter.Interpretable{reqAttr}", "[]interpreter.InterpretableV2{reqAttr}"))
	if fmt.Sprintf("%x", sha256.Sum256(patched)) != patchedSHA {
		return nil, fmt.Errorf("Caddy CEL backport differs from reviewed upstream change")
	}
	return patched, nil
}

func prepare() error {
	data, err := exec.Command("go", "list", "-mod=readonly", "-m", "-json", "github.com/caddyserver/caddy/v2").Output()
	if err != nil {
		return err
	}
	var module struct{ Dir, Version string }
	if err := json.Unmarshal(data, &module); err != nil {
		return err
	}
	if module.Version != "v2.11.4" || module.Dir == "" {
		return fmt.Errorf("unexpected Caddy module")
	}
	sourcePath := filepath.Join("vendor", "github.com", "caddyserver", "caddy", "v2", "modules", "caddyhttp", "celmatcher.go")
	source, err := os.ReadFile(sourcePath)
	if err != nil {
		return err
	}
	patched, err := backport(source)
	if err != nil {
		return err
	}
	// go mod vendor omits dependency tests. Retain the matching upstream
	// expression regression from the checksum-verified original module.
	tests, err := os.ReadFile(filepath.Join(module.Dir, "modules", "caddyhttp", "celmatcher_test.go"))
	if err != nil {
		return err
	}
	if err := os.WriteFile(sourcePath, patched, 0644); err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(filepath.Dir(sourcePath), "celmatcher_test.go"), tests, 0644)
}

func main() {
	if err := prepare(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
