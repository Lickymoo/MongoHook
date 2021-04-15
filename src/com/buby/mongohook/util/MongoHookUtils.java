package com.buby.mongohook.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.bson.codecs.configuration.CodecConfigurationException;

import com.buby.mongohook.annotation.Exclude;
import com.buby.mongohook.annotation.Serializer;
import com.buby.mongohook.model.VariableSerializer;
import com.google.gson.Gson;
import com.mongodb.MongoClient;

public class MongoHookUtils {
	public static Map<String,Object> getVariableMap(Object data){
		Map<String, Object> variableMap = new HashMap<>();
		
    	for(Field field : data.getClass().getDeclaredFields()) {
    		try {
    			Object value = getVariable(field, data);
        		variableMap.put(field.getName(), value);
    		}catch(Exception e) {
    			e.printStackTrace();
    		}
    	}
    	return variableMap;
	}
	
	private static <T> T getVariable(Field field, Object data) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		field.setAccessible(true);
		if(field.getName().contains("SWITCH_TABLE")) return null;
		if(field.getAnnotation(Exclude.class) != null) return null;


		@SuppressWarnings("unchecked")
		Class<T> typeClass = (Class<T>) field.get(data).getClass();
		
		T value = typeClass.cast(field.get(data));	     
	     
		if(field.getAnnotation(Serializer.class) != null) {
			@SuppressWarnings("unchecked")
			Class<VariableSerializer<T>> annotation = (Class<VariableSerializer<T>>) field.getAnnotation(Serializer.class).value();
			VariableSerializer<T> serializer = annotation.getDeclaredConstructor().newInstance();
			value = typeClass.cast(serializer.serialize(typeClass.cast(field.get(data))));
		}
		
		if(!mongoHasCodec(field.get(data).getClass()) &&
				field.getAnnotation(Serializer.class) == null){
			Gson gson = new Gson();
			value = typeClass.cast("MHJSON:" + gson.toJson(value));
		}
		
		return value;
	}
	
	public static boolean mongoHasCodec(Class<?> clazz) {
		//No better way of checking if codec exists
		try{
			MongoClient.getDefaultCodecRegistry().get(clazz);
			return true;
		}catch(CodecConfigurationException e) {
    		return false;
    		
		}
	}
}
