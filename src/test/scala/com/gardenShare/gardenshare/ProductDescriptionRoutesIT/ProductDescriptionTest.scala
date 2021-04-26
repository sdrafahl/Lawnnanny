package com.gardenShare.gardenshare

import utest.TestSuite
import utest.test
import utest.Tests
import com.typesafe.config.ConfigFactory
import java.net.URI

object ProductDescriptionTest extends TestSuite {
  lazy implicit val config = ConfigFactory.load()
  lazy implicit val dbClient = PostGresSetup.createPostgresClient
  val executionStuff = ConcurrencyHelper.createConcurrencyValues(2)
  implicit val cs = executionStuff._3

  val testEmail = "shane@gmail.com"
  val testPassword = "teST12$5jljasdf"
  val testRefreshURL = URI.create("http://localhost:3000/")
  val testReturnURL = URI.create("http://localhost:3000/")

  val tests = Tests {
    test("Product Description Routes") {
      test("productDescription/BrownOysterMushrooms") {
        val descriptionOfMushrooms = UserTestsHelper.makeRequestToGetProductDescription("BrownOysterMushrooms")
        val expectedProductDescription = ProductDescription("Brown-Oyster-Mushrooms",Pound,BrownOysterMushrooms)
        assert(descriptionOfMushrooms equals expectedProductDescription)
      }
    }    
  }
}
