package test.me.buby.mongohook;

import me.buby.mongohook.Host;
import me.buby.mongohook.MongoHook;

public class MongoHookTest {
	public static void main(String[] args) {
		MongoHook mongo = new MongoHook(new Host("192.168.250.100", 27017));
		mongo.start().setDatabase("test_db").setCollection("test_coll");
		//mongo.saveObject("MY_ID", new MyAbstractObject());
		MyAbstractObject mysecondabs = mongo.getObjectById("MY_ID", MyAbstractObject.class);
		System.out.println(mysecondabs.cc.t);
	}
}
