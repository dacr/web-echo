/*
 * Copyright 2020 David Crosson
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

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.json4s._
import org.json4s.ext.{JavaTimeSerializers, JavaTypesSerializers}
import org.json4s.jackson.Serialization
import org.scalatest.matchers._
import org.scalatest.wordspec._
import webecho.routing.Health


class ServiceTest extends AnyWordSpec with should.Matchers with ScalatestRouteTest {
  implicit val chosenSerialization: Serialization.type = Serialization
  implicit val chosenFormats: Formats = DefaultFormats.lossless ++ JavaTimeSerializers.all ++ JavaTypesSerializers.all

  val routes = ServiceRoutes(ServiceDependencies.defaults).routes

  "Web Echo Service" should  {
    "Respond OK when pinged" in {
      Get("/health") ~> routes ~> check {
        import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
        responseAs[Health] shouldBe Health(true,"alive")
      }
    }
  }
}

