package sancho.gnarlymusicplayer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BookmarksDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION)
{
	companion object
	{
		private const val DATABASE_VERSION = 1
		private const val DATABASE_NAME = "BookmarksDb.db"
		private const val COLUMN_ID = "id"
		private const val TABLE_BOOKMARKS = "bookmarks"
		private const val BOOKMARKS_COLUMN_PATH = "path"
		private const val BOOKMARKS_COLUMN_LABEL = "label"
	}

	override fun onCreate(db: SQLiteDatabase)
	{
		db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_BOOKMARKS ($COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, $BOOKMARKS_COLUMN_PATH TEXT, $BOOKMARKS_COLUMN_LABEL TEXT)")
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)
	{
		db.execSQL("DROP TABLE IF EXISTS $TABLE_BOOKMARKS")
		onCreate(db)
	}

	fun insertBookmark(path: String, label: String)
	{
		val values = ContentValues().apply {
			put(BOOKMARKS_COLUMN_PATH, path)
			put(BOOKMARKS_COLUMN_LABEL, label)
		}

		writableDatabase.insert(TABLE_BOOKMARKS, null, values)
	}

	fun deleteBookmark(id: Int)
	{
		val whereClause = "$COLUMN_ID = ?"
		val whereArgs = arrayOf(id.toString())

		writableDatabase.delete(TABLE_BOOKMARKS, whereClause, whereArgs)
	}

	fun getBookmarks(): MutableList<Bookmark>
	{
		val projection = arrayOf(COLUMN_ID, BOOKMARKS_COLUMN_PATH, BOOKMARKS_COLUMN_LABEL)

		val sortOrder = "$COLUMN_ID DESC"

		val cursor = writableDatabase.query(
			TABLE_BOOKMARKS,
			projection,
			null,
			null,
			null,
			null,
			sortOrder)

		val bookmarks = ArrayList<Bookmark>()
		cursor?.moveToFirst()
		while (cursor?.isAfterLast != true)
		{
			bookmarks.add(Bookmark(cursor.getInt(0), cursor.getString(1), cursor.getString(2)))
			cursor.moveToNext()
		}
		cursor.close()

		return bookmarks
	}
}
