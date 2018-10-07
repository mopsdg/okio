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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test

class BandwidthCapTest {
  @Test fun test() {
    val flowRate = BandwidthCap()
    flowRate.setCap(bytesPerSecond = 20, minTake = 5, maxTake = 10)

    val stopwatch = Stopwatch()

    // We get the first 10 bytes immediately (that's maxTake).
    assertThat(flowRate.take(40)).isEqualTo(10)
    stopwatch.assertElapsed(0.00)

    // Wait a quarter second for each subsequent 5 bytes (that's minTake).
    assertThat(flowRate.take(30)).isEqualTo(5)
    stopwatch.assertElapsed(0.25)

    assertThat(flowRate.take(25)).isEqualTo(5)
    stopwatch.assertElapsed(0.25)

    assertThat(flowRate.take(20)).isEqualTo(5)
    stopwatch.assertElapsed(0.25)

    assertThat(flowRate.take(15)).isEqualTo(5)
    stopwatch.assertElapsed(0.25)

    assertThat(flowRate.take(10)).isEqualTo(5)
    stopwatch.assertElapsed(0.25)

    assertThat(flowRate.take(5)).isEqualTo(5)
    stopwatch.assertElapsed(0.25)
  }

  class Stopwatch {
    private val start = System.nanoTime() / 1e9
    private var offset = 0.0

    fun assertElapsed(elapsed: Double) {
      offset += elapsed
      assertThat(System.nanoTime() / 1e9 - start).isCloseTo(offset, within(0.2))
    }
  }
}
