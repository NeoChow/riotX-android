/*
 *
 *  * Copyright 2019 New Vector Ltd
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package im.vector.matrix.android.internal.session.room.timeline

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import im.vector.matrix.android.api.MatrixCallback
import im.vector.matrix.android.api.session.events.model.EventType
import im.vector.matrix.android.api.session.room.timeline.Timeline
import im.vector.matrix.android.api.session.room.timeline.TimelineEvent
import im.vector.matrix.android.api.util.CancelableBag
import im.vector.matrix.android.api.util.addTo
import im.vector.matrix.android.internal.database.mapper.asDomain
import im.vector.matrix.android.internal.database.model.*
import im.vector.matrix.android.internal.database.query.findIncludingEvent
import im.vector.matrix.android.internal.database.query.findLastLiveChunkFromRoom
import im.vector.matrix.android.internal.database.query.where
import im.vector.matrix.android.internal.database.query.whereInRoom
import im.vector.matrix.android.internal.task.TaskExecutor
import im.vector.matrix.android.internal.task.configureWith
import im.vector.matrix.android.internal.util.Debouncer
import io.realm.*
import timber.log.Timber
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


private const val INITIAL_LOAD_SIZE = 20
private const val MIN_FETCHING_COUNT = 30
private const val DISPLAY_INDEX_UNKNOWN = Int.MIN_VALUE
private const val THREAD_NAME = "TIMELINE_DB_THREAD"

internal class DefaultTimeline(
        private val roomId: String,
        private val initialEventId: String? = null,
        private val realmConfiguration: RealmConfiguration,
        private val taskExecutor: TaskExecutor,
        private val contextOfEventTask: GetContextOfEventTask,
        private val timelineEventFactory: TimelineEventFactory,
        private val paginationTask: PaginationTask,
        private val allowedTypes: List<String>?
) : Timeline {

    override var listener: Timeline.Listener? = null
        set(value) {
            field = value
            backgroundHandler.get()?.post {
                postSnapshot()
            }
        }

    private val isStarted = AtomicBoolean(false)
    private val isReady = AtomicBoolean(false)
    private val backgroundHandlerThread = AtomicReference<HandlerThread>()
    private val backgroundHandler = AtomicReference<Handler>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundRealm = AtomicReference<Realm>()
    private val cancelableBag = CancelableBag()
    private val debouncer = Debouncer(mainHandler)

    private lateinit var liveEvents: RealmResults<EventEntity>
    private var roomEntity: RoomEntity? = null

    private var prevDisplayIndex: Int = DISPLAY_INDEX_UNKNOWN
    private var nextDisplayIndex: Int = DISPLAY_INDEX_UNKNOWN
    private val isLive = initialEventId == null
    private val builtEvents = Collections.synchronizedList<TimelineEvent>(ArrayList())
    private val builtEventsIdMap = Collections.synchronizedMap(HashMap<String, Int>())
    private val backwardsPaginationState = AtomicReference(PaginationState())
    private val forwardsPaginationState = AtomicReference(PaginationState())


    private lateinit var eventRelations: RealmResults<EventAnnotationsSummaryEntity>

    private val eventsChangeListener = OrderedRealmCollectionChangeListener<RealmResults<EventEntity>> { results, changeSet ->
        if (changeSet.state == OrderedCollectionChangeSet.State.INITIAL) {
            handleInitialLoad()
        } else {
            // If changeSet has deletion we are having a gap, so we clear everything
            if (changeSet.deletionRanges.isNotEmpty()) {
                prevDisplayIndex = DISPLAY_INDEX_UNKNOWN
                nextDisplayIndex = DISPLAY_INDEX_UNKNOWN
                builtEvents.clear()
                builtEventsIdMap.clear()
                timelineEventFactory.clear()
            }
            changeSet.insertionRanges.forEach { range ->
                val (startDisplayIndex, direction) = if (range.startIndex == 0) {
                    Pair(liveEvents[range.length - 1]!!.displayIndex, Timeline.Direction.FORWARDS)
                } else {
                    Pair(liveEvents[range.startIndex]!!.displayIndex, Timeline.Direction.BACKWARDS)
                }
                val state = getPaginationState(direction)
                if (state.isPaginating) {
                    // We are getting new items from pagination
                    val shouldPostSnapshot = paginateInternal(startDisplayIndex, direction, state.requestedCount)
                    if (shouldPostSnapshot) {
                        postSnapshot()
                    }
                } else {
                    // We are getting new items from sync
                    buildTimelineEvents(startDisplayIndex, direction, range.length.toLong())
                    postSnapshot()
                }
            }

            var hasChanged = false
            changeSet.changes.forEach {index ->
                val eventEntity = results[index]
                eventEntity?.eventId?.let { eventId ->
                    builtEventsIdMap[eventId]?.let { builtIndex ->
                        //Update the relation of existing event
                        builtEvents[builtIndex]?.let { te ->
                            builtEvents[builtIndex] = timelineEventFactory.create(eventEntity)
                            hasChanged = true
                        }
                    }
                }
            }
            if (hasChanged) postSnapshot()
        }
    }

    private val relationsListener = OrderedRealmCollectionChangeListener<RealmResults<EventAnnotationsSummaryEntity>> { collection, changeSet ->

        var hasChange = false

        (changeSet.insertions + changeSet.changes).forEach {
            val eventRelations = collection[it]
            if (eventRelations != null) {
                builtEventsIdMap[eventRelations.eventId]?.let { builtIndex ->
                    //Update the relation of existing event
                    builtEvents[builtIndex]?.let { te ->
                        builtEvents[builtIndex] = te.copy(annotations = eventRelations.asDomain())
                        hasChange = true
                    }
                }
            }
        }
        if (hasChange)
            postSnapshot()
    }

// Public methods ******************************************************************************

    override fun paginate(direction: Timeline.Direction, count: Int) {
        backgroundHandler.get()?.post {
            if (!canPaginate(direction)) {
                return@post
            }
            Timber.v("Paginate $direction of $count items")
            val startDisplayIndex = if (direction == Timeline.Direction.BACKWARDS) prevDisplayIndex else nextDisplayIndex
            val shouldPostSnapshot = paginateInternal(startDisplayIndex, direction, count)
            if (shouldPostSnapshot) {
                postSnapshot()
            }
        }
    }


    override fun start() {
        if (isStarted.compareAndSet(false, true)) {
            Timber.v("Start timeline for roomId: $roomId and eventId: $initialEventId")
            val handlerThread = HandlerThread(THREAD_NAME + hashCode())
            handlerThread.start()
            val handler = Handler(handlerThread.looper)
            this.backgroundHandlerThread.set(handlerThread)
            this.backgroundHandler.set(handler)
            handler.post {
                val realm = Realm.getInstance(realmConfiguration)
                backgroundRealm.set(realm)
                clearUnlinkedEvents(realm)

                roomEntity = RoomEntity.where(realm, roomId = roomId).findFirst()?.also {
                    it.sendingTimelineEvents.addChangeListener { _ ->
                        postSnapshot()
                    }
                }

                liveEvents = buildEventQuery(realm)
                        .sort(EventEntityFields.DISPLAY_INDEX, Sort.DESCENDING)
                        .findAllAsync()
                        .also { it.addChangeListener(eventsChangeListener) }

                isReady.set(true)

                eventRelations = EventAnnotationsSummaryEntity.whereInRoom(realm, roomId)
                        .findAllAsync()
                        .also { it.addChangeListener(relationsListener) }
            }
        }
    }

    override fun dispose() {
        if (isStarted.compareAndSet(true, false)) {
            Timber.v("Dispose timeline for roomId: $roomId and eventId: $initialEventId")
            backgroundHandler.get()?.post {
                cancelableBag.cancel()
                liveEvents.removeAllChangeListeners()
                backgroundRealm.getAndSet(null).also {
                    it.close()
                }
                backgroundHandler.set(null)
                backgroundHandlerThread.getAndSet(null)?.quit()
            }
        }
    }

    override fun hasMoreToLoad(direction: Timeline.Direction): Boolean {
        return hasMoreInCache(direction) || !hasReachedEnd(direction)
    }

// Private methods *****************************************************************************

    private fun hasMoreInCache(direction: Timeline.Direction): Boolean {
        val localRealm = Realm.getInstance(realmConfiguration)
        val eventEntity = buildEventQuery(localRealm).findFirst(direction) ?: return false
        val hasMoreInCache = if (direction == Timeline.Direction.FORWARDS) {
            val firstEvent = builtEvents.firstOrNull() ?: return true
            firstEvent.displayIndex < eventEntity.displayIndex
        } else {
            val lastEvent = builtEvents.lastOrNull() ?: return true
            lastEvent.displayIndex > eventEntity.displayIndex
        }
        localRealm.close()
        return hasMoreInCache
    }

    private fun hasReachedEnd(direction: Timeline.Direction): Boolean {
        val localRealm = Realm.getInstance(realmConfiguration)
        val currentChunk = findCurrentChunk(localRealm) ?: return false
        val hasReachedEnd = if (direction == Timeline.Direction.FORWARDS) {
            currentChunk.isLastForward
        } else {
            val eventEntity = buildEventQuery(localRealm).findFirst(direction)
            currentChunk.isLastBackward || eventEntity?.type == EventType.STATE_ROOM_CREATE
        }
        localRealm.close()
        return hasReachedEnd
    }


    /**
     * This has to be called on TimelineThread as it access realm live results
     * @return true if createSnapshot should be posted
     */
    private fun paginateInternal(startDisplayIndex: Int,
                                 direction: Timeline.Direction,
                                 count: Int): Boolean {
        updatePaginationState(direction) { it.copy(requestedCount = count, isPaginating = true) }
        val builtCount = buildTimelineEvents(startDisplayIndex, direction, count.toLong())
        val shouldFetchMore = builtCount < count && !hasReachedEnd(direction)
        if (shouldFetchMore) {
            val newRequestedCount = count - builtCount
            updatePaginationState(direction) { it.copy(requestedCount = newRequestedCount) }
            val fetchingCount = Math.max(MIN_FETCHING_COUNT, newRequestedCount)
            executePaginationTask(direction, fetchingCount)
        } else {
            updatePaginationState(direction) { it.copy(isPaginating = false, requestedCount = 0) }
        }

        return !shouldFetchMore
    }

    private fun createSnapshot(): List<TimelineEvent> {
        return buildSendingEvents() + builtEvents.toList()
    }

    private fun buildSendingEvents(): List<TimelineEvent> {
        val sendingEvents = ArrayList<TimelineEvent>()
        if (hasReachedEnd(Timeline.Direction.FORWARDS)) {
            roomEntity?.sendingTimelineEvents?.forEach {
                val timelineEvent = timelineEventFactory.create(it)
                sendingEvents.add(timelineEvent)
            }
        }
        return sendingEvents
    }

    private fun canPaginate(direction: Timeline.Direction): Boolean {
        return isReady.get() && !getPaginationState(direction).isPaginating && hasMoreToLoad(direction)
    }

    private fun getPaginationState(direction: Timeline.Direction): PaginationState {
        return when (direction) {
            Timeline.Direction.FORWARDS -> forwardsPaginationState.get()
            Timeline.Direction.BACKWARDS -> backwardsPaginationState.get()
        }
    }

    private fun updatePaginationState(direction: Timeline.Direction, update: (PaginationState) -> PaginationState) {
        val stateReference = when (direction) {
            Timeline.Direction.FORWARDS -> forwardsPaginationState
            Timeline.Direction.BACKWARDS -> backwardsPaginationState
        }
        val currentValue = stateReference.get()
        val newValue = update(currentValue)
        stateReference.set(newValue)
    }

    /**
     * This has to be called on TimelineThread as it access realm live results
     */
    private fun handleInitialLoad() {
        var shouldFetchInitialEvent = false
        val initialDisplayIndex = if (isLive) {
            liveEvents.firstOrNull()?.displayIndex
        } else {
            val initialEvent = liveEvents.where().equalTo(EventEntityFields.EVENT_ID, initialEventId).findFirst()
            shouldFetchInitialEvent = initialEvent == null
            initialEvent?.displayIndex
        } ?: DISPLAY_INDEX_UNKNOWN

        prevDisplayIndex = initialDisplayIndex
        nextDisplayIndex = initialDisplayIndex
        if (initialEventId != null && shouldFetchInitialEvent) {
            fetchEvent(initialEventId)
        } else {
            val count = Math.min(INITIAL_LOAD_SIZE, liveEvents.size)
            if (isLive) {
                paginateInternal(initialDisplayIndex, Timeline.Direction.BACKWARDS, count)
            } else {
                paginateInternal(initialDisplayIndex, Timeline.Direction.FORWARDS, count / 2)
                paginateInternal(initialDisplayIndex, Timeline.Direction.BACKWARDS, count / 2)
            }
        }
        postSnapshot()
    }

    /**
     * This has to be called on TimelineThread as it access realm live results
     */
    private fun executePaginationTask(direction: Timeline.Direction, limit: Int) {
        val token = getTokenLive(direction) ?: return
        val params = PaginationTask.Params(roomId = roomId,
                from = token,
                direction = direction.toPaginationDirection(),
                limit = limit)

        Timber.v("Should fetch $limit items $direction")
        paginationTask.configureWith(params)
                .enableRetry()
                .dispatchTo(object : MatrixCallback<TokenChunkEventPersistor.Result> {
                    override fun onSuccess(data: TokenChunkEventPersistor.Result) {
                        if (data == TokenChunkEventPersistor.Result.SUCCESS) {
                            Timber.v("Success fetching $limit items $direction from pagination request")
                        } else {
                            // Database won't be updated, so we force pagination request
                            backgroundHandler.get()?.post {
                                executePaginationTask(direction, limit)
                            }
                        }
                    }

                    override fun onFailure(failure: Throwable) {
                        Timber.v("Failure fetching $limit items $direction from pagination request")
                    }
                })
                .executeBy(taskExecutor)
                .addTo(cancelableBag)
    }

    /**
     * This has to be called on TimelineThread as it access realm live results
     */

    private fun getTokenLive(direction: Timeline.Direction): String? {
        val chunkEntity = getLiveChunk() ?: return null
        return if (direction == Timeline.Direction.BACKWARDS) chunkEntity.prevToken else chunkEntity.nextToken
    }


    /**
     * This has to be called on TimelineThread as it access realm live results
     */
    private fun getLiveChunk(): ChunkEntity? {
        return liveEvents.firstOrNull()?.chunk?.firstOrNull()
    }

    /**
     * This has to be called on TimelineThread as it access realm live results
     * @return number of items who have been added
     */
    private fun buildTimelineEvents(startDisplayIndex: Int,
                                    direction: Timeline.Direction,
                                    count: Long): Int {
        if (count < 1) {
            return 0
        }
        val offsetResults = getOffsetResults(startDisplayIndex, direction, count)
        if (offsetResults.isEmpty()) {
            return 0
        }
        val offsetIndex = offsetResults.last()!!.displayIndex
        if (direction == Timeline.Direction.BACKWARDS) {
            prevDisplayIndex = offsetIndex - 1
        } else {
            nextDisplayIndex = offsetIndex + 1
        }
        offsetResults.forEach { eventEntity ->
            val timelineEvent = timelineEventFactory.create(eventEntity)
            val position = if (direction == Timeline.Direction.FORWARDS) 0 else builtEvents.size
            builtEvents.add(position, timelineEvent)
            //Need to shift :/
            builtEventsIdMap.entries.filter { it.value >= position }.forEach { it.setValue(it.value + 1) }
            builtEventsIdMap[eventEntity.eventId] = position
        }
        Timber.v("Built ${offsetResults.size} items from db")
        return offsetResults.size
    }

    /**
     * This has to be called on TimelineThread as it access realm live results
     */
    private fun getOffsetResults(startDisplayIndex: Int,
                                 direction: Timeline.Direction,
                                 count: Long): RealmResults<EventEntity> {
        val offsetQuery = liveEvents.where()
        if (direction == Timeline.Direction.BACKWARDS) {
            offsetQuery
                    .sort(EventEntityFields.DISPLAY_INDEX, Sort.DESCENDING)
                    .lessThanOrEqualTo(EventEntityFields.DISPLAY_INDEX, startDisplayIndex)
        } else {
            offsetQuery
                    .sort(EventEntityFields.DISPLAY_INDEX, Sort.ASCENDING)
                    .greaterThanOrEqualTo(EventEntityFields.DISPLAY_INDEX, startDisplayIndex)
        }
        return offsetQuery
                .filterAllowedTypes()
                .limit(count)
                .findAll()
    }


    private fun buildEventQuery(realm: Realm): RealmQuery<EventEntity> {
        return if (initialEventId == null) {
            EventEntity
                    .where(realm, roomId = roomId, linkFilterMode = EventEntity.LinkFilterMode.LINKED_ONLY)
                    .equalTo("${EventEntityFields.CHUNK}.${ChunkEntityFields.IS_LAST_FORWARD}", true)
        } else {
            EventEntity
                    .where(realm, roomId = roomId, linkFilterMode = EventEntity.LinkFilterMode.BOTH)
                    .`in`("${EventEntityFields.CHUNK}.${ChunkEntityFields.EVENTS.EVENT_ID}", arrayOf(initialEventId))
        }
    }

    private fun findCurrentChunk(realm: Realm): ChunkEntity? {
        return if (initialEventId == null) {
            ChunkEntity.findLastLiveChunkFromRoom(realm, roomId)
        } else {
            ChunkEntity.findIncludingEvent(realm, initialEventId)
        }
    }

    private fun clearUnlinkedEvents(realm: Realm) {
        realm.executeTransaction {
            val unlinkedChunks = ChunkEntity
                    .where(it, roomId = roomId)
                    .equalTo(ChunkEntityFields.EVENTS.IS_UNLINKED, true)
                    .findAll()
            unlinkedChunks.deleteAllFromRealm()
        }
    }

    private fun fetchEvent(eventId: String) {
        val params = GetContextOfEventTask.Params(roomId, eventId)
        contextOfEventTask.configureWith(params).executeBy(taskExecutor)
    }

    private fun postSnapshot() {
        val snapshot = createSnapshot()
        val runnable = Runnable { listener?.onUpdated(snapshot) }
        debouncer.debounce("post_snapshot", runnable, 50)
    }

// Extension methods ***************************************************************************

    private fun Timeline.Direction.toPaginationDirection(): PaginationDirection {
        return if (this == Timeline.Direction.BACKWARDS) PaginationDirection.BACKWARDS else PaginationDirection.FORWARDS
    }

    private fun RealmQuery<EventEntity>.findFirst(direction: Timeline.Direction): EventEntity? {
        return if (direction == Timeline.Direction.FORWARDS) {
            sort(EventEntityFields.DISPLAY_INDEX, Sort.DESCENDING)
        } else {
            sort(EventEntityFields.DISPLAY_INDEX, Sort.ASCENDING)
        }
                .filterAllowedTypes()
                .findFirst()
    }

    private fun RealmQuery<EventEntity>.filterAllowedTypes(): RealmQuery<EventEntity> {
        if (allowedTypes != null) {
            `in`(EventEntityFields.TYPE, allowedTypes.toTypedArray())
        }
        return this
    }
}

private data class PaginationState(
        val isPaginating: Boolean = false,
        val requestedCount: Int = 0
)
