package main

import "testing"

func TestBackportRejectsUnexpectedSource(t *testing.T) {
	// A matching replacement token alone must never authorize different source.
	if _, err := backport([]byte("[]interpreter.Interpretable{reqAttr}")); err == nil {
		t.Fatal("unverified source accepted")
	}
}
