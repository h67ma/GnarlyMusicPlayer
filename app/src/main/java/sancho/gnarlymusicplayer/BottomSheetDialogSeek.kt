package sancho.gnarlymusicplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import com.google.android.material.bottomsheet.BottomSheetDialog
import sancho.gnarlymusicplayer.databinding.BottomSheetSeekBinding
import sancho.gnarlymusicplayer.playbackservice.MediaPlaybackService
import java.io.File

class BottomSheetDialogSeek(context: Context, service: MediaPlaybackService?, inflater: LayoutInflater) : BottomSheetDialog(context)
{
	init
	{
		// inflater needs to be passed from activity so that app theme is set correctly
		val seekBinding = BottomSheetSeekBinding.inflate(inflater)
		setContentView(seekBinding.root)

		// set current/total time
		val currentTime = service?.getCurrentTime() ?: 0
		val totalTime = service?.getTotalTime() ?: 0
		val totalTimeMinutes = totalTime / 60
		val totalTimeSeconds = totalTime % 60
		seekBinding.seekSeekbar.max = totalTime
		seekBinding.seekSeekbar.progress = currentTime
		seekBinding.seekCurrenttotaltime.text = context.getString(
			R.string.seek_total_time,
			currentTime / 60,
			currentTime % 60,
			totalTimeMinutes,
			totalTimeSeconds
		)

		val savedTrack = File(AppSettingsManager.savedTrackPath)
		if (savedTrack.exists() && PlaybackQueue.getCurrentTrackPath() == AppSettingsManager.savedTrackPath)
		{
			seekBinding.seekLoadbtn.visibility = View.VISIBLE
			seekBinding.seekLoadbtn.setOnClickListener{
				seekBinding.seekSeekbar.progress = AppSettingsManager.savedTrackTime
				service?.seekAndPlay(seekBinding.seekSeekbar.progress)
			}
		}

		seekBinding.seekSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
			override fun onProgressChanged(seekbar: SeekBar?, progress: Int, fromUser: Boolean)
			{
				seekBinding.seekCurrenttotaltime.text = context.getString(
					R.string.seek_total_time,
					progress / 60,
					progress % 60,
					totalTimeMinutes,
					totalTimeSeconds
				)
			}

			override fun onStartTrackingTouch(seekbar: SeekBar?) {}

			override fun onStopTrackingTouch(seekbar: SeekBar?)
			{
				if (MediaPlaybackService.mediaPlaybackServiceStarted && service != null)
				{
					service.seekAndPlay(seekBinding.seekSeekbar.progress)
				}
				else
					Toaster.show(context, context.getString(R.string.playback_service_not_running))
			}
		})
	}
}
