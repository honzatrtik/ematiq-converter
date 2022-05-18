package ematiq.converter.infrastructure

import cats.*
import cats.effect.*
import cats.implicits.*
import ematiq.converter.domain.*
import io.circe.*
import io.circe.generic.semiauto.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.*
import org.http4s.implicits.*

import java.time.format.DateTimeFormatter

case class FixerProvider(client: Client[IO], config: FixerProvider.Config)
    extends ExchangeRateProvider[IO] {

  import FixerProvider.*

  override def provide(query: ExchangeRateQuery): IO[ExchangeRate] = {
    val request = Request[IO](
      uri = makeUri(query),
      headers = Headers("apikey" -> config.apiKey)
    )
    client.expect[ExchangeRate](request)(exchangeRateEntityDecoder(query.to.getCode))
  }

  def makeUri(query: ExchangeRateQuery): Uri =
    val dateFormatted = query.date
      .format(DateTimeFormatter.ISO_LOCAL_DATE)

    (uri"https://api.apilayer.com/fixer/" / dateFormatted)
      .withQueryParams(
        Map(
          "base" -> query.from.getCode,
          "symbols" -> query.to.getCode
        )
      )
}

object FixerProvider {
  /*
    Example response
    ```
    {
      "base": "GBP",
      "date": "2013-12-24",
      "historical": true,
      "rates": {
        "CAD": 1.739516,
        "EUR": 1.196476,
        "USD": 1.636492
      },
      "success": true,
      "timestamp": 1387929599
    }
    ```
   */
  def exchangeRateDecoder(currencyCode: String): Decoder[ExchangeRate] =
    deriveDecoder[Response].emap {
      _.rates
        .get(currencyCode)
        .toRight(s"Currency code $currencyCode not present in rates map")
        .map(rate => ExchangeRate(BigDecimal(rate)))
    }

  def exchangeRateEntityDecoder(currencyCode: String): EntityDecoder[IO, ExchangeRate] = {
    implicit val decoder: Decoder[ExchangeRate] = exchangeRateDecoder(currencyCode)
    jsonOf[IO, ExchangeRate]
  }
  case class Response(rates: Map[String, Double])

  case class Config(apiKey: String)
}
