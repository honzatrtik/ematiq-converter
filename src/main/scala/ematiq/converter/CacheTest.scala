package ematiq.converter

import ematiq.converter.domain.*
import ematiq.converter.infrastructure.IOCache
import cats.effect.*
import cats.implicits.*

import scala.concurrent.duration.*
import org.joda.money.CurrencyUnit
import java.time.LocalDate

object CacheTest extends IOApp {

  val queryEur = ExchangeRateQuery(CurrencyUnit.EUR, CurrencyUnit.EUR, LocalDate.now)
  val queryCad = ExchangeRateQuery(CurrencyUnit.CAD, CurrencyUnit.EUR, LocalDate.now)
  val queryChf = ExchangeRateQuery(CurrencyUnit.CHF, CurrencyUnit.EUR, LocalDate.now)
  val queryJpy = ExchangeRateQuery(CurrencyUnit.JPY, CurrencyUnit.EUR, LocalDate.now)

  def computeAfter(duration: FiniteDuration): ExchangeRateQuery => IO[ExchangeRate] = 
    _ => IO.sleep(duration) *> ExchangeRate(1).pure[IO]
  override def run(args: List[String]): IO[ExitCode] = for {
    cache <- IOCache(6.seconds)
    fiberEur1 <- cache.get(queryEur, computeAfter(5.seconds)).start
    fiberEur2 <- cache.get(queryEur, computeAfter(10.seconds)).start // This wont be called
    fiberCad <- cache.get(queryCad, computeAfter(1.seconds)).start
    fiberChf <- cache.get(queryChf, computeAfter(3.seconds)).start
    fiberJpy <- cache.get(queryJpy, computeAfter(2.seconds)).start
    _ <- Vector(fiberEur1, fiberEur2, fiberCad, fiberChf, fiberJpy).parTraverse(_.join)
    _ <- IO.println("Sleeping for 7 seconds...") *> IO.sleep(7.seconds) // Cache will be invalidated
    _ <- cache.get(queryEur, computeAfter(0.seconds)) // This will be calculated
  } yield ExitCode.Success
}
