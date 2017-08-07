/**
  * Created by Ashish Pushp Singh on 7/8/17.
  */
package controllers

import javax.inject.Inject

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.WebSocket.FrameFormatter
import play.api.mvc._
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.{Cursor, QueryOpts}
import play.modules.reactivemongo.json._  // BSON-JSON conversions
import scala.concurrent.Future

class Application @Inject()(val reactiveMongoApi: ReactiveMongoApi, val dataService: DataService)
  extends Controller with MongoController with ReactiveMongoComponents {

  var batchId: String = null

  def saveData: Action[AnyContent] = Action { request =>
    batchId = java.util.UUID.randomUUID.toString.replaceAll("-", "")
    dataService.saveData(request.body.asJson.get.toString(), batchId)
    Ok("Got request [" + request + "]")
  }

  // let's be sure that the collections exists and is capped
  val futureCollection: Future[JSONCollection] = {
    val db = reactiveMongoApi.db
    val collection = db.collection[JSONCollection]("acappedcollection")

    collection.stats().flatMap {
      case stats if !stats.capped =>
        // the collection is not capped, so we convert it
        println("converting to capped")
        collection.convertToCapped(1024 * 1024, None)
      case _ => Future(collection)
    }.recover {
      // the collection does not exist, so we create it
      case _ =>
        println("creating capped collection...")
        collection.createCapped(1024 * 1024, None)
    }.map { _ =>
      println("the capped collection is available")
      collection
    }
  }

  def index = Action {
    Ok(views.html.index())
  }

  implicit val jsObjFrame: FrameFormatter[JsObject] = WebSocket.FrameFormatter.jsonFrame.
    transform[JsObject]({ obj: JsObject => obj: JsValue }, {
    case obj: JsObject => obj
    case js => sys.error(s"unexpected JSON value: $js")
  })

  def watchCollection: WebSocket[JsObject, JsObject] = WebSocket.using[JsObject] { request =>
    // Inserts the received messages into the capped collection
    val in = Iteratee.flatten(futureCollection.
      map(collection => Iteratee.foreach[JsObject] { json =>
        println(s"received $json")
        collection.insert(json)
      }))

    // Enumerates the capped collection
    val out = {
      val futureEnumerator = futureCollection.map { collection =>
        // so we are sure that the collection exists and is a capped one
        val query = Json.obj("batchId" -> batchId)
        val cursor: Cursor[JsObject] = if(batchId != null) collection
          // we want all the documents
          //.find(Json.obj())
          .find(query)
          // the cursor must be tailable and await data
          .options(QueryOpts().tailable.awaitData)
          .cursor[JsObject]
        else collection
          .find(Json.obj())
          .options(QueryOpts()
            .tailable.awaitData)
          .cursor[JsObject]

        // ok, let's enumerate it
        cursor.enumerate()
      }
      Enumerator.flatten(futureEnumerator)
    }

    // We're done!
    (in, out)
  }
}


/*
println("-----------------------------------------------------------------")
val query = Json.obj("_id" -> Json.obj("$oid" ->"59842ecc33955137b028fe4b"))
//println(collection.find(query))

val futureItem = collection.
find(query).
one[JsValue]

futureItem.map {
  case Some(Item) => println(Json.obj("message" -> Item))
  case None => println(Json.obj("message" -> "No such Item"))
}*/
