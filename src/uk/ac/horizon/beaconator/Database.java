package uk.ac.horizon.beaconator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.provider.BaseColumns;
import android.widget.Toast;

public class Database extends SQLiteOpenHelper {

	// If the database schema changes increment the database version.
	public static final int DATABASE_VERSION = 1;
	public static final String DATABASE_NAME = "beaconator.db";
	public static final String DATABASE_PATH = "uk.ac.horizon.beaconator";

	private static Database instance = null;
	private static SQLiteDatabase db = null;

	public static Database getInstance(Context context) {
		if (null == instance) {
			instance = new Database(context);
			db = instance.getWritableDatabase();
		}
		return instance;
	}

	public SQLiteDatabase getDb() {
		return db;
	}

	protected Database(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	public void onCreate(SQLiteDatabase db) {
		db.execSQL(SQL_CREATE_CONFIG);
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// This database is only a cache for online data, so its upgrade
		// policy is to simply to discard the data and start over
		db.execSQL(SQL_DELETE_CONFIG);
		onCreate(db);
	}

	public void onDowngrade(SQLiteDatabase db, int oldVersion,
			int newVersion) {
		onUpgrade(db, oldVersion, newVersion);
	}

	/**
	 * methods that create and maintain the database and tables
	 */
	private static final String SEP = ",";
	private static final String SQL_CREATE_CONFIG = "CREATE TABLE IF NOT EXISTS "
			+ ObservationColumns.TABLE_NAME
			+ "("
			+ ObservationColumns.COLUMN_NAME_OBS_TIME
			+ " INTEGER NOT NULL"
			+ SEP
			+ ObservationColumns.COLUMN_NAME_SESSION
			+ " INTEGER NOT NULL"
			+ SEP
			+ ObservationColumns.COLUMN_NAME_UUID
			+ " TEXT NOT NULL"
			+ SEP
			+ ObservationColumns.COLUMN_NAME_DEV_NAME
			+ " TEXT NOT NULL"
			+ SEP
			+ ObservationColumns.COLUMN_NAME_RSSI
			+ " INTEGER"
			+ SEP
			+ ObservationColumns.COLUMN_NAME_MAJOR
			+ " INTEGER"
			+ SEP
			+ ObservationColumns.COLUMN_NAME_MINOR
			+ " INTEGER"
			+ SEP
			+ ObservationColumns.COLUMN_NAME_TXPOWER
			+ " INTEGER"
			+ SEP
			+ " PRIMARY KEY ("
			+ ObservationColumns.COLUMN_NAME_OBS_TIME + SEP
			+ ObservationColumns.COLUMN_NAME_UUID + "))";

	private static final String SQL_DELETE_CONFIG = "DROP TABLE IF EXISTS "
			+ ObservationColumns.TABLE_NAME;

	/**
	 * SQLite Database contract for storing observations
	 * 
	 * @author Jesse
	 * 
	 */
	protected static abstract class ObservationColumns implements
			BaseColumns {
		public static final String TABLE_NAME = "BeaconScans";
		public static final String COLUMN_NAME_SESSION = "session";
		public static final String COLUMN_NAME_OBS_TIME = "obs_time";
		public static final String COLUMN_NAME_DEV_NAME = "devname";
		public static final String COLUMN_NAME_RSSI = "rssi";
		public static final String COLUMN_NAME_MAJOR = "major";
		public static final String COLUMN_NAME_MINOR = "minor";
		public static final String COLUMN_NAME_TXPOWER = "txPower";
		public static final String COLUMN_NAME_UUID = "uuid";
	}
	/**
	 * Export the DB contents to file
	 */
    @TargetApi(19)
	public static void exportDB(Context context) {

        try {
        	//Add to manifest: <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
            File sd = Environment.getExternalStorageDirectory();

            if (sd.canWrite()) {
                File currentDB = context.getDatabasePath(DATABASE_NAME);
                File backupDB = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), DATABASE_NAME );
                backupDB.createNewFile();

                FileInputStream fis = new FileInputStream(currentDB);
                FileChannel src = fis.getChannel();
                FileOutputStream fos = new FileOutputStream(backupDB);
                FileChannel dst = fos.getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();
                fos.close();
                fis.close();
                Toast.makeText(context, "Database backed up to: " + backupDB.toString(),
                        Toast.LENGTH_LONG).show();

            } else{
                Toast.makeText(context, "Couldn't write to SD", Toast.LENGTH_LONG)
                        .show();
            }
        } catch (Exception e) {

            Toast.makeText(context, e.toString(), Toast.LENGTH_LONG)
                    .show();

        }
    }
}

