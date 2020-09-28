package sancho.gnarlymusicplayer.playbackservice

interface BoundServiceListeners
{
	fun onTrackChanged(oldPos: Int, trackFinished: Boolean)
	fun onEnd()
}
