package com.procrastilearn.app.e2e;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.util.Log;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class TestAssetFileProvider extends ContentProvider {
    private static final String TAG = "TestAssetFileProvider";

    @Override
    public boolean onCreate() {
        Context ctx = getContext();
        Log.d(TAG, "onCreate context=" + (ctx != null ? ctx.getPackageName() : "null"));
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "application/octet-stream";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
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
        Log.d(
            TAG,
            "openFile uri=" + uri + " mode=" + mode + " providerPkg=" + providerContext.getPackageName()
                + " assetPath=" + assetPath
        );

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
                        Log.e(TAG, "Failed to stream asset " + assetPath, ioException);
                    }
                },
                "TestAssetFileProvider-" + assetPath
            );
        copyThread.start();
        return readSide;
    }

    @Override
    public Cursor query(
        Uri uri,
        String[] projection,
        String selection,
        String[] selectionArgs,
        String sortOrder
    ) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
