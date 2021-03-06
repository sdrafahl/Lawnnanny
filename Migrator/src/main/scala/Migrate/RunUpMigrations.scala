package com.gardenShare.gardenshare.Migrator

import com.gardenShare.gardenshare.migrator.GetPostgreClient
import com.gardenShare.gardenshare.migrator._
import cats.effect.ContextShift
import cats.Applicative
import cats.implicits._
import cats.Apply
import slick.dbio.Effect
import slick.dbio.DBIOAction
import slick.dbio.NoStream
import cats.effect.IO
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import scala.concurrent.ExecutionContext
import com.gardenShare.gardenshare.Migrator._
import com.gardenShare.gardenshare.ProductTable._

abstract class RunMigration[F[_]] {
  def runUp(l: List[MigrateDB[F]]): F[Unit]
  def runDown(l: List[MigrateDB[F]]): F[Unit]
}

object RunMigration {
  def apply[F[_]:RunMigration]() = implicitly[RunMigration[F]]
  implicit object IORunMigrations extends RunMigration[IO] {
    def runUp(l: List[MigrateDB[IO]]): IO[Unit] = l.map(_.up).sequence.map(_ => ())
    def runDown(l: List[MigrateDB[IO]]): IO[Unit] = l.map(_.down).sequence.map(_ => ())
  }
}
