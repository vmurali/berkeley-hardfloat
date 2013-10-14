package hardfloat

import Chisel._
import Node._
import scala.sys.process._

class FMA(sigWidth: Int, expWidth: Int)  extends Module {
  val io = new Bundle {
    val a = Bits(INPUT, sigWidth + expWidth)
    val b = Bits(INPUT, sigWidth + expWidth)
    val c = Bits(INPUT, sigWidth + expWidth)
    val out = Bits(OUTPUT, sigWidth + expWidth)
  }

  val fma = Module(new mulAddSubRecodedFloatN(sigWidth, expWidth))
  fma.io.op := UInt(0)
  fma.io.a := floatNToRecodedFloatN(io.a, sigWidth, expWidth)
  fma.io.b := floatNToRecodedFloatN(io.b, sigWidth, expWidth)
  fma.io.c := floatNToRecodedFloatN(io.c, sigWidth, expWidth)
  fma.io.roundingMode := UInt(0)
  io.out := recodedFloatNToFloatN(fma.io.out, sigWidth, expWidth)
}

class FMATests(c: FMA) extends Tester(c, Array(c.io)) {
  def d2i(x: Double) = java.lang.Double.doubleToLongBits(x)
  def i2d(x: Long) = java.lang.Double.longBitsToDouble(x)
  def gen = util.Random.nextDouble
  val vars = new collection.mutable.HashMap[Node, Node]()

  def canonicalize(x: BigInt) = {
    if (i2d(x.toLong).isNaN)
      (BigInt(1) << 64) - 1
    else
      x
  }

  var ok: Boolean = true

  def testOne(s: String) = if (ok) {
    val t = s.split(' ')
    vars(c.io.a) = Bits(BigInt(t(0), 16))
    vars(c.io.b) = Bits(BigInt(t(1), 16))
    vars(c.io.c) = Bits(BigInt(t(2), 16))
    vars(c.io.out) = Bits(canonicalize(BigInt(t(3), 16)))
    ok = step(vars)
  }

  def testAll: Boolean = {
    val logger = ProcessLogger(testOne, _ => ())
    Seq("bash", "-c", "testfloat_gen -rnear_even -n 6133248 f64_mulAdd | head -n10000") ! logger
    ok
  }
  defTests {
    testAll
  }
}

object FMATest {
  def main(args: Array[String]): Unit = {
    chiselMainTest(args ++ Array("--compile", "--test",  "--genHarness", "--debug", "--vcd"),
                   () => Module(new FMA(52, 12))) { c => new FMATests(c) }
  }
}
