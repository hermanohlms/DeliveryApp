package com.example.deliveryapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

/** Durable branch menu cache populated only from the hosted API. */
public final class MenuCacheDb extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "crowdcam_catalog.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE = "branch_menu";
    private static final String CATALOG_TABLE = "catalog_cache";
    private static final Type MENU_TYPE = new TypeToken<List<Models.MenuItem>>() {}.getType();

    public MenuCacheDb(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (branch_id INTEGER PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE " + CATALOG_TABLE + " (cache_key TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + CATALOG_TABLE + " (cache_key TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)");
    }

    public synchronized List<Models.MenuItem> getMenu(int branchId, Gson gson) {
        try (SQLiteDatabase db = getReadableDatabase();
             Cursor cursor = db.query(TABLE, new String[]{"payload"}, "branch_id=?",
                     new String[]{String.valueOf(branchId)}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            return gson.fromJson(cursor.getString(0), MENU_TYPE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public synchronized void saveMenu(int branchId, List<Models.MenuItem> items, Gson gson) {
        ContentValues values = new ContentValues();
        values.put("branch_id", branchId);
        values.put("payload", gson.toJson(items, MENU_TYPE));
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized String getJson(String key) {
        try (SQLiteDatabase db = getReadableDatabase();
             Cursor cursor = db.query(CATALOG_TABLE, new String[]{"payload"}, "cache_key=?",
                     new String[]{key}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public synchronized void putJson(String key, String json) {
        ContentValues values = new ContentValues();
        values.put("cache_key", key);
        values.put("payload", json);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(CATALOG_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
}