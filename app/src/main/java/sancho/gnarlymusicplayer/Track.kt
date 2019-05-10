package sancho.gnarlymusicplayer

import android.os.Parcel
import android.os.Parcelable

class Track(var path: String, var name: String) : Parcelable
{
	constructor(parcel: Parcel) : this(
		parcel.readString() ?: "well that didn't work", parcel.readString() ?: "well that didn't work"
	)

	override fun writeToParcel(parcel: Parcel, flags: Int)
	{
		parcel.writeString(path)
		parcel.writeString(name)
	}

	override fun describeContents(): Int
	{
		return 0
	}

	companion object CREATOR : Parcelable.Creator<Track>
	{
		override fun createFromParcel(parcel: Parcel): Track
		{
			return Track(parcel)
		}

		override fun newArray(size: Int): Array<Track?>
		{
			return arrayOfNulls<Track?>(size)
		}
	}
}
