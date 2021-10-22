package sancho.gnarlymusicplayer

import org.junit.Before
import org.junit.Test
import sancho.gnarlymusicplayer.models.QueueItem
import org.junit.Assert.*

class PlaybackQueueTest
{
	@Before
	fun init()
	{
		PlaybackQueue.queue.clear()
		PlaybackQueue.add(QueueItem("a", "1"))
		PlaybackQueue.add(QueueItem("b", "2"))
		PlaybackQueue.add(QueueItem("c", "3")) // <- currentIdx
		PlaybackQueue.add(QueueItem("d", "4"))
		PlaybackQueue.add(QueueItem("e", "5"))
		PlaybackQueue.currentIdx = 2
		PlaybackQueue.hasChanged = false
	}

	@Test
	fun size()
	{
		assertEquals(5, PlaybackQueue.size)
	}

	@Test
	fun lastIdx()
	{
		assertEquals(4, PlaybackQueue.lastIdx)
	}

	@Test
	fun addSize()
	{
		PlaybackQueue.add(QueueItem("f", "6"))
		assertEquals(6, PlaybackQueue.size)
	}

	@Test
	fun removeSize()
	{
		PlaybackQueue.removeCurrent()
		assertEquals(4, PlaybackQueue.size)
	}

	@Test
	fun addHasChanged()
	{
		PlaybackQueue.add(QueueItem("f", "6"))
		assertTrue(PlaybackQueue.hasChanged)
	}

	@Test
	fun addMultipleHasChanged()
	{
		PlaybackQueue.add(listOf(
			QueueItem("f", "6"),
			QueueItem("g", "7")
		))
		assertTrue(PlaybackQueue.hasChanged)
	}

	@Test
	fun removeCurrentHasChanged()
	{
		PlaybackQueue.removeCurrent()
		assertTrue(PlaybackQueue.hasChanged)
	}

	@Test
	fun removeEmptyCurrentHasntChanged()
	{
		PlaybackQueue.queue.clear()
		PlaybackQueue.hasChanged = false

		PlaybackQueue.removeCurrent()
		assertFalse(PlaybackQueue.hasChanged)
	}

	@Test
	fun removeAtHasChanged()
	{
		PlaybackQueue.removeAt(0)
		assertTrue(PlaybackQueue.hasChanged)
	}

	@Test
	fun removeAboveCurrentHasChanged()
	{
		PlaybackQueue.removeAbove(PlaybackQueue.currentIdx)
		assertTrue(PlaybackQueue.hasChanged)
	}

	@Test
	fun removeZeroBeforeHasntChanged()
	{
		PlaybackQueue.currentIdx = 0
		PlaybackQueue.removeAbove(PlaybackQueue.currentIdx)
		assertFalse(PlaybackQueue.hasChanged)
	}

	@Test
	fun removeAfterCurrentHasChanged()
	{
		PlaybackQueue.removeBelow(PlaybackQueue.currentIdx)
		assertTrue(PlaybackQueue.hasChanged)
	}

	@Test
	fun removeZeroAfterHasntChanged()
	{
		PlaybackQueue.currentIdx = PlaybackQueue.size - 1
		PlaybackQueue.removeBelow(PlaybackQueue.currentIdx)
		assertFalse(PlaybackQueue.hasChanged)
	}

	@Test
	fun removeAllHasChanged()
	{
		PlaybackQueue.removeAll()
		assertTrue(PlaybackQueue.hasChanged)
	}

	@Test
	fun removeZeroAllHasntChanged()
	{
		PlaybackQueue.queue.clear()
		PlaybackQueue.hasChanged = false
		PlaybackQueue.removeAll()
		assertFalse(PlaybackQueue.hasChanged)
	}

	@Test
	fun moveHasChanged()
	{
		PlaybackQueue.moveItem(0, 2)
		assertTrue(PlaybackQueue.hasChanged)
	}

	@Test
	fun removeCurrent()
	{
		PlaybackQueue.removeCurrent()
		assertArrayEquals(listOf(
			QueueItem("a", "1"),
			QueueItem("b", "2"),
			QueueItem("d", "4"),
			QueueItem("e", "5")
		).toTypedArray(), PlaybackQueue.queue.toTypedArray())
	}

	@Test
	fun removeCurrentMidIdx()
	{
		PlaybackQueue.removeCurrent()
		assertEquals(2, PlaybackQueue.currentIdx) // shouldn't change
	}

	@Test
	fun removeCurrentLastIdx()
	{
		PlaybackQueue.currentIdx = 4
		PlaybackQueue.removeCurrent()
		assertEquals(0, PlaybackQueue.currentIdx) // should be reset to start of queue
	}

	@Test
	fun removeLastCurrentIdx()
	{
		PlaybackQueue.removeCurrent()
		PlaybackQueue.removeCurrent()
		PlaybackQueue.removeCurrent()
		PlaybackQueue.removeCurrent()
		PlaybackQueue.removeCurrent()
		assertEquals(0, PlaybackQueue.currentIdx) // should return to default 0
	}

	@Test
	fun removeRetval()
	{
		// should return true if empty
		var result = PlaybackQueue.removeCurrent()
		assertFalse(result)
		result = PlaybackQueue.removeCurrent()
		assertFalse(result)
		result = PlaybackQueue.removeCurrent()
		assertFalse(result)
		result = PlaybackQueue.removeCurrent()
		assertFalse(result)
		result = PlaybackQueue.removeCurrent()
		assertTrue(result) // last item
		result = PlaybackQueue.removeCurrent()
		assertTrue(result) // just in case
	}

	@Test
	fun removeAtBeforeCurrentIdx()
	{
		PlaybackQueue.removeAt(0)
		assertEquals(1, PlaybackQueue.currentIdx) // should decrement
	}

	@Test
	fun removeAtAfterCurrentIdx()
	{
		PlaybackQueue.removeAt(4)
		assertEquals(2, PlaybackQueue.currentIdx) // shouldn't change
	}

	@Test
	fun removeBeforeCurrent()
	{
		PlaybackQueue.removeAbove(PlaybackQueue.currentIdx)
		assertArrayEquals(listOf(
			QueueItem("c", "3"),
			QueueItem("d", "4"),
			QueueItem("e", "5")
		).toTypedArray(), PlaybackQueue.queue.toTypedArray())
	}

	@Test
	fun removeAfterCurrent()
	{
		PlaybackQueue.removeBelow(PlaybackQueue.currentIdx)
		assertArrayEquals(listOf(
			QueueItem("a", "1"),
			QueueItem("b", "2"),
			QueueItem("c", "3")
		).toTypedArray(), PlaybackQueue.queue.toTypedArray())
	}

	@Test
	fun removeBeforeCurrentRetval()
	{
		var retval = PlaybackQueue.removeAbove(PlaybackQueue.currentIdx)
		assertEquals(2, retval)
		retval = PlaybackQueue.removeAbove(PlaybackQueue.currentIdx)
		assertEquals(0, retval)
	}

	@Test
	fun removeAfterCurrentRetval()
	{
		var retval = PlaybackQueue.removeBelow(PlaybackQueue.currentIdx)
		assertEquals(2, retval)
		retval = PlaybackQueue.removeBelow(PlaybackQueue.currentIdx)
		assertEquals(0, retval)
	}

	@Test
	fun removeBeforeCurrentIndex()
	{
		PlaybackQueue.removeAbove(PlaybackQueue.currentIdx)
		assertEquals(0, PlaybackQueue.currentIdx) // should be first
	}

	@Test
	fun removeAfterCurrentIndex()
	{
		PlaybackQueue.removeBelow(PlaybackQueue.currentIdx)
		assertEquals(2, PlaybackQueue.currentIdx) // should stay the same
	}

	@Test
	fun removeAllIdx()
	{
		PlaybackQueue.removeAll()
		assertTrue(PlaybackQueue.queue.isEmpty())
		assertEquals(0, PlaybackQueue.currentIdx)
	}

	@Test
	fun removeAllRetval()
	{
		val retval = PlaybackQueue.removeAll()
		assertEquals(5, retval)
	}

	@Test
	fun moveFromBeforeToBeforeIdx()
	{
		PlaybackQueue.moveItem(0, 1)
		assertEquals(2, PlaybackQueue.currentIdx)
	}

	@Test
	fun moveFromBeforeToCurrentIdx()
	{
		PlaybackQueue.moveItem(0, 2)
		assertEquals(1, PlaybackQueue.currentIdx)
	}

	@Test
	fun moveFromBeforeToAfterIdx()
	{
		PlaybackQueue.moveItem(0, 3)
		assertEquals(1, PlaybackQueue.currentIdx)
	}

	@Test
	fun moveFromAfterToAfterIdx()
	{
		PlaybackQueue.moveItem(4, 3)
		assertEquals(2, PlaybackQueue.currentIdx)
	}

	@Test
	fun moveFromAfterToCurrentIdx()
	{
		PlaybackQueue.moveItem(4, 2)
		assertEquals(3, PlaybackQueue.currentIdx)
	}

	@Test
	fun moveFromAfterToBeforeIdx()
	{
		PlaybackQueue.moveItem(4, 0)
		assertEquals(3, PlaybackQueue.currentIdx)
	}

	@Test
	fun moveCurrentUpIdx()
	{
		PlaybackQueue.moveItem(2, 4)
		assertEquals(4, PlaybackQueue.currentIdx)
	}

	@Test
	fun moveCurrentDownIdx()
	{
		PlaybackQueue.moveItem(2, 1)
		assertEquals(1, PlaybackQueue.currentIdx)
	}

	@Test
	fun idxValidNegative()
	{
		PlaybackQueue.currentIdx = -1
		val valid = PlaybackQueue.currentIdxValid()
		assertFalse(valid)
	}

	@Test
	fun idxValidGreaterThanSize()
	{
		PlaybackQueue.currentIdx = 5
		val valid = PlaybackQueue.currentIdxValid()
		assertFalse(valid)
	}

	@Test
	fun idxValidOkMin()
	{
		PlaybackQueue.currentIdx = 0
		val valid = PlaybackQueue.currentIdxValid()
		assertTrue(valid)
	}

	@Test
	fun idxValidOkMax()
	{
		PlaybackQueue.currentIdx = 3
		val valid = PlaybackQueue.currentIdxValid()
		assertTrue(valid)
	}

	@Test
	fun setNextTrackIdxNormal()
	{
		PlaybackQueue.setNextTrackIdx()
		assertEquals(3, PlaybackQueue.currentIdx)
	}

	@Test
	fun setNextTrackIdxLast()
	{
		PlaybackQueue.currentIdx = 4
		PlaybackQueue.setNextTrackIdx()
		assertEquals(0, PlaybackQueue.currentIdx)
	}

	@Test
	fun setPrevTrackIdxNormal()
	{
		PlaybackQueue.setPrevTrackIdx()
		assertEquals(1, PlaybackQueue.currentIdx)
	}

	@Test
	fun setPrevTrackIdxFirst()
	{
		PlaybackQueue.currentIdx = 0
		PlaybackQueue.setPrevTrackIdx()
		assertEquals(4, PlaybackQueue.currentIdx)
	}

	@Test
	fun setNextIdxHasntChanged()
	{
		PlaybackQueue.hasChanged = false
		PlaybackQueue.setNextTrackIdx()
		assertFalse(PlaybackQueue.hasChanged)
	}

	@Test
	fun setPrevIdxHasntChanged()
	{
		PlaybackQueue.hasChanged = false
		PlaybackQueue.setPrevTrackIdx()
		assertFalse(PlaybackQueue.hasChanged)
	}
}
