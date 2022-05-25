package ematiq.converter.infrastructure

import cats.effect.*
import cats.effect.std.Semaphore
import cats.implicits.*
import ematiq.converter.domain.Cache
import ematiq.converter.domain.ExchangeRate
import ematiq.converter.domain.ExchangeRateQuery

import scala.concurrent.duration.*

object IOCache {

  def apply(ttl: FiniteDuration): IO[Cache[IO]] = {

    /*
      Cache holds reference to map of deferred rate values
      What is deferred? From the docs:

        ... primitive which represents a single value which may not yet be available.

      See https://typelevel.org/cats-effect/docs/std/deferred
     */
    (
      Ref.of[IO, Map[ExchangeRateQuery, Deferred[IO, ExchangeRate]]](Map.empty),
      Semaphore[IO](1)
    ).mapN { (cache, semaphore) =>
      new Cache[IO] {
        override def get(
            query: ExchangeRateQuery,
            computeRate: ExchangeRateQuery => IO[ExchangeRate]
        ): IO[ExchangeRate] = {
          val result = for {
            _ <- IO.println(s"getting ${query}")
            _ <- semaphore.acquire
            // Check if deferred value exists for query and create one if necessary
            deferred <- cache.get.map(_.get(query)).flatMap {
              case Some(deffered) => deffered.pure[IO] <* IO.println(s"cache hit ${query}")
              case None =>
                for {
                  _ <- IO.println(s"cache miss ${query}, calculating")
                  newDeferred <- Deferred.apply[IO, ExchangeRate]
                  // Launch value computation in separate fiber, after computation is done, start ttl
                  _ <- computeRate(query)
                    .flatMap(newDeferred.complete) // Fulfill deferred
                    .flatMap(_ =>
                      IO.println(s"calculated ${query}, starting ttl ticking") *> IO.sleep(ttl)
                    )
                    .flatMap(_ => remove(query) *> IO.println(s"removed ${query}"))
                    .start
                } yield newDeferred
            }
            _ <- cache.update(_ + (query -> deferred))
            _ <- semaphore.release
          } yield deferred

          result.flatMap(_.get)
        }
        private def remove(query: ExchangeRateQuery): IO[Unit] =
          semaphore.acquire *> cache.update(_ - query) <* semaphore.release

      }
    }
  }
}
