package sancho.gnarlymusicplayer

import androidx.recyclerview.widget.RecyclerView
import sancho.gnarlymusicplayer.models.QueueItem
import java.io.File

const val NO_TRACK = RecyclerView.NO_POSITION

object PlaybackQueue
{
	var queue = mutableListOf<QueueItem>()

	var currentIdx: Int = NO_TRACK
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
				currentIdx = NO_TRACK
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
			currentIdx = NO_TRACK
			hasChanged = true
			return removedCnt
		}
		return 0
	}

	// returns number of cleared items
	fun removeAfter(idx: Int): Int
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

	// changing idx doesn't mean queue has changed
	fun updateIdxAfterItemMoved(fromPosition: Int, toPosition: Int)
	{
		if (fromPosition == toPosition)
			return

		if (fromPosition == currentIdx)
		{
			currentIdx = toPosition
		}
		else if (toPosition >= currentIdx && fromPosition < currentIdx)
		{
			currentIdx--
		}
		else if (toPosition <= currentIdx && fromPosition > currentIdx)
		{
			currentIdx++
		}
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
