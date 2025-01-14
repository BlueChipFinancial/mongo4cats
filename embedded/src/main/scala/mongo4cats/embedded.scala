/*
 * Copyright 2020 Kirill5k
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mongo4cats

import cats.effect.{Async, Resource}
import cats.implicits._
import de.flapdoodle.embed.mongo.config.{MongodConfig, Net}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{MongodProcess, MongodStarter}
import de.flapdoodle.embed.process.runtime.Network
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object embedded {

  object EmbeddedMongo {
    private val starter = MongodStarter.getDefaultInstance
    private val logger  = LoggerFactory.getLogger("EmbeddedMongo")

    def start[F[_]](
        config: MongodConfig,
        maxAttempts: Int = 10,
        attempt: Int = 0
    )(implicit F: Async[F]): Resource[F, MongodProcess] =
      if (attempt >= maxAttempts)
        Resource.eval(new RuntimeException("Failed to start embedded mongo too many times").raiseError[F, MongodProcess])
      else
        Resource
          .make(F.delay(starter.prepare(config)))(ex => F.delay(ex.stop()))
          .flatMap(ex => Resource.make(F.delay(ex.start()))(p => F.delay(p.stop())))
          .handleErrorWith { e =>
            Resource.eval(F.delay(logger.error(e.getMessage, e)) *> F.sleep(attempt.seconds)) *>
              start[F](config, maxAttempts, attempt + 1)
          }
  }

  trait EmbeddedMongo {
    protected val mongoHost = "localhost"
    protected val mongoPort = 12345

    def withRunningEmbeddedMongo[F[_]: Async, A](test: => F[A]): F[A] = {
      val mongodConfig = MongodConfig
        .builder()
        .version(Version.Main.PRODUCTION)
        .net(new Net(mongoHost, mongoPort, Network.localhostIsIPv6))
        .build

      EmbeddedMongo
        .start[F](mongodConfig)
        .use(_ => test)
    }
  }
}
