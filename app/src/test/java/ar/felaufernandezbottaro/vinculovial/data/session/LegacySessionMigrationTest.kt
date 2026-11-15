package ar.felaufernandezbottaro.vinculovial.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LegacySessionMigrationTest {

    private class FakeLegacyStorage(var legacy: Boolean) : LegacySessionStorage {
        var purgeCount = 0
            private set

        override fun hasLegacyData(): Boolean = legacy

        override fun purge() {
            purgeCount++
            legacy = false
        }
    }

    @Test
    fun `con tokens y keyset de Tink del esquema anterior los borra`() {
        val storage = FakeLegacyStorage(legacy = true)

        LegacySessionMigration(storage).run()

        assertEquals(1, storage.purgeCount)
        assertFalse(storage.hasLegacyData())
    }

    @Test
    fun `sin restos del esquema anterior no hace nada`() {
        val storage = FakeLegacyStorage(legacy = false)

        LegacySessionMigration(storage).run()

        assertEquals(0, storage.purgeCount)
    }

    @Test
    fun `es idempotente, correr en cada arranque purga una sola vez`() {
        val storage = FakeLegacyStorage(legacy = true)
        val migration = LegacySessionMigration(storage)

        migration.run()
        migration.run()
        migration.run()

        assertEquals(1, storage.purgeCount)
    }
}
