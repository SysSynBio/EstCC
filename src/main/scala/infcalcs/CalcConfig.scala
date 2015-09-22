package infcalcs

import java.util.Date

import Containers.{Parameters, Weight}
import EstimateCC._
import EstimateMI._
import TreeDef.Tree
import cern.jet.random.engine.MersenneTwister

/**
 * Created by ryansuderman on 9/9/15.
 */
object CalcConfig {
  def apply(p: Parameters, r: MersenneTwister) = new CalcConfig(p, r)

  def apply(r: MersenneTwister) = new CalcConfig(InfConfig.defaultParameters, r)

  def apply(p: Parameters) = new CalcConfig(p, new MersenneTwister(new Date))

  def apply() = new CalcConfig(InfConfig.defaultParameters, new MersenneTwister(new Date))
}

class CalcConfig(val parameters: Parameters, val rEngine: MersenneTwister) {

  def this(r: MersenneTwister) = this(InfConfig.defaultParameters, r)

  def this(p: Parameters) = this(p, new MersenneTwister(new Date))

  def this() = this(InfConfig.defaultParameters, new MersenneTwister(new Date))

  //produces new CalcConfig with fresh MT instance (for actors)
  def resetMtEngine(seed: Int) = CalcConfig(parameters, new MersenneTwister(seed))

  def resetMtEngine() = CalcConfig(parameters, new MersenneTwister(new Date))

  // These parameters are set as variables not values (val not val) so that
  // they can be set during test execution
  lazy val listParameters = parameters.listParams
  lazy val numParameters = parameters.numParams
  lazy val stringParameters = parameters.stringParams
  lazy val srParameters = parameters.sigRespParams

  // Load data given pair of columns
  lazy val sigCols = listParameters("signalColumns").toVector map (_.toInt)
  lazy val respCols = listParameters("responseColumns").toVector map (_.toInt)

  lazy val sigDim = sigCols.length
  lazy val respDim = respCols.length

  assert(sigDim == listParameters("sigBinSpacing").length)
  assert(respDim == listParameters("respBinSpacing").length)

  assert(srParameters("responseValues").isDefined || !listParameters("respBinSpacing").isEmpty)
  assert(srParameters("signalValues").isDefined || !listParameters("sigBinSpacing").isEmpty)

  // Determine number of response bins if values not specified
  lazy val initResponseBins: NTuple[Int] = srParameters("responseValues") match {
    case None => listParameters("respBinSpacing").toVector map (_.toInt)
    case Some(x) => {
      val xt = x.transpose
      assert(xt.length == respDim)
      xt map (_.toSet.size)
    }
  }

  // Determine number of signal bins if values not specified
  lazy val initSignalBins: NTuple[Int] = srParameters("signalValues") match {
    case None => listParameters("sigBinSpacing").toVector map (_.toInt)
    case Some(x) => {
      val xt = x.transpose
      assert(xt.length == sigDim)
      xt map (_.toSet.size)
    }
  }

  lazy val initBinTuples = (initSignalBins, initResponseBins)

  //confirm that bin dimensions correspond to data dimensions
  //  assert((signalBins map (x => x.length)).foldLeft(true)((x,y) => x && y== sigDim))
  //  assert((responseBins map (x => x.length)).foldLeft(true)((x,y) => x && y== respDim))

  lazy val fracList = ({
    for {
      f <- listParameters("sampleFractions")
      n <- 0 until numParameters("repsPerFraction").toInt
    } yield f
  } :+ 1.0).toVector

  lazy val outF = if (stringParameters("filePrefix").trim.isEmpty) None else Some(stringParameters("filePrefix"))

}