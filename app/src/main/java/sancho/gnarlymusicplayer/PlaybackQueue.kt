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
	fun removeBeforeCurrent(): Int
	{
		if (currentIdx != NO_TRACK && currentIdx > 0)
		{
			// there are items to clear before current track

			for (i in 0 until currentIdx)
				queue.removeAt(0)

			val removedCnt = currentIdx
			currentIdx = 0
			hasChanged = true
			return removedCnt
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
	fun removeAfterCurrent(): Int
	{
		if (currentIdx != NO_TRACK && currentIdx < lastIdx)
		{
			// there are items to clear at the end

			val removedCnt = lastIdx - currentIdx
			for (i in lastIdx downTo currentIdx + 1)
				queue.removeAt(i)

			currentIdx = lastIdx
			hasChanged = true
			return removedCnt
		}
		return 0
	}

	fun moveItem(fromPosition: Int, toPosition: Int)
	{
		if (fromPosition == currentIdx)
		{
			currentIdx = toPosition
		}
		else if (toPosition == currentIdx) // TODO does this really work?
		{
			if (fromPosition < currentIdx)
				currentIdx--
			else if (fromPosition > currentIdx)
				currentIdx++
		}

		hasChanged = true
	}

	fun trackExists(pos: Int): Boolean
	{
		return File(queue[pos].path).exists()
	}

	fun getCurrentTrackPath(): String?
	{
		if (trackSelected())
			return queue[currentIdx].path

		return null
	}

	fun getCurrentTrackName(): String?
	{
		if (trackSelected())
			return queue[currentIdx].name

		return null
	}

	fun getCurrentTrack(): QueueItem?
	{
		if (trackSelected())
			return queue[currentIdx]

		return null
	}

	fun getCurrentTrackDir(): File?
	{
		val track = File(queue[currentIdx].path)
		if (track.parentFile?.exists() == true)
			return track.parentFile

		return null
	}

	fun trackSelected(): Boolean
	{
		return size > 0 && currentIdx < size && currentIdx != NO_TRACK
	}

	fun setNextTrackIdx(oldIdx: Int)
	{
		currentIdx = (oldIdx + 1) % size
	}

	fun setPrevTrackIdx()
	{
		currentIdx = (currentIdx - 1 + size) % size
	}
}
