@file:Suppress("ConvertTwoComparisonsToRangeCheck") // just let me focus on the logic

package sancho.gnarlymusicplayer

import sancho.gnarlymusicplayer.models.QueueItem
import java.io.File
import java.util.Collections

object PlaybackQueue
{
	var queue = mutableListOf<QueueItem>()

	var currentIdx: Int = 0
	var autoClean: Boolean = false
	var hasChanged: Boolean = false

	val size: Int
		get() =  queue.size

	val lastIdx: Int
		get() = queue.size - 1

	fun add(item: QueueItem)
	{
		queue.add(item)
		hasChanged = true
	}

	fun addAt(idx: Int, item: QueueItem)
	{
		hasChanged = true

		if (idx > size) // illegal, just do normal add instead
		{
			queue.add(item)
			// currentIdx didn't change
			return
		}

		queue.add(idx, item)

		if (idx <= currentIdx)
		{
			currentIdx += 1
		}
	}

	fun add(trackList: List<QueueItem>)
	{
		queue.addAll(trackList)
		hasChanged = true
	}

	// returns true if the queue is empty after removing
	fun removeCurrent(): Boolean
	{
		return removeAt(currentIdx)
	}

	// returns true if the queue is empty after removing
	fun removeAt(pos: Int): Boolean
	{
		if (pos in 0..lastIdx)
		{
			queue.removeAt(pos)
			hasChanged = true
		}

		if (pos < currentIdx)
		{
			currentIdx--
		}
		else if (pos == currentIdx)
		{
			// we've removed currently selected track
			if (size <= 0)
			{
				// no other track available
				currentIdx = 0
			}
			else if (pos >= size)
			{
				// removed track was last - select first track
				currentIdx = 0
			}
		}

		return size <= 0
	}

	// returns number of cleared items
	@Suppress("ReplaceRangeToWithUntil")
	fun removeAbove(idx: Int): Int
	{
		if (idx > 0 && idx < size) // note: idxValid checks if idx >= 0, here clearing "before 0" should do nothing
		{
			// there are items to clear before idx

			for (i in 0..idx - 1)
				queue.removeAt(0)

			currentIdx -= idx
			if (currentIdx < 0)
				currentIdx = 0

			hasChanged = true
			return idx // = removed cnt
		}
		return 0
	}

	// returns number of cleared items
	fun removeAll(): Int
	{
		if (size > 0)
		{
			val removedCnt = size
			queue.clear()
			currentIdx = 0
			hasChanged = true
			return removedCnt
		}
		return 0
	}

	// returns number of cleared items
	fun removeBelow(idx: Int): Int
	{
		if (idx >= 0 && idx < lastIdx) // note: idxValid checks if idx < size, here clearing "after last item" should do nothing
		{
			// there are items to clear at the end

			val removedCnt = lastIdx - idx
			for (i in idx+1..lastIdx)
				queue.removeAt(idx+1)

			if (currentIdx > idx) // current track was in cleared range, should now be last item in queue
				currentIdx = lastIdx
			hasChanged = true
			return removedCnt
		}
		return 0
	}

	fun moveItem(fromPosition: Int, toPosition: Int)
	{
		if (fromPosition == toPosition)
			return // no need to update anything

		if (fromPosition < toPosition)
		{
			for (i in fromPosition until toPosition)
			{
				Collections.swap(queue, i, i + 1)
			}
		}
		else
		{
			for (i in fromPosition downTo toPosition + 1)
			{
				Collections.swap(queue, i, i - 1)
			}
		}

		if (fromPosition == currentIdx)
		{
			currentIdx = toPosition // item moved to current idx
		}
		else if (toPosition >= currentIdx && fromPosition < currentIdx)
		{
			currentIdx-- // item moved from behind to after current idx
		}
		else if (toPosition <= currentIdx && fromPosition > currentIdx)
		{
			currentIdx++ // item moved from after to behind current idx
		}

		hasChanged = true
	}

	fun trackExists(idx: Int): Boolean
	{
		if (!idxValid(idx)) return false
		return File(queue[idx].path).exists()
	}

	fun getCurrentTrackPath(): String?
	{
		return getTrackPath(currentIdx)
	}

	fun getTrackPath(idx: Int): String?
	{
		if (!idxValid(idx))
			return null

		return queue[idx].path
	}

	fun getTrackName(idx: Int): String?
	{
		if (!idxValid(idx))
			return null

		return queue[idx].name
	}

	fun getCurrentTrack(): QueueItem?
	{
		if (!idxValid(currentIdx))
			return null

		return queue[currentIdx]
	}

	fun getTrackParent(idx: Int): File?
	{
		if (!idxValid(idx))
			return null

		val track = File(queue[idx].path)
		if (track.parentFile?.exists() == true)
			return track.parentFile

		return null
	}

	fun currentIdxValid(): Boolean
	{
		return idxValid(currentIdx)
	}

	private fun idxValid(idx: Int): Boolean
	{
		return idx >= 0 && idx < size
	}

	// changing idx doesn't mean queue has changed
	fun setNextTrackIdx()
	{
		currentIdx = (currentIdx + 1) % size
	}

	// changing idx doesn't mean queue has changed
	fun setPrevTrackIdx()
	{
		currentIdx = (currentIdx - 1 + size) % size
	}
}
