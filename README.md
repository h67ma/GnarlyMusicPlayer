Gnarly Music Player is a lightweight folder music player, focused on quick, intuitive navigation.

# Features #
* No library
* No tags
* No album art
* No sound effects/equalizer
* No playlists
* No ads
* Based on folder structure
* Easly editable playing queue
* Simple context search
* Simple bookmarks
* Seek current track
* Restore track position
* Media buttons support
* Get track info (tags) on demand
* Designed for convenience and simplicity
* Customizable accent colour
* Open source

# Download #
[Signed .apk](https://github.com/szycikm/GnarlyMusicPlayer/releases/latest)

# Description #
This music player is highly inspired by [Folder Music Player](https://play.google.com/store/apps/details?id=com.suphi.foldermusicplayerunlocker) by Suphi (free version no longer available on Play Store). It lacked a few essential (in my opinion) features to become perfect - search, bookmarks and saving scroll position of previous dir. I used it for a few years, because I couldn't find any app that was even half as convenient to use. Finally I made my own music player and I'm very happy with the result.

In my opinion adding too many features to a mobile music player makes it harder to use. I just want to be able to quickly change my playing queue, not watch album art, lyrics, tags, or animations. Also, why bundle equalizer with music player - there are apps just for DSP. Unfortunately, most music players on the Play Store are very feature-rich.

This was my first Kotlin project. I'm positively surprised by Kotlin - it has some quirks, like the absence of static keyword, but overall I had much better time working with it than with Java.

I regret nothing.

# Potential improvements #
* ~Seek bar~
* Export/Import settings, queue, bookmarks to xml/json or something

# Q&A #

**Q:** Why??  
**A:** I wanted a perfect music player for Android. I made this mostly for myself and my weird music app needs. I doubt I'll need to switch to another music player app for a long time.

**Q:** Why is _[feature name]_ missing?  
**A:** I specifically only implemented features I personally need. Anything else would just disturb me.

**Q:** Why search is only one level deep?  
**A:** You probably more-less know where is the song you're looking for. It's your offline music library after all. First-level dirs are for a case when you have lots of songs in a single dir, or when you have a bunch of albums and can't remember which one has the song you want.

**Q:** Why does adding folders to queue not include subdirs?  
**A:** I don't think it's necessary. The way I use a music player (and the way I intend this app to be used) is to keep the queue relatively short (<100 tracks or something like that) and edit it frequently. There's no point in adding hundreds of songs to queue with one tap - the list would be too long and you'd probably only listen to some part of it before changing your mind.

**Q:** Why is the interface not user-friendly for new users? :(  
**A:** I know that not every action is clearly labeled, but it's on purpose. For example the two sliding navs on left and right can be opened only by sliding from left on right, and there's no indication that they exist on main screen. Of course, I could've added some kind of button on the action menu, but this kind of thing takes up space for controls that you actually use. This approach might not be very user-friendly for new users, but I made it mostly for myself. Besides, everything is explained in the help dialog.

**Q:** Why is this not on the Play Store?  
**A:** I don't feel like paying 25 bucks just to have 5 people download this.

**Q:** Why there are no media buttons in main app interface?  
**A:** All buttons are in the notification, so you can access them everywhere in the system. There's no reason to have a copy of them taking space in the main app interface.

**Q:** Why does the notification use custom layout instead of the standard media one?  
**A:** Let's just say I don't like that "media layout". Buttons are too small and for some reason there's a timer showing how long ago the notification appeared.

# Tested on #
* wt88047/7.1.2/LineageOS 14.1
