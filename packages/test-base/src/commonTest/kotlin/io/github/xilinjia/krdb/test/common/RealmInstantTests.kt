@file:Suppress("invisible_member", "invisible_reference")

package io.github.xilinjia.krdb.test.common

import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.RealmConfiguration
import io.github.xilinjia.krdb.entities.Sample
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.internal.platform.runBlocking
import io.github.xilinjia.krdb.internal.toDuration
import io.github.xilinjia.krdb.internal.toRealmInstant
import io.github.xilinjia.krdb.query.find
import io.github.xilinjia.krdb.test.common.utils.assertFailsWithMessage
import io.github.xilinjia.krdb.test.platform.PlatformUtils
import io.github.xilinjia.krdb.types.RealmInstant
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class RealmInstantTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun millisConversions() {
        listOf(
            0L.milliseconds,
            1669029663120L.milliseconds,
            (-1669029663120L).milliseconds
        ).forEach {
            assertEquals(it, it.toRealmInstant().toDuration())
        }
    }

    @Test
    fun nanosConversions() {
        listOf(
            0L.nanoseconds,
            1669029663120L.nanoseconds,
            (-1669029663120L).nanoseconds
        ).forEach {
            assertEquals(it, it.toRealmInstant().toDuration())
        }
    }

    // Test both unmanaged and managed boundaries
    @Test
    fun timestamp_boundaries() {
        roundTrip(RealmInstant.from(Long.MIN_VALUE, -999_999_999)) { min ->
            assertEquals(Long.MIN_VALUE, min.epochSeconds)
            assertEquals(-999_999_999, min.nanosecondsOfSecond)
            assertEquals(RealmInstant.MIN, min)
        }

        roundTrip(RealmInstant.from(Long.MAX_VALUE, 999_999_999)) { max ->
            assertEquals(Long.MAX_VALUE, max.epochSeconds)
            assertEquals(999_999_999, max.nanosecondsOfSecond)
            assertEquals(RealmInstant.MAX, max)
        }

        roundTrip(RealmInstant.from(Long.MAX_VALUE, Int.MAX_VALUE)) { maxOverflow ->
            assertEquals(RealmInstant.MAX, maxOverflow)
        }

        roundTrip(RealmInstant.from(Long.MAX_VALUE, 1_000_000_000)) { minOverflow ->
            assertEquals(RealmInstant.MAX, minOverflow)
        }

        roundTrip(RealmInstant.from(Long.MIN_VALUE, Int.MIN_VALUE)) { maxUnderflow ->
            assertEquals(RealmInstant.MIN, maxUnderflow)
        }

        roundTrip(RealmInstant.from(Long.MIN_VALUE, -1_000_000_000)) { minUnderflow ->
            assertEquals(RealmInstant.MIN, minUnderflow)
        }

        roundTrip(RealmInstant.from(0, 0)) { zero ->
            assertEquals(0, zero.epochSeconds)
            assertEquals(0, zero.nanosecondsOfSecond)
        }

        roundTrip(RealmInstant.from(0, 1)) { zeroPlusOne ->
            assertEquals(0, zeroPlusOne.epochSeconds)
            assertEquals(1, zeroPlusOne.nanosecondsOfSecond)
        }

        roundTrip(RealmInstant.from(0, -1)) { zeroMinusOne ->
            assertEquals(0, zeroMinusOne.epochSeconds)
            assertEquals(-1, zeroMinusOne.nanosecondsOfSecond)
        }
    }

    // Store value and retrieve it again
    private fun roundTrip(timestamp: RealmInstant, function: (RealmInstant) -> Unit) {

        // Test unmanaged objects
        function(timestamp)

        // Test managed objects
        realm.writeBlocking {
            val sample = copyToRealm(
                Sample().apply {
                    timestampField = timestamp
                }
            )
            val managedTimestamp = query<Sample>()
                .first()
                .find { sampleObject ->
                    assertNotNull(sampleObject)
                    sampleObject.timestampField
                }
            function(managedTimestamp)
            cancelWrite() // So we can use .first()
        }
    }

    @Test
    fun equals() {
        assertTrue(RealmInstant.from(42, 42) == (RealmInstant.from(42, 42)))
        assertFalse(RealmInstant.from(0, 0) == (RealmInstant.from(42, 42)))
        assertFalse(RealmInstant.from(42, 0) == (RealmInstant.from(42, 42)))
        assertFalse(RealmInstant.from(0, 42) == (RealmInstant.from(42, 42)))
    }

    @Test
    fun timestamp_hashCode() {
        assertEquals(
            RealmInstant.from(42, 42).hashCode(),
            (RealmInstant.from(42, 42).hashCode())
        )
        assertNotEquals(
            RealmInstant.from(0, 0).hashCode(),
            RealmInstant.from(42, 42).hashCode()
        )
        assertNotEquals(
            RealmInstant.from(42, 0).hashCode(),
            RealmInstant.from(42, 42).hashCode()
        )
        assertNotEquals(
            RealmInstant.from(0, 42).hashCode(),
            RealmInstant.from(42, 42).hashCode()
        )
    }

    @Test
    fun timestamp_toString() {
        val ts = RealmInstant.from(42, 420)
        assertEquals("RealmInstant(epochSeconds=42, nanosecondsOfSecond=420)", ts.toString())
    }

    @Test
    fun now() {
        runBlocking {
            // Get two different instants with some time in between calls
            val ts1 = RealmInstant.now()
            delay(100.milliseconds)
            val ts2 = RealmInstant.now()

            // When this method was implemented the unix epoch time was 1664980145.
            // Nanoseconds are too granular to perform tests on especially since the
            // Darwin implementation rounds the timestamp to a precision of milliseconds.
            val baselineEpoch = 1664980145
            assertTrue(ts1.epochSeconds > baselineEpoch)
            assertTrue(ts1.nanosecondsOfSecond >= 0)
            assertTrue(ts2.epochSeconds > baselineEpoch)
            assertTrue(ts2.nanosecondsOfSecond >= 0)

            // Assert the second instant is greater than the first one, also using Duration
            assertTrue(ts2 > ts1)
            val duration1 = ts1.epochSeconds.seconds + ts1.nanosecondsOfSecond.nanoseconds
            val duration2 = ts2.epochSeconds.seconds + ts2.nanosecondsOfSecond.nanoseconds
            assertTrue(duration2 > duration1)
        }
    }

    @Test
    fun mismatchingSign_throws() {
        assertFailsWithMessage<IllegalArgumentException>("Arguments must be both positive or negative.") {
            RealmInstant.from(Long.MAX_VALUE, Int.MIN_VALUE)
        }

        assertFailsWithMessage<IllegalArgumentException>("Arguments must be both positive or negative.") {
            RealmInstant.from(Long.MIN_VALUE, Int.MAX_VALUE)
        }
    }

    @Test
    fun compare() {
        val ts1 = RealmInstant.from(0, 0)
        val ts2 = RealmInstant.from(0, 1)
        val ts3 = RealmInstant.from(0, -1)
        val ts4 = RealmInstant.from(1, 0)
        val ts5 = RealmInstant.from(-1, 0)

        assertTrue(ts1.compareTo(ts2) < 0)
        assertTrue(ts1.compareTo(ts1) == 0)
        assertTrue(ts1.compareTo(ts3) > 0)
        assertTrue(ts1.compareTo(ts4) < 0)
        assertTrue(ts1.compareTo(ts5) > 0)
    }
}
