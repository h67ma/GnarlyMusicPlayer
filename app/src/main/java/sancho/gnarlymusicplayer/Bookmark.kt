package sancho.gnarlymusicplayer

class Bookmark(var id: Int, var path: String, var label: String)
{
	constructor(path: String, label: String) : this(0, path, label)
}
