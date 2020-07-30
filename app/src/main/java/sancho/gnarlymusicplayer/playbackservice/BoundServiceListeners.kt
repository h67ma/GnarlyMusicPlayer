package sancho.gnarlymusicplayer.playbackservice

interface BoundServiceListeners
{
	fun onTrackChanged(oldPos: Int)
	fun onEnd()
}
