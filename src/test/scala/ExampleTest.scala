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
  override lazy val tests = List(
    property("distinct doesn't change list", propDistinct),
    property("reverse", testReverse)
  )

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

  // In the above, `intGen` and `listGen` are example generators, which is defined using `Gen.int` and `Gen.list` helper functions. For example, `intGen` will produce an integer from 0 to 100, distributed linearly.
  def propDistinct: PropertyT[Result] = listGen.forAll.map { xs =>
    Result.assert(xs.distinct == xs)
  }

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


  // more test cases
    


       

}
