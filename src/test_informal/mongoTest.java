package test_informal;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class mongoTest {
	public static void main(String[] args) {
		MongoClient mongoClient = new MongoClient();
		MongoDatabase database = mongoClient.getDatabase("test-app");
		
		
		
		MongoCollection<Document> measures = database.getCollection("measures");
		
		Document toy = new Document("name", "yoyo").append("ages", new Document("min", 5)); 
		measures.insertOne(toy);
		
		mongoClient.close();
	}
}
