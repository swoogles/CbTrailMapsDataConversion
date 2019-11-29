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
import java.{io => javaIo}

//import java.io.jenetics.jpx.GPX
//import io.jenetics.jpx.GPX

case class Person(name: String, age: Int, city: Option[String])


case class GpsEntry(latitude: Double, longitude: Double, createdOn: Instant)


object Main extends App {

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
        gpsResult <- ZIO {Parser.parse[GpsEntry](gpsCsv)}
        _ <- writeGpxDataToFile("testFile")
        _ <- putStrLn(s"CSV conversion result $gpsResult")
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
