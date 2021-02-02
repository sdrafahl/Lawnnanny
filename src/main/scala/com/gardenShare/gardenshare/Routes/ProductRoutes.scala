package com.gardenShare.gardenshare

import cats.effect.Async
import com.gardenShare.gardenshare.authenticateUser.AuthUser.AuthUser
import com.gardenShare.gardenshare.authenticateUser.AuthJWT.AuthJWT
import com.gardenShare.gardenshare.Storage.Relational.InsertStore
import com.gardenShare.gardenshare.GoogleMapsClient.GetDistance
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.util.CaseInsensitiveString
import com.gardenShare.gardenshare.UserEntities.JWTValidationTokens
import com.gardenShare.gardenshare.authenticateUser.AuthUser.AuthUser
import com.gardenShare.gardenshare.authenticateUser.AuthUser.AuthUser._
import io.circe._, io.circe.parser._
import io.circe.generic.auto._, io.circe.syntax._
import com.gardenShare.gardenshare.authenticateUser.AuthJWT.AuthJWT._
import com.gardenShare.gardenshare.UserEntities.InvalidToken
import com.gardenShare.gardenshare.UserEntities.ValidToken
import com.gardenShare.gardenshare.domain.Store.Address
import com.gardenShare.gardenshare.UserEntities.Email
import com.gardenShare.gardenshare.domain.Store.CreateStoreRequest
import com.gardenShare.gardenshare.Storage.Relational.InsertStore.CreateStoreRequestOps
import com.gardenShare.gardenshare.Storage.Relational.InsertStore
import scala.util.Try
import com.gardenShare.gardenshare.GoogleMapsClient.Distance
import com.gardenShare.gardenshare.SignupUser._
import com.gardenShare.gardenshare.SignupUser.SignupUser._
import com.gardenShare.gardenshare.GetListOfProductNames.GetListOfProductNames
import com.gardenShare.gardenshare.Storage.Relational.InsertProduct
import com.gardenShare.gardenshare.Helpers._
import com.gardenShare.gardenshare
import com.gardenShare.gardenshare.GetListOfProductNames.DescriptionName
import com.gardenShare.gardenshare.CreateProductRequest
import cats.Applicative
import com.gardenShare.gardenshare.ParseProduce
import cats.effect.ContextShift
import cats.ApplicativeError
import cats.FlatMap
import com.gardenShare.gardenshare.UserEntities.JWTValidationResult
import cats.Monad
import cats.implicits._
import com.gardenShare.gardenshare.domain.ProcessAndJsonResponse.ProcessData
import com.gardenShare.gardenshare.Storage.Relational.GetStore
import com.gardenShare.gardenshare.Storage.Relational.GetProductsByStore
import com.gardenShare.gardenshare.ProcessAndJsonResponse.ProcessAndJsonResponseOps
import com.gardenShare.gardenshare.Helpers.ResponseHelper

object ProductRoutes {
  def productRoutes[F[_]: Async: AuthUser: AuthJWT: GetDistance: InsertProduct: AddProductToStoreForSeller:GetUserInfo:AddProductToStore:ContextShift: Monad: GetProductsSoldFromSeller:GetStore:GetProductsByStore](implicit pp: ParseProduce[String], ae: ApplicativeError[F, Throwable], e:EncodeProduce[String])
      : HttpRoutes[F] = {
    implicit val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case req @ POST -> Root / "product" / "add" / produce => {
        parseJWTokenFromRequest(req)
          .map{(a: JWTValidationTokens) =>
            a.auth.flatMap {
                case InvalidToken(msg) => Applicative[F].pure(InvalidToken(msg).asJson)
                case ValidToken(None) => Applicative[F].pure(InvalidToken("Token is valid but without email").asJson)
                case ValidToken(Some(email)) => {
                  pp.parse(produce) match {
                    case Right(pd) => {
                      implicitly[AddProductToStoreForSeller[F]].add(Email(email), pd).map(_ => ResponseBody("Product was added to the store", true).asJson)
                    }
                    case Left(err) => Applicative[F].pure(ResponseBody("Invalid produce", false).asJson)
                  }
                }
            }
          }.leftMap(no => no.asJson)
        .fold(a => Ok(a.toString), b => b.flatMap(js => Ok(js.toString()))).catchError
      }
      case req @ GET -> Root / "product" => {
        parseJWTokenFromRequest(req)
          .map{(a: JWTValidationTokens) =>
            a.auth.flatMap{
              case InvalidToken(msg) => Applicative[F].pure(InvalidToken(msg).asJson)
              case ValidToken(None) => Applicative[F].pure(InvalidToken("Token is valid but without email").asJson)
              case ValidToken(Some(email)) => {
                ProcessData(
                  implicitly[GetProductsSoldFromSeller[F]].get(Email(email)),
                  (a:List[Produce]) => ListOfProduce(a.map(b => e.to(b))).asJson,
                  (err:Throwable) => ResponseBody(s"Failed to get products from seller: ${err.getMessage()}", false))
                
                  .process
              }
            }
          }
          .leftMap(a => Applicative[F].pure(a.asJson))
          .fold(a => a, b => b)
          .flatMap(a => Ok(a.toString())).catchError
      }
    }
  }
}
