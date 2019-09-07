package sancho.gnarlymusicplayer

interface BoundServiceListeners
{
	fun onTrackChanged(oldPos: Int)
	fun onEnd()
}
