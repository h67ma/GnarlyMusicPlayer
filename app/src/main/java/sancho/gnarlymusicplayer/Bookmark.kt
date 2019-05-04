package sancho.gnarlymusicplayer

import android.os.Parcel
import android.os.Parcelable

class Bookmark(var path: String, var label: String) : Parcelable
{
	constructor(parcel: Parcel) : this(
		parcel.readString() ?: "", parcel.readString() ?: "well that didn't work"
	)

	override fun writeToParcel(parcel: Parcel, flags: Int)
	{
		parcel.writeString(path)
		parcel.writeString(label)
	}

	override fun describeContents(): Int
	{
		return 0
	}

	companion object CREATOR : Parcelable.Creator<Bookmark>
	{
		override fun createFromParcel(parcel: Parcel): Bookmark
		{
			return Bookmark(parcel)
		}

		override fun newArray(size: Int): Array<Bookmark?>
		{
			return arrayOfNulls<Bookmark?>(size)
		}
	}
}
