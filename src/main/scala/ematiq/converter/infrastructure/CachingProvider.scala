package ematiq.converter.infrastructure

import cats.effect.*
import cats.implicits.*
import com.github.blemale.scaffeine.{Scaffeine, AsyncCache as SCache}
import ematiq.converter.domain.{ExchangeRate, ExchangeRateProvider, ExchangeRateQuery}
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*
import scala.jdk.DurationConverters.*

object CachingProvider {

  case class Config(ttl: FiniteDuration, maximumSize: Long)

  def apply(logger: Logger[IO], provider: ExchangeRateProvider[IO], config: Config)(implicit
      runtime: unsafe.IORuntime
  ) = {

    val cache: SCache[ExchangeRateQuery, ExchangeRate] =
      Scaffeine()
        .recordStats()
        .expireAfterWrite(config.ttl)
        .maximumSize(config.maximumSize)
        .buildAsync()

    new ExchangeRateProvider[IO] {
      override def provide(query: ExchangeRateQuery): IO[ExchangeRate] =
        // Scaffeine does not provide IO api, so we must do some unwrapping here
        IO.fromFuture(IO {
          cache.getFuture(
            query,
            query =>
              (logger.info("Cache miss, calling underlying provider") >> provider.provide(query))
                .unsafeToFuture()
          )
        })
    }
  }
}
