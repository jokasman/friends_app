package pt.relacoes.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;

/**
 * Cópia de segurança completa em JSON (todas as tabelas e fotos).
 * A cópia automática para o Google Drive é feita pelo Android (Auto Backup), sem login na app.
 */
public final class Backup {
    private Backup() {}

    private static final String[] TABLES = {"people", "groups_", "person_groups", "interactions", "reminders", "templates", "relations", "gifts"};

    public static String export(Context c) throws JSONException, IOException {
        SQLiteDatabase db = Db.get(c).raw();
        JSONObject root = new JSONObject();
        root.put("app", "relacoes");
        root.put("version", 1);
        root.put("exported", System.currentTimeMillis());
        JSONObject tables = new JSONObject();
        for (String t : TABLES) {
            JSONArray rows = new JSONArray();
            try (Cursor cur = db.rawQuery("SELECT * FROM " + t, null)) {
                while (cur.moveToNext()) {
                    JSONObject o = new JSONObject();
                    for (int i = 0; i < cur.getColumnCount(); i++) {
                        switch (cur.getType(i)) {
                            case Cursor.FIELD_TYPE_NULL: break;
                            case Cursor.FIELD_TYPE_INTEGER: o.put(cur.getColumnName(i), cur.getLong(i)); break;
                            case Cursor.FIELD_TYPE_FLOAT: o.put(cur.getColumnName(i), cur.getDouble(i)); break;
                            default: o.put(cur.getColumnName(i), cur.getString(i));
                        }
                    }
                    rows.put(o);
                }
            }
            tables.put(t, rows);
        }
        root.put("tables", tables);
        JSONObject photos = new JSONObject();
        File[] files = Photos.dir(c).listFiles();
        if (files != null) for (File f : files) photos.put(f.getName(), Base64.encodeToString(Files.readAllBytes(f.toPath()), Base64.NO_WRAP));
        root.put("photos", photos);
        return root.toString();
    }

    /** Substitui todos os dados pelos da cópia. Devolve o número de pessoas. */
    public static int restore(Context c, String json) throws JSONException, IOException {
        JSONObject root = new JSONObject(json);
        if (!"relacoes".equals(root.optString("app"))) throw new JSONException("O ficheiro não é uma cópia desta app.");
        JSONObject tables = root.getJSONObject("tables");
        SQLiteDatabase db = Db.get(c).raw();
        int people = 0;
        db.beginTransaction();
        try {
            for (String t : TABLES) {
                JSONArray rows = tables.optJSONArray(t);
                if (rows == null) continue;
                db.delete(t, null, null);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject o = rows.getJSONObject(i);
                    ContentValues v = new ContentValues();
                    for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                        String k = it.next();
                        Object val = o.get(k);
                        if (val instanceof Number) {
                            if (val instanceof Double || val instanceof Float) v.put(k, ((Number) val).doubleValue());
                            else v.put(k, ((Number) val).longValue());
                        } else if (val != JSONObject.NULL) v.put(k, String.valueOf(val));
                    }
                    db.insert(t, null, v);
                }
                if (t.equals("people")) people = rows.length();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        JSONObject photos = root.optJSONObject("photos");
        if (photos != null) {
            File dir = Photos.dir(c);
            for (Iterator<String> it = photos.keys(); it.hasNext(); ) {
                String name = it.next();
                if (name.contains("/") || name.contains("..")) continue;
                try (FileOutputStream os = new FileOutputStream(new File(dir, name))) {
                    os.write(Base64.decode(photos.getString(name), Base64.DEFAULT));
                }
            }
        }
        return people;
    }
}
