package webecho.tools

import com.fasterxml.uuid.Generators

import java.util.UUID
import scala.util.Try

object UniqueIdentifiers {
  private val timeBasedGenerator   = Generators.timeBasedEpochRandomGenerator
  private val randomBasedGenerator = Generators.randomBasedGenerator

  def timedUUID(): UUID  = timeBasedGenerator.generate()
  def randomUUID(): UUID = randomBasedGenerator.generate()

  def getTime(uuid: UUID): Try[Long] = Try(uuid.timestamp())

  def fromString(uuid: String): Try[UUID] = Try(UUID.fromString(uuid))
}
