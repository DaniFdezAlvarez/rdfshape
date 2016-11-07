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
import es.weso.shex.Schema

import scala.util.{Try, Failure => TryFailure, Success => TrySuccess}
import es.weso.rdf._
import es.weso.rdf.nodes.IRI
import es.weso.rdf.jena._
import es.weso.monads.{Failure => SchemaFailure, Result => SchemaResult}
import es.weso.utils._
import es.weso.utils.TryUtils._
import es.weso.utils.RDFUtils._
import java.net.URL
import java.io.File
import javax.inject.{Inject, Singleton}

import es.weso.utils.IOUtils._
import Multipart._
import es.weso.htmlschema.DataFormat
import play.api.libs.json._
import es.weso.schema._
import play.api.i18n.{I18nSupport, MessagesApi}

@Singleton
class DataConverter @Inject()(implicit val webJarAssets: WebJarAssets,
                              val messagesApi: MessagesApi) extends Controller with I18nSupport {

 def converterDataFuture(
          data: String
        , dataFormat: String
        , outputFormat: String
        , rdfs: Boolean
    ) : Future[Try[String]]= {
       Future(parseStrAsRDFReader(data,dataFormat,rdfs).map(_.serialize(outputFormat)))
  }
  
  
  def convert_data_get(
          data: String
        , dataFormat: String
        , targetFormat: String
        , schemaName: String
        , rdfs: Boolean = false
        ) = Action.async {  
        converterDataFuture(data, dataFormat, targetFormat, rdfs).map(output => {
              output match {
                case TrySuccess(result) => {
                  val vf = ValidationForm.fromDataConversion(data, dataFormat, schemaName, rdfs)
                  Ok(views.html.convert_data(vf,targetFormat,result))
                }
                case TryFailure(e) => BadRequest(views.html.errorPage(e.getMessage))
              }
          })
  }


    def convert_data_post = Action { request => {
     val r = for ( 
       mf <- getMultipartForm(request)
     ; vf <- getValidationForm(request)
     ; str_data <- vf.dataInput.getDataStr
     ; outputFormat <- parseKey(mf, "outputFormat")
     ; data <- vf.dataInput.getData(vf.dataOptions.format,vf.dataOptions.rdfs)
     ) yield (vf,outputFormat,data.serialize(outputFormat))
     
      r match {
       case TrySuccess((vf,outputFormat,result)) =>
             Ok(views.html.convert_data(vf,outputFormat,result))
       case TryFailure(e) => BadRequest(views.html.errorPage(e.getMessage)) 
      }
    } 
  }
  
  def dataFormats = Action {
    Ok(Json.toJson(DataFormat.toList))
  }
   
}

