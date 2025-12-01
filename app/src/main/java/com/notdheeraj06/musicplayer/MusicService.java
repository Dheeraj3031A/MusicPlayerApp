package com.notdheeraj06.musicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicService extends Service {

    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_PREV = "ACTION_PREV";

    private static final String CHANNEL_ID = "MusicChannel";
    private static final int NOTIFICATION_ID = 1;

    private MediaPlayer mediaPlayer;
    private final List<Song> songs = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0=off, 1=one, 2=all

    private MediaSessionCompat mediaSession;
    private final IBinder binder = new MusicBinder();
    private OnSongChangedListener songChangedListener;

    public interface OnSongChangedListener {
        void onSongChanged(Song song, boolean isPlaying);
    }

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSession = new MediaSessionCompat(this, "MusicService");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY:
                    if (mediaPlayer != null && !mediaPlayer.isPlaying()) playSong(currentIndex);
                    break;
                case ACTION_PAUSE:
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) pause();
                    break;
                case ACTION_NEXT:
                    playNext();
                    break;
                case ACTION_PREV:
                    playPrevious();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaSession.release();
    }

    public void setSongs(List<Song> newSongs) {
        songs.clear();
        songs.addAll(newSongs);
    }

    public void setSongChangedListener(OnSongChangedListener listener) {
        this.songChangedListener = listener;
    }

    public void playSong(int index) {
        if (songs.isEmpty()) return;
        if (index < 0) index = 0;
        if (index >= songs.size()) index = 0;

        currentIndex = index;
        Song song = songs.get(currentIndex);

        if (mediaPlayer != null) {
            mediaPlayer.reset();
        } else {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build());
        }

        try {
            mediaPlayer.setDataSource(getApplicationContext(), song.getUri());
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(mp -> onSongCompleted());

            showNotification(song, true);
            if (songChangedListener != null) songChangedListener.onSongChanged(song, true);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            showNotification(songs.get(currentIndex), false);
            if (songChangedListener != null) songChangedListener.onSongChanged(songs.get(currentIndex), false);
        }
    }

    public void resume() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            showNotification(songs.get(currentIndex), true);
            if (songChangedListener != null) songChangedListener.onSongChanged(songs.get(currentIndex), true);
        }
    }

    public void playNext() {
        if (songs.isEmpty()) return;
        int nextIndex = currentIndex + 1;
        if (isShuffle) nextIndex = new Random().nextInt(songs.size());
        if (nextIndex >= songs.size()) nextIndex = 0;
        playSong(nextIndex);
    }

    public void playPrevious() {
        if (songs.isEmpty()) return;
        int prevIndex = currentIndex - 1;
        if (isShuffle) prevIndex = new Random().nextInt(songs.size());
        if (prevIndex < 0) prevIndex = songs.size() - 1;
        playSong(prevIndex);
    }

    private void onSongCompleted() {
        if (repeatMode == 1) { // Repeat One
            playSong(currentIndex);
        } else {
            playNext();
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public int getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
    }

    public void toggleShuffle() { isShuffle = !isShuffle; }
    public void toggleRepeat() { repeatMode = (repeatMode + 1) % 3; }
    public Song getCurrentSong() { return (currentIndex >= 0 && currentIndex < songs.size()) ? songs.get(currentIndex) : null; }

    private void showNotification(Song song, boolean isPlaying) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Intent prevIntent = new Intent(this, MusicService.class).setAction(ACTION_PREV);
        PendingIntent prevPending = PendingIntent.getService(this, 0, prevIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent playIntent = new Intent(this, MusicService.class).setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent playPending = PendingIntent.getService(this, 1, playIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent nextIntent = new Intent(this, MusicService.class).setAction(ACTION_NEXT);
        PendingIntent nextPending = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with a music note icon if you have one
                .setContentTitle(song.getTitle())
                .setContentText("Playing Music")
                .setLargeIcon(null) // Add album art bitmap here if you parse it
                .setContentIntent(contentIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)) // Indexes of buttons to show in collapsed view
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
                .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, isPlaying ? "Pause" : "Play", playPending)
                .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true) // Don't vibrate on update
                .setOngoing(isPlaying) // Persistent when playing
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Controls for music playback");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
