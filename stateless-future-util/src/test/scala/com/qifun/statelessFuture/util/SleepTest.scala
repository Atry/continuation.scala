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

import scala.concurrent.duration._
import scala.util.control.Exception.Catcher
import com.qifun.statelessFuture.Future

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.Test
import org.junit.Assert._
import scala.util.control.TailCalls._

object SleepTest {
  private implicit val (logger, formatter, appender) = ZeroLoggerFactory.newLogger(this)
}

final class SleepTest {
  import SleepTest._
  @Test
  def `testSleep`() {
    val executor = Executors.newSingleThreadScheduledExecutor
    val sleepTime = 1.seconds
    val startTime = System.currentTimeMillis().milliseconds
    val sleep = Promise[Unit]
    sleep.completeWith(Future[Unit] {
      val sleep1s = Sleep(executor, 1.seconds)
      sleep1s.await
      Future[Unit] {
        logger.fine(s"I have slept 1 Seconds.")
      }.await
    })
    assertFalse(sleep.isCompleted)
    logger.fine("Before the evaluation of the Stateless Future `sleep`.")
    Blocking.blockingAwait(sleep)
    val finishTime = System.currentTimeMillis().milliseconds
    val actuallySleepTime = finishTime - startTime
    assertTrue(sleep.isCompleted)
    assertTrue(actuallySleepTime >= sleepTime)
  }
  

}