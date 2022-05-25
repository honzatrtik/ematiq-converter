package ematiq.converter.domain

import cats.data.EitherT
import cats.implicits.*
import cats.{Monad, MonadThrow}
import ematiq.converter.domain.Converter.Error.*
import org.joda.money.*
import org.typelevel.log4cats.Logger

import java.math.RoundingMode
import java.time.LocalDate
import scala.util.Try

case class CurrencyToConvert(
    amount: BigMoney,
    date: LocalDate
)

case class Converter[F[_]: Monad: MonadThrow](
    logger: Logger[F],
    exchangeRateProvider: ExchangeRateProvider[F],
    cache: Cache[F]
) {
  def convert(currencyToConvert: CurrencyToConvert): F[Either[Converter.Error, BigMoney]] = {

    val CurrencyToConvert(amount, date) = currencyToConvert

    val query = ExchangeRateQuery(
      amount.getCurrencyUnit,
      CurrencyUnit.EUR,
      date
    )

    val result = for {
      exchangeRate <- EitherT(cache.get(query, exchangeRateProvider.provide).attempt)
        .leftSemiflatTap(error => logger.warn(s"Failed to get exchange rate: $error"))
        .leftMap(FailedToGetExchangeRate.apply)

      converted <- EitherT.fromEither {
        Try(
          amount
            .withScale(5) // Decimal points precision
            .convertRetainScale(
              CurrencyUnit.EUR,
              exchangeRate.toJavaBigDecimal,
              RoundingMode.DOWN
            )
        ).toEither
          .leftMap(ConversionFailed.apply)
      }
    } yield converted

    result.value
  }
}

object Converter {
  sealed trait Error extends RuntimeException
  object Error {
    case class FailedToGetExchangeRate(cause: Throwable) extends Error
    case class ConversionFailed(cause: Throwable) extends Error
  }
}
