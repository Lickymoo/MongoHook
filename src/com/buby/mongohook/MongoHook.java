package com.buby.mongohook;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.buby.mongohook.annotation.Exclude;
import com.buby.mongohook.annotation.Serializer;
import com.buby.mongohook.model.Host;
import com.buby.mongohook.model.VariableSerializer;
import com.buby.mongohook.util.MongoHookUtils;
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

public class MongoHook {

	private MongoClient mongoClient;
	private MongoDatabase mongoDatabase = null;
	private MongoCollection<Document> collection = null;
	
	@Getter @Setter private Logger logger;
	@Getter @Setter private boolean loggerEnabled = true;
	@Getter private Host[] hosts;
	@Getter private Thread thread;
	@Getter private boolean async;
	@Getter private boolean ssl = false;
	@Getter private boolean connected = false;
	
	public MongoHook(boolean isAsync, Host... hosts) {
		this.logger = LogManager.getLogger(MongoHook.class);
		this.async = isAsync;
		this.hosts = hosts;
	}
	
	public MongoHook(Host... hosts) {
		this(true, hosts);
	}

	public MongoHook start() {
		    if(async) {
		      this.thread = new Thread() {
		    	  @Override
		    	  public void run() {
		    		  connect();
		    	  }
		      };
		      thread.start();
		    }else {
		      connect();
		    }
		    
		while(!connected);
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
			connected = true;
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public MongoHook setSSLEnabled(boolean enabled) {
		ssl = enabled;
		return this;
	}
	
	public MongoHook setAsync(boolean enabled) {
		async = enabled;
		return this;
	}
	
	/*
	 * @param database Mongo database name
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
	
	/*
	 * @param id Element id
	 * @param data object in question
	 */
	public void saveObject(String id, Object data) {
		isReady();
		
		Map<String, Object> variableMap = MongoHookUtils.getVariableMap(data);
    	
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
		isReady();
		
		T object = null;
		try {
			object = clazz.getDeclaredConstructor().newInstance();
		
			Document document = collection.find(new Document(columnName, searchValue)).first();
			if(document == null) return object;
			for(Field field : clazz.getDeclaredFields()) {
				field.setAccessible(true);
				if(field.getAnnotation(Exclude.class) != null) continue;
					
				Object value = document.get(field.getName());
    		
				if(field.getAnnotation(Serializer.class) != null) {
					value = ((VariableSerializer<?>)field.getAnnotation(Serializer.class).value().getDeclaredConstructor().newInstance()).deserialize(document.getString(field.getName()));
				}
						
				if(!MongoHookUtils.mongoHasCodec(field.get(object).getClass()) &&
					field.getAnnotation(Serializer.class) == null){
					Gson gson = new Gson();
					value = gson.fromJson(document.getString(field.getName()).replace("MHJSON:", ""), field.get(object).getClass());
				}
	        		
				field.set(object, value);
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
		isReady();
		
		Document document = new Document(columnName, searchValue);
		collection.deleteOne(document);
	}
	
	/*
	 * @param searchValue value of element
	 * @param columnName name of variable column
	 */
	public boolean objectExists(String searchValue, String columnName) {
		isReady();
		Document document = collection.find(new Document(columnName, searchValue)).first();
		return document != null;
	}
	
	/*
	 * @param searchValue value of element
	 * @param columnName name of variable column
	 */
	public boolean valueExists(String searchValue, String columnName) {
		isReady();

		Document document = collection.find(new Document(columnName, searchValue)).first();
		return document != null;
	}
	
	/*
	 * @param columnName name of variable column
	 */
	public List<String> getAllValues(String columnName){
		isReady();
	    
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
		isReady();
		
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
	
	/*
	 * @param id ID of element
	 * @param columnName name of variable column
	 * @param clazz class of object being returned
	 */
	public <T> T getValue(String id, String columnName, Class<T> clazz) {
		isReady();
	    
		Document document = (Document) collection.find(new Document("_id", id)).first();
		if(document == null) return null;
		if(!document.get(columnName).getClass().isAssignableFrom(clazz)) return null;
		return clazz.cast(document.get(columnName));
	}
	
	private void log(Level level, String str, Object... args) {
		if(!loggerEnabled) return;
		logger.log(level, String.format(str, args));
	}
	
	private void log(String str, Object... args) {
		log(Level.INFO, str, args);
	}
	
	public void disable() {
		mongoClient.close();
		connected = false;
	}
	
	private void isReady() {
		Preconditions.checkArgument(mongoClient != null, "Client not connected");
	    Preconditions.checkArgument(mongoDatabase != null, "Database is not set");
	    Preconditions.checkArgument(collection != null, "Collection is not set");
	}
}






























