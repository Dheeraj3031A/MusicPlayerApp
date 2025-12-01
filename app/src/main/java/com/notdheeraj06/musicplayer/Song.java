package com.notdheeraj06.musicplayer;
import android.net.Uri;

public class Song {
    private final String title;
    private final Uri uri;

    public Song(String title, Uri uri) {
        this.title = title;
        this.uri = uri;
    }

    public String getTitle() { return title; }
    public Uri getUri() { return uri; }
}
