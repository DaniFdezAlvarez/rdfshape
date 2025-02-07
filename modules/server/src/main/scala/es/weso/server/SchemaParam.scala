package es.weso.server

import Defaults._
import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import es.weso.rdf.RDFReasoner
import es.weso.schema.{Schema, Schemas}
import scala.io.Source
import scala.util.Try
import org.log4s._
import es.weso.server.format._

case class SchemaParam(schema: Option[String],
                       schemaURL: Option[String],
                       schemaFile: Option[String],
                       schemaFormatTextArea: Option[SchemaFormat],
                       schemaFormatUrl: Option[SchemaFormat],
                       schemaFormatFile: Option[SchemaFormat],
                       schemaFormatValue: Option[SchemaFormat],
                       schemaEngine: Option[String],
                       schemaEmbedded: Option[Boolean],
                       targetSchemaEngine: Option[String],
                       targetSchemaFormat: Option[String],
                       activeSchemaTab: Option[String]
                      ) {

  
  sealed abstract class SchemaInputType {
    val id: String
  }
  case object SchemaUrlType extends SchemaInputType {
    override val id = "#schemaUrl"
  }
  case object SchemaFileType extends SchemaInputType {
    override val id = "#schemaFile"
  }
  case object SchemaTextAreaType extends SchemaInputType {
    override val id = "#schemaTextArea"
  }

  def parseSchemaTab(tab: String): Either[String,SchemaInputType] = {
    val inputTypes = List(SchemaUrlType,SchemaFileType,SchemaTextAreaType)
    inputTypes.find(_.id == tab) match {
      case Some(x) => Right(x)
      case None => Left(s"Wrong value of tab: $tab, must be one of [${inputTypes.map(_.id).mkString(",")}]")
    }
  }

  val schemaFormat: Option[SchemaFormat] = {
    val schemaTab = parseSchemaTab(activeSchemaTab.getOrElse(defaultActiveSchemaTab))
    pprint.log(schemaTab)
    schemaTab match {
      case Right(`SchemaUrlType`) => schemaFormatUrl orElse schemaFormatValue
      case Right(`SchemaFileType`) => schemaFormatFile orElse schemaFormatValue
      case Right(`SchemaTextAreaType`) => schemaFormatTextArea orElse schemaFormatValue
      case _ => schemaFormatValue
    }
  }

  private def chooseSchemaTab: String = {
    (schema, schemaURL) match {
      case (Some(_),None) => SchemaTextAreaType.id
      case (None,Some(_)) => SchemaUrlType.id
      case (None,None) => defaultActiveSchemaTab
      case (Some(_),Some(_)) => defaultActiveSchemaTab
    }
  }

  def getSchema(data: Option[RDFReasoner]): 
     IO[(Option[String], Either[String, Schema])] = {
    pprint.log(schemaEmbedded)
    val v: IO[(Option[String],Either[String,Schema])] = schemaEmbedded match {
      case Some(true) => data match {
        case None => IO((None, Left(s"Schema embedded but no data found")))
        case Some(rdf) => for {
          eitherSchema <- {
            Schemas.fromRDF(rdf, schemaEngine.getOrElse(defaultSchemaEngine)).attempt
          }
          resp <- eitherSchema match {
            case Left(str) => 
              IO((None, Left(s"Error obtaining schema from RDF $rdf")))
            case Right(schema) => for { 
              str <- schema.serialize(schemaFormat.getOrElse(SchemaFormat.default).name)
            } yield (Some(str), Right(schema))
          }
        } yield resp
      }
      case _ => {
        pprint.log(activeSchemaTab)
        pprint.log(schemaEngine, "@@@@ Schema Engine")
        val inputType = activeSchemaTab match {
          case Some(a) => parseSchemaTab(a)
          case None if schema.isDefined => Right(SchemaTextAreaType)
          case None if schemaURL.isDefined => Right(SchemaUrlType)
          case None if schemaFile.isDefined => Right(SchemaFileType)
          case None => Right(SchemaTextAreaType)
        }
        pprint.log(inputType)
        inputType match {
          case Right(`SchemaUrlType`) => {
            schemaURL match {
              case None => IO((None, Left(s"Non value for schemaURL")))
              case Some(schemaUrl) => { 
              val e: IO[(String,Schema)] = for {
                str <- IO.fromEither(Try(Source.fromURL(schemaUrl).mkString).toEither)
                schema <- Schemas.fromString(
                  str,schemaFormat.getOrElse(SchemaFormat.default).name,
                  schemaEngine.getOrElse(defaultSchemaEngine),
                  ApiHelper.getBase)  // .leftMap(s => s"Error parsing contents of $schemaUrl: $s\nContents:\n$str")
                _ <- IO { pprint.log(schema,s"Schema parsed") }
              } yield (str,schema)
              e.attempt.map(_.fold(
                s => ((none[String], s.getMessage.asLeft[Schema])),
                pair => { 
                  val (str,schema) = pair
                  ((Some(str), Right(schema)))
                }
              ))
            }
           }
          }
          case Right(`SchemaFileType`) => {
            schemaFile match {
              case None => IO((None, Left(s"No value for schemaFile")))
              case Some(schemaStr) =>
                val schemaFormatStr = schemaFormat.getOrElse(SchemaFormat.default).name
                val schemaEngineStr = schemaEngine.getOrElse(defaultSchemaEngine)
                Schemas.fromString(schemaStr, schemaFormatStr, schemaEngineStr, ApiHelper.getBase).attempt.map(_.fold(
                  s => (Some(schemaStr), Left(s"Error parsing file: $s")),
                  schema => (Some(schemaStr), Right(schema))
                ))
            }
          }
          case Right(`SchemaTextAreaType`) => {
            pprint.log(schemaFormat)
            val schemaStr = schema.getOrElse("")
            for { 
              pair <- Schemas.fromString(schemaStr, schemaFormat.getOrElse(SchemaFormat.default).name,
                                                      schemaEngine.getOrElse(defaultSchemaEngine),
                                                      ApiHelper.getBase).attempt.map(_.fold(
                s => (Some(schemaStr), Left(s.getMessage)), 
                schema => (Some(schemaStr), Right(schema))
              ))
              (str,eitherSchema) = pair
              nameSchema = eitherSchema.map(_.name).getOrElse(s"No schema")
              _ <- IO { pprint.log(nameSchema, s"@@@ Schema ") }
              foundSchema <- Schemas.lookupSchema(schemaEngine.getOrElse(defaultSchemaEngine))
              _ <- IO { pprint.log(foundSchema.name, s"Found schema")}
            } yield pair
          }
          case Right(other) => IO((None, Left(s"Unknown value for activeSchemaTab: $other")))
          case Left(msg) => IO((None, Left(msg)))
        }
      }
    }
   v 
  }


}

object SchemaParam {

  private[server] def mkSchema(partsMap: PartsMap,
                               data: Option[RDFReasoner]
                      ): IO[(Schema, SchemaParam)] = {
    // val L = implicitly[LiftIO[F]]
    // val E = implicitly[MonadError[F,Throwable]]
    val r: IO[Either[String, (Schema,SchemaParam)]] = for {
      sp <- {
        mkSchemaParam(partsMap)
      }
      eitherPair <- sp.getSchema(data).attempt
      resp <- eitherPair.fold(
        s => IO.pure(Left(s"Error: $s")), 
        pair => {
          val (maybeStr, maybeSchema) = pair
          maybeSchema match {
            case Left(str) => IO.pure(Left(str))
            case Right(schema) => IO.pure(Right((schema, sp.copy(schema = maybeStr)))) 
          }
        })
      } yield resp
    r.flatMap(_.fold(
      str => IO.raiseError(new RuntimeException(s"Error obtaining schema: $str")),
      IO.pure(_)
      )
    )
  }

/*    private[server] def mkSchemaIO[F[_]:Effect](partsMap: PartsMap[F],
                               data: Option[RDFReasoner]
                      ): IO[Either[String,(Schema, SchemaParam)]] = {
    val L = implicitly[LiftIO[F]]
    val r: IO[Either[String,(Schema,SchemaParam)]] = for {
      sp <- mkSchemaParam(partsMap)
      eitherPair <- sp.getSchema(data).attempt
      resp <- eitherPair.fold(
        s => IO.pure(Left(s"Error: $s")), 
        pair => {
          val (maybeStr, maybeSchema) = pair
          maybeSchema match {
            case Left(str) => IO.pure(Left(str))
            case Right(schema) => IO.pure(Right((schema, sp.copy(schema = maybeStr)))) 
          }
        })
      } yield resp
    r
  } */

  private def getSchemaFormat(name: String, partsMap: PartsMap): IO[Option[SchemaFormat]] = for {
    maybeStr <- partsMap.optPartValue(name)
  } yield maybeStr match {
    case None => None
    case Some(str) => SchemaFormat.fromString(str).fold(
      err => {
        pprint.log(s"Unsupported schemaFormat for ${name}: $str")
        None
      },
      df => Some(df)
    )
  }

  private[server] def mkSchemaParam(partsMap: PartsMap): IO[SchemaParam] = for {
    schema <- partsMap.optPartValue("schema")
    schemaURL <- partsMap.optPartValue("schemaURL")
    schemaFile <- partsMap.optPartValue("schemaFile")
    schemaFormatTextArea <- getSchemaFormat("schemaFormatTextArea",partsMap)
    schemaFormatUrl <- getSchemaFormat("schemaFormatUrl",partsMap)
    schemaFormatFile <- getSchemaFormat("schemaFormatFile",partsMap)
    schemaFormatValue <- getSchemaFormat("schemaFormat", partsMap)
    schemaEngine <- partsMap.optPartValue("schemaEngine")
    targetSchemaEngine <- partsMap.optPartValue("targetSchemaEngine")
    targetSchemaFormat <- partsMap.optPartValue("targetSchemaFormat")
    activeSchemaTab <- partsMap.optPartValue("activeSchemaTab")
    schemaEmbedded <- partsMap.optPartValueBoolean("schemaEmbedded")
  } yield {
    SchemaParam(schema, schemaURL, schemaFile,
      schemaFormatTextArea, 
      schemaFormatUrl, 
      schemaFormatFile,
      schemaFormatValue,
      schemaEngine, schemaEmbedded,
      targetSchemaEngine, targetSchemaFormat, activeSchemaTab
    )
  }



  private[server] def empty: SchemaParam =
    SchemaParam(None,None,None,None,None,None,None,None,None,None,None,None)

}