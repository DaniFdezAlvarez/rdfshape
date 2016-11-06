package controllers

import scala.util.{ Success, Try }
import es.weso.utils.IOUtils.{ failMsg, getFileContents, getURI }
import es.weso.schema._
import es.weso.rdf.nodes._

case class ValidationForm(
      dataInput: DataInput
    , dataOptions: DataOptions
    , withSchema : Boolean
    , schemaInput: SchemaInput
    , schemaOptions: SchemaOptions
    ) {
 
 
 // This method is used to show if form is withIRI or not in index.scala.html
 // the values must match the prefix of values in tabs.js
 def opt_iri_str = {
   val iri = "iri"
   val noIri = "noIri"
   schemaOptions.trigger match {
      	case _:NodeAllShapes  => iri
      	case _:NodeShape => iri
      	case ScopeDeclarations => noIri
      	case AllNodesAllShapes => noIri  
    }
 }
 
 // this method is used in index.scala.html to show if schema is toggled or no
 // the values must match the values in tabs.js
 def schema_toggle = {
   if (withSchema) "#schema"
   else "#no_schema"
 }

 /*
 def getSchemaStr(): Try[Option[String]] = {
   if (withSchema) {
	   schemaInput.input_type_Schema match {
      	case ByUri => getURI(schemaInput.schema_uri).map(str => Some(str))
      	case ByFile => getFileContents(schemaInput.schema_file).map(str => Some(str))
      	case ByInput => Success(Some(schemaInput.schema_textarea))
      	case _ => failMsg("parseSchema: non supported input type: " + schemaInput.input_type_Schema)
      }     
   }
   else Success(None)
 }*/

/* private def extract_str : String = {
   // (this.getSchemaStr().map(opt => opt.getOrElse(""))).getOrElse("")
   dataInput.extract_str
 } */

 private def getSchemaOptions(): Try[Option[SchemaOptions]] = {
   if (withSchema) {
     Success(Some(schemaOptions))
   } else 
     Success(None)
 }
 
 def input_type_Schema_str : String = {
   schemaInput.input_type_Schema.toString
 } 

 // Public methods
 
 def dataStr : String = {
   dataInput.extract_str
 }
 
  def dataFormat : String = {
   dataOptions.format
 }

 def schemaName : String = schemaInput.schemaName
 def schemaFormat : String = schemaInput.inputFormat
 def schemaStr : String = schemaInput.getSchemaStr.getOrElse("")
 def rdfs: Boolean = dataOptions.rdfs
 
 def focusNode : String = {
   schemaOptions.trigger.extractNode
 }

 def maybeFocusNode : Option[String] = {
   schemaOptions.trigger match {
     case ScopeDeclarations => None
     case AllNodesAllShapes => None
     case NodeShape(node,_) => Some(node.toString)
     case NodeAllShapes(node) => Some(node.toString)
   }
 }
 
  def nodes: List[String] = {
    val tryNodes = for {
      rdf <- dataInput.getData(dataFormat,dataOptions.rdfs)
    } yield rdf.iris().map(_.toString).toList
    tryNodes.getOrElse(List())
  }
 
  def shapes: List[String] = {
    val tryNodes = for {
      schema <- schemaInput.getSchema
    } yield schema.shapes
    tryNodes.getOrElse(List())
  }
  
}
    
object ValidationForm {
  def apply(): ValidationForm = 
    ValidationForm(
          dataInput = DataInput()
        , dataOptions = DataOptions.default
        , withSchema = false
        , schemaInput = SchemaInput(HTMLSchemas.defaultSchemaName)
        , schemaOptions = SchemaOptions.default
        )
        
  def fromResult(vr:ValidationResult): ValidationForm = {
    ValidationForm(
      dataInput = DataInput(vr.dataStr)
    , dataOptions = vr.dataOptions
    , withSchema = 
       if (vr.together) false
       else vr.withSchema
    , schemaInput = SchemaInput(vr.schemaStr,vr.schemaFormat, vr.schemaName)
    , schemaOptions = vr.schemaOptions
    )
  }
  
  def fromDataConversion(data: String, format: String, schemaName: String, rdfs: Boolean): ValidationForm = {
    ValidationForm(
      dataInput = DataInput(data)
    , dataOptions = DataOptions(format = format,rdfs = rdfs,showData = false)
    , withSchema = false
    , schemaInput = SchemaInput(schemaName)
    , schemaOptions = SchemaOptions.default
    )
  }
  
  def fromSchemaConversion(schemaInput: SchemaInput): ValidationForm = {
    ValidationForm(
      dataInput = DataInput()
    , dataOptions = DataOptions.default
    , withSchema = true
    , schemaInput = schemaInput
    , schemaOptions = SchemaOptions.fromSchemaInput(schemaInput)
    )
  }

}
