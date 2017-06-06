package org.bithub.jannad.test1

import com.github.simplyscala.MongoEmbedDatabase
import org.mongodb.scala.bson._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Projections.include
import org.mongodb.scala.{MongoDatabase, Document => MongoDocument}
import sangria.ast.{Document, FieldDefinition, TypeDefinition}
import sangria.execution.Executor
import sangria.parser.QueryParser
import sangria.schema.{Action, AstSchemaMaterializer, Context, DefaultAstSchemaBuilder, FutureValue, ProjectedName, Projector, Schema}
import spray.json.{JsObject, JsString}

import scala.concurrent.{Await, Future}

/**
  * @author jan.nad
  * @since 6/5/2017
  */
object Xxxx extends MongoEmbedDatabase {

  import io.Source._

  def readSchemaString() = fromInputStream(getClass.getResourceAsStream("schema.graphql")).mkString

  type Ctx = Repo
  type Val = Any
  type Res = Any

  def loadSchema() = {
    val schemaString = readSchemaString()

    println("=========== Schema string:")
    println(schemaString)

    val schemaAst = QueryParser.parse(schemaString).get

    val schemaBuilder = new DefaultAstSchemaBuilder[Ctx] {
      override def resolveField(typeDefinition: TypeDefinition, definition: FieldDefinition): (Context[Ctx, _]) => Action[Ctx, _] = {
        println(s"Preparing resolveField function for: ${definition.name}")
        def projectionPaths(pn: ProjectedName): scala.Vector[String] = {
          if (pn.children.isEmpty) {
            scala.Vector(pn.name)
          } else {
            pn.children.flatMap(projectionPaths(_).map(s => s"${pn.name}.${s}"))
          }
        }
        def resolveDocument(collection: String): Context[Ctx, Val] => Action[Ctx, Res] = Projector[Ctx, Val, Res]( (ctx: Context[Ctx, _], f: Vector[ProjectedName]) => {
          println(s"   Projection: ${f.flatMap(projectionPaths(_)).mkString(", ")}")

          println("   ctx.args.raw: " + ctx.args.raw)
          import org.mongodb.scala.model.Filters._

          val o = ctx.ctx.findFirst(collection,
            filter = equal("id", ctx.args.arg[String]("id")), // TODO not cool
            projection = f.flatMap(projectionPaths(_)).toList)
          import scala.concurrent.duration._
          println("   Resolved from repo: " + Await.result(o, 10.seconds))
          FutureValue(o)
        })

        definition.name match {
          case "query" => (ctx => ())
          case c@"person" => resolveDocument(c).asInstanceOf[Context[Ctx, _] => Action[Ctx, _]]
          case field => ctx => ctx.value.asInstanceOf[MongoDocument].get(field).get match {
            case o: BsonString => o.getValue
            case o: BsonInt32 => o.getValue
            case o: BsonObjectId => o.getValue.toHexString
            case o: BsonDocument => MongoDocument(o)
            case x => throw new IllegalStateException(x.getClass.getSimpleName)
          }
        }
      }
    }

    val schema: Schema[Ctx, Any] = AstSchemaMaterializer.buildSchema[Ctx](schemaAst, schemaBuilder)

    //println(schema)
    schema
  }

  val queryPersonWithAddressById = {
    import sangria.macros._
    graphql"""
         query FetchPersonByIdQuery($$personId: ID!) {
           person(id: $$personId) {
              name
              age
              address {
                city
              }
           }
         }
       """
  }

  def executeQuery(query: Document, queryParams: JsObject)(implicit schema: Schema[Ctx, Any], repo: Repo) = {
    import sangria.marshalling.sprayJson._

    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.duration._

    val futureResult = Executor.execute(schema, query, userContext = repo, variables = queryParams)

    Await.result(futureResult, 10.seconds)
  }

  def main(args: Array[String]): Unit = {
    implicit val schema = loadSchema()

    import org.mongodb.scala._

    val mongoProps = mongoStart()
    val mongoClient: MongoClient = MongoClient("mongodb://localhost:12345")
    val database = mongoClient.getDatabase("mydb")

    implicit val ctx = new Repo(database)

    ctx.insertTestData()

    println("executeQuery...")
    val res = executeQuery(queryPersonWithAddressById, JsObject("personId" -> JsString("customId123")))
    println(s"Query result: $res")

    mongoStop(mongoProps)
  }
}

class Repo(val database: MongoDatabase) {

  def findFirst(collection: String, filter: Bson = BsonDocument(), projection: List[String]): Future[MongoDocument] =
    database.getCollection(collection).find(filter).projection(include(projection:_*)).first().toFuture

  def insertTestData() = {
    println("inserting record into mongo...")
    val person1 = MongoDocument("id" -> "customId123",
      "name" -> "Aaaaa B. Cccc",
      "age" -> 40,
      "address" -> MongoDocument("street" -> "Vodickova 10", "city" -> "Prague"))
    import scala.concurrent.duration._
    Await.ready(database.getCollection("person").insertOne(person1).toFuture, 10.seconds)
  }

}