package me.buby.mongohook;

import com.mongodb.MongoCredential;

import lombok.Getter;

public class Host {
	@Getter private String address;
	@Getter private int port;
	
	@Getter private String user;
	@Getter private String password;
	@Getter private String database;
	@Getter private boolean hasAuth = false;
	
	public MongoCredential asMongoCredential() {
		return MongoCredential.createCredential(user, database, password.toCharArray());
	}
	
	public Host(String address, int port) {
		this.address = address;
		this.port = port;
	}
	
	public Host withAuth(String user, String password, String database) {
		this.hasAuth = true;
		this.user = user;
		this.database = database;
		this.password = password;
		return this;
	}
}
