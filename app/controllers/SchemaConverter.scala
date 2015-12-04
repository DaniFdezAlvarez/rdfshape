package controllers

import play.api._
import play.api.mvc._
import scala.concurrent._
import akka.actor._

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.io.ByteArrayInputStream
import play.api._
import play.api.mvc._
import play.api.libs.Files._
import es.weso.shacl.Schema
import scala.util.{Try, Success => TrySuccess, Failure => TryFailure}
import es.weso.rdf._
import es.weso.rdfgraph.nodes.IRI
import es.weso.rdf.jena._
import es.weso.monads.{Result => SchemaResult, Failure => SchemaFailure}
import es.weso.shacl.{Schema => ShExSchema, SchemaFormats}
import es.weso.utils._
import es.weso.utils.TryUtils._
import es.weso.utils.RDFUtils._
import java.net.URL
import java.io.File
import es.weso.utils.IOUtils._
import Multipart._
import play.api.libs.json._

trait SchemaConverter { this: Controller => 

 def converterSchemaFuture(
          schema: String
        , inputFormat: String
        , schemaVocabulary: String
        , schemaProcessor: String
        , outputFormat: String
    ) : Future[Try[String]]= {
       val schemaInput = SchemaInput(schema,inputFormat,schemaVocabulary,schemaProcessor)
       Future(schemaInput.convertSchema(outputFormat))
  }
  
  
  def convert_schema_get(
          schema: String
        , inputFormat: String
        , schemaVocabulary: String
        , schemaProcessor: String
        , outputFormat: String
        ) = Action.async {  
        converterSchemaFuture(schema,inputFormat, schemaVocabulary, schemaProcessor,outputFormat).map(output => {
              output match {
                case TrySuccess(result) => {
                  val schemaInput = SchemaInput(schema,inputFormat,schemaVocabulary,schemaProcessor)
                  val vf = ValidationForm.fromSchemaConversion(schemaInput)
                  Ok(views.html.convert_schema(vf,outputFormat,result))
                }
                case TryFailure(e) => BadRequest(views.html.errorPage(e.getMessage))
              }
          })
  }

  def convert_schema_post = Action { request => {
     val r = for ( mf <- getMultipartForm(request)
                 ; schemaInput <- parseSchemaInput(mf)
                 ; str_schema <- schemaInput.getSchemaStr
                 ; outputFormat <- parseKey(mf, "outputFormat")
                 ; outputStr <- schemaInput.convertSchema(outputFormat)
                 ) yield (schemaInput, outputFormat,outputStr)
     
      r match {
       case TrySuccess((schemaInput, outputFormat,result)) => {
         Logger.info("Convert_schema_post: " + schemaInput)
         val vf = ValidationForm.fromSchemaConversion(schemaInput)
         Ok(views.html.convert_schema(vf,outputFormat,result))
       }
       case TryFailure(e) => {
        Logger.info("Exception raised: " + e.getMessage)
        BadRequest(views.html.errorPage(e.getMessage)) 
       } 
      }
    } 
  }

  def schemaFormats = Action {
    Ok(Json.toJson(SchemaFormats.toList))
  }
    
}

object SchemaConverter extends Controller with SchemaConverter
