package geotrellis.spark.io.accumulo


import geotrellis.spark.io.AttributeStore.Fields
import geotrellis.spark.io.avro._
import geotrellis.spark.io.json._
import geotrellis.spark._
import geotrellis.spark.io.index.KeyIndex
import geotrellis.spark.io._
import org.apache.avro.Schema
import org.apache.hadoop.io.Text
import org.apache.spark.SparkContext
import org.apache.accumulo.core.data.{Range => AccumuloRange, Key}
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.reflect._

class AccumuloLayerReader[K: Boundable: AvroRecordCodec: JsonFormat: ClassTag, TileType: AvroRecordCodec: ClassTag, Container](
    val attributeStore: AttributeStore.Aux[JsonFormat],
    rddReader: BaseAccumuloRDDReader[K, TileType])
  (implicit sc: SparkContext, val cons: ContainerConstructor[K, TileType, Container])
  extends FilteringLayerReader[LayerId, K, Container] {

  type MetaDataType = cons.MetaDataType

  val defaultNumPartitions = sc.defaultParallelism

  def read(id: LayerId, rasterQuery: RDDQuery[K, MetaDataType], numPartitions: Int) = {
    try {
      val layerMetaData = attributeStore.cacheRead[AccumuloLayerMetaData](id, Fields.layerMetaData)
      val metadata = attributeStore.cacheRead[MetaDataType](id, Fields.rddMetadata)(cons.metaDataFormat)
      val keyBounds = attributeStore.cacheRead[KeyBounds[K]](id, Fields.keyBounds)
      val keyIndex = attributeStore.cacheRead[KeyIndex[K]](id, Fields.keyIndex)
      val writerSchema: Schema = (new Schema.Parser)
        .parse(attributeStore.cacheRead[JsObject](id, Fields.schema).toString())

      val queryKeyBounds = rasterQuery(metadata, keyBounds)

      val decompose = (bounds: KeyBounds[K]) =>
        keyIndex.indexRanges(bounds).map{ case (min, max) =>
          new AccumuloRange(new Text(long2Bytes(min)), new Text(long2Bytes(max)))
        }

      val rdd = rddReader.read(layerMetaData.tileTable, columnFamily(id), queryKeyBounds, decompose, Some(writerSchema))
      cons.makeContainer(rdd, keyBounds, metadata)
    } catch {
      case e: AttributeNotFoundError => throw new LayerReadError(id).initCause(e)
    }
  }
}

object AccumuloLayerReader {
  def apply[K: Boundable: AvroRecordCodec: JsonFormat: ClassTag, V: AvroRecordCodec: ClassTag, C[_]](instance: AccumuloInstance)
    (implicit sc: SparkContext, cons: ContainerConstructor[K, V, C[K]]): AccumuloLayerReader[K, V, C[K]] =
    new AccumuloLayerReader (
      AccumuloAttributeStore(instance.connector),
      new AccumuloRDDReader[K, V](instance))

}
