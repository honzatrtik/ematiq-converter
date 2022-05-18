package ematiq.converter.api

import cats.effect.*
import cats.implicits.*
import ematiq.converter.api.Routes.*
import ematiq.converter.domain.{Converter, CurrencyToConvert}
import io.circe.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.joda.money.{BigMoney, CurrencyUnit}
import org.typelevel.log4cats.Logger

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.Try

object Routes {

  def conversionTradeEndpoint(logger: Logger[IO], converter: Converter[IO]) = HttpRoutes.of[IO] {
    case httpRequest @ POST -> Root / "conversion" / "trade" =>
      val response = for {
        request <- httpRequest.as[ConversionTradeRequest]
        currencyToConvert <- request.toCurrencyToConvert
          .pure[IO]
          .rethrow
          .adaptError(error => InvalidMessageBodyFailure.apply(error.getMessage, error.some))
        converted <- converter.convert(currencyToConvert).rethrow
        response <- Ok.apply(
          request.copy(stake = converted.getAmount, converted.getCurrencyUnit.getCode)
        )
      } yield response

      response.onError { error =>
        logger.warn(error.getMessage)
      }

      response
  }

  /* {"marketId": 123456, "selectionId": 987654, "odds": 2.2, "stake": 253.67, "currency": "USD", "date": "2021-05-18T21:32:42.324Z"} */
  case class ConversionTradeRequest(
      marketId: Long,
      selectionId: Long,
      odds: Double,
      stake: BigDecimal,
      currency: String,
      date: String
  )

  object ConversionTradeRequest {
    extension (request: ConversionTradeRequest) {
      def toCurrencyToConvert: Either[Throwable, CurrencyToConvert] =
        (
          Try(CurrencyUnit.of(request.currency)),
          Try(LocalDate.parse(request.date, DateTimeFormatter.ISO_DATE_TIME))
        )
          .mapN((currency, date) =>
            CurrencyToConvert.apply(BigMoney.of(currency, request.stake.bigDecimal), date)
          )
          .toEither
    }
  }

  implicit val conversionTradeRequestEntityEncoder: EntityEncoder[IO, ConversionTradeRequest] =
    jsonEncoderOf[IO, ConversionTradeRequest]
  implicit val conversionTradeRequestEntityDecoder: EntityDecoder[IO, ConversionTradeRequest] =
    jsonOf[IO, ConversionTradeRequest]
}
