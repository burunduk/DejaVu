package org.fitchfamily.android.dejavu;
/*
 *    DejaVu - A location provider backend for microG/UnifiedNlp
 *
 *    Copyright (C) 2017 Tod Fitch
 *
 *    This program is Free Software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as
 *    published by the Free Software Foundation, either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


/**
 * Created by tfitch on 9/1/17.
 */

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import java.util.HashSet;

/**
 * Interface to our on flash SQL database. Note that these methods are not
 * thread safe. However all access to the database is through the Cache object
 * which is thread safe.
 */
public class Database extends SQLiteOpenHelper {
    private static final String TAG = "DejaVu DB";

    private static final int VERSION = 2;
    private static final String NAME = "rf.db";

    private static final String TABLE_SAMPLES = "emitters";

    private static final String COL_TYPE = "rfType";
    private static final String COL_RFID = "rfID";
    private static final String COL_TRUST = "trust";
    private static final String COL_LAT = "latitude";
    private static final String COL_LON = "longitude";
    private static final String COL_RAD = "radius";          // v1 of database
    private static final String COL_RAD_NS = "radius_ns";    // v2 of database
    private static final String COL_RAD_EW= "radius_ew";     // v2 of database
    private static final String COL_NOTE = "note";

    private SQLiteDatabase database;
    private boolean withinTransaction;
    private boolean updatesMade;

    private SQLiteStatement sqlSampleInsert;
    private SQLiteStatement sqlSampleUpdate;
    private SQLiteStatement sqlAPdrop;

    public class EmitterInfo {
        public double latitude;
        public double longitude;
        public float radius_ns;
        public float radius_ew;
        public long trust;
        public String note;
    }

    public Database(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        database = db;
        withinTransaction = false;
        // Always create version 1 of database, then update the schema
        // in the same order it might occur "in the wild". Avoids having
        // to check to see if the table exists (may be old version)
        // or not (can be new version).
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SAMPLES + "(" +
                COL_RFID + " STRING PRIMARY KEY, " +
                COL_TYPE + " STRING, " +
                COL_TRUST + " INTEGER, " +
                COL_LAT + " REAL, " +
                COL_LON + " REAL, " +
                COL_RAD + " REAL, " +
                COL_NOTE + " STRING);");

        onUpgrade(db, 1, VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) { // upgrade to 2
            Log.d(TAG, "onUpgrade(): From: "+ oldVersion + " to 2");
            // Sqlite3 does not support dropping columns so we create a new table with our
            // current fields and copy the old data into it.
            db.execSQL("BEGIN TRANSACTION;");
            db.execSQL("ALTER TABLE " + TABLE_SAMPLES + " RENAME TO " + TABLE_SAMPLES + "_old;");
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SAMPLES + "(" +
                    COL_RFID + " STRING PRIMARY KEY, " +
                    COL_TYPE + " STRING, " +
                    COL_TRUST + " INTEGER, " +
                    COL_LAT + " REAL, " +
                    COL_LON + " REAL, " +
                    COL_RAD_NS + " REAL, " +
                    COL_RAD_EW + " REAL, " +
                    COL_NOTE + " STRING);");

            db.execSQL("INSERT INTO " + TABLE_SAMPLES + "(" +
                    COL_RFID + ", " +
                    COL_TYPE + ", " +
                    COL_TRUST + ", " +
                    COL_LAT + ", " +
                    COL_LON + ", " +
                    COL_RAD_NS + ", " +
                    COL_RAD_EW + ", " +
                    COL_NOTE +
                    ") SELECT " +
                    COL_RFID + ", " +
                    COL_TYPE + ", " +
                    COL_TRUST + ", " +
                    COL_LAT + ", " +
                    COL_LON + ", " +
                    COL_RAD + ", " +
                    COL_RAD + ", " +
                    COL_NOTE +
                    " FROM " + TABLE_SAMPLES + "_old;");
            db.execSQL("DROP TABLE " + TABLE_SAMPLES + "_old;");
            db.execSQL("COMMIT;");
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
    }

    /**
     * Start an update operation.
     *
     * We make sure we are not already in a transaction, make sure
     * our database is writeable, compile the insert, update and drop
     * statements that are likely to be used, etc. Then we actually
     * start the transaction on the underlying SQL database.
     */
    public void beginTransaction() {
        //Log.d(TAG,"beginTransaction()");
        if (withinTransaction) {
            Log.d(TAG,"beginTransaction() - Already in a transaction?");
            return;
        }
        withinTransaction = true;
        updatesMade = false;
        database = getWritableDatabase();

        sqlSampleInsert = database.compileStatement("INSERT INTO " +
                TABLE_SAMPLES + "("+
                COL_RFID + ", " +
                COL_TYPE + ", " +
                COL_TRUST + ", " +
                COL_LAT + ", " +
                COL_LON + ", " +
                COL_RAD_NS + ", " +
                COL_RAD_EW + ", " +
                COL_NOTE + ") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?);");

        sqlSampleUpdate = database.compileStatement("UPDATE " +
                TABLE_SAMPLES + " SET "+
                COL_TRUST + "=?, " +
                COL_LAT + "=?, " +
                COL_LON + "=?, " +
                COL_RAD_NS + "=?, " +
                COL_RAD_EW + "=?, " +
                COL_NOTE + "=? " +
                "WHERE " + COL_RFID + "=? AND " + COL_TYPE + "=?;");

        sqlAPdrop = database.compileStatement("DELETE FROM " +
                TABLE_SAMPLES +
                " WHERE " + COL_RFID + "=? AND " + COL_TYPE  + "=?;");

        database.beginTransaction();
    }

    /**
     * End a transaction. If we actually made any changes then we mark
     * the transaction as successful. Once marked as successful we
     * end the transaction with the underlying SQL database.
     */
    public void endTransaction() {
        //Log.d(TAG,"endTransaction()");
        if (!withinTransaction) {
            Log.d(TAG,"Asked to end transaction but we are not in one???");
        }

        if (updatesMade) {
            //Log.d(TAG,"endTransaction() - Setting transaction successful.");
            database.setTransactionSuccessful();
        }
        updatesMade = false;
        database.endTransaction();
        withinTransaction = false;
    }

    /**
     * Drop an RF emitter from the database.
     *
     * @param emitter The emitter to be dropped.
     */
    public void drop(RfEmitter emitter) {
        //Log.d(TAG, "Dropping " + emitter.logString() + " from db");

        sqlAPdrop.bindString(1, emitter.getId());
        sqlAPdrop.bindString(2, emitter.getTypeString());
        sqlAPdrop.executeInsert();
        sqlAPdrop.clearBindings();
        updatesMade = true;
    }

    /**
     * Insert a new RF emitter into the database.
     *
     * @param emitter The emitter to be added.
     */
    public void insert(RfEmitter emitter) {
        //Log.d(TAG, "Inserting " + emitter.logString() + " into db");
        sqlSampleInsert.bindString(1, emitter.getId());
        sqlSampleInsert.bindString(2, String.valueOf(emitter.getType()));
        sqlSampleInsert.bindString(3, String.valueOf(emitter.getTrust()));
        sqlSampleInsert.bindString(4, String.valueOf(emitter.getLat()));
        sqlSampleInsert.bindString(5, String.valueOf(emitter.getLon()));
        sqlSampleInsert.bindString(6, String.valueOf(emitter.getRadiusNS()));
        sqlSampleInsert.bindString(7, String.valueOf(emitter.getRadiusEW()));
        sqlSampleInsert.bindString(8, emitter.getNote());

        sqlSampleInsert.executeInsert();
        sqlSampleInsert.clearBindings();
        updatesMade = true;
    }

    /**
     * Update information about an emitter already existing in the database
     *
     * @param emitter The emitter to be updated
     */
    public void update(RfEmitter emitter) {
        //Log.d(TAG, "Updating " + emitter.logString() + " in db");
        // the data fields
        sqlSampleUpdate.bindString(1, String.valueOf(emitter.getTrust()));
        sqlSampleUpdate.bindString(2, String.valueOf(emitter.getLat()));
        sqlSampleUpdate.bindString(3, String.valueOf(emitter.getLon()));
        sqlSampleUpdate.bindString(4, String.valueOf(emitter.getRadiusNS()));
        sqlSampleUpdate.bindString(5, String.valueOf(emitter.getRadiusEW()));
        sqlSampleUpdate.bindString(6, emitter.getNote());

        // the Where fields
        sqlSampleUpdate.bindString(7, emitter.getId());
        sqlSampleUpdate.bindString(8, String.valueOf(emitter.getType()));
        sqlSampleUpdate.executeInsert();
        sqlSampleUpdate.clearBindings();
        updatesMade = true;
    }

    /**
     * Return a list of all emitters of a specified type within a bounding box.
     *
     * @param rfType The type of emitter the caller is interested in
     * @param bb The lat,lon bounding box.
     * @return A collection of RF emitter identifications
     */
    public HashSet<RfIdentification> getEmitters(RfEmitter.EmitterType rfType, BoundingBox bb) {
        HashSet<RfIdentification> rslt = new HashSet<RfIdentification>();
        String query = "SELECT " +
                COL_RFID + " " +
                " FROM " + TABLE_SAMPLES +
                " WHERE " + COL_TYPE + "='" + rfType +
                "' AND " + COL_LAT + ">='" + bb.getSouth() +
                "' AND " + COL_LAT + "<='" + bb.getNorth() +
                "' AND " + COL_LON + ">='" + bb.getWest() +
                "' AND " + COL_LON + "<='" + bb.getEast() + "';";

        //Log.d(TAG, "getEmitters(): query='"+query+"'");
        Cursor cursor = getReadableDatabase().rawQuery(query, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    RfIdentification e = new RfIdentification(cursor.getString(0), rfType);
                    rslt.add(e);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return rslt;
    }

    /**
     * Get all the information we have on an RF emitter
     *
     * @param ident The identification of the emitter caller wants
     * @return A emitter object with all the information we have. Or null if we have nothing.
     */
    public RfEmitter getEmitter(RfIdentification ident) {
        RfEmitter rslt = null;
        String query = "SELECT " +
                COL_TYPE + ", " +
                COL_TRUST + ", " +
                COL_LAT + ", " +
                COL_LON + ", " +
                COL_RAD_NS+ ", " +
                COL_RAD_EW+ ", " +
                COL_NOTE + " " +
                " FROM " + TABLE_SAMPLES +
                " WHERE " + COL_TYPE + "='" + ident.getRfType() +
                "' AND " + COL_RFID + "='" + ident.getRfId() + "';";

        // Log.d(TAG, "getEmitter(): query='"+query+"'");
        Cursor cursor = getReadableDatabase().rawQuery(query, null);
        try {
            if (cursor.moveToFirst()) {
                rslt = new RfEmitter(ident);
                EmitterInfo ei = new EmitterInfo();
                ei.trust = (int) cursor.getLong(1);
                ei.latitude = (double) cursor.getDouble(2);
                ei.longitude = (double) cursor.getDouble(3);
                ei.radius_ns = (float) cursor.getDouble(4);
                ei.radius_ew = (float) cursor.getDouble(5);
                ei.note = cursor.getString(6);
                if (ei.note == null)
                    ei.note = "";
                rslt.updateInfo(ei);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return rslt;
    }
}
