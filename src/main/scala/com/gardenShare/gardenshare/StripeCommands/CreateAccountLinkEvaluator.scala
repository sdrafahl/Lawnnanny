package com.gardenShare.gardenshare

import cats.effect.IO
import com.gardenShare.gardenshare.GetStripePrivateKey
import com.stripe.Stripe
import com.stripe.param.AccountLinkCreateParams
import com.stripe.model.AccountLink

abstract class CreateAccountLinkEvalutor[F[_]] {
  def eval(x: CreateStripeAccountLink): F[AccountLink]
}

object CreateAccountLinkEvalutor {
  implicit def createIOCreateAccountLinkEvalutor(implicit getStripeKey: GetStripePrivateKey[IO]) = new CreateAccountLinkEvalutor[IO] {
    def eval(x: CreateStripeAccountLink): IO[AccountLink] = {
      for {
        stripeApiKey <-  getStripeKey.getKey
        _ <- IO(Stripe.apiKey = stripeApiKey.n)
        accountLinkParams = AccountLinkCreateParams
        .builder()
        .setAccount(x.sellerId)
        .setRefreshUrl(x.refreshUrl.toString())
        .setReturnUrl(x.returnUrl.toString())
        .setType(com.stripe.param.AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)        
        .build()
        accountLink <- IO(AccountLink.create(accountLinkParams))
      } yield accountLink
    }
  }
}
