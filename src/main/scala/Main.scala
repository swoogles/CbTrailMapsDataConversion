package hello

import zio.App
import zio.console.putStrLn
import io.jenetics.jpx.GPX
import io.jenetics.jpx.Track
import io.jenetics.jpx.TrackSegment
import io.jenetics.jpx.WayPoint
import java.io
import java.time.Instant

import zio.{App, ZIO}
import zio.console._
import zamblauskas.csv.parser._
import better.files._
import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty}
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.module.scala.ScalaObjectMapper
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import ujson.StringRenderer
import upickle.default._

import scala.beans.BeanProperty

//import java.io.jenetics.jpx.GPX
//import io.jenetics.jpx.GPX

case class Person(name: String, age: Int, city: Option[String])


case class GpsEntry(latitude: Double, longitude: Double, altitude: Double)

@JsonIgnoreProperties(ignoreUnknown = true)
case class GpsTrack(
                    @JsonProperty("id") val int: Int
                  )


@JsonIgnoreProperties(ignoreUnknown = true)
case class GpsData(
                           @JsonProperty("track") val track: GpsTrack
                         )

@JsonIgnoreProperties(ignoreUnknown = true)
case class FullGpsPayload(
                   @JsonProperty("layout") val layout: String,
                     @JsonProperty("data") val data: Array[GpsData]
                 )

object Main extends App {
  val browser = JsoupBrowser()

  def parseCoordinatesFromRawJson(input: String) = { // with shitty bogus regexes
    import collection.JavaConverters._
    import java.util
    val jso: util.Map[String, AnyRef] = Json.parseJSON(input)
    val dataValue = jso
      .entrySet()
      .asScala
      .filter( entry => entry.getKey == "data")
      .map(_.getValue)
      .head
    val coordinatesRegex =
      """.*coordinates:\[\[\[(.*)\]\]\].*""".r
    val formattedCoordinates = input.toString match {
//      case coordinatesRegex(coordinates) => s"[$coordinates]")
      case coordinatesRegex(coordinates) => coordinates
    }
    formattedCoordinates
      .split("""],\[""")
      .filter(_.forall(character=>character.isDigit || character == ',' || character == '.' || character == '-')) // Dunno WTF this is doing in the GPS data
      .map(coords => {
        println("eh?!?!")
        val coordFields = coords.split(",")
//        println(coordFields.mkString(","))
        GpsEntry(coordFields(0).toDouble, coordFields(1).toDouble, coordFields(2).toDouble)
      })
      .foreach(println)
  }

  def parseRawJson2(input: String) = {
    import com.fasterxml.jackson.databind.ObjectMapper
    import com.fasterxml.jackson.core.JsonFactory
    import java.io.FileInputStream
    val factory = new JsonFactory
    factory.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
    val jp = factory.createParser(input)
    val mapper = new ObjectMapper with ScalaObjectMapper
    val typedResult = mapper.readValue[FullGpsPayload](jp)
    println(typedResult)

//    val parsedJson = mapper.readTree(input)
//    println(parsedJson)
    jp
  }


//  def parseRawJson(input: String) = {
//    println(ujson.transform(input, StringRenderer()))
//  }

  def getRawJsonFromDataScriptInFile(input: File) = ZIO {
    val result =
      (browser.parseString(input.contentAsString) >> elementList("script"))
        .map(_.innerHtml)
      .filter(_.contains("data"))
      .map( content => content.dropWhile( _ != '{').reverse.dropWhile( _ != '}').reverse)
      .map( content => content.drop(1).dropRight(1))
      .map( content => content.dropWhile( _ != '{').reverse.dropWhile( _ != '}').reverse)
      .head
    result
  }

  def getInputFiles() = ZIO {
    File("./src/resources").list
      .filter(!_.name.contains("strava_example"))
      .toList
  }

  def writeGpxDataToFile(fileName: String) = ZIO {
//    JavaGPX.writeToFile(
    val wayPoint: WayPoint = WayPoint.builder().build(45.2323, 62.2343242)
    val trackSegment: TrackSegment = TrackSegment.builder().addPoint(wayPoint).build()
    val track: Track = Track.builder().addSegment(trackSegment).build()
      val finalGpxValue = GPX.builder()
                .addTrack(track)
        //          .addSegment(segment => segment
        //            .addPoint(p => p.lat(48.2081743).lon(16.3738189).ele(160))
        //            .addPoint(p => p.lat(48.2081743).lon(16.3738189).ele(161))
        //            .addPoint(p => p.lat(48.2081743).lon(16.3738189).ele(162))))
        .build()
//      , fileName)
    GPX.write(finalGpxValue, fileName)
  }

  implicit val instantReads:  zamblauskas.csv.parser.Reads[java.time.Instant] = new Reads[Instant] {
    def read(column: Column): ReadResult[Instant] = {
      ReadSuccess(Instant.parse(column.value))
    }

  }
  val csv = """
              |name,age,height,city
              |Emily,33,169,London
              |Thomas,25,,
          """.stripMargin

  val gpsCsv = """
                 |latitude,longitude,createdOn
                 |23.52423,33.125269,2007-12-03T10:15:30.00Z
          """.stripMargin

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] = {
    //    val liveDefaultEnvironment: Environment =
    //      new Clock.Live with Console.Live with System.Live with Random.Live
    //     = Environment.console

    val logic: ZIO[Console, io.Serializable, Int] =
      for {
        result <- ZIO {Parser.parse[Person](csv)}
        files <- getInputFiles()
        _ <- putStrLn(files(0).name)
        rawJsonData <- getRawJsonFromDataScriptInFile(files(0))
        _ <- ZIO {parseCoordinatesFromRawJson(rawJsonData) }
//        _ <- putStrLn(rawJsonData.toString)
//        gpsResult <- ZIO {Parser.parse[GpsEntry](gpsCsv)}
        _ <- writeGpxDataToFile("testFile")
//        _ <- putStrLn(s"CSV conversion result $gpsResult")
      } yield (0)
    logic
      .provide(Environment)
      .fold(failure => {
        println("Failure: " + failure)
        println("ouch!")
        1
      }, _ => {
        0
      })
  }
}
