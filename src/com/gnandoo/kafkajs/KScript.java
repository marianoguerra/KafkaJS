package com.gnandoo.kafkajs;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

public class KScript {
	private final Invocable invocable;
	private Object scriptState;

	public KScript(final ScriptEngine engine) {
		this.invocable = (Invocable)engine;
	}
	
	public void init(final String config) throws NoSuchMethodException, ScriptException {
		this.scriptState = this.invocable.invokeFunction("init", config);
	}
	
	public String onMessage(final String key, final String value) throws NoSuchMethodException, ScriptException {
		return (String) this.invocable.invokeFunction("onMessage", key, value, this.scriptState);
	}
}
