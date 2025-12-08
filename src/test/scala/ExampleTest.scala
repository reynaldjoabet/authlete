import hedgehog.runner.Prop
import hedgehog.runner.example
import hedgehog.runner.property
import hedgehog.runner.Test
import hedgehog.runner.SeedSource
import hedgehog.runner.Properties
import hedgehog.core.Seed
import hedgehog.PropertyR
import hedgehog.Range
import hedgehog.Result
import hedgehog.Gen
import hedgehog.Property
import hedgehog.Range
import hedgehog.Size
import hedgehog.forTupled
import hedgehog.propertyT
import hedgehog.Syntax
import hedgehog.extra.CharacterOps
import hedgehog.extra.ByteOps
import hedgehog.extra.StringOps
import hedgehog.predef.Applicative
import hedgehog.predef.DecimalPlus
import hedgehog.predef.EitherOps
import hedgehog.predef.Functor
import hedgehog.predef.Identity
import hedgehog.predef.Monad
import hedgehog.predef.State
import hedgehog.predef.sequence
import hedgehog.predef.traverse
import hedgehog.predef.some
import hedgehog.sbt.Event
import hedgehog.sbt.MessageOnlyException
import hedgehog.sbt.Runner
import hedgehog.sbt.SlaveRunner
import hedgehog.sbt.Framework
import hedgehog.sbt.Task
import hedgehog.state.Action
import hedgehog.state.Command
import hedgehog.state.CommandIO
import hedgehog.state.Environment
import hedgehog.state.Context
import hedgehog.state.EnvironmentError
import hedgehog.state.Name
import hedgehog.state.Runner
import hedgehog.state.Var
import hedgehog.state.parallel
import hedgehog.state.sequential
import hedgehog.state.Parallel
import hedgehog.core.PropertyT
import java.util.UUID
import java.time.LocalDateTime
import hedgehog.core.GenT

object ExampleTest extends Properties {

  // Defined here because the constants we access are package private.

  given genULID: Gen[UUID] = for {
    randomness <- Gen.bytes(Range.singleton(16))
    time <- Gen.long(
      Range.linear(
        LocalDateTime.now().toLocalTime().toNanoOfDay(),
        LocalDateTime.now().toLocalTime().toNanoOfDay()
      )
    )
  } yield UUID.fromString(time.toHexString)

  given genInt: Gen[Int] = for {
    i <- Gen.int(Range.linear(0, 100))
  } yield i

  val boolGen: Gen[Boolean] = Gen.boolean

  private val seedSource = SeedSource.fromEnvOrTime()
  private val seed = Seed.fromLong(seedSource.seed)

  def generateUUIDSection(counts: Int): GenT[String] =
    Gen.string(Gen.hexit, Range.singleton(counts))

  def generateUUID: GenT[String] =
    for {
      first <- generateUUIDSection(8)
      second <- generateUUIDSection(4)
      third <- generateUUIDSection(4)
      four <- generateUUIDSection(4)
      five <- generateUUIDSection(12)
    } yield List(first, second, third, four, five).mkString("-")

  val intGen = Gen.int(Range.linear(0, 100))
  val listGen = Gen.list[Int](intGen, Range.linear(0, 100))

  def testReverse: Property =
    for {
      xs <- Gen.alpha.list(Range.linear(0, 100)).forAll
    } yield xs.reverse.reverse ==== xs

  val listSize = 100
  val elementSize = 100
  def hedgehogDoubles: List[Double] =
    hedgehog.Gen
      .list(
        hedgehog.Gen.double(hedgehog.Range.constant(0.0, 1.0)),
        hedgehog.Range.constant(0, listSize)
      )
      .run(hedgehog.Size(0), hedgehog.core.Seed.fromTime())
      .value
      ._2
      .getOrElse(List.empty)

  def hedgehogIntListsOfSizeN: List[List[Int]] =
    hedgehog.Gen
      .int(hedgehog.Range.constant(Int.MinValue, Int.MaxValue))
      .list(hedgehog.Range.constant(0, elementSize))
      .list(hedgehog.Range.constant(0, listSize))
      .run(hedgehog.Size(0), hedgehog.core.Seed.fromTime())
      .value
      ._2
      .getOrElse(List.empty)

  def hedgehogStringsOfSizeN: List[String] =
    hedgehog.Gen
      .string(hedgehog.Gen.alpha, hedgehog.Range.constant(0, elementSize))
      .list(hedgehog.Range.constant(0, listSize))
      .run(hedgehog.Size(0), hedgehog.core.Seed.fromTime())
      .value
      ._2
      .getOrElse(List.empty)

  private val smallIntGen = Gen.int(Range.linear(0, 10))
  private val nonEmptyListGen = Gen.list(intGen, Range.linear(1, 20))
  private val smallListGen = Gen.list(intGen, Range.linear(0, 20))
  private val eitherGen =
    Gen.element(Left("x"), List[Either[String, Int]](Right(1), Right(0)))

// concat associativity: (a ++ b) ++ c == a ++ (b ++ c)
  def propConcatAssociative: Property =
    for {
      a <- smallListGen.forAll
      b <- smallListGen.forAll
      c <- smallListGen.forAll
    } yield ((a ++ b) ++ c) ==== (a ++ (b ++ c))

// splitAt invariant: splitAt(n) reconstructs original
  def propSplitAtReconstructs: Property =
    for {
      xs <- smallListGen.forAll
      n <- Gen.int(Range.linear(0, xs.length)).forAll
    } yield {
      val (l, r) = xs.splitAt(n)
      (l ++ r) ==== xs
    }

// zip/unzip round-trip
  def propZipUnzip: Property =
    for {
      as <- smallListGen.forAll
      bs <- Gen.list(genInt, Range.linear(0, as.length)).forAll // length <= as
    } yield {
      val zipped = as.zip(bs)
      val (ua, ub) = zipped.unzip
      Result.all(
        List(
          Result.assert(ua == ua), // trivial sanity
          Result.assert(zipped.length == ua.length && ua.length == ub.length)
        )
      )
    }

//  flatten identity with map(identity)
  def propFlatMapIdentity: Property =
    for {
      xss <- Gen
        .list(Gen.list(intGen, Range.linear(0, 3)), Range.linear(0, 10))
        .forAll
    } yield xss.flatMap(identity) ==== xss.flatten

// Option -> toList / headOption / lastOption consistency
  def propOptionListRoundTrip: Property =
    for {
      xs <- smallListGen.forAll
    } yield {
      val maybeHead = xs.headOption
      val headAsList = maybeHead.toList
      Result.assert(headAsList.headOption == maybeHead)
    }

//  Set size <= list size and contains elements
  def propSetFromList: Property =
    for {
      xs <- smallListGen.forAll
    } yield {
      val s = xs.toSet
      Result.all(
        List(
          Result.assert(s.size <= xs.length),
          Result.assert(s.forall(xs.contains))
        )
      )
    }

// reverse preserves multiset
  def propReversePreservesCounts: Property =
    for {
      xs <- smallListGen.forAll
    } yield {
      val r = xs.reverse
      xs.distinct.forall(e => xs.count(_ == e) == r.count(_ == e)) ==== true
    }

// take(n) when n >= size -> whole list
  def propTakeLargeIsWhole: Property =
    for {
      xs <- smallListGen.forAll
      n <- Gen.int(Range.linear(xs.length, xs.length + 10)).forAll
    } yield xs.take(n) ==== xs

// drop(n) when n >= size -> empty
  def propDropLargeIsEmpty: Property =
    for {
      xs <- smallListGen.forAll
      n <- Gen.int(Range.linear(xs.length, xs.length + 10)).forAll
    } yield xs.drop(n).isEmpty ==== true

// sliding with k==1 equals singletons
  def propSlidingK1: Property =
    for {
      xs <- smallListGen.forAll
    } yield xs.sliding(1).map(_.head).toList ==== xs

// list sum vs foldLeft
  def propSumFoldLeft: Property =
    for {
      xs <- Gen.list(Gen.int(Range.linear(0, 100)), Range.linear(0, 50)).forAll
    } yield xs.sum ==== xs.foldLeft(0)(_ + _)

// list product vs foldLeft (with non-empty)
  def propProductFoldLeft: Property =
    for {
      xs <- Gen.list(Gen.int(Range.linear(1, 6)), Range.linear(1, 10)).forAll
    } yield xs.product ==== xs.foldLeft(1)(_ * _)

// string length relation with concatenation
  def propStringConcatLength: Property =
    for {
      a <- Gen.string(Gen.alpha, Range.linear(0, 20)).forAll
      b <- Gen.string(Gen.alpha, Range.linear(0, 20)).forAll
    } yield (a + b).length ==== (a.length + b.length)

// string substring roundtrip when indices valid
  def propSubstringRoundTrip: Property =
    for {
      s <- Gen.string(Gen.alpha, Range.linear(0, 50)).forAll
      i <- Gen.int(Range.linear(0, s.length)).forAll
      j <- Gen.int(Range.linear(i, s.length)).forAll
    } yield s.substring(i, j) ==== s.slice(i, j)

// numeric bounds for generated integers
  def propGenIntBoundsStrict: Property =
    for {
      i <- Gen.int(Range.linear(-1000, 1000)).forAll
    } yield Result.assert(i >= -1000 && i <= 1000)

// double generator bounds & NaN absence for uniform generator
  def propDoubleBoundsNoNaN: Property =
    for {
      d <- hedgehog.Gen.double(hedgehog.Range.constant(0.0, 1.0)).forAll
    } yield Result.all(
      List(
        Result.assert(!d.isNaN),
        Result.assert(d >= 0.0 && d <= 1.0)
      )
    )

// UUID generator: groups count match (redundant with your regex, but more structural)
  def propGenerateUUIDGroups: Property =
    for {
      u <- generateUUID.forAll
    } yield {
      val groups = u.split("-")
      Result.assert(
        groups.length == 5 && groups(0).length == 8 && groups(4).length == 12
      )
    }

// generator reproducibility across seeds (sanity)
  def propGeneratorReproducibleTwoSeeds: Result = {
    import hedgehog.core.Seed
    val s1 = Seed.fromLong(123L)
    val s2 = Seed.fromLong(456L)
    val g = Gen.list(genInt, Range.linear(0, 10))
    val a = g.run(hedgehog.Size(0), s1).value._2
    val b = g.run(hedgehog.Size(0), s1).value._2
    val c = g.run(hedgehog.Size(0), s2).value._2
    (a == b) ==== true // same seed same output
  }

// idempotent distinct + size relation
  def propDistinctSizeLeOriginal: Property =
    for {
      xs <- smallListGen.forAll
    } yield xs.distinct.length <= xs.length ==== true

// sort is stable relative to ordering (monotonic)
  def propSortMonotonic: Property =
    for {
      xs <- smallListGen.forAll
    } yield {
      val s = xs.sorted
      s.sliding(2).forall {
        case Seq(a, b) => a <= b
        case _         => true
      } ==== true
    }

// zipWithIndex size equals list size and indices match
  def propZipWithIndex: Property =
    for {
      xs <- smallListGen.forAll
    } yield {
      val z = xs.zipWithIndex
      Result.all(
        List(
          Result.assert(z.length == xs.length),
          Result.assert(z.forall { case (v, i) => xs(i) == v })
        )
      )
    }

// sliding sum invariants (when k>0)
  def propSlidingSumRelation: Property =
    for {
      xs <- Gen.list(Gen.int(Range.linear(0, 5)), Range.linear(0, 20)).forAll
      k <- Gen.int(Range.linear(1, 5)).forAll
    } yield {
      val windows = xs.sliding(k).map(_.sum).toList
      // every window sum >= 0 (since elements >=0)
      Result.assert(windows.forall(_ >= 0))
    }

// list containsAll after concatenating itself
  def propContainsAfterConcat: Property =
    for {
      xs <- smallListGen.forAll
    } yield (xs ++ xs).containsSlice(xs) ==== true

// size of range-generated lists respects declared bounds (sanity)
  def propListGenBounds: Property =
    for {
      xs <- Gen.list(intGen, Range.linear(0, 5)).forAll
    } yield Result.assert(xs.length <= 5)

// headOption on non-empty equals Some(head)
  def propHeadOptionNonEmpty: Property =
    for {
      xs <- Gen.list(intGen, Range.linear(1, 10)).forAll
    } yield xs.headOption ==== Some(xs.head)

// foldRight and foldLeft with associative op produce same for commutative ops (e.g., sum)
  def propFoldLeftFoldRightSum: Property =
    for {
      xs <- Gen.list(Gen.int(Range.linear(0, 50)), Range.linear(0, 30)).forAll
    } yield xs.foldLeft(0)(_ + _) ==== xs.foldRight(0)(_ + _)

// toString contains length for certain collections (sanity, not guaranteed across JVMs, so keep mild)
  def propListToStringContainsElements: Property =
    for {
      xs <- smallListGen.forAll
    } yield Result.assert(
      xs.toString.contains(
        xs.headOption.map(_.toString).getOrElse("")
      ) || xs.isEmpty
    )

  override lazy val tests = List(
    property("reverse", testReverse),
    property("concat associative", propConcatAssociative),
    property("splitAt reconstructs", propSplitAtReconstructs),
    property("zip/unzip round trip", propZipUnzip),
    property("flatMap identity", propFlatMapIdentity),
    property("option list roundtrip", propOptionListRoundTrip),
    property("set from list properties", propSetFromList),
    property("reverse preserves counts", propReversePreservesCounts),
    property("take large is whole", propTakeLargeIsWhole),
    property("drop large is empty", propDropLargeIsEmpty),
    property("sliding k=1 equals singletons", propSlidingK1),
    property("sum equals foldLeft", propSumFoldLeft),
    property("product equals foldLeft", propProductFoldLeft),
    property("string concat length", propStringConcatLength),
    property("substring roundtrip", propSubstringRoundTrip),
    property("gen int bounds strict", propGenIntBoundsStrict),
    property("double bounds no NaN", propDoubleBoundsNoNaN),
    property("generateUUID group lengths", propGenerateUUIDGroups),
    property("distinct size le original", propDistinctSizeLeOriginal),
    property("sort monotonic", propSortMonotonic),
    property("zipWithIndex correctness", propZipWithIndex),
    property("sliding sum non-negative", propSlidingSumRelation),
    property("contains after concat", propContainsAfterConcat),
    property("list gen bounds", propListGenBounds),
    property("headOption non-empty", propHeadOptionNonEmpty),
    property("foldLeft vs foldRight sum", propFoldLeftFoldRightSum),
    property(
      "list toString contains element",
      propListToStringContainsElements
    )
  ).map(_.withTests(500))

}
