/*
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.wallet

import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.room.Room
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.query.QueryStringValue

class DCARoomUtilsTest {

    @Test
    fun `test getDAOInfoIfDCARoom returns null for non-DCA room`() {
        // Given
        val session = mock(Session::class.java)
        val room = mock(Room::class.java)
        val roomSummary = mock(RoomSummary::class.java)
        
        // Mock room state service to return empty space parent events
        val stateService = mock(org.matrix.android.sdk.api.session.room.RoomStateService::class.java)
        `when`(room.stateService()).thenReturn(stateService)
        `when`(stateService.getStateEvents(EventType.STATE_SPACE_PARENT, QueryStringValue.IsEmpty))
            .thenReturn(emptyList())
        
        // When
        val result = DCARoomUtils.getDAOInfoIfDCARoom(room, session)
        
        // Then
        assertNull(result)
    }

    @Test
    fun `test getDAOInfoIfDCARoom returns DAO info for DCA room`() {
        // Given
        val session = mock(Session::class.java)
        val room = mock(Room::class.java)
        val dcaSpace = mock(Room::class.java)
        val daoSpace = mock(Room::class.java)
        val dcaSpaceSummary = mock(RoomSummary::class.java)
        val daoSpaceSummary = mock(RoomSummary::class.java)
        
        val dcaSpaceId = "!dca:example.com"
        val daoSpaceId = "!dao:example.com"
        val daoName = "Test DAO"
        
        // Mock room state service
        val roomStateService = mock(org.matrix.android.sdk.api.session.room.RoomStateService::class.java)
        val dcaStateService = mock(org.matrix.android.sdk.api.session.room.RoomStateService::class.java)
        
        `when`(room.stateService()).thenReturn(roomStateService)
        `when`(dcaSpace.stateService()).thenReturn(dcaStateService)
        
        // Mock space parent events
        val spaceParentEvent = mock(Event::class.java)
        `when`(spaceParentEvent.stateKey).thenReturn(dcaSpaceId)
        
        val daoSpaceParentEvent = mock(Event::class.java)
        `when`(daoSpaceParentEvent.stateKey).thenReturn(daoSpaceId)
        
        `when`(roomStateService.getStateEvents(EventType.STATE_SPACE_PARENT, QueryStringValue.IsEmpty))
            .thenReturn(listOf(spaceParentEvent))
        `when`(dcaStateService.getStateEvents(EventType.STATE_SPACE_PARENT, QueryStringValue.IsEmpty))
            .thenReturn(listOf(daoSpaceParentEvent))
        
        // Mock session methods
        `when`(session.getRoom(dcaSpaceId)).thenReturn(dcaSpace)
        `when`(session.getRoom(daoSpaceId)).thenReturn(daoSpace)
        `when`(session.getRoomSummary(dcaSpaceId)).thenReturn(dcaSpaceSummary)
        `when`(session.getRoomSummary(daoSpaceId)).thenReturn(daoSpaceSummary)
        
        // Mock room properties
        `when`(dcaSpace.isSpace()).thenReturn(true)
        `when`(daoSpace.isSpace()).thenReturn(true)
        `when`(dcaSpaceSummary.name).thenReturn("DCA")
        `when`(daoSpaceSummary.name).thenReturn(daoName)
        
        // When
        val result = DCARoomUtils.getDAOInfoIfDCARoom(room, session)
        
        // Then
        assertNotNull(result)
        assertEquals(daoSpaceId, result?.daoId)
        assertEquals(daoName, result?.daoName)
    }

    @Test
    fun `test isDCARoom returns false for non-DCA room`() {
        // Given
        val session = mock(Session::class.java)
        val room = mock(Room::class.java)
        
        val stateService = mock(org.matrix.android.sdk.api.session.room.RoomStateService::class.java)
        `when`(room.stateService()).thenReturn(stateService)
        `when`(stateService.getStateEvents(EventType.STATE_SPACE_PARENT, QueryStringValue.IsEmpty))
            .thenReturn(emptyList())
        
        // When
        val result = DCARoomUtils.isDCARoom(room, session)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `test isDCARoom returns true for DCA room`() {
        // Given
        val session = mock(Session::class.java)
        val room = mock(Room::class.java)
        val dcaSpace = mock(Room::class.java)
        val daoSpace = mock(Room::class.java)
        val dcaSpaceSummary = mock(RoomSummary::class.java)
        val daoSpaceSummary = mock(RoomSummary::class.java)
        
        val dcaSpaceId = "!dca:example.com"
        val daoSpaceId = "!dao:example.com"
        
        // Mock room state service
        val roomStateService = mock(org.matrix.android.sdk.api.session.room.RoomStateService::class.java)
        val dcaStateService = mock(org.matrix.android.sdk.api.session.room.RoomStateService::class.java)
        
        `when`(room.stateService()).thenReturn(roomStateService)
        `when`(dcaSpace.stateService()).thenReturn(dcaStateService)
        
        // Mock space parent events
        val spaceParentEvent = mock(Event::class.java)
        `when`(spaceParentEvent.stateKey).thenReturn(dcaSpaceId)
        
        val daoSpaceParentEvent = mock(Event::class.java)
        `when`(daoSpaceParentEvent.stateKey).thenReturn(daoSpaceId)
        
        `when`(roomStateService.getStateEvents(EventType.STATE_SPACE_PARENT, QueryStringValue.IsEmpty))
            .thenReturn(listOf(spaceParentEvent))
        `when`(dcaStateService.getStateEvents(EventType.STATE_SPACE_PARENT, QueryStringValue.IsEmpty))
            .thenReturn(listOf(daoSpaceParentEvent))
        
        // Mock session methods
        `when`(session.getRoom(dcaSpaceId)).thenReturn(dcaSpace)
        `when`(session.getRoom(daoSpaceId)).thenReturn(daoSpace)
        `when`(session.getRoomSummary(dcaSpaceId)).thenReturn(dcaSpaceSummary)
        `when`(session.getRoomSummary(daoSpaceId)).thenReturn(daoSpaceSummary)
        
        // Mock room properties
        `when`(dcaSpace.isSpace()).thenReturn(true)
        `when`(daoSpace.isSpace()).thenReturn(true)
        `when`(dcaSpaceSummary.name).thenReturn("DCA")
        `when`(daoSpaceSummary.name).thenReturn("Test DAO")
        
        // When
        val result = DCARoomUtils.isDCARoom(room, session)
        
        // Then
        assertTrue(result)
    }
}
