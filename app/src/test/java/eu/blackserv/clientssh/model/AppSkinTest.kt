package eu.blackserv.clientssh.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSkinTest {
    @Test
    fun selectableSkinsContainOnlyApprovedPremiumThemes() {
        assertEquals(
            listOf(AppSkin.SAPPHIRE, AppSkin.AURORA, AppSkin.OBSIDIAN),
            AppSkin.selectableEntries,
        )
        assertTrue(AppSkin.selectableEntries.all { it.selectable })
    }

    @Test
    fun legacySkinsMigrateVisuallyToSapphire() {
        assertEquals(AppSkin.SAPPHIRE, AppSkin.GRAPHITE.canonical)
        assertEquals(AppSkin.SAPPHIRE, AppSkin.NEON.canonical)
        assertFalse(AppSkin.GRAPHITE.selectable)
        assertFalse(AppSkin.NEON.selectable)
    }
}
