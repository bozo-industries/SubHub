package com.subhub.app.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class BookmarkStoreTest {
    @Test
    public void addsDeduplicatesAndRemovesBookmark() {
        Context context = ApplicationProvider.getApplicationContext();
        BookmarkStore store = new BookmarkStore(context);
        String url = "https://bookmark-test.invalid/path";
        store.remove(url);
        store.add("First", url);
        store.add("Updated", url);

        long matches = store.load().stream().filter(item -> item.url.equals(url)).count();
        assertTrue(matches == 1);
        assertTrue(store.load().stream().anyMatch(
                item -> item.url.equals(url) && item.title.equals("Updated")));

        store.remove(url);
        assertFalse(store.load().stream().anyMatch(item -> item.url.equals(url)));
    }
}
