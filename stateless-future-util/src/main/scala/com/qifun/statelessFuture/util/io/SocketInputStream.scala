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
package io

import scala.util.control.Exception.Catcher
import java.nio.channels._
import scala.annotation.tailrec
import java.io.InputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.io.IOException
import com.dongxiguo.fastring.Fastring.Implicits._
import scala.concurrent.duration.Duration
import scala.collection.generic.Growable

private object SocketInputStream {
  implicit private val (logger, formatter, appender) = ZeroLoggerFactory.newLogger(this)
}

/**
 * @define This SocketInputStream
 */
abstract class SocketInputStream
  extends PagedInputStream(collection.mutable.Queue.empty[ByteBuffer])
  with LimitablePagedInputStream {
  import SocketInputStream._
  import formatter._

  protected val socket: AsynchronousSocketChannel

  /**
   * 每次读取Socket最少要准备多大的缓冲区
   */
  protected def minBufferSizePerRead = 1500

  protected def readingTimeout: Duration

  private def readingTimeoutLong: Long = {
    if (readingTimeout.isFinite) {
      readingTimeout.length match {
        case 0L => throw new IllegalArgumentException("writingTimeout must not be zero!")
        case l => l
      }
    } else {
      0L
    }
  }

  private def readingTimeoutUnit: TimeUnit = {
    if (readingTimeout.isFinite) {
      readingTimeout.unit
    } else {
      TimeUnit.SECONDS
    }
  }

  /**
   * Close [[socket]].
   */
  override final def close() {
    socket.close()
  }

  /**
   * Reads data from channel, until the bytes read is not less than `bytesToRead`
   */
  @throws(classOf[IOException])
  private def readChannel(
    bytesToRead: Long, buffers: Array[ByteBuffer],
    offset: Int, length: Int): Future[Unit] = Future {
    val n =
      Nio2Future.read(
        socket,
        buffers, offset, length,
        readingTimeoutLong, readingTimeoutUnit).await
    if (n >= 0 && n < bytesToRead) {
      val newOffset = buffers.indexWhere(
        { buffer => buffer.hasRemaining },
        offset)
      readChannel(bytesToRead - n,
        buffers,
        newOffset,
        length - newOffset + offset).await
    }
  }

  /**
   * Reads data from channel, until the bytes read is not less than `bytesToRead`
   */
  @throws(classOf[IOException])
  private def readChannel(bytesToRead: Int, buffer: ByteBuffer): Future[Unit] = Future {
    val n =
      Nio2Future.read(socket, buffer, readingTimeoutLong, readingTimeoutUnit).await
    if (n >= 0 && n < bytesToRead) {
      readChannel(bytesToRead - n, buffer).await
    }
  }

  @throws(classOf[IOException])
  private def externalRead(bytesToRead: Int): Future[Unit] = Future {
    val bufferSize = math.max(bytesToRead, minBufferSizePerRead)
    if (buffers.isEmpty) {
      val buffer = ByteBuffer.allocate(bufferSize)
      readChannel(bytesToRead, buffer).await
      buffer.flip()
      buffers.enqueue(buffer)
    } else {
      val buffer0 = {
        val t = buffers.last.duplicate
        t.mark()
        t.position(t.limit)
        t.limit(t.capacity)
        t.slice
      }
      if (bufferSize > buffer0.remaining) {
        val buffer1 = ByteBuffer.allocate(bufferSize - buffer0.remaining)
        readChannel(bytesToRead, Array(buffer0, buffer1), 0, 2).await
        buffer0.flip()
        buffers.enqueue(buffer0)
        buffer1.flip()
        buffers.enqueue(buffer1)
      } else {
        readChannel(bytesToRead, buffer0).await
        buffer0.flip()
        buffers.enqueue(buffer0)
      }
    }
  }

  /**
   * 准备`bytesRequired`字节的数据。
   *
   * @return 返回的[[Future]]成功执行完毕后，[[available]]会变为`bytesRequired`.
   * @note 在[[available_=]]返回的[[Future]]执行完毕前，如果调用[[read]]或[[skip]]，将导致未定义行为。
   * @throws java.io.EOFException 如果对面已经断开连接，会触发本异常
   * @example (socketInputStream.available = 20).await // 等待20字节的数据
   */
  @throws(classOf[EOFException])
  final def available_=(bytesRequired: Int): Future.Stateless[Unit] = Future {
    logger.fine {
      fast"Bytes avaliable now: ${available.toString}, expected: ${bytesRequired.toString}"
    }
    val c = capacity
    if (bytesRequired > c) {
      logger.finest("Read from socket.")
      try {
        externalRead(bytesRequired - c).await
      } catch {
        case e: Exception =>
          limit = math.min(bytesRequired, capacity)
          logger.fine(e)
          throw e
      }
      val newCapacity = capacity
      if (bytesRequired > newCapacity) {
        limit = newCapacity
        val e = new EOFException
        logger.fine(e)
        throw e
      } else {
        limit = bytesRequired
      }
    } else {
      logger.finest("Bytes available is enough. Don't read from socket.")
      limit = bytesRequired
    }
    logger.finer {
      fast"Bytes available is ${limit.toString} now."
    }
  }
}
