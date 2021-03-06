package br.ufcg.embedded.health.database;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import br.ufcg.embedded.health.structures.HealthData;

/**
 * Class of interface to use methods of database.
 * 
 * @author Tiago Brasileiro Araujo
 * 
 */
public class HealthDAO {
    public static final String TABLE_NAME = "HEALTH";
    public static final String COLUMN_ID = "ID";
    public static final String COLUMN_SYS = "SYSTOLIC";
    public static final String COLUMN_DIS = "DIASTOLIC";
    public static final String COLUMN_MAP = "MAP";
    public static final String COLUMN_DEVICE = "DEVICE";
    public static final String COLUMN_DATE = "DATE_MEASUREMENT";

    public static final String SCRIPT_CREATE_TABLE = "CREATE TABLE "
            + TABLE_NAME + "(" + COLUMN_ID
            + " INTEGER PRIMARY KEY AUTOINCREMENT," + COLUMN_SYS + " REAL,"
            + COLUMN_DIS + " REAL," + COLUMN_MAP + " REAL," + COLUMN_DATE
            + " TEXT," + COLUMN_DEVICE + " TEXT" + ")";

    public static final String SCRIPT_DROP_TABLE = "DROP TABLE IF EXISTS "
            + TABLE_NAME;

    private SQLiteDatabase dataBase = null;

    private static HealthDAO instance;

    public static HealthDAO getInstance(Context context) {
        if (instance == null)
            instance = new HealthDAO(context);
        return instance;
    }

    /**
     * Singleton pattern.
     * 
     * @param context
     */
    private HealthDAO(Context context) {
        PersistenceHelper persistenceHelper = PersistenceHelper
                .getInstance(context);
        dataBase = persistenceHelper.getWritableDatabase();
    }

    public void save(HealthData healthData) {
        ContentValues values = contentValues(healthData);
        dataBase.insert(TABLE_NAME, null, values);
    }

    public List<HealthData> ListAll() {
        String queryReturnAll = "SELECT * FROM " + TABLE_NAME;
        Cursor cursor = dataBase.rawQuery(queryReturnAll, null);
        ArrayList<HealthData> healthdatas = (ArrayList<HealthData>) createHealthList(cursor);
        Collections.sort(healthdatas);

        return healthdatas;
    }

    public HealthData lastInsert() {
        String queryReturnAll = "SELECT * FROM " + TABLE_NAME
                + " WHERE id = (SELECT MAX(id) FROM " + TABLE_NAME + ")";
        Cursor cursor = dataBase.rawQuery(queryReturnAll, null);
        ArrayList<HealthData> healthdatas = (ArrayList<HealthData>) createHealthList(cursor);
        Collections.sort(healthdatas);
        if (healthdatas.isEmpty()) {
            return null;
        }
        return healthdatas.get(0);
    }

    public void delete(HealthData health) {
        String[] whereArgs = { String.valueOf(health.getId()) };

        dataBase.delete(TABLE_NAME, COLUMN_ID + " = ?", whereArgs);
    }

    public void deleteAll() {
        dataBase.delete(TABLE_NAME, null, null);
    }

    public void closeConnection() {
        if (dataBase != null && dataBase.isOpen())
            dataBase.close();
    }

    private List<HealthData> createHealthList(Cursor cursor) {
        List<HealthData> healthDatas = new ArrayList<HealthData>();
        if (cursor == null)
            return healthDatas;

        try {

            if (cursor.moveToFirst()) {
                do {

                    int indexID = cursor.getColumnIndex(COLUMN_ID);
                    int indexSystolic = cursor.getColumnIndex(COLUMN_SYS);
                    int indexDiastolic = cursor.getColumnIndex(COLUMN_DIS);
                    int indexMap = cursor.getColumnIndex(COLUMN_MAP);
                    int indexDevice = cursor.getColumnIndex(COLUMN_DEVICE);
                    int indexDate = cursor.getColumnIndex(COLUMN_DATE);

                    int id = cursor.getInt(indexID);
                    Double sys = cursor.getDouble(indexSystolic);
                    Double dis = cursor.getDouble(indexDiastolic);
                    Double map = cursor.getDouble(indexMap);
                    String device = cursor.getString(indexDevice);
                    String date = cursor.getString(indexDate);

                    HealthData health = new HealthData(device, sys, dis, map,
                            convertToDate(date), id);

                    healthDatas.add(health);

                } while (cursor.moveToNext());
            }

        } finally {
            cursor.close();
        }
        return healthDatas;
    }

    private ContentValues contentValues(HealthData healthData) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYS, healthData.getSystolic());
        values.put(COLUMN_DIS, healthData.getDiastolic());
        values.put(COLUMN_MAP, healthData.getMAP());
        values.put(COLUMN_DEVICE, healthData.getDevice());
        values.put(COLUMN_DATE, convertToString(healthData.getDate()));

        return values;
    }

    private String convertToString(Date date) {
        DateFormat formater = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String reportDate = formater.format(date);

        return reportDate;
    }

    private Date convertToDate(String date_string) {
        Date date = null;
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            date = formatter.parse(date_string);
            return date;
        } catch (ParseException e) {
            return null;
        }
    }
}
