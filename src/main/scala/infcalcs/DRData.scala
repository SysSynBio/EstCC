package infcalcs
import TreeDef._
import annotation.tailrec
import scala.collection.mutable.HashMap

/**
 * Structure for input-output (dose-response) data.
 *
 *  Both the signal and response points are entered into Vectors whose
 *  elements are themselves Vectors of length N, where N is the number
 *  of distinct signal or response types of the system (i.e. given a
 *  channel which simultaneously takes signal A and signal B, the input
 *  Vector would have elements of length 2.)
 *
 *  @param sig 2D vector of signal values
 *  @param resp 2D vector of response values
 */
class DRData(val sig: Vector[NTuple[Double]], val resp: Vector[NTuple[Double]]) {
  Predef.assert(sig.length == resp.length)
  Predef.assert(checkSize(sig) == 1 && checkSize(resp) == 1)

  lazy val zippedVals = (sig, resp).zipped.toSeq
  
  lazy val sigVals = EstCC.srParameters("signalValues") != None
  lazy val respVals = EstCC.srParameters("responseValues") != None
  
  val sigDim = dim(sig)
  val respDim = dim(resp)
  
  var exSigDelims: HashMap[NTuple[Int], NTuple[Tree]] = HashMap()
  var exRespDelims: HashMap[NTuple[Int], NTuple[Tree]] = HashMap()
  var exSigKeys: HashMap[NTuple[Int], Map[NTuple[Int],Int]] = HashMap()
  var exRespKeys: HashMap[NTuple[Int], Map[NTuple[Int],Int]] = HashMap()

  lazy val numObs: Int = sig.length
  lazy val isEmpty: Boolean = numObs == 0

  /**
   * Confirms that each data point has the same dimensionality
   * 
   * @param d Vector of n-tuple data points
   * @return number of different dimensioned points (should be 1)
   */
  private def checkSize(d: Vector[NTuple[Double]]): Int =
    (d map (_.length)).toSet.size

  /**
   * Checks the dimensionality of the data assuming each point has
   * the same number of dimensions (see [[checkSize]])
   * 
   * @param d Vector of n-tuple data points
   * @return number of dimensions
   */
  private def dim(d: Vector[NTuple[Double]]): Int = d.head.length

  /**
   * Calculates bin delimiters for arbitrary dimensioned data points
   *
   * @note calling this function when a discrete value set is provided for the
   * data results in delimiter calculation from the set of values, not the 
   * provided bin number 
   * 
   * @param valuesPresent determines if explicit value sets are present for data
   * @param data data points
   * @param numBins number of bins per dimension
   * @return Vector of trees ([[TreeDef.Tree]])
   */
  private def getDelims(
    valuesPresent: Boolean,
    data: Vector[NTuple[Double]],
    numBins: NTuple[Int]): NTuple[Tree] =

    if (valuesPresent)
      (EstCC.srParameters("signalValues").get.transpose map (_.toSet.toList) map 
        (x => TreeDef.buildTree(TreeDef.buildOrderedNodeList(x)))).toVector
    else {
      val dt = data.transpose
      ((0 until dim(data)) map (x => CTBuild.getBinDelims(dt(x),numBins(x)))).toVector
    }

  /** 
   *  Calculates key for vector of bin-delimiting trees
   *  
   *  @param trees vector of partition trees (corresponding to ordered pair data
   *  points)
   *
   *  @return vector of bin index vectors
   *  
   */
  private def calcBinKey(
    trees: NTuple[Tree]): Map[NTuple[Int],Int] = {

    val dimLengths = trees map (_.toList.length)
    val vtups = CTBuild.keyFromDimLengths(dimLengths, Vector(Vector()))
    ((0 until vtups.length) map (x => (vtups(x),x))).toMap
  }

  /**
   * Determines the delimiters for a particular set of data points given a 
   * specified number of bins
   * 
   * @param v boolean to check if values are specified (overrides bin-based 
   * delimiting)
   * @param data Vector of n-dimensional data points
   * @param h HashMap of previously calculated delimiters for efficiency
   * @param nb n-dimensional vector of bins
   * @return n-dimensional vector of bin-delimiters (Vector of [[TreeDef.Tree]])
   */
  private def delims(
    v: Boolean,
    data: Vector[NTuple[Double]],
    h: HashMap[NTuple[Int], NTuple[Tree]],
    nb: NTuple[Int]): NTuple[Tree] =
    if (h contains nb) h(nb)
    else {
      val d = getDelims(v, data, nb)
      h update (nb, d)
      d
    }

  /**
   * Constructs a key for building a 2-dimensional contingency table from 
   * an n-dimensional data set. This function constructs a Map from n-dimensinal
   * bin indices to 1-dimensional bin indices
   * 
   * @param h HashMap of previously calculated keys (similar to [[delims]])
   * @param trees n-dimensional bin-delimiting trees for data
   * @param nb n-dimensional vector of bin numbers
   * @return mapping of n-dimensional indices to 1-dimensional indices  
   */
  private def keys(
    h: HashMap[NTuple[Int], Map[NTuple[Int],Int]],
    trees: NTuple[Tree],
    nb: NTuple[Int]): Map[NTuple[Int],Int] =
    if (h contains nb) h(nb)
    else {
      val k = calcBinKey(trees)
      h update (nb, k)
      k
    }

  /**
   * Implements [[delims]] for signal space
   * 
   * @param numBins n-dimensional vector of bin numbers
   * @return n-dimensional vector of bin-delimiting [[TreeDef.Tree]]s
   */
  def sigDelims(numBins: NTuple[Int]): NTuple[Tree] =
    delims(sigVals, sig, exSigDelims, numBins)
    
  /**
   * Implements [[delims]] for response space
   * 
   * @param numBins n-dimensional vector of bin numbers
   * @return n-dimensional vector of bin-delimiting [[TreeDef.Tree]]s
   */
  def respDelims(numBins: NTuple[Int]): NTuple[Tree] =
    delims(respVals, resp, exRespDelims, numBins)

  /**
   * Implements [[keys]] for response space
   * 
   * @param numBins n-dimensional vector of bin numbers
   * @return mapping of n-dimensional indices to 1-dimensional indices
   */
  def sigKey(numBins: NTuple[Int]): Map[NTuple[Int],Int] =
    keys(exSigKeys, sigDelims(numBins), numBins)
  
  /**
   * Implements [[keys]] for response space
   * 
   * @param numBins n-dimensional vector of bin numbers
   * @return mapping of n-dimensional indices to 1-dimensional indices
   */
  def respKey(numBins: NTuple[Int]): Map[NTuple[Int],Int] =
    keys(exRespKeys, respDelims(numBins), numBins)
    
  /**
   * Writes data to stdout
   */
  override def toString() = 
    ((0 until sig.length) map { x=> 
      s"${sig(x)}\t${resp(x)}\n"
    }).mkString
  
  /**
   * Writes data to file
   * 
   * @param f file name
   */
  def toFile(f: String) = {
    val writer =
      new java.io.BufferedWriter(new java.io.FileWriter(new java.io.File(f)))
    for (i <- (0 until sig.length).toList) {
      writer.write(s"${sig(i).mkString(" ")}\t${resp(i).mkString(" ")}")
      writer.newLine()
    }
    writer.flush()
    writer.close()
  }
    
    
}