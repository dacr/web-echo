/*
 * Copyright 2021 David Crosson
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
package webecho.tools

import webecho.ServiceConfig
import yamusca.imports._
import yamusca.syntax._
import yamusca.implicits._

case class Templating(config: ServiceConfig) {

  def makeTemplateLayout[T](templateName: String)(context: Context): String = {
    val templateInput = getClass().getClassLoader().getResourceAsStream(templateName)
    val templateString = scala.io.Source.fromInputStream(templateInput).iterator.mkString
    val template = mustache.parse(templateString)
    mustache.render(template.toOption.get)(context)
  }
}
