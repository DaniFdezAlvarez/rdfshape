package es.weso.server

import java.time.Instant
import java.util.Calendar
import java.util.concurrent.TimeUnit

import cats.effect._
import es.weso.server.APIDefinitions._
import es.weso.server.QueryParams.{UrlCodeParam, UrlParam, urlCode}
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.mongodb.scala._
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.result.{InsertOneResult, UpdateResult}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Random
import java.net.{MalformedURLException, URL}

import org.mongodb.scala.model.Updates.set

class PermalinkService(client: Client[IO]) extends Http4sDsl[IO] {

  lazy val mongoClient: MongoClient = MongoClient(mongoConnectionString)
  lazy val db: MongoDatabase                     = mongoClient.getDatabase(mongoDatabase)
  lazy val collection: MongoCollection[Document] = db.getCollection(collectionName)

  // Utils for url generation
  val urlPrefix           = "http://rdfshape.weso.es/link/"
  val random: Random.type = Random
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // Insert a reference to the permalink in DB
    case GET -> Root / `api` / "permalink" / "generate" :?
      UrlParam(url) =>

      val existingUrl = retrieveUrl(url)
      if (existingUrl.isDefined){
        Ok(existingUrl.get)
      }
      else {
        try {
          val longUrl = new URL(url)
          val urlCode          = Instant.now.getEpochSecond.toString + random.nextInt(10)
          val shortUrl: String = urlPrefix + urlCode

          // Create doc
          val doc: Document = Document(
            // "_id" -> Autogenerated,
            "longUrl"  -> url,
            "shortUrl" -> shortUrl,
            "urlCode"  -> urlCode.toLong,
            "date"     -> Calendar.getInstance().getTime
          )

          // Insert doc
          val observable: Observable[InsertOneResult] = collection.insertOne(doc)
          observable.subscribe(new Observer[InsertOneResult] {
            override def onSubscribe(subscription: Subscription): Unit = subscription.request(1)
            override def onNext(result: InsertOneResult): Unit         = println(s"Created permalink: $url => $shortUrl")
            override def onError(e: Throwable): Unit                   = println(s"Permalink creation failed: ${e.getMessage}")
            override def onComplete(): Unit                            = println("Permalink processing completed.")
          })

          Created(shortUrl)

        } catch {
          case _: MalformedURLException =>
            BadRequest(s"Invalid URL provided for shortening: $url")
          case _: Exception =>
            InternalServerError(s"Could not execute generate the permalink for url: $url")
        }

      }

    // Retrieve a URL given the link
    case GET -> Root / `api` / "permalink" / "get" :?
      UrlCodeParam(urlCode) =>
      try {
        val code    = urlCode.toLong
        val promise = Promise[IO[Response[IO]]]

        // Fetch document in database
        val observable: SingleObservable[Document] = collection.find(equal("urlCode", code)).first()
        observable.subscribe(new Observer[Document] {

          override def onSubscribe(subscription: Subscription): Unit = subscription.request(1)
          override def onNext(result: Document): Unit = {
            val longUrl = result.getString("longUrl")
            val urlCode = result.getLong("urlCode")

            println(s"Retrieved original url: $urlCode => $longUrl")
            promise.success(Ok(longUrl))

            // Refresh use date of the link
            updateUrl(urlCode)
          }
          override def onError(e: Throwable): Unit = {
            println(s"Original url recovery failed: ${e.getMessage}")
            promise.success(BadGateway(s"Original url recovery failed for code: $urlCode"))
          }
          override def onComplete(): Unit = {
            if (!promise.isCompleted) {
              println(s"Could not find the original url for code: $urlCode")
              promise.success(NotFound(s"Could not find the original url for code: $urlCode"))
            }
            println("Permalink processing completed.")
          }
        })

        val result = Await.result(promise.future, Duration(8, TimeUnit.SECONDS))
        result


      } catch {
        case _: NumberFormatException =>
          BadRequest(s"Invalid permalink code: $urlCode")
        case _: Exception =>
          InternalServerError(s"Could not execute the request for the permalink with code: $urlCode")
      }
  }

  private def retrieveUrl (url: String): Option[String] =
  {

    val promise = Promise[Option[String]]

    // Fetch document in database
    val observable: SingleObservable[Document] = collection.find(equal("longUrl", url)).first()
    observable.subscribe(new Observer[Document] {
      override def onSubscribe(subscription: Subscription): Unit = subscription.request(1)
      override def onNext(result: Document): Unit = {
        val shortUrl = result.getString("shortUrl")
        val urlCode = result.getLong("urlCode")

        println(s"Retrieved permalink: $url => $shortUrl")
        promise.success(Option(shortUrl))

        // Refresh use date of the link
        updateUrl(urlCode)
      }
      override def onError(e: Throwable): Unit = {
        println(s"Permalink recovery failed: ${e.getMessage}")
        promise.success(None)
      }
      override def onComplete(): Unit = {
        if (!promise.isCompleted) {
          println(s"Could not find the permalink for url: $url")
          promise.success(None)
        }
        println("Permalink processing completed.")
      }
    })

    val result = Await.result(promise.future, Duration(8, TimeUnit.SECONDS))
    result
  }


  private def updateUrl (code: Long): Unit =
  {
    println(s"URL code to update: $code")
    // Update date of document in database
    val observable: SingleObservable[UpdateResult] = collection.updateOne(equal("urlCode", code),
      set("date", Calendar.getInstance().getTime))

    observable.subscribe(new Observer[UpdateResult] {
      override def onSubscribe(subscription: Subscription): Unit = subscription.request(1)
      override def onNext(result: UpdateResult): Unit = {
        println(s"Refreshed date of permalink: $code")
      }
      override def onError(e: Throwable): Unit = Unit
      override def onComplete(): Unit = Unit
    })
  }

  // DB credentials
  private val mongoUser     = sys.env.getOrElse("MONGO_USER", "")
  private val mongoPassword = sys.env.getOrElse("MONGO_PASSWORD", "")
  private val mongoDatabase = sys.env.getOrElse("MONGO_DATABASE", "")
  private val collectionName                = "permalinks"
  private val mongoConnectionString =
    s"mongodb+srv://$mongoUser:$mongoPassword@cluster0.pnja6.mongodb.net/$mongoDatabase" +
      "?retryWrites=true&w=majority"
}

object PermalinkService {
  def apply(client: Client[IO]): PermalinkService = new PermalinkService(client)
}
