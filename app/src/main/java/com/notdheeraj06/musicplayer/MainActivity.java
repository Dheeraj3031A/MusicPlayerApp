package com.notdheeraj06.musicplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PICK_FOLDER = 1001;
    private static final String PREFS = "music_prefs";
    private static final String KEY_FOLDER_URI = "folder_uri";

    private RecyclerView recyclerSongs;
    private TextView txtTitle;
    private SeekBar seekBar;
    private ImageButton btnPlayPause;

    private final List<Song> songs = new ArrayList<>();
    private int currentIndex = -1;
    private MediaPlayer mediaPlayer;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 = off, 1 = one, 2 = all

    private final Handler handler = new Handler();
    private final Random random = new Random();

    private final Runnable updateSeekRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                seekBar.setProgress(mediaPlayer.getCurrentPosition());
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        recyclerSongs = findViewById(R.id.recyclerSongs);
        txtTitle = findViewById(R.id.txtTitle);
        seekBar = findViewById(R.id.seekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        ImageButton btnNext = findViewById(R.id.btnNext);
        ImageButton btnPrev = findViewById(R.id.btnPrev);
        ImageButton btnShuffle = findViewById(R.id.btnShuffle);
        ImageButton btnRepeat = findViewById(R.id.btnRepeat);

        recyclerSongs.setLayoutManager(new LinearLayoutManager(this));
        recyclerSongs.setAdapter(new SongsAdapter(songs, this::playSong));

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnNext.setOnClickListener(v -> playNext());
        btnPrev.setOnClickListener(v -> playPrevious());
        btnShuffle.setOnClickListener(v -> toggleShuffle());
        btnRepeat.setOnClickListener(v -> toggleRepeat());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null) mediaPlayer.seekTo(seekBar.getProgress());
            }
        });

        loadSavedFolder();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    private void loadSavedFolder() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String folderUriString = sp.getString(KEY_FOLDER_URI, null);
        if (folderUriString != null) {
            try {
                Uri uri = Uri.parse(folderUriString);
                boolean hasPerm = false;
                for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions()) {
                    if (p.getUri().equals(uri)) {
                        hasPerm = true;
                        break;
                    }
                }
                if(hasPerm) loadSongsFromFolder(uri);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        handler.removeCallbacks(updateSeekRunnable);
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadSongsFromFolder(Uri treeUri) {
        songs.clear();
        DocumentFile root = DocumentFile.fromTreeUri(this, treeUri);
        if (root != null && root.isDirectory()) {
            for (DocumentFile file : root.listFiles()) {
                if (file.isFile()) {
                    String name = file.getName();
                    if (name != null && isAudioFile(name)) {
                        songs.add(new Song(name, file.getUri()));
                    }
                }
            }
        }
        if(recyclerSongs.getAdapter() != null) recyclerSongs.getAdapter().notifyDataSetChanged();
        if (songs.isEmpty()) Toast.makeText(this, "No audio files found", Toast.LENGTH_SHORT).show();
    }

    private boolean isAudioFile(String name) {
        name = name.toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") || 
               name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".m4a");
    }

    private void playSong(int index) {
        if (index < 0 || index >= songs.size()) return;
        currentIndex = index;
        Song song = songs.get(index);
        txtTitle.setText(song.getTitle());
        releasePlayer();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA).build());
        try {
            mediaPlayer.setDataSource(this, song.getUri());
            mediaPlayer.setOnPreparedListener(mp -> {
                seekBar.setMax(mp.getDuration());
                mp.start();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                handler.post(updateSeekRunnable);
            });
            mediaPlayer.setOnCompletionListener(mp -> onSongCompleted());
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error playing file", Toast.LENGTH_SHORT).show();
        }
    }

    private void onSongCompleted() {
        if (repeatMode == 1) playSong(currentIndex);
        else if (isShuffle) playRandom();
        else {
            if (currentIndex == songs.size() - 1) {
                if (repeatMode == 2) playSong(0);
                else {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                    seekBar.setProgress(0);
                }
            } else playSong(currentIndex + 1);
        }
    }

    private void playRandom() {
        if (!songs.isEmpty()) playSong(random.nextInt(songs.size()));
    }

    private void playNext() {
        if (songs.isEmpty()) return;
        if (isShuffle) { playRandom(); return; }
        int nextIndex = currentIndex + 1;
        if (nextIndex >= songs.size()) {
            if (repeatMode == 2) nextIndex = 0;
            else return;
        }
        playSong(nextIndex);
    }

    private void playPrevious() {
        if (songs.isEmpty()) return;
        if (isShuffle) { playRandom(); return; }
        int prevIndex = currentIndex - 1;
        if (prevIndex < 0) {
            if (repeatMode == 2) prevIndex = songs.size() - 1;
            else return;
        }
        playSong(prevIndex);
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) {
            if (!songs.isEmpty()) playSong(Math.max(0, currentIndex));
            return;
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        } else {
            mediaPlayer.start();
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            handler.post(updateSeekRunnable);
        }
    }

    private void toggleShuffle() {
        isShuffle = !isShuffle;
        Toast.makeText(this, "Shuffle " + (isShuffle ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
    }

    private void toggleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        String text = (repeatMode == 0) ? "Repeat OFF" : (repeatMode == 1) ? "Repeat ONE" : "Repeat ALL";
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_choose_folder) {
            pickFolder();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(treeUri, 
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                } catch (SecurityException ignored) { }
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_FOLDER_URI, treeUri.toString()).apply();
                loadSongsFromFolder(treeUri);
            }
        }
    }
    public class MainActivity extends AppCompatActivity implements ServiceConnection {

        private static final int REQ_PICK_FOLDER = 1001;
        private MusicService musicService;
        private boolean isBound = false;
        private final List<Song> songs = new ArrayList<>();
        private SongsAdapter adapter;

        private TextView txtTitle;
        private ImageButton btnPlayPause;
        private SeekBar seekBar;
        private Handler handler = new Handler();

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);

            recyclerSongs = findViewById(R.id.recyclerSongs);
            txtTitle = findViewById(R.id.txtTitle);
            btnPlayPause = findViewById(R.id.btnPlayPause);
            seekBar = findViewById(R.id.seekBar);
            adapter = new SongsAdapter(songs, position -> {
                if (isBound) musicService.playSong(position);
            });
            recyclerSongs.setLayoutManager(new LinearLayoutManager(this));
            recyclerSongs.setAdapter(adapter);
            btnPlayPause.setOnClickListener(v -> {
                if (isBound) {
                    if (musicService.isPlaying()) musicService.pause();
                    else musicService.resume();
                }
            });
            findViewById(R.id.btnNext).setOnClickListener(v -> { if(isBound) musicService.playNext(); });
            findViewById(R.id.btnPrev).setOnClickListener(v -> { if(isBound) musicService.playPrevious(); });
            findViewById(R.id.btnShuffle).setOnClickListener(v -> { if(isBound) musicService.toggleShuffle(); });
            findViewById(R.id.btnRepeat).setOnClickListener(v -> { if(isBound) musicService.toggleRepeat(); });
            Intent intent = new Intent(this, MusicService.class);
            startService(intent); // Keeps service alive even if activity dies
            bindService(intent, this, Context.BIND_AUTO_CREATE);
            loadSavedFolder();
            handler.post(updateSeekRunnable);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            MusicService.MusicBinder musicBinder = (MusicService.MusicBinder) binder;
            musicService = musicBinder.getService();
            isBound = true;
            musicService.setSongs(songs);
            musicService.setSongChangedListener((song, isPlaying) -> {
                txtTitle.setText(song.getTitle());
                btnPlayPause.setImageResource(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            });

            if (musicService.isPlaying() && musicService.getCurrentSong() != null) {
                txtTitle.setText(musicService.getCurrentSong().getTitle());
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }

        private Runnable updateSeekRunnable = new Runnable() {
            @Override
            public void run() {
                if (isBound && musicService != null && musicService.isPlaying()) {
                    seekBar.setMax(musicService.getDuration());
                    seekBar.setProgress(musicService.getCurrentPosition());
                }
                handler.postDelayed(this, 1000);
            }
        };
    }
}
