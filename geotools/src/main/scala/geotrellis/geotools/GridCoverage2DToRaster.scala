/*
 * Copyright (c) 2016 Azavea.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.geotools

import geotrellis.raster._
import geotrellis.vector._

import org.geotools.coverage.grid._
import org.geotools.gce.geotiff._
import org.geotools.coverage.grid.io._

import scala.math.{min, max}


object GridCoverage2DToRaster {

  def apply(gridCoverage: GridCoverage2D): Raster[MultibandTile] = {
    val extent = GridCoverage2DToRaster.extent(gridCoverage)
    val tile = GridCoverage2DMultibandTile(gridCoverage)

    Raster(tile, extent)
  }

  def extent(gridCoverage: GridCoverage2D): Extent = {
    val envelope = gridCoverage.getEnvelope
    val Array(xmin, ymin) = envelope.getUpperCorner.getCoordinate
    val Array(xmax, ymax) = envelope.getLowerCorner.getCoordinate

    Extent(min(xmin, xmax), min(ymin, ymax), max(xmin, xmax), max(ymin, ymax))
  }

  def cellType(gridCoverage: GridCoverage2D): CellType = {
    val n = gridCoverage.getNumSampleDimensions
    val noDataValue = noData(gridCoverage)
    val renderedImage = gridCoverage.getRenderedImage
    val buffer = renderedImage.getData.getDataBuffer
    val typeEnum = buffer.getDataType

    if (noDataValue.isEmpty) {
      typeEnum match {
        case 0 => UByteCellType
        case 1 => UShortCellType
        case 2 => ShortCellType
        case 3 => IntCellType
        case 4 => FloatCellType
        case 5 => DoubleCellType
        case _ => throw new Exception("Unknown CellType")
      }
    }
    else {
      val noData = noDataValue.get
      typeEnum match {
        case 0 => ByteUserDefinedNoDataCellType(noDataValue.get.toByte)
        case 1 => UShortUserDefinedNoDataCellType(noDataValue.get.toShort)
        case 2 => ShortUserDefinedNoDataCellType(noDataValue.get.toShort)
        case 3 => IntUserDefinedNoDataCellType(noDataValue.get.toInt)
        case 4 => FloatUserDefinedNoDataCellType(noDataValue.get.toFloat)
        case 5 => DoubleUserDefinedNoDataCellType(noDataValue.get.toDouble)
        case _ => throw new Exception("Unknown CellType")
      }
    }
  }
}
