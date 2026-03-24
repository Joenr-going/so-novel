package com.pcdd.sonovel.util;

import lombok.experimental.UtilityClass;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

@UtilityClass
public class JsCaller {

    public final String JS_TEMPLATE = """
            function func(r) {
                %s;
                return r;
            }
            """;

    public String call(String jsCode, String input) {
        Context ctx = Context.enter();
        try {
            ctx.setOptimizationLevel(-1);
            Scriptable scope = ctx.initStandardObjects();
            String scriptString = JS_TEMPLATE.formatted(jsCode);
            ctx.evaluateString(scope, scriptString, "sonovel", 1, null);
            Object fn = scope.get("func", scope);
            if (!(fn instanceof Function function)) {
                return "";
            }
            Object result = function.call(ctx, scope, scope, new Object[]{input});
            return Context.toString(result);
        } finally {
            Context.exit();
        }
    }

    public Object callFunction(String jsFunctionCode, String functionName, Object... args) {
        Context ctx = Context.enter();
        try {
            ctx.setOptimizationLevel(-1);
            Scriptable scope = ctx.initStandardObjects();
            ctx.evaluateString(scope, jsFunctionCode, "sonovel", 1, null);
            Object fn = scope.get(functionName, scope);
            if (!(fn instanceof Function function)) {
                return null;
            }
            return function.call(ctx, scope, scope, args);
        } finally {
            Context.exit();
        }
    }

    public String eval(String jsCode) {
        Context ctx = Context.enter();
        try {
            ctx.setOptimizationLevel(-1);
            Scriptable scope = ctx.initStandardObjects();
            Object result = ctx.evaluateString(scope, jsCode, "sonovel", 1, null);
            return Context.toString(result);
        } finally {
            Context.exit();
        }
    }

    public void close() {
    }

}
