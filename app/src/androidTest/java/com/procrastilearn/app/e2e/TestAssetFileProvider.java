package com.procrastilearn.app.e2e;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

public final class TestAssetFileProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "application/octet-stream";
    }

    @NonNull
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Context providerContext = getContext();
        if (providerContext == null) {
            throw new FileNotFoundException("Missing provider context for " + uri.getPath());
        }

        List<String> segments = uri.getPathSegments();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(segments.get(i));
        }
        final String assetPath = builder.toString();

        final InputStream assetStream;
        try {
            assetStream = providerContext.getAssets().open(assetPath);
        } catch (IOException ioException) {
            throw new FileNotFoundException("Asset for " + uri.getPath() + " not found: " + ioException.getMessage());
        }

        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException ioException) {
            throw new FileNotFoundException("Unable to open pipe for " + uri.getPath());
        }

        final ParcelFileDescriptor readSide = pipe[0];
        final ParcelFileDescriptor writeSide = pipe[1];
        Thread copyThread =
            new Thread(
                () -> {
                    try (InputStream input = assetStream; AutoCloseOutputStream output = new AutoCloseOutputStream(writeSide)) {
                        byte[] buffer = new byte[8_192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                        }
                    } catch (IOException ioException) {
                        throw new UncheckedIOException("Failed to stream asset " + assetPath, ioException);
                    }
                },
                "TestAssetFileProvider-" + assetPath
            );
        copyThread.start();
        return readSide;
    }

    @Nullable
    @Override
    public Cursor query(
        @NonNull Uri uri,
        @Nullable String[] projection,
        @Nullable String selection,
        @Nullable String[] selectionArgs,
        @Nullable String sortOrder
    ) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @NonNull ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @NonNull ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
