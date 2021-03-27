package me.buby.mongohook;

public abstract interface VariableSerializer<T> {
	public abstract String serialize(T obj);
	
	public abstract T deserialize(String str);
}
