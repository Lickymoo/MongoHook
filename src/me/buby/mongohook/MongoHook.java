package me.buby.mongohook;

import java.io.FileInputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.conversions.Bson;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import lombok.Getter;
import lombok.Setter;
import me.buby.mongohook.annotation.Exclude;
import me.buby.mongohook.annotation.Serializer;

public class MongoHook {

	private MongoClient mongoClient;
	private MongoDatabase mongoDatabase = null;
	private MongoCollection<Document> collection = null;
	
	@Getter private Host[] hosts;
	@Getter @Setter private Logger logger;
	@Getter @Setter private boolean loggerEnabled = true;
	@Getter private boolean isAsync;
	@Getter private Thread thread;
	@Getter private boolean ssl = false;
	
	public MongoHook(boolean isAsync, Host... hosts) {
		this.isAsync = isAsync;
		this.logger = LogManager.getLogger(MongoHook.class);
		this.hosts = hosts;
	}
	
	public MongoHook(Host... hosts) {
		this(true, hosts);
	}

	public MongoHook start() {
		if(isAsync) {
			this.thread = new Thread() {
				@Override
				public void run() {
					connect();
				}
			};
			thread.run();
		}else {
			connect();
		}
		return this;
	}
	
	private void connect() {
		try {
			log("Connecting to mongoDB...");
			
			MongoClientOptions options = MongoClientOptions.builder().sslEnabled(ssl).build();
			
			ArrayList<ServerAddress> addresses = new ArrayList<>();
			ArrayList<MongoCredential> auth = new ArrayList<>();
			for(Host host : hosts) {
				addresses.add(new ServerAddress(host.getAddress(), host.getPort()));
				if(host.isHasAuth())
					auth.add(host.asMongoCredential());
			}
			
			mongoClient = new MongoClient(addresses, auth, options);	
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public MongoHook setSSLEnabled(boolean enabled) {
		ssl = enabled;
		return this;
	}
	
	/*
	 * @param database Mongo database name
	 * @return MongoHook
	 */
	public MongoHook setDatabase(String database) {
		Preconditions.checkArgument(mongoClient != null, "Client not connected");
		this.mongoDatabase = mongoClient.getDatabase(database);
		return this;
	}
	
	/*
	 * @param collection Mongo collection name
	 */
	public MongoHook setCollection(String collection) {
		Preconditions.checkArgument(mongoClient != null, "Client not connected");
	    Preconditions.checkArgument(mongoDatabase != null, "Database is not set");
		this.collection = mongoDatabase.getCollection(collection);	
		return this;
	}

	@SuppressWarnings("unchecked")
	/*
	 * @param id Element id
	 * @param data object in question
	 */
	public void saveObject(String id, Object data) {
		Preconditions.checkArgument(mongoClient != null, "Client not connected");
	    Preconditions.checkArgument(mongoDatabase != null, "Database is not set");
	    Preconditions.checkArgument(collection != null, "Collection is not set");
		
		Map<String, Object> variableMap = new HashMap<>();
		
    	for(Field field : data.getClass().getDeclaredFields()) {
    		try {
    			field.setAccessible(true);
    			Object value = field.get(data);
    			if(field.getName().contains("SWITCH_TABLE")) continue;
        		
    			if(field.getAnnotation(Exclude.class) != null) continue;
        		
        		if(field.getAnnotation(Serializer.class) != null) {
        			value = ((VariableSerializer<Object>)field.getAnnotation(Serializer.class).value().getDeclaredConstructor().newInstance()).serialize(field.get(data));
        		}
        		
        		//No better way of checking if codec exists
        		try{
        			MongoClient.getDefaultCodecRegistry().get(field.get(data).getClass());
        		}catch(CodecConfigurationException e) {
        			System.out.println(field.getType());
            		if(field.getAnnotation(Serializer.class) == null) {
            			Gson gson = new Gson();
            			value = "MHJSON:" + gson.toJson(value);
            		}
            		
        		}

        		variableMap.put(field.getName(), value);
    		}catch(Exception e) {
    			e.printStackTrace();
    		}
    	}
    	
    	Document document = collection.find(new Document("_id", id)).first();
    	if(document == null){
			Document newValue = new Document("_id", id);
			collection.insertOne(newValue);
			document = collection.find(new Document("_id", id)).first();
    	}
    	for(Entry<String, Object> entrySet : variableMap.entrySet()) {
    		if(entrySet.getValue() == null) continue;
    		Bson bsonValue = new Document(entrySet.getKey(), entrySet.getValue());
    		Bson bsonOperation = new Document("$set", bsonValue);
    		collection.updateOne(document, bsonOperation);
    	}
	}
	
	/*
	 * @param id Element id
	 * @param clazz class of object being returned
	 */
	public <T> T getObjectById(String id, Class<T> clazz) {
		return getObject(id, "_id", clazz);
	}
	
	/*
	 * @param searchValue value of element
	 * @param columnName name of variable column
	 * @param clazz class of object being returned
	 */
	public <T> T getObject(String searchValue, String columnName, Class<T> clazz){
		Preconditions.checkArgument(mongoClient != null, "Client not connected");
	    Preconditions.checkArgument(mongoDatabase != null, "Database is not set");
	    Preconditions.checkArgument(collection != null, "Collection is not set");
		
		T object = null;
		try {
			object = clazz.getDeclaredConstructor().newInstance();
		
			Document document = collection.find(new Document(columnName, searchValue)).first();
			if(document == null) return object;
			for(Field field : clazz.getDeclaredFields()) {
				try {
					if(field == null) continue;
					if(field.getName().isEmpty()) continue;
					
					field.setAccessible(true);
					Object value = null;
    		
					if(field.getAnnotation(Exclude.class) != null) continue;
	        		if(field.getAnnotation(Serializer.class) != null) {
	        			value = ((VariableSerializer<?>)field.getAnnotation(Serializer.class).value().getDeclaredConstructor().newInstance()).deserialize(document.getString(field.getName()));
	        		}else {
	        			value = document.get(field.getName());
	        		}
						
	        		try{
	        			MongoClient.getDefaultCodecRegistry().get(field.get(object).getClass());
	        		}catch(CodecConfigurationException e) {
	            		if(field.getAnnotation(Serializer.class) == null) {
	            			Gson gson = new Gson();
	            			value = gson.fromJson(document.getString(field.getName()).replace("MHJSON:", ""), field.get(object).getClass());
	            		}
	            		
	        		}

					field.set(object, value);

				}catch(Exception e) {
					e.printStackTrace();
				}
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return object;
	}
	
	/*
	 * @param id Element id
	 */
	public void deleteObjectById(String id) {
		deleteObject(id, "_id");
	}
	
	/*
	 * @param searchValue value of element
	 * @param columnName name of variable column
	 */
	public void deleteObject(String searchValue, String columnName) {
		Preconditions.checkArgument(mongoClient != null, "Client not connected");
	    Preconditions.checkArgument(mongoDatabase != null, "Database is not set");
	    Preconditions.checkArgument(collection != null, "Collection is not set");
		
		Document document = new Document(columnName, searchValue);
		collection.deleteOne(document);
	}
	
	/*
	 * @param searchValue value of element
	 * @param columnName name of variable column
	 */
	public boolean objectExists(String searchValue, String columnName) {
		Preconditions.checkArgument(mongoClient != null, "Client not connected");
	    Preconditions.checkArgument(mongoDatabase != null, "Database is not set");
	    Preconditions.checkArgument(collection != null, "Collection is not set");
		Document document = collection.find(new Document(columnName, searchValue)).first();
		return document != null;
	}
	
	/*
	 * @param searchValue value of element
	 * @param columnName name of variable column
	 */
	public boolean valueExists(String searchValue, String columnName) {
		Preconditions.checkArgument(mongoClient != null, "Client not connected");
	    Preconditions.checkArgument(mongoDatabase != null, "Database is not set");
	    Preconditions.checkArgument(collection != null, "Collection is not set");

		Document document = collection.find(new Document(columnName, searchValue)).first();
		return document != null;
	}
	
	/*
	 * @param columnName name of variable column
	 */
	public List<String> getAllValues(String columnName){
		Preconditions.checkArgument(mongoClient != null, "Client not connected");
	    Preconditions.checkArgument(mongoDatabase != null, "Database is not set");
	    Preconditions.checkArgument(collection != null, "Collection is not set");
	    
	    FindIterable<Document> iterDoc = collection.find();
	    MongoCursor<Document> it = iterDoc.iterator();
	    List<String> ret = new ArrayList<>();
	    while (it.hasNext()) {
	    	Document doc = new Document(it.next());
	    	ret.add(doc.getString(columnName));
	    }
	    return ret;
	}
	
	/*
	 * @param id ID of element
	 * @param columnName name of variable column
	 * @param value value being inserted
	 */
	public void saveValue(String id, String columnName, Object value) {
		Preconditions.checkArgument(mongoClient != null, "Client not connected");
	    Preconditions.checkArgument(mongoDatabase != null, "Database is not set");
	    Preconditions.checkArgument(collection != null, "Collection is not set");

		Document found = collection.find(new Document("_id", id)).first();	    
		if(found == null) {
			Document document = new Document("_id", id);
			document.append(columnName, value);
			collection.insertOne(document);
		}else {
			Bson updatedValue = new Document(columnName, value);			
			Bson updateOperation = new Document("$set", updatedValue);
			collection.updateOne(found, updateOperation);
		}
	}
	
	@SuppressWarnings("unchecked")
	/*
	 * @param id ID of element
	 * @param columnName name of variable column
	 * @param clazz class of object being returned
	 */
	public <T> T getValue(String id, String columnName, Class<T> clazz) {
		Preconditions.checkArgument(mongoClient != null, "Client not connected");
	    Preconditions.checkArgument(mongoDatabase != null, "Database is not set");
	    Preconditions.checkArgument(collection != null, "Collection is not set");
	    
		Document document = (Document) collection.find(new Document("_id", id)).first();
		if(document == null) return null;
		return (T) document.get(columnName);
	}
	
	private void log(Level level, String str, Object... args) {
		if(!loggerEnabled) return;
		logger.log(level, String.format(str, args));
	}
	
	private void log(String str, Object... args) {
		if(!loggerEnabled) return;
		logger.log(Level.INFO, String.format(str, args));
	}
	
	public void disable() {
		mongoClient.close();
	}
}






























