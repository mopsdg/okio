/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

/**
 * Limits throughput in bytes per second. Calls to [take] may wait until the bytes can be allocated.
 * Note that [take] may allocate fewer bytes than those requested; the number of bytes allocated is
 * returned.
 *
 * This class has these tuning parameters:
 *
 *  * `bytesPerSecond`: maximum sustained throughput.
 *  * `minTake`: when the requested byte count isn't available, wait until we can allocate at least
 *    this many bytes. Use this to avoid a large number of small allocations.
 *  * `maxTake`: maximum number of bytes to allocate on any call. This is also the number of bytes
 *    that will returned before any waiting.
 */
internal class BandwidthCap {
  private var bytesPerSecond: Long = 0L
  private var minTake: Long = 8 * 1024 // 8 KiB.
  private var maxTake: Long = 256 * 1024 // 256 KiB.
  private var nanosForMaxTake: Long = 0L

  /**
   * The nanoTime that we've consumed all bytes through. This is never greater than the current
   * nanoTime plus nanosForMaxTake.
   */
  private var allocatedUntil: Long

  init {
    this.allocatedUntil = System.nanoTime()
  }

  /** Sets the rate at which bytes will be allocated. Use 0 for no limit. */
  fun setCap(
    bytesPerSecond: Long,
    minTake: Long = this.minTake,
    maxTake: Long = this.maxTake
  ) {
    synchronized(this) {
      require(bytesPerSecond >= 0)
      require(minTake > 0)
      require(maxTake >= minTake)

      this.bytesPerSecond = bytesPerSecond
      this.minTake = minTake
      this.maxTake = maxTake
      this.nanosForMaxTake = when {
        bytesPerSecond != 0L -> maxTake * 1_000_000_000L / bytesPerSecond
        else -> -1L
      }

      (this as Object).notifyAll()
    }
  }

  /**
   * Take up to `byteCount` bytes, waiting if necessary. Returns the number of bytes that were
   * taken.
   */
  fun take(byteCount: Long): Long {
    require(byteCount > 0)

    synchronized(this) {
      while (true) {
        if (bytesPerSecond == 0L) return byteCount // No limits.

        val now = System.nanoTime()
        val idleInNanos = maxOf(allocatedUntil - now, 0L)
        val usableNanos = nanosForMaxTake - idleInNanos
        val immediateBytes = bytesPerSecond * usableNanos / 1_000_000_000L

        // Fulfill the entire request without waiting.
        if (immediateBytes >= byteCount) {
          val byteCountNanos = byteCount * 1_000_000_000L / bytesPerSecond
          allocatedUntil = now + idleInNanos + byteCountNanos
          return byteCount
        }

        // Fulfill a big-enough block without waiting.
        if (immediateBytes >= minTake) {
          allocatedUntil = now + idleInNanos + usableNanos
          return immediateBytes
        }

        // Wait until we can write some bytes.
        val byteCountNanos = minOf(minTake, byteCount) * 1_000_000_000L / bytesPerSecond
        waitNanos(byteCountNanos - usableNanos)
      }
    }
    throw AssertionError() // Unreachable, but synchronized() doesn't know that.
  }

  private fun waitNanos(nanosToWait: Long) {
    val millisToWait = nanosToWait / 1_000_000L
    val remainderNanos = nanosToWait - (millisToWait * 1_000_000L)
    (this as Object).wait(millisToWait, remainderNanos.toInt())
  }
}