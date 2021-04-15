package com.buby.mongohook.model;

public interface VariableSerializer<T> {

	public abstract String serialize(T obj);
	
	public abstract T deserialize(String str);

}
