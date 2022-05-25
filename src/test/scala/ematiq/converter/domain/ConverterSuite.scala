package ematiq.converter.domain

import cats.effect.*
import cats.effect.implicits.*
import cats.implicits.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import ematiq.converter.domain.Converter.Error.FailedToGetExchangeRate
import org.joda.money.{BigMoney, CurrencyUnit}
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}

import java.time.LocalDate
import ematiq.converter.infrastructure.NoOpCache

class ConverterSuite extends munit.FunSuite {

  import cats.effect.unsafe.implicits.global

  def constExchangeRateProvider(rate: BigDecimal): ExchangeRateProvider[IO] = _ =>
    ExchangeRate(rate).pure[IO]

  def failingExchangeRateProvider(throwable: Throwable): ExchangeRateProvider[IO] = _ =>
    IO.raiseError(throwable)

  val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  test("Converter should return converted amount if provided with exchange rate") {
    val converter = new Converter(logger, constExchangeRateProvider(0.04), NoOpCache())
    val result = converter
      .convert(
        CurrencyToConvert(
          BigMoney.of(CurrencyUnit.of("CZK"), 100),
          LocalDate.of(2022, 1, 1)
        )
      )
      .unsafeRunSync()

    assert(result.isRight)

    val money = result.toOption.get
    val expectedMoney = BigMoney.of(CurrencyUnit.of("EUR"), 4)

    assertEquals(money.getCurrencyUnit, expectedMoney.getCurrencyUnit)
    assert(money.isEqual(expectedMoney), s"$money is not equal $expectedMoney")
  }

  test("Converter should return converted amount for rate with 5 decimal places precision") {
    val converter =
      new Converter(logger, constExchangeRateProvider(0.33333333333333), NoOpCache())
    val result = converter
      .convert(
        CurrencyToConvert(
          BigMoney.of(CurrencyUnit.of("CZK"), 100),
          LocalDate.of(2022, 1, 1)
        )
      )
      .unsafeRunSync()

    assert(result.isRight)

    val money = result.toOption.get
    val expectedMoney = BigMoney.of(CurrencyUnit.of("EUR"), 33.33333)

    assertEquals(money.getCurrencyUnit, expectedMoney.getCurrencyUnit)
    assert(money.isEqual(expectedMoney), s"$money is not equal $expectedMoney")
  }

  test(
    "Converter should fail with `FailedToGetExchangeRate` if exchange rate provider fail to provide rate"
  ) {
    val error = Throwable("Bad stuff happened")
    val converter = new Converter(logger, failingExchangeRateProvider(error), NoOpCache())
    val result = converter.convert(
      CurrencyToConvert(
        BigMoney.of(CurrencyUnit.of("CZK"), 100),
        LocalDate.of(2022, 1, 1)
      )
    )
    assertEquals(
      result.unsafeRunSync(),
      FailedToGetExchangeRate(error).asLeft
    )
  }

}
