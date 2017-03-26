/*
 * stateless-future-util
 * Copyright 2014 深圳岂凡网络有限公司 (Shenzhen QiFun Network Corp., LTD)
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

package com.qifun.statelessFuture
package util

import scala.util.control.Exception.Catcher
import scala.util.control.TailCalls._
import java.util.concurrent.Executor

/**
 * Let the code after [[JumpInto.await]] run in `executor`.
 * @param executor Where the code after [[JumpInto.await]] runs.
 */
final case class JumpInto[TailRecResult](executor: Executor) extends Future.Stateless[Unit] {

  override final def onComplete(handler: Unit => TailRec[Unit])(implicit catcher: Catcher[TailRec[Unit]]): TailRec[Unit] = {
    done(executor.execute(new Runnable {
      override final def run(): Unit = {
        handler(()).result
      }
    }))
  }

}
