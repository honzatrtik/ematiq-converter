package ematiq.converter

import ematiq.converter.domain.{Converter, CurrencyToConvert}
import ematiq.converter.infrastructure.*
import ematiq.converter.api.Routes
import cats.data.NonEmptyList
import cats.effect.*
import cats.implicits.*
import com.comcast.ip4s.*
import org.http4s.blaze.server.*
import org.http4s.implicits.*
import org.http4s.server.Router
import ematiq.converter.api.Routes
import org.http4s.blaze.client.*
import org.http4s.client.*
import org.joda.money.{BigMoney, CurrencyUnit}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDate
import scala.concurrent.duration.*

object ConverterServer extends IOApp {

  import cats.effect.unsafe.implicits.global

  // Belongs to ENV
  val fixerConfig: FixerProvider.Config =
    FixerProvider.Config("D82PLsB19yvDAVSCN29KqW2znZ2oqD5h")
  val ttl: FiniteDuration = 2.hours

  override def run(args: List[String]): IO[ExitCode] = {

    val loggerResource = Resource.make(Slf4jLogger.fromName[IO]("root"))(_ => IO.unit)
    val providerResource = for {
      client <- BlazeClientBuilder[IO].resource
      logger <- loggerResource
      exchangeRateHostProvider = ExchangeRateHostProvider(client)
      fixerProvider = FixerProvider(client, fixerConfig)
    } yield ProviderChain(logger, NonEmptyList.of(exchangeRateHostProvider, fixerProvider))

    (loggerResource, providerResource).tupled
      .use { (logger, provider) =>
        val app = Router(
          "/api/v1" -> Routes.conversionTradeEndpoint(
            logger,
            Converter(logger, provider, NoOpCache())
          )
        ).orNotFound

        BlazeServerBuilder[IO]
          .bindHttp(8080, "localhost")
          .withHttpApp(app)
          .resource
          .use(_ => logger.info("Server is running!") *> IO.never)
          .as(ExitCode.Success)
      }
  }

}
