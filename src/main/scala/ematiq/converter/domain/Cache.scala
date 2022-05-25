package ematiq.converter.domain

import cats.effect.IO

trait Cache[F[_]] {
  def get(
      query: ExchangeRateQuery,
      computeRate: ExchangeRateQuery => F[ExchangeRate]
  ): F[ExchangeRate]
}
