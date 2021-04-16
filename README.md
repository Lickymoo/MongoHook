[![](https://jitpack.io/v/Lickymoo/MongoHook.svg)](https://jitpack.io/#Lickymoo/MongoHook) [![CodeFactor](https://www.codefactor.io/repository/github/lickymoo/mongohook/badge)](https://www.codefactor.io/repository/github/lickymoo/mongohook)
# MongoHook
A java mongoDB utility

## Contents
1. Installing
1. Getting Started
1. Basic saving & loading
1. Serializing Complex objects
1. Excluding a variable from serialization

## Installing
Maven
```xml
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>
```
```xml
        <dependency>
	    <groupId>com.github.Lickymoo</groupId>
	    <artifactId>MongoHook</artifactId>
	    <version>LATEST-VERSION</version>
	</dependency>
```  
  
Grade
```
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
  
	dependencies {
	        implementation 'com.github.Lickymoo:MongoHook:LATEST-VERSION'
	}  
```

## Getting Started
To get started simply create a new `MongoHook` class
```java
MongoHook mongo = new MongoHook(new Host("1.1.1.1", 27017));
```

You can connect multiple hosts like so:
```java
MongoHook mongo = new MongoHook(new Host("1.1.1.1", 27017), new Host("2.2.2.2", 27017));
```

Authentication can be done inline
```java
MongoHook mongo = new MongoHook(new Host("1.1.1.1", 27017).withAuth("USER", "PASSWORD", "DATABASE"));
```

After we have connected a our client we want to start the hook and connect our database and collection
```java
mongo.start();
mongo.setDatabase("test_db");
mongo.setCollection("test_coll");

OR

mongo.start().setDatabase("test_db").setCollection("test_coll");
```

## Basic saving & loading
MongoHook supports saving & loading for both entire classes & single variables

### Classes
#### saving
```java
mongoHook.saveObject(ID, MyObject);
```
#### loading
```java
//Serach by ID
MyObject myObject = mongoHook.getObjectById(ID, MyObject.class);

OR

//Alternatively, Search by value of element
MyObject myObject = mongoHook.getObject(VALUE, COLUMN NAME, MyObject.class);
```

### Variables
#### saving
```java
mongoHook.saveValue(ID, COLUMN NAME, VALUE);
```
#### loading
```java 
String myVar = mongoHook.getValue(ID, COLUMN NAME, String.class);
MyObject myObject = mongoHook.getValue(ID, COLUMN NAME, MyObject.class);
```

## Serializing complex objects
By default, MongoHook uses Gson to serialize objects, so in theory, you do not need to use a serializer.
However, in the case of needing to save data in a different format or Gson not supporting your codec, MongoHook provides the `VariableSerializer` interface

```java
public class MyClassToBeSaved{
  int myInt;
  @Serializer(MyObjectSerializer.class) MyObject;
}
```

```java
public class MyObjectSerializer implements VariableSerializer<MyObject>{
  @Override
  public String serialize(MyObject obj) {    	
  //Serialize
  }
  
  @Override
  public MyObject deserialize(String str) {
  //Deserialize
  }
}
```
MongoHook will automatically serialize this through the VariableSerializer class

## Excluding a variable from serialization
To exclude a variable from being serialized, simply add the `@Exclude` annotation
```java
public class MyClassToBeSaved{
  int myInt;
  @Exclude MyObject;
}
```
