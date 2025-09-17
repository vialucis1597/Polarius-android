/*
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.wallet

import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.room.Room
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.room.model.RoomType
import org.matrix.android.sdk.api.query.QueryStringValue
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.api.session.getRoomSummary
import timber.log.Timber

data class DAOInfo(
    val daoId: String,
    val daoName: String
)

/**
 * DCA 룸 관련 유틸리티 클래스
 */
object DCARoomUtils {
    
    /**
     * 룸이 DCA 룸인지 확인하고 DAO 정보를 반환
     * @param room 확인할 룸
     * @param session Matrix 세션
     * @return DCA 룸이면 DAO 정보, 아니면 null
     */
    fun getDAOInfoIfDCARoom(room: Room, session: Session): DAOInfo? {
        try {
            Timber.d("🔍 Android: Checking if room ${room.roomId} is DCA room")
            // 룸의 부모 스페이스 이벤트들을 확인 (웹과 동일한 방식)
            val spaceParentEvents = room.stateService().getStateEvents(
                setOf(EventType.STATE_SPACE_PARENT),
                QueryStringValue.IsEmpty
            )
            Timber.d("🔍 Android: Found ${spaceParentEvents.size} space parent events")
            
            // 모든 스페이스 부모 이벤트의 상세 정보 로그
            spaceParentEvents.forEachIndexed { index, event ->
                Timber.d("🔍 Android: Space parent event $index: stateKey=${event.stateKey}, content=${event.content}")
            }
            
            for (event in spaceParentEvents) {
                val dcaSpaceId = event.stateKey
                if (dcaSpaceId.isNullOrEmpty()) continue
                
                // DCA 스페이스인지 확인
                val dcaSpace = session.getRoom(dcaSpaceId)
                val dcaSpaceSummary = session.getRoomSummary(dcaSpaceId)
                Timber.d("🔍 Android: Checking DCA space: $dcaSpaceId, type: ${dcaSpaceSummary?.roomType}, name: ${dcaSpaceSummary?.name}")
                
                if (dcaSpaceSummary?.roomType == RoomType.SPACE && dcaSpaceSummary.name == "DCA") {
                    // DCA 스페이스의 부모 DAO 스페이스 찾기
                    val daoSpaceParentEvents = dcaSpace?.stateService()?.getStateEvents(
                        setOf(EventType.STATE_SPACE_PARENT),
                        QueryStringValue.IsEmpty
                    ) ?: emptyList()
                    
                    for (daoEvent in daoSpaceParentEvents) {
                        val daoSpaceId = daoEvent.stateKey
                        if (daoSpaceId.isNullOrEmpty()) continue
                        
                        val daoSpaceSummary = session.getRoomSummary(daoSpaceId)
                        
                        if (daoSpaceSummary?.roomType == RoomType.SPACE) {
                            Timber.d("🔍 Android: Found DAO space: $daoSpaceId, name: ${daoSpaceSummary.name}")
                            return DAOInfo(
                                daoId = daoSpaceId,
                                daoName = daoSpaceSummary.name
                            )
                        }
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            Timber.e(e, "Error checking if room is DCA room")
            return null
        }
    }
    
    /**
     * 룸 ID로 DCA 룸인지 확인
     * @param roomId 확인할 룸 ID
     * @param session Matrix 세션
     * @return DCA 룸이면 DAO 정보, 아니면 null
     */
    fun getDAOInfoIfDCARoomById(roomId: String, session: Session): DAOInfo? {
        val room = session.getRoom(roomId) ?: return null
        return getDAOInfoIfDCARoom(room, session)
    }
    
    /**
     * 룸이 DCA 룸인지 간단히 확인
     * @param room 확인할 룸
     * @param session Matrix 세션
     * @return DCA 룸이면 true, 아니면 false
     */
    fun isDCARoom(room: Room, session: Session): Boolean {
        return getDAOInfoIfDCARoom(room, session) != null
    }
    
    /**
     * 룸 ID로 DCA 룸인지 간단히 확인
     * @param roomId 확인할 룸 ID
     * @param session Matrix 세션
     * @return DCA 룸이면 true, 아니면 false
     */
    fun isDCARoomById(roomId: String, session: Session): Boolean {
        return getDAOInfoIfDCARoomById(roomId, session) != null
    }
}
