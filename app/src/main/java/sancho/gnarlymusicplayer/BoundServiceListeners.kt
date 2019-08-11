package sancho.gnarlymusicplayer

interface BoundServiceListeners
{
	fun updateQueueRecycler(oldPos: Int)
	fun initSeekBar(max: Int)
	fun updateSeekbar(pos: Int)
}
