package sancho.gnarlymusicplayer.settings

import androidx.preference.PreferenceFragmentCompat
import sancho.gnarlymusicplayer.R

abstract class NestedSettingsFragment : PreferenceFragmentCompat()
{
	override fun onDetach()
	{
		// there's probably a better way to do this
		activity?.title = getString(R.string.settings)
		super.onDetach()
	}
}
