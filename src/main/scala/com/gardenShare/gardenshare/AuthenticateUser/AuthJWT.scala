package com.gardenShare.gardenshare.authenticateUser.AuthJWT

import com.chatwork.scala.jwk.JWKSet
import io.circe.parser._
import com.chatwork.scala.jwk.ECJWK
import com.chatwork.scala.jwk.JWK
import com.chatwork.scala.jwk.KeyId
import io.circe.Json
import com.chatwork.scala.jwk.JWKSet
import io.circe.parser._
import com.gardenShare.gardenshare.UserEntities._
import cats.effect.IO
import com.gardenShare.gardenshare.Config.GetUserPoolId
import org.jose4j.jwk.HttpsJwks
import com.gardenShare.gardenshare.Config._
import com.gardenShare.gardenshare.Config.StringReps._
import com.gardenShare.gardenshare.Config.StringReps.UseastOneRep
import com.gardenShare.gardenshare.Config.StringReps
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.jwt.consumer.JwtConsumer
import org.jose4j.jwt.consumer.JwtContext
import scala.util.Try
import scala.util.Success
import software.amazon.awssdk.services.ecs.model.Failure
import com.gardenShare.gardenshare.Config.GetUserPoolId
import com.gardenShare.gardenshare.Config.GetUserPoolId._
import cats.MonadError
import cats.implicits._

abstract class AuthJWT[F[_]] {
  def authJWT(jwt:JWTValidationTokens): F[JWTValidationResult]
}

object AuthJWT {
  implicit def apply[F[_]: AuthJWT]() = implicitly[AuthJWT[F]]

  implicit def createAuthJWT[F[_]](implicit getUserPoolId: GetUserPoolId[F], builder: HttpsJwksBuilder[F], getRegion: GetRegion[F],joseProcessJwt:JoseProcessJwt, getUserPoolName: GetUserPoolName[F], get: GetTypeSafeConfig[F], ae: MonadError[F, Throwable]) = new AuthJWT[F] {
    def authJWT(jwt:JWTValidationTokens): F[JWTValidationResult] = {
      (for {
        id <- getUserPoolId.exec()
        region <- getRegion.exec
        userPoolName <- getUserPoolName.exec()
        st = StringReps[USEastOne]()
        url = s"https://cognito-idp.${region.stringRep}.amazonaws.com/${id.id}/.well-known/jwks.json"
        consumer <- builder.build(url, userPoolName)
        result = consumer.process(jwt.idToken)
      } yield joseProcessJwt.processJwt(consumer, jwt))
        .attempt
        .map{
          case Left(err) => InvalidToken(err.getMessage())
          case Right(res) => res
        }
    }
  }

  implicit class AuthJwtOps(underlying:JWTValidationTokens) {
    def auth[F[_]](implicit auth: AuthJWT[F], joe: JoseProcessJwt) = auth.authJWT(underlying)
  }
}


abstract class JoseProcessJwt {
  def processJwt(c: JwtConsumer, jwt:JWTValidationTokens): JWTValidationResult  
}

object JoseProcessJwt {
  implicit def apply() = default
  implicit object default extends JoseProcessJwt {
    def processJwt(c: JwtConsumer, jwt:JWTValidationTokens): JWTValidationResult = {
      Try(c.processToClaims(jwt.idToken)).fold (        
        err => InvalidToken(""),
        claim => {
          ValidToken(Option(claim.getClaimValueAsString("email")))
        }
      )
    }
  }
}

abstract class HttpsJwksBuilder[F[_]] {
  def build(url: String, userPoolId: UserPoolName): F[JwtConsumer]
}

object HttpsJwksBuilder {
  implicit def apply[F[_]: HttpsJwksBuilder]() = implicitly[HttpsJwksBuilder[F]]
  implicit object default extends HttpsJwksBuilder[IO] {
    def build(url: String, userPoolId: UserPoolName): IO[JwtConsumer] = {
      IO {
        val httpsjwks = new HttpsJwks(url)
        val resolver = new HttpsJwksVerificationKeyResolver(httpsjwks)
        new JwtConsumerBuilder()
          .setVerificationKeyResolver(resolver)
          .setExpectedAudience(userPoolId.name)
          .build()
      }      
    }
  }
}
