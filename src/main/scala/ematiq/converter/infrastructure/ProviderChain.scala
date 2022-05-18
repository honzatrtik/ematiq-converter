package ematiq.converter.infrastructure

import cats.{MonadError, MonadThrow}
import cats.implicits.*
import cats.data.NonEmptyList
import ematiq.converter.domain.{ExchangeRate, ExchangeRateProvider, ExchangeRateQuery}
import org.typelevel.log4cats.Logger

case class ProviderChain[F[_]: MonadThrow](
    logger: Logger[F],
    providers: NonEmptyList[ExchangeRateProvider[F]]
) extends ExchangeRateProvider[F] {

  import ProviderChain.*

  override def provide(query: ExchangeRateQuery): F[ExchangeRate] = {
    def tryProvide(
        providers: List[ExchangeRateProvider[F]],
        errors: List[Throwable]
    ): F[ExchangeRate] = providers match {
      case provider :: tail =>
        logger.info(s"Trying provider: $provider") *>
          provider.provide(query).recoverWith { error =>
            logger.warn(s"Exchange rate provider failed with $error, will try next if available")
            tryProvide(tail, errors :+ error)
          }
      case Nil => MonadThrow[F].raiseError(Error.AllProvidersFailed(errors))
    }

    tryProvide(providers.toList, List.empty)
  }
}

object ProviderChain {
  sealed trait Error extends RuntimeException
  object Error {
    case class AllProvidersFailed(errors: List[Throwable]) extends Error
  }
}
