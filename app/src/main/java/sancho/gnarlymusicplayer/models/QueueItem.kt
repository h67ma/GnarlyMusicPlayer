package sancho.gnarlymusicplayer.models

class QueueItem(var path: String, var name: String)
{
	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other?.javaClass != javaClass) return false

		other as QueueItem

		return path == other.path && name == other.name
	}

	override fun hashCode(): Int
	{
		return 31 * path.hashCode() + name.hashCode()
	}
}
