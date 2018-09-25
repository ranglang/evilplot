/*
 * Copyright (c) 2018, CiBO Technologies, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.cibo.evilplot

import scala.language.implicitConversions
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

package object numeric {
  type Grid = Vector[Vector[Double]]

  trait Point2d {
    val x: Double
    val y: Double
    def setXY(x: Double = this.x, y: Double = this.y): Point2d
  }

  case class Point3d[Z: Numeric](x: Double, y: Double, z: Z) extends Datum2d[Point3d[Z]]{
    def setXY(x: Double, y: Double): Point3d[Z] = this.copy(x, y, z)
  }

  trait Datum2d[A <: Datum2d[A]] extends Point2d {
    val x: Double
    val y: Double
    def setXY(x: Double = this.x, y: Double = this.y): A
  }

  final case class Point(x: Double, y: Double) extends Datum2d[Point] {
    def -(that: Point): Point = Point(x - that.x, y - that.y)

    def setXY(x: Double = this.x, y: Double = this.y): Point = this.copy(x = x, y = y)
  }

  object Point {
    implicit val encoder: Encoder[Point] = io.circe.generic.semiauto.deriveEncoder[Point]
    implicit val decoder: Decoder[Point] = io.circe.generic.semiauto.deriveDecoder[Point]
    def tupled(t: (Double, Double)): Point = Point(t._1, t._2)
    implicit def toTuple(p: Point): (Double, Double) = (p.x, p.y)
  }

  final case class Point3(x: Double, y: Double, z: Double)

  object Point3 {
    def tupled(t: (Double, Double, Double)): Point3 = Point3(t._1, t._2, t._3)
  }

  final case class GridData(
    grid: Grid,
    xBounds: Bounds,
    yBounds: Bounds,
    zBounds: Bounds,
    xSpacing: Double,
    ySpacing: Double)

  final case class Bounds(min: Double, max: Double) {
    require(!(min > max), s"Bounds min must be <= max, $min !<= $max")

    lazy val range: Double = max - min

    lazy val midpoint: Double = (max + min) / 2.0

    def isInBounds(x: Double): Boolean = x >= min && x <= max
  }

  object Bounds {

    implicit val encoder: Encoder[Bounds] = deriveEncoder[Bounds]
    implicit val decoder: Decoder[Bounds] = deriveDecoder[Bounds]

    private def lift[T](expr: => T): Option[T] = {
      try {
        Some(expr)
      } catch {
        case _: Exception => None
      }
    }

    def union(bounds: Seq[Bounds]): Bounds = {
      Bounds(
        min = bounds.map(_.min).min,
        max = bounds.map(_.max).max
      )
    }

    def getBy[T](data: Seq[T])(f: T => Double): Option[Bounds] = {
      val mapped = data.map(f).filterNot(_.isNaN)
      for {
        min <- lift(mapped.min)
        max <- lift(mapped.max)
      } yield Bounds(min, max)
    }

    def get(data: Seq[Double]): Option[Bounds] = {
      data.foldLeft(None: Option[Bounds]) { (bounds, value) =>
        bounds match {
          case None => Some(Bounds(value, value))
          case Some(Bounds(min, max)) =>
            Some(Bounds(math.min(min, value), math.max(max, value)))
        }
      }
    }

    def widest(bounds: Seq[Option[Bounds]]): Option[Bounds] =
      bounds.flatten.foldLeft(None: Option[Bounds]) { (acc, curr) =>
        if (acc.isEmpty) Some(curr)
        else
          Some(Bounds(math.min(acc.get.min, curr.min), math.max(acc.get.max, curr.max)))
      }
  }

  private val normalConstant = 1.0 / math.sqrt(2 * math.Pi)

  // with sigma = 1.0 and mu = 0, like R's dnorm.
  private[numeric] def probabilityDensityInNormal(x: Double): Double =
    normalConstant * math.exp(-math.pow(x, 2) / 2)

  // quantiles using linear interpolation.
  private[numeric] def quantile(data: Seq[Double], quantiles: Seq[Double]): Seq[Double] = {
    if (data.isEmpty) Seq.fill(quantiles.length)(Double.NaN)
    else {
      val length = data.length
      val sorted = data.sorted
      for {
        quantile <- quantiles
        _ = require(quantile >= 0.0 && quantile <= 1.0)
        index = quantile * (length - 1)
        result = {
          if (index >= length - 1) sorted.last
          else {
            val lower = sorted(math.floor(index).toInt)
            val upper = sorted(math.ceil(index).toInt)
            lower + (upper - lower) * (index - math.floor(index))
          }
        }
      } yield result
    }
  }

  private[numeric] def mean(data: Seq[Double]): Double = data.sum / data.length

  private[numeric] def variance(data: Seq[Double]): Double = {
    val _mean = mean(data)
    data.map(x => math.pow(x - _mean, 2)).sum / (data.length - 1)
  }

  private[numeric] def standardDeviation(data: Seq[Double]): Double = math.sqrt(variance(data))

}
