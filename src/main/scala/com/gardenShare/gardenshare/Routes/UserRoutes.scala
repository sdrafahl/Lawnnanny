package com.gardenShare.gardenshare

import io.circe._, io.circe.parser._
import cats.effect.Async
import cats.effect.ContextShift
import org.http4s.Request
import com.gardenShare.gardenshare.Helpers._
import cats.ApplicativeError
import com.gardenShare.gardenshare.Storage.Users.Cognito.CogitoClient
import com.gardenShare.gardenshare.Config.GetUserPoolName
import com.gardenShare.gardenshare.Config.GetTypeSafeConfig
import com.gardenShare.gardenshare.Config.GetUserPoolSecret
import com.gardenShare.gardenshare.authenticateUser.AuthUser.AuthUser
import com.gardenShare.gardenshare.Config.GetUserPoolId
import com.gardenShare.gardenshare.authenticateUser.AuthJWT.AuthJWT
import com.gardenShare.gardenshare.Config.GetRegion
import com.gardenShare.gardenshare.authenticateUser.AuthJWT.HttpsJwksBuilder
import com.gardenShare.gardenshare.GoogleMapsClient.GetDistance
import org.http4s.dsl.Http4sDsl
import com.gardenShare.gardenshare.UserEntities.Email
import com.gardenShare.gardenshare.UserEntities.Password
import com.gardenShare.gardenshare.UserEntities.User
import com.gardenShare.gardenshare.UserEntities.JWTValidationTokens
import org.http4s.HttpRoutes
import com.gardenShare.gardenshare.SignupUser.SignupUser._
import cats.implicits._
import io.circe.generic.auto._, io.circe.syntax._
import org.http4s.Header
import com.gardenShare.gardenshare.authenticateUser.AuthJWT.AuthJWT.AuthJwtOps
import com.gardenShare.gardenshare.authenticateUser.AuthUser.AuthUser
import com.gardenShare.gardenshare.authenticateUser.AuthUser.AuthUser.AuthUserOps
import com.gardenShare.gardenshare.UserEntities.AuthenticatedUser
import com.gardenShare.gardenshare.UserEntities.FailedToAuthenticate
import com.gardenShare.gardenshare.UserEntities.ValidToken
import com.gardenShare.gardenshare.Encoders.Encoders._
import com.gardenShare.gardenshare.UserEntities.InvalidToken
import com.gardenShare.gardenshare.ProcessAndJsonResponse
import com.gardenShare.gardenshare.ProcessAndJsonResponse.ProcessAndJsonResponseOps
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse
import com.gardenShare.gardenshare.domain.ProcessAndJsonResponse.ProcessData
import com.gardenShare.gardenshare.UserEntities.UserResponse
import com.gardenShare.gardenshare.UserEntities.JWTValidationResult
import cats.Applicative
import com.gardenShare.gardenshare.UserEntities.Sellers
import com.gardenShare.gardenshare.domain._
import com.gardenShare.gardenshare.GetUserInfo.GetUserInfoOps
import com.gardenShare.gardenshare.domain.User.UserInfo
import com.gardenShare.gardenshare.domain.Store.Address
import com.gardenShare.gardenshare.UserEntities.AddressNotProvided
import com.gardenShare.gardenshare.Helpers.ResponseHelper
import _root_.fs2.text

object UserRoutes {
  def userRoutes[F[_]: Async: GetTypeSafeConfig:GetUserInfo: com.gardenShare.gardenshare.SignupUser.SignupUser: GetUserPoolSecret: AuthUser: GetUserPoolId: AuthJWT: GetRegion: HttpsJwksBuilder: GetDistance:GetUserPoolName: CogitoClient:ApplyUserToBecomeSeller: ContextShift]()
      : HttpRoutes[F] = {
    implicit val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case POST -> Root / "user" / "signup" / email / password => {
        val emailToPass = Email(email)
        val passwordToPass = Password(password)
        val user = User(emailToPass, passwordToPass)

        addJsonHeaders(
          ProcessData(
            user.signUp[F](),
            (resp:SignUpResponse) => UserCreationRespose(
              s"User Request Made: ${resp.codeDeliveryDetails().toString()}",
              true
            ),
            (err:Throwable) => UserCreationRespose(
              s"User Request Failed: ${err.getMessage()}",
              false
            )
          )
            .process
            .flatMap(js => Ok(js.toString()))
        ).catchError
      }

      case GET -> Root / "user" / "auth" / email / password => {
        addJsonHeaders(ProcessData(
          User(Email(email), Password(password)).auth,
          (usr:UserResponse) => usr match {
            case AuthenticatedUser(user, jwt, accToken) => AuthUserResponse(
              "jwt token is valid",
              Option(AuthenticatedUser(user, jwt, accToken)),
              true
            )
            case FailedToAuthenticate(msg) => AuthUserResponse(s"User failed to verify: ${msg}", None, false)
          },
          (error: Throwable) => AuthUserResponse(s"Error Occurred: ${error}", None, false)
        )
          .process
          .flatMap(js => Ok(js.toString()))
        ).catchError
      }
      case GET -> Root / "user" / "jwt" / jwtToken => {
        addJsonHeaders(
          ProcessData(
            JWTValidationTokens(jwtToken).auth[F],
            (jwtR:JWTValidationResult) => jwtR match {
              case ValidToken(email) => IsJwtValidResponse("Token is valid", true)
              case InvalidToken(msg) => IsJwtValidResponse("Token is not valid", false)
            },
            (error:Throwable) => IsJwtValidResponse(s"Error occured: ${error}", false)
          )
            .process
            .flatMap(js => Ok(js.toString()))
        ).catchError
      }
      case req @ POST -> Root / "user" / "apply-to-become-seller" => {
          parseJWTokenFromRequest(req)
            .map(_.auth)
            .map(a => a.flatMap(ab => parseBodyFromRequest[Address, F](req).map(ac => (ab, ac))))
            .map{a =>
              a.flatMap{
                case (InvalidToken(msg), _) => Applicative[F].pure(ResponseBody(msg, false).asJson)
                case (ValidToken(None), _) => Applicative[F].pure(ResponseBody("Token is valid but without email", false).asJson)
                case (_, None) => Applicative[F].pure(ResponseBody("Address not provided", false).asJson)
                case (ValidToken(Some(email)), Some(address)) => {
                  ProcessData(
                    implicitly[ApplyUserToBecomeSeller[F]].applyUser(Email(email), Sellers, address),
                    (_: Unit) => ResponseBody("User is now a seller", true),
                    (err:Throwable) => ResponseBody(err.getMessage(), false)
                  )
                    .process
                }
                case _ => Applicative[F].pure(ResponseBody("Token is valid but without email", false).asJson)
              }
            }.left.map(noValidJwt => Ok(noValidJwt.asJson.toString()))
            .map(_.flatMap(js => Ok(js.toString())))
            .fold(a => a, b => b)
            .catchError
      }
      case req @ GET -> Root / "user" / "info" => {
        addJsonHeaders(
        parseJWTokenFromRequest(req)
          .map(_.auth)
          .map{a =>
            a.flatMap {
              case InvalidToken(msg) => Applicative[F].pure(ResponseBody(msg, false).asJson)
              case ValidToken(None) => Applicative[F].pure(ResponseBody("Token is valid but without email", false).asJson)
              case ValidToken(Some(email)) => {
                ProcessData(
                  Email(email).getUserInfo,
                  (a:UserInfo) => a.asJson,
                  (err:Throwable) => FailedToGetUserInfo(err.getMessage())
                )
                  .process
              }
            }
          }
          .left.map(noValidJwt => Ok(noValidJwt.asJson.toString()))
          .map(_.flatMap(js => Ok(js.toString())))
          .fold(a => a, b => b)
        ).catchError
      }
    }
  }
}
