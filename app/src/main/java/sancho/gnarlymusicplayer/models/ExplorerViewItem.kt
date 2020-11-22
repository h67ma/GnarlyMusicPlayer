package sancho.gnarlymusicplayer.models

abstract class ExplorerViewItem(var path: String, var displayName: String, var isDirectory: Boolean, var isHeader: Boolean, var isError: Boolean)
{
	override fun equals(other: Any?): Boolean
	{
		return (other as ExplorerViewItem).path == this.path
	}

	override fun hashCode(): Int
	{
		var result = path.hashCode()
		result = 31 * result + displayName.hashCode()
		result = 31 * result + isDirectory.hashCode()
		result = 31 * result + isHeader.hashCode()
		result = 31 * result + isError.hashCode()
		return result
	}
}
