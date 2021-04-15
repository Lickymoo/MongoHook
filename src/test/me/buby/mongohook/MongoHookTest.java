package test.me.buby.mongohook;

import com.buby.mongohook.MongoHook;
import com.buby.mongohook.model.Host;

public class MongoHookTest {
	public static void main(String[] args) {
		MongoHook mongo = new MongoHook(new Host("192.168.250.100", 27017));
		mongo.start();
		mongo.setDatabase("test_db").setCollection("test_coll");
		//mongo.saveObject("MY_ID", new MyAbstractObject());
		MyAbstractObject mysecondabs = mongo.getObjectById("MY_ID", MyAbstractObject.class);
		System.out.println(mysecondabs.cc.t);
	}
}
