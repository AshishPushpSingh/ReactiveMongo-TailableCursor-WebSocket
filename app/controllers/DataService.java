package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;
import play.libs.Json;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Created by Ashish Pushp Singh on 7/8/17.
 */
public class DataService {


    private ExecutorService ec = Executors.newCachedThreadPool();

    private MongoClient mongo = new MongoClient("localhost", 27017);
    private MongoDatabase mongoDatabase = mongo.getDatabase("reactivemongo-tailablecursor-demo");
    private MongoCollection<Document> collection;

    public DataService() {
        this.collection = mongoDatabase.getCollection("acappedcollection");
    }

    public void saveData(String jsonString, String batchId) {

        CompletableFuture.runAsync(() -> {

            Item[] items = Json.fromJson(Json.parse(jsonString), Item[].class);
            Stream.of(items).parallel().forEach(item -> {
                item.setBatchId(batchId);
                JsonNode jsonBody = Json.toJson(item);
                String data = Json.asciiStringify(jsonBody);
                Document document = Document.parse(data);
                collection.insertOne(new Document(document));
            });
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Stream.of(items).parallel().forEach(item -> {
                String id = item.getId();
                Bson filter = new Document("id", id);
                Bson newValue = new Document("status", true);
                Bson updateOperationDocument = new Document("$set", newValue);
                collection.updateOne(filter, updateOperationDocument);
            });
        }, ec);
    }

}
