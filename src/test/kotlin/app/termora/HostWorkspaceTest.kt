package app.termora

import com.formdev.flatlaf.util.UIScale
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostWorkspaceTest {
    @Test
    fun `SSH address omits default port and does not duplicate host in protocol`() {
        val host = Host(name = "web", protocol = "SSH", host = "192.0.2.10", username = "deploy", port = 22)
        assertEquals("deploy@192.0.2.10", hostCardAddress(host))
        assertEquals("SSH", hostCardProtocol(host))
        assertEquals("192.0.2.10:2222", hostCardAddress(host.copy(username = "", port = 2222)))
    }

    @Test
    fun `nonstandard IPv6 port is unambiguous`() {
        val host = Host(name = "ipv6", protocol = "SSH", host = "2001:db8::1", port = 2200)
        assertEquals("[2001:db8::1]:2200", hostCardAddress(host))
        assertEquals("[2001:db8::1]:2200", hostCardAddress(host.copy(host = "[2001:db8::1]")))
    }

    @Test
    fun `serial cards retain port and baud rate`() {
        val host = Host(name = "serial", protocol = "Serial",
            options = Options.Default.copy(serialComm = SerialComm(port = "COM3", baudRate = 115200)))
        assertEquals("COM3", hostCardAddress(host))
        assertEquals("Serial · 115200 baud", hostCardProtocol(host))
    }

    @Test
    fun `groups share columns and wrap without overflowing at different widths`() {
        for (logicalWidth in listOf(180, 532, 900, 1440, 1900)) {
            val width = UIScale.scale(logicalWidth)
            val grid = HostCardGridLayout { width }
            val panel = JPanel(grid)
            repeat(13) { panel.add(JPanel()) }
            panel.setSize(width, panel.preferredSize.height)
            panel.doLayout()
            val columns = grid.columns(width)
            assertEquals(width, panel.getComponent(columns - 1).bounds.let { it.x + it.width })
            panel.components.forEach {
                assertTrue(it.x >= 0 && it.x + it.width <= width)
                assertTrue(it.y >= 0 && it.y + it.height <= panel.height)
                assertTrue(it.width > 0)
            }
            assertEquals(panel.getComponent(0).x, panel.getComponent(columns).x)
            val single = JPanel(HostCardGridLayout { width }).apply {
                add(JPanel())
                setSize(width, preferredSize.height)
                doLayout()
            }
            assertEquals(panel.getComponent(0).bounds, single.getComponent(0).bounds)
        }
    }

    @Test
    fun `card drag reorders hosts inside a group without replacing identities`() {
        val a = Host(id = "a", name = "A", protocol = "SSH", sort = 0)
        val b = Host(id = "b", name = "B", protocol = "SSH", sort = 1)
        val c = Host(id = "c", name = "C", protocol = "SSH", sort = 2)
        val original = listOf(a, b, c)
        val updates = cardDropUpdates(original, draggedId = "c", targetParentId = "0", targetIndex = 0)
        val byId = updates.associateBy { it.id }
        val result = original.map { byId[it.id] ?: it }.sortedBy { it.sort }

        assertEquals(listOf("c", "a", "b"), result.map { it.id })
        assertEquals(listOf(0L, 1L, 2L), result.map { it.sort })
        assertEquals(setOf("a", "b", "c"), result.mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun `card drag can move a host into a folder while preserving its id`() {
        val folder = Host(id = "folder", name = "Production", protocol = "Folder", ownerId = "me")
        val root = Host(id = "root", name = "Root", protocol = "SSH", ownerId = "me", sort = 0)
        val nested = Host(id = "nested", name = "Nested", protocol = "SSH", ownerId = "me", parentId = folder.id, sort = 0)
        val original = listOf(folder, root, nested)
        val updates = cardDropUpdates(original, draggedId = root.id, targetParentId = folder.id, targetIndex = 1)
        val byId = updates.associateBy { it.id }
        val result = original.map { byId[it.id] ?: it }
        val moved = result.single { it.id == root.id }
        val folderHosts = result.filter { !it.isFolder && it.parentId == folder.id }.sortedBy { it.sort }

        assertEquals(root.id, moved.id)
        assertEquals(folder.id, moved.parentId)
        assertEquals(listOf("nested", "root"), folderHosts.map { it.id })
        assertEquals(listOf(0L, 1L), folderHosts.map { it.sort })
    }
}