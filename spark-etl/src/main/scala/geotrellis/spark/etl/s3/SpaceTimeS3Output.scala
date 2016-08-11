package geotrellis.spark.etl.s3

import geotrellis.raster.Tile
import geotrellis.spark._
import geotrellis.spark.etl.config.EtlConf
import geotrellis.spark.io._
import geotrellis.spark.io.s3.S3LayerWriter

import org.apache.spark.SparkContext

class SpaceTimeS3Output extends S3Output[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]] {
  def writer(conf: EtlConf)(implicit sc: SparkContext) =
    S3LayerWriter(conf.outputProps("bucket"), conf.outputProps("key")).writer[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](conf.output.getKeyIndexMethod[SpaceTimeKey])
}
