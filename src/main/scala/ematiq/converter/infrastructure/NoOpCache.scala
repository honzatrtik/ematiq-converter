package ematiq.converter.infrastructure

import cats.effect.*
import cats.effect.std.Semaphore
import cats.implicits.*
import ematiq.converter.domain.{ExchangeRate, ExchangeRateQuery}
import ematiq.converter.domain.Cache

import scala.concurrent.duration.FiniteDuration

object NoOpCache {
  def apply(): Cache[IO] = (query, compute) => compute(query)
}
