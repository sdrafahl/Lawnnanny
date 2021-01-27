package com.gardenShare.gardenshare

import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import cats.effect.IO
import cats.syntax._
import cats.implicits._
import com.gardenShare.gardenshare.Shows._ 
import com.gardenShare.gardenshare.Storage.Relational.GetStore
import com.gardenShare.gardenshare.Storage.Relational.InsertStore
import io.circe.Decoder
import com.gardenShare.gardenshare.domain.Store.State

abstract class GetRoutes[F[_], T <: RoutesTypes] {
  def getRoutes: HttpRoutes[F]
}

object GetRoutes {
  def apply[F[_], T <: RoutesTypes]()(implicit x:GetRoutes[F, T]) = x

  implicit def ioTestingRoutes(implicit e: ApplyUserToBecomeSeller[IO]) = new GetRoutes[IO, TestingAndProductionRoutes]{
    def getRoutes: HttpRoutes[IO] = (
      UserRoutes.userRoutes[IO]() <+>
      TestUserRoutes.userRoutes[IO]()
    )
  }

  implicit def ioProductionRoutes(implicit e: ApplyUserToBecomeSeller[IO]) = new GetRoutes[IO, OnlyProductionRoutes] {
    def getRoutes: HttpRoutes[IO] = UserRoutes.userRoutes[IO]()
  }
}
