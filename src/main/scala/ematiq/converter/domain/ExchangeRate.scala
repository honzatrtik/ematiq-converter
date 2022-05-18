package ematiq.converter.domain

import org.joda.money.CurrencyUnit

import java.time.LocalDate

opaque type ExchangeRate = BigDecimal
object ExchangeRate {
  def apply(rate: BigDecimal): ExchangeRate = rate

  extension (exchangeRate: ExchangeRate) {
    def toBigDecimal: BigDecimal = exchangeRate
    def toJavaBigDecimal: java.math.BigDecimal = exchangeRate.toBigDecimal.bigDecimal
  }
}

case class ExchangeRateQuery(
    from: CurrencyUnit,
    to: CurrencyUnit,
    date: LocalDate
)

trait ExchangeRateProvider[F[_]] {
  def provide(query: ExchangeRateQuery): F[ExchangeRate]
}
