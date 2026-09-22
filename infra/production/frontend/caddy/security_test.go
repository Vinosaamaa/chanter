package main

import (
	"reflect"
	"strings"
	"testing"

	"github.com/google/cel-go/cel"
	"github.com/google/cel-go/ext"
)

type privateFields struct {
	Visible string `json:"visible"`
	Hidden  string `json:"-"`
}

// Exercise the advisory's dynamic lookup, which bypasses static field checking.
func TestCELDoesNotExposeJSONExcludedFields(t *testing.T) {
	env, err := cel.NewEnv(ext.NativeTypes(reflect.TypeFor[privateFields](), ext.ParseStructTag("json")), cel.Variable("item", cel.DynType))
	if err != nil {
		t.Fatal(err)
	}
	for _, expression := range []string{`item.visible`, `dyn(item)["-"]`} {
		ast, issues := env.Compile(expression)
		if issues.Err() != nil {
			t.Fatal(issues.Err())
		}
		program, err := env.Program(ast)
		if err != nil {
			t.Fatal(err)
		}
		value, _, err := program.Eval(map[string]any{"item": privateFields{Visible: "public", Hidden: "must-not-leak"}})
		if expression == `item.visible` {
			if err != nil || value.Value() != "public" {
				t.Fatalf("public field unavailable: %v", err)
			}
		} else if err == nil || !strings.Contains(err.Error(), "no such field") {
			t.Fatal("JSON-excluded field must fail lookup")
		}
	}
}
