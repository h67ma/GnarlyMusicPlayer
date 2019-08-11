package sancho.gnarlymusicplayer

interface BoundServiceListeners
{
	fun updateQueueRecycler(oldPos: Int)
	fun initSeekBar(max: Int)
	fun playbackStarted()
	fun playbackStopped()
}
