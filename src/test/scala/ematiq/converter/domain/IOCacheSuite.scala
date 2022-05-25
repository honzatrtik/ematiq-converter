package ematiq.converter.domain

import cats.effect.*
import cats.effect.kernel.Ref
import cats.implicits.*
import ematiq.converter.infrastructure.IOCache
import munit.CatsEffectSuite
import org.joda.money.CurrencyUnit

import java.time.LocalDate
import scala.concurrent.duration.*

class IOCacheSuite extends CatsEffectSuite {
  
  test("Cache should compute key for the first time and return cached value on consequent calls") {

    val query = ExchangeRateQuery(CurrencyUnit.EUR, CurrencyUnit.EUR, LocalDate.now)
    val rate = ExchangeRate(1)

    for {
      counter <- Ref.of[IO, Int](0)
      cache <- IOCache(10.hours)
      _ <- cache.get(query, _ => counter.update(_ + 1) *> rate.pure[IO])
      _ <- cache.get(query, _ => counter.update(_ + 1) *> rate.pure[IO])
      _ <- cache.get(query, _ => counter.update(_ + 1) *> rate.pure[IO])
      _ <- cache.get(query, _ => counter.update(_ + 1) *> rate.pure[IO])
      count <- counter.get
    } yield assertEquals(count, 1)

  }

  test("Cache should wait if value for required key is already being calculated") {

    val query = ExchangeRateQuery(CurrencyUnit.EUR, CurrencyUnit.EUR, LocalDate.now)
    val rate = ExchangeRate(1)

    for {
      counter <- Ref.of[IO, Int](0)
      cache <- IOCache(10.hours)
      fiber1 <- cache.get(query, _ => IO.sleep(5.seconds) *> rate.pure[IO]).start // This will calculate value after 5 seconds
      fiber2 <- cache.get(query, _ => counter.update(_ + 1) *> rate.pure[IO]).start
      rate1 <- fiber1.join
      rate2 <- fiber2.join
      count <- counter.get
    } yield {
      assertEquals(rate1, rate2)
      assertEquals(count, 0) // Second cache call did not run the computation thus counter was not incremented
    }

  }

  test("Cache should invalidate after ttl is over") {
    val query = ExchangeRateQuery(CurrencyUnit.EUR, CurrencyUnit.EUR, LocalDate.now)
    val rate = ExchangeRate(1)

    for {
      counter <- Ref.of[IO, Int](0)
      cache <- IOCache(1.second)
      _ <- cache.get(query, _ => counter.update(_ + 1) *> rate.pure[IO]) // will compute value
      _ <- IO.sleep(2.seconds) // ttl is 1 second
      _ <- cache.get(query, _ => counter.update(_ + 1) *> rate.pure[IO]) // will compute value
      _ <- IO.sleep(500.millis) // ttl is 1 second
      _ <- cache.get(query, _ => counter.update(_ + 1) *> rate.pure[IO]) // wont compute value
      count <- counter.get
    } yield assertEquals(count, 2)
  }

  test("Cache should not block when computing multiple keys") {
    val query1 = ExchangeRateQuery(CurrencyUnit.EUR, CurrencyUnit.EUR, LocalDate.now)
    val query2 = ExchangeRateQuery(CurrencyUnit.CAD, CurrencyUnit.EUR, LocalDate.now)
    val query3 = ExchangeRateQuery(CurrencyUnit.CHF, CurrencyUnit.EUR, LocalDate.now)
    val query4 = ExchangeRateQuery(CurrencyUnit.JPY, CurrencyUnit.EUR, LocalDate.now)
    val rate = ExchangeRate(1)

    val parallelCalculating = for {
      cache <- IOCache(1.second)
      fiber1 <- cache.get(query1, _ => IO.sleep(2.seconds) *> rate.pure[IO]).start
      fiber2 <- cache.get(query2, _ => IO.sleep(4.seconds) *> rate.pure[IO]).start
      fiber3 <- cache.get(query3, _ => IO.sleep(5.seconds) *> rate.pure[IO]).start
      fiber4 <- cache.get(query4, _ => IO.sleep(1.seconds) *> rate.pure[IO]).start
      _ <- Vector(fiber1, fiber2, fiber3, fiber4).parTraverse(_.join)
    } yield ()

    // Longest calculation duration is 5s, we must finish w/ all calculation under 6s
    val race = IO.race(
      IO.sleep(6.seconds) *> IO.raiseError(Throwable("Time is out!")),
      parallelCalculating
    )
    assertIO(race, ().asRight)
  }
}
