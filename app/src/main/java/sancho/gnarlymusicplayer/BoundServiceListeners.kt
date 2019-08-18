package sancho.gnarlymusicplayer

interface BoundServiceListeners
{
	fun updateQueueRecycler(oldPos: Int)
	fun initSeekBar()
	fun playbackStateChanged()
}
