package hello

import zio.console.putStrLn
import io.jenetics.jpx.GPX
import io.jenetics.jpx.Track
import io.jenetics.jpx.TrackSegment
import io.jenetics.jpx.WayPoint
import java.io.Serializable
import java.time.{Duration, Instant}

import zio.{App, Task, ZIO}
import zio.console._
import better.files.File
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract.elementList // Expand to wild-card of this package, if needed

case class GpsEntry(latitude: Double, longitude: Double, altitude: Double)
case class TimestampedGpsEntry(gpsEntry: GpsEntry, timestamp: Instant)

object Main extends App {
  val browser = JsoupBrowser()

  private val startedAtRegex =
    """.*startedAt:\"([^\"]*)\".*""".r

  def parseStartTimeFromRawJson(input: String) = ZIO {
    Instant.parse(
      input.toString match {
        case startedAtRegex(coordinates) => coordinates
      }
    )
  }

  private val endedAtRegex =
    """.*endedAt:\"([^\"]*)\".*""".r

  def parseEndTimeFromRawJson(input: String) = ZIO {
    Instant.parse(
      input.toString match {
        case endedAtRegex(coordinates) => coordinates
      }
    )
  }

  private val coordinatesRegex =
    """.*coordinates:\[\[\[(.*)\]\]\].*""".r

  def parseCoordinatesFromRawJson(input: String) = ZIO { // with shitty bogus regexes
    val formattedCoordinates = input.toString match {
      case coordinatesRegex(coordinates) => coordinates
    }
    formattedCoordinates
      .split("""],\[""")
      .filter(_.forall(character=>character.isDigit || character == ',' || character == '.' || character == '-')) // There's all kinds of garbage in the GPS data
      .map(coords => {
        val coordFields = coords.split(",")
        GpsEntry(coordFields(0).toDouble, coordFields(1).toDouble, coordFields(2).toDouble)
      })
  }

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

  def getInputFiles(): Task[List[File]] = ZIO {
    File("./src/resources").list
      .filter(!_.name.contains("strava_example"))
      .toList
  }

  def createWayPointWith(gpsEntry: TimestampedGpsEntry) = {
    WayPoint.builder()
      .ele(gpsEntry.gpsEntry.altitude)
      .time(gpsEntry.timestamp)
      .build(gpsEntry.gpsEntry.longitude, gpsEntry.gpsEntry.latitude) // TODO ARE THESE BACKWARDS??
  }

  def writeGpxDataToFile(fileName: String, gpsEntries: Array[TimestampedGpsEntry]) = ZIO {
    val foldedTrackBuilder = gpsEntries
      .foldLeft(TrackSegment.builder()) {
        case (builder, gpsEntry) => builder.addPoint(createWayPointWith(gpsEntry))
      }
    val trackSegment: TrackSegment = foldedTrackBuilder.build()
    val track: Track = Track.builder()
      .name(fileName.dropWhile(_ != '_').tail.dropRight(4))
      .addSegment(trackSegment).build()
    val finalGpxValue = GPX.builder()
      .addTrack(track)
      .build()
    GPX.writer("  ")
      .write(finalGpxValue, s"CONVERTED_$fileName".replace("txt", "gpx"))
  }

  def fullFileProcess(file: File) =
    for {
      _ <- putStrLn(file.name)
      rawJsonData <- getRawJsonFromDataScriptInFile(file)
      typedGpsCoordinates <- parseCoordinatesFromRawJson(rawJsonData)
      _ <- putStrLn("Number of coordinates " + typedGpsCoordinates.length)
      startedAt <- parseStartTimeFromRawJson(rawJsonData)
      endedAt <- parseEndTimeFromRawJson(rawJsonData)
      timestampedGpsCoordinates <- ZIO {
        val duration = Duration.between(startedAt, endedAt)
        val durationPerStep = duration.dividedBy(typedGpsCoordinates.length)
        typedGpsCoordinates
          .zipWithIndex
          .map { case (gpsEntry, index) => TimestampedGpsEntry(gpsEntry, startedAt.plus(durationPerStep.multipliedBy(index))) }
      }
      _ <- writeGpxDataToFile(file.name, timestampedGpsCoordinates)
    } yield {
      ()
    }

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] = {
    val logic: ZIO[Console, Serializable, Int] =
      for {
        files <- getInputFiles()
        _ <- ZIO.collectAll(files.map(fullFileProcess))
      } yield (0)
    logic
      .provide(Environment)
      .fold(failure => {
        println("Failure: " + failure)
        1
      }, _ => {
        0
      })
  }
}
