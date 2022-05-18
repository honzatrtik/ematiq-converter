package ematiq.converter.infrastructure

import cats.*
import cats.effect.*
import cats.implicits.*
import ematiq.converter.domain.*
import io.circe.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.*
import org.http4s.implicits.*

import java.time.format.DateTimeFormatter

case class ExchangeRateHostProvider(client: Client[IO]) extends ExchangeRateProvider[IO] {

  import ExchangeRateHostProvider.*

  override def provide(query: ExchangeRateQuery): IO[ExchangeRate] = {
    val request = Request[IO](uri = makeUri(query))
    client.expect[ExchangeRate](request)
  }

  def makeUri(query: ExchangeRateQuery): Uri =
    uri"https://api.exchangerate.host/convert"
      .withQueryParams(
        Map(
          "from" -> query.from.getCode,
          "to" -> query.to.getCode,
          "date" -> query.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
      )
}

object ExchangeRateHostProvider {
  /*
    Example response
    ```
    {
      "motd": {
        "msg": "If you or your company use this project or like what we doing, please consider backing us so we can continue maintaining and evolving this project.",
        "url": "https://exchangerate.host/#/donate"
      },
      "success": true,
      "query": {
        "from": "CZK",
        "to": "EUR",
        "amount": 1
      },
      "info": {
        "rate": 0.040488
      },
      "historical": false,
      "date": "2022-05-17",
      "result": 0.040488
    }
    ```
   */
  implicit val exchangeRateEntityDecoder: EntityDecoder[IO, ExchangeRate] =
    jsonOf[IO, Response].map(response => ExchangeRate(BigDecimal(response.result)))

  case class Response(result: Double)
}
