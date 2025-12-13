/*
 * Copyright 2020-2022 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package webecho

import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers._
import org.scalatest.wordspec._
import webecho.apimodel.ApiHealth
import webecho.tools.JsonSupport
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.apache.pekko.http.scaladsl.model.MediaTypes
import com.github.plokhotnyuk.jsoniter_scala.core._
import org.apache.pekko.util.ByteString

trait JsoniterScalaSupport {
  implicit def unmarshaller[A](implicit codec: JsonValueCodec[A]): FromEntityUnmarshaller[A] =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(MediaTypes.`application/json`)
      .map {
        case ByteString.empty => throw Unmarshaller.NoContentException
        case data             => readFromArray(data.toArray)
      }
}

class ServiceTest extends AnyWordSpec with should.Matchers with ScalatestRouteTest with JsonSupport with JsoniterScalaSupport {

  val routes = ServiceRoutes(ServiceDependencies.defaults).routes

  "Web Echo Service" should {
    "Respond OK when pinged" in {
      Get("/api/v2/system/health") ~> routes ~> check {
        responseAs[ApiHealth] shouldBe ApiHealth(true, "alive")
      }
    }
    "Be able to return a static asset" in {
      Get("/txt/LICENSE-2.0.txt") ~> routes ~> check {
        responseAs[String] should include regex "Apache License"
      }
      Get("/txt/TERMS-OF-SERVICE.txt") ~> routes ~> check {
        responseAs[String] should include regex "WARRANTY"
      }
    }
    "Be able to return an embedded webjar asset" in {
      Get("/assets/jquery/jquery.js") ~> routes ~> check {
        responseAs[String] should include regex "jQuery JavaScript Library"
      }
    }
    "Respond a web-echo related home page content" in {
      info("The first content page can be slow because of templates runtime compilation")
      Get() ~> routes ~> check {
        responseAs[String] should include regex "Echo"
      }
    }
  }
}

