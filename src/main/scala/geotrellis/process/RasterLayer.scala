package geotrellis.process

import geotrellis._
import geotrellis.util._

import scala.concurrent._
import scala.concurrent.Future
import scala.util._

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._

import spray.http._
import spray.client.pipelining._

import scala.io.Source
import java.io.File

/**
 * Represents a Raster Layer that can give detailed information
 * about the Raster it represents, cache the raster, and get the 
 * raster cropped to an extent or at a different resolution.
 * 
 * This represents a layer in a bound Context, not an abstract
 * representation of the Raster. In other words, if you are
 * holding one of these objects, then the code that uses it
 * should only execute on the machine that the RasterLayer is
 * from. If you pass around RasterLayers, you will be passing around
 * the cache as well, which is not ideal.
 * 
 * To implement a new RasterLayer, inherit from this class, implement
 * the cache(c:Cache) method for caching the raster layer, and implement
 * the getRaster() (for getting a Raster with it's native RasterExtent) and
 * getRaster(rasterExtent:RasterExtent) (for getting a Raster at a different
 * extent\resolution). Optionally you can override getRaster(extent:Extent),
 * which by default just creates a RasterExtent with that extent snapped to 
 * the raster's native resolution.
 */
abstract class RasterLayer(val info:RasterLayerInfo) {
  private var _cache:Option[Cache[String]] = None
  protected def getCache = 
    _cache match {
      case Some(c) => c
      case None =>
        sys.error("No cache is currently set. Check isCached before accessing this member.")
    }

  def setCache(c:Option[Cache[String]]) = {
    _cache = c
  }

  private var _isCached = false
  def isCached = _isCached

  def cache():Unit = {
    _cache match {
      case Some(c) => 
        cache(c)
        _isCached = true
        info.cached = true
      case None => //pass
    }
  }

  protected def cache(c:Cache[String]):Unit

  def getRaster():Raster = getRaster(None)
  def getRaster(targetExtent:Option[RasterExtent]):Raster

  def getRaster(extent:Extent):Raster = 
    getRaster(Some(info.rasterExtent.createAligned(extent)))

  def getTile(tileCol:Int, tileRow:Int):Raster = getTile(tileCol,tileRow,None)
  def getTile(tileCol:Int, tileRow:Int, targetExtent:Option[RasterExtent]):Raster
}

abstract class UntiledRasterLayer(info:RasterLayerInfo) extends RasterLayer(info) {
  def getTile(tileCol:Int, tileRow:Int, targetExtent:Option[RasterExtent]) =
    getRaster(targetExtent)
}

object RasterLayer {
  /**
   * Build a RasterLayer instance given a path to a JSON file.
   */
  def fromPath(path:String):Try[RasterLayer] =
    try {
      val base = Filesystem.basename(path) + ".json"
      val src = Source.fromFile(path)
      val data = src.mkString
      src.close()
      fromJSON(data, base)
    } catch {
      case e:Exception => Failure(e)
    }

  def fromFile(f:File):Try[RasterLayer] = RasterLayer.fromPath(f.getAbsolutePath)

  /**
    * Build a RasterLayer instance given a JSON string.
   */
  def fromJSON(data:String, basePath:String):Try[RasterLayer] =
    try {
      Success(json.RasterLayerParser(data, basePath))
    } catch {
      case e:Exception => Failure(e)
    }

  def fromUrl(jsonUrl:String, d: Duration = 5 seconds)
             (implicit t: Timeout = 5 seconds):Try[RasterLayer] = {
    val (system, shutdown) =
      if(GeoTrellis.isInit)
        // If the GeoTrellis.server is being used, use it's Actor System
        (GeoTrellis.server.system, { () => })
      else {
        // otherwise, create a temporary one
        val s = ActorSystem(s"system-request-$jsonUrl")
        (s, () => s.shutdown)
      }
    implicit val s = system
    import s.dispatcher

    try {
      val pipeline = sendReceive ~> unmarshal[String]
      val response: Future[String] =
        pipeline(Get(jsonUrl))

      val json = Await.result(response, d)
      fromJSON(json, jsonUrl)
    } catch {
      case e:Exception => Failure(e)
    } finally {
      shutdown()
    }
  }
}