package me.buby.mongohook.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import me.buby.mongohook.VariableSerializer;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Serializer {
	Class<? extends VariableSerializer<?>> value();
}
