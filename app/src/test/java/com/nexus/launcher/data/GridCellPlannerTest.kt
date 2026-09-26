package com.nexus.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridCellPlannerTest {

    private fun cellsOf(placements: List<GridCellPlanner.Placement>): List<GridCellPlanner.Cell> =
        placements.flatMap { p ->
            (p.row until p.row + p.spanY).flatMap { r ->
                (p.column until p.column + p.spanX).map { c -> GridCellPlanner.Cell(p.page, c, r) }
            }
        }

    @Test
    fun portraitLayoutMapsIntoLandscapeWithoutOverlap() {
        // The real page-0 layout from the device: 6x10 portrait -> 10x6 landscape.
        val sources = listOf(
            GridCellPlanner.Source(55, 0, 0, 0, 4, 2),   // mosaic
            GridCellPlanner.Source(143, 0, 4, 0, 1, 2),  // live app box
            GridCellPlanner.Source(159, 0, 0, 2, 2, 2),  // weather widget
            GridCellPlanner.Source(123, 0, 2, 3, 1, 1),  // battery ring
            GridCellPlanner.Source(58, 0, 4, 5, 2, 2),   // Folder 1 (2x2)
            GridCellPlanner.Source(124, 0, 0, 8, 1, 1),  // app box
            GridCellPlanner.Source(89, 0, 0, 9, 1, 1),   // Reddit
            GridCellPlanner.Source(152, 0, 1, 9, 1, 1),  // Photos
            GridCellPlanner.Source(60, 0, 2, 9, 1, 1)    // Citi
        )
        val placements = GridCellPlanner.mapInto(sources, 6, 10, 10, 6, mutableSetOf())

        assertEquals(sources.size, placements.size)
        val cells = cellsOf(placements)
        assertEquals("no two items share a cell", cells.size, cells.toSet().size)
        placements.forEach { p ->
            assertTrue(p.column >= 0 && p.column + p.spanX <= 10)
            assertTrue(p.row >= 0 && p.row + p.spanY <= 6)
            assertEquals("everything fits on its own page", 0, p.page)
        }
    }

    @Test
    fun keepsRelativePosition() {
        // Bottom-left in portrait stays bottom-left in landscape.
        val placements = GridCellPlanner.mapInto(
            listOf(GridCellPlanner.Source(1, 0, 0, 9, 1, 1)), 6, 10, 10, 6, mutableSetOf()
        )
        assertEquals(0, placements.single().column)
        assertEquals(5, placements.single().row)
    }

    @Test
    fun foldsPortraitPageIntoTwoLandscapeHalves() {
        // 6x10 into 12x5: rows 0-4 stay put, rows 5-9 move to columns 6-11, rows 0-4.
        val sources = listOf(
            GridCellPlanner.Source(55, 0, 0, 0, 4, 2),   // mosaic, top half
            GridCellPlanner.Source(123, 0, 2, 3, 1, 1),  // battery ring, top half
            GridCellPlanner.Source(58, 0, 4, 5, 2, 2),   // Folder 1, bottom half
            GridCellPlanner.Source(89, 0, 0, 9, 1, 1)    // Reddit, bottom row
        )
        val byId = GridCellPlanner.mapInto(sources, 6, 10, 12, 5, mutableSetOf()).associateBy { it.id }
        assertEquals(0 to 0, byId.getValue(55).column to byId.getValue(55).row)
        assertEquals(2 to 3, byId.getValue(123).column to byId.getValue(123).row)
        assertEquals(10 to 0, byId.getValue(58).column to byId.getValue(58).row)
        assertEquals(6 to 4, byId.getValue(89).column to byId.getValue(89).row)
    }

    @Test
    fun foldsIntoElevenColumnsSharingTheMiddleColumn() {
        val sources = listOf(
            GridCellPlanner.Source(1, 0, 5, 0, 1, 1),  // top half, last column
            GridCellPlanner.Source(2, 0, 0, 5, 1, 1),  // bottom half, first column -> column 5
            GridCellPlanner.Source(3, 0, 5, 9, 1, 1)   // bottom half, last column -> column 10
        )
        val placements = GridCellPlanner.mapInto(sources, 6, 10, 11, 5, mutableSetOf())
        val byId = placements.associateBy { it.id }
        assertEquals(5 to 0, byId.getValue(1).column to byId.getValue(1).row)
        // Item 2 also targets (5,0), which item 1 took first: it moves to an adjacent free cell.
        val moved = byId.getValue(2)
        assertTrue(Math.abs(moved.column - 5) + Math.abs(moved.row - 0) == 1)
        assertEquals(10 to 4, byId.getValue(3).column to byId.getValue(3).row)
        val cells = placements.map { Triple(it.page, it.column, it.row) }
        assertEquals("no two items share a cell", cells.size, cells.toSet().size)
    }

    @Test
    fun foldsIntoFourRowsKeepingHalvesApart() {
        // 6x10 into 11x4 (exact-size landscape cells): halves still side by side, rows compressed.
        val sources = listOf(
            GridCellPlanner.Source(55, 0, 0, 0, 4, 2),   // mosaic, top half
            GridCellPlanner.Source(159, 0, 0, 2, 2, 2),  // weather, top half
            GridCellPlanner.Source(123, 0, 2, 3, 1, 1),  // battery ring, top half
            GridCellPlanner.Source(58, 0, 4, 5, 2, 2),   // Folder 1, bottom half
            GridCellPlanner.Source(89, 0, 0, 9, 1, 1),   // Reddit, bottom half
            GridCellPlanner.Source(60, 0, 2, 9, 1, 1)    // Citi, bottom half
        )
        val placements = GridCellPlanner.mapInto(sources, 6, 10, 11, 4, mutableSetOf())
        assertEquals(sources.size, placements.size)
        val cells = cellsOf(placements)
        assertEquals("no two items share a cell", cells.size, cells.toSet().size)
        val byId = placements.associateBy { it.id }
        placements.forEach { assertEquals(0, it.page); assertTrue(it.row + it.spanY <= 4) }
        // Top-half items stay left of the bottom-half ones.
        assertTrue(byId.getValue(55).column < 5 && byId.getValue(123).column < 6)
        assertTrue(byId.getValue(89).column >= 5 && byId.getValue(60).column >= 5)
    }

    @Test
    fun spillsToNextPageWhenFull() {
        val occupied = mutableSetOf<GridCellPlanner.Cell>()
        GridCellPlanner.occupy(occupied, 0, 0, 0, 2, 2)
        val cell = GridCellPlanner.place(occupied, 0, 0, 0, 1, 1, 2, 2)
        assertNotNull(cell)
        assertEquals(1, cell!!.page)
    }

    @Test
    fun spanLargerThanGridIsClamped() {
        val placements = GridCellPlanner.mapInto(
            listOf(GridCellPlanner.Source(1, 0, 0, 0, 5, 8)), 6, 10, 4, 4, mutableSetOf()
        )
        assertEquals(4, placements.single().spanX)
        assertEquals(4, placements.single().spanY)
    }

    @Test
    fun fitsRejectsOutOfBoundsAndOccupied() {
        val occupied = mutableSetOf(GridCellPlanner.Cell(0, 1, 1))
        assertFalse(GridCellPlanner.fits(occupied, 0, 0, 0, 2, 2, 4, 4))
        assertFalse(GridCellPlanner.fits(occupied, 0, 3, 0, 2, 1, 4, 4))
        assertTrue(GridCellPlanner.fits(occupied, 0, 2, 2, 2, 2, 4, 4))
        assertNull(GridCellPlanner.nearestFree(occupied, 0, 0, 0, 4, 4, 4, 4))
    }
}
