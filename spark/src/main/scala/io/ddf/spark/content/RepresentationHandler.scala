/**
 *
 */
package io.ddf.spark.content

import io.ddf._
import io.ddf.content.{Representation, RepresentationHandler => RH}
import io.ddf.spark.SparkDDF
import io.ddf.spark.content.RepresentationHandler._
import io.ddf.types.TupleMatrixVector
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.recommendation.Rating
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.catalyst.expressions.Row
import org.rosuda.REngine._

import scala.collection.JavaConversions._
import scala.reflect.Manifest

/**
 * RDD-based SparkRepresentationHandler
 *
 */

class RepresentationHandler(mDDF: DDF) extends RH(mDDF) {
  /**
   * Initialize RepresentationGraph
   */
  this.addConvertFunction(RDD_ARR_DOUBLE, RDD_ARR_OBJECT, new ArrayDouble2ArrayObject(this.mDDF))
  this.addConvertFunction(RDD_ARR_DOUBLE, RDD_LABELED_POINT, new ArrayDouble2LabeledPoint(this.mDDF))
  this.addConvertFunction(RDD_ARR_OBJECT, RDD_ARR_DOUBLE, new ArrayObject2ArrayDouble(this.mDDF))

  this.addConvertFunction(RDD_REXP, RDD_ARR_OBJECT, new REXP2ArrayObject(this.mDDF))
  this.addConvertFunction(RDD_ROW, RDD_ARR_OBJECT, new RDDRow2ArrayObject(this.mDDF))
  this.addConvertFunction(RDD_ROW, RDD_ARR_DOUBLE, new RDDRow2ArrayDouble(this.mDDF))
  this.addConvertFunction(RDD_ARR_DOUBLE, RDD_VECTOR, new ArrayDouble2Vector(this.mDDF))
  this.addConvertFunction(RDD_ARR_OBJECT, DATAFRAME, new ArrayObject2DataFrame(this.mDDF))
  this.addConvertFunction(RDD_ROW, RDD_REXP, new RDDROW2REXP(this.mDDF))
  this.addConvertFunction(DATAFRAME, RDD_MATRIX_VECTOR, new DataFrame2MatrixVector(this.mDDF))
  this.addConvertFunction(RDD_ROW, DATAFRAME, new Row2DataFrame(this.mDDF))
  this.addConvertFunction(DATAFRAME, RDD_ROW, new DataFrame2RDDRow(this.mDDF))
  this.addConvertFunction(RDD_ROW, RDD_RATING, new Row2Rating(this.mDDF))
  this.addConvertFunction(RDD_INT, RDD_ROW, new RDDInt2RDDRow(this.mDDF))
  this.addConvertFunction(RDD_DOUBLE, RDD_ROW, new RDDDouble2RDDRow(this.mDDF))

  override def getDefaultDataType: Array[Class[_]] = Array(classOf[RDD[_]], classOf[Array[Object]])

  /**
   * Sets a new and unique representation for our {@link DDF}, clearing out any existing ones
   */
  def set[T](data: RDD[T])(implicit m: Manifest[T]) = {
    this.reset
    this.add(data)
  }

  /**
   * Adds a new and unique representation for our {@link DDF}, keeping any existing ones
   */
  def add[T](data: RDD[T])(implicit m: Manifest[T]): Unit = this.add(data, classOf[RDD[_]], m.erasure)

  private def forAllReps[T](f: RDD[_] ⇒ Any) {
    mReps.foreach {
      kv ⇒ if (kv._2 != null) {
        kv._2.getValue match {
          case rdd: RDD[_] => f(rdd)
          case _ =>
        }
      } //f(kv._2.asInstanceOf[RDD[_]])
    }
  }

  /**
   * Cache SchemaRDD in memory
   **/
  override def cache(isLazy: Boolean) = {
    val ddf = this.getDDF.asInstanceOf[SparkDDF]
    ddf.saveAsTable()
    val dataFrame = ddf.getRepresentationHandler.get(classOf[DataFrame]).asInstanceOf[DataFrame]
    dataFrame.persist()
    if (!isLazy) {
      dataFrame.count()
    }
  }

  override def cacheAll = {
    forAllReps({
      rdd: RDD[_] ⇒
        if (rdd != null) {
          mLog.info(this.getClass() + ": Persisting " + rdd)
          rdd.persist
        }
    })
  }

  override def uncacheAll = {
    forAllReps({
      rdd: RDD[_] ⇒
        if (rdd != null) {
          mLog.info(this.getClass() + ": Unpersisting " + rdd.toString())
          rdd.unpersist(false)
        }
    })
    
    val dataFrame = this.get(classOf[DataFrame]).asInstanceOf[DataFrame]
    if (dataFrame != null) {
      dataFrame.unpersist()
    }
  }
}

object RepresentationHandler {

  /**
   * Supported Representations
   */
  val RDD_ARR_DOUBLE = new Representation(classOf[RDD[_]], classOf[Array[Double]])
  val RDD_ARR_OBJECT = new Representation(classOf[RDD[_]], classOf[Array[Object]])
  val RDD_LABELED_POINT = new Representation(classOf[RDD[_]], classOf[LabeledPoint])
  val RDD_MATRIX_VECTOR = new Representation(classOf[RDD[_]], classOf[TupleMatrixVector])
  val RDD_REXP = new Representation(classOf[RDD[_]], classOf[REXP])
  val DATAFRAME = new Representation(classOf[DataFrame])
  val RDD_ROW = new Representation(classOf[RDD[_]], classOf[Row])
  val RDD_VECTOR = new Representation(classOf[RDD[_]], classOf[Vector])
  val RDD_RATING = new Representation(classOf[RDD[_]], classOf[Rating])
  val RDD_DOUBLE = new Representation(classOf[RDD[_]], classOf[Double])
  val RDD_INT = new Representation(classOf[RDD[_]], classOf[Int])
}
