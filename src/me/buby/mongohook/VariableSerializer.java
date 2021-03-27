package me.buby.mongohook;

public abstract class VariableSerializer<T> {
	public abstract String serialize(T obj);
	
	public abstract T deserialize(String str);
}
