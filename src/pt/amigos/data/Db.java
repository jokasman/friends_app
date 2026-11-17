package pt.amigos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import pt.amigos.core.Contact;
import pt.amigos.core.Friend;

/** Base de dados SQLite local: amigos e histórico de contactos. */
public final class Db extends SQLiteOpenHelper {
    private static Db inst;

    public static synchronized Db get(Context c) {
        if (inst == null) inst = new Db(c.getApplicationContext());
        return inst;
    }

    private Db(Context c) { super(c, "amigos.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE friends (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, email TEXT, notes TEXT,"
                + " bmonth INTEGER DEFAULT 0, bday INTEGER DEFAULT 0, byear INTEGER DEFAULT 0, every INTEGER DEFAULT 0,"
                + " last INTEGER DEFAULT 0, created INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE contacts (id INTEGER PRIMARY KEY AUTOINCREMENT, friend INTEGER NOT NULL, at INTEGER NOT NULL,"
                + " kind INTEGER DEFAULT 0, note TEXT)");
        db.execSQL("CREATE INDEX contacts_friend ON contacts(friend, at)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {}

    private static Friend friend(Cursor c) {
        Friend f = new Friend();
        f.id = c.getLong(0); f.name = c.getString(1);
        f.phone = nz(c.getString(2)); f.email = nz(c.getString(3)); f.notes = nz(c.getString(4));
        f.bMonth = c.getInt(5); f.bDay = c.getInt(6); f.bYear = c.getInt(7); f.everyDays = c.getInt(8);
        f.lastContact = c.getLong(9); f.created = c.getLong(10);
        return f;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static final String COLS = "id, name, phone, email, notes, bmonth, bday, byear, every, last, created";

    /** Todos os amigos por ordem alfabética. */
    public List<Friend> all() {
        List<Friend> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM friends", null)) {
            while (c.moveToNext()) out.add(friend(c));
        }
        final Collator col = Collator.getInstance(new Locale("pt", "PT"));
        col.setStrength(Collator.PRIMARY);
        Collections.sort(out, (a, b) -> col.compare(a.name, b.name));
        return out;
    }

    public Friend friend(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM friends WHERE id=?", new String[]{"" + id})) {
            return c.moveToFirst() ? friend(c) : null;
        }
    }

    public long save(Friend f) {
        ContentValues v = new ContentValues();
        v.put("name", f.name); v.put("phone", f.phone); v.put("email", f.email); v.put("notes", f.notes);
        v.put("bmonth", f.bMonth); v.put("bday", f.bDay); v.put("byear", f.bYear); v.put("every", f.everyDays);
        v.put("last", f.lastContact);
        if (f.created == 0) f.created = System.currentTimeMillis();
        v.put("created", f.created);
        if (f.id > 0) getWritableDatabase().update("friends", v, "id=?", new String[]{"" + f.id});
        else f.id = getWritableDatabase().insert("friends", null, v);
        return f.id;
    }

    public void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("contacts", "friend=?", new String[]{"" + id});
        db.delete("friends", "id=?", new String[]{"" + id});
    }

    public List<Contact> contacts(long friendId) {
        List<Contact> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, friend, at, kind, note FROM contacts WHERE friend=? ORDER BY at DESC", new String[]{"" + friendId})) {
            while (c.moveToNext()) out.add(contact(c));
        }
        return out;
    }

    public List<Contact> allContacts() {
        List<Contact> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, friend, at, kind, note FROM contacts ORDER BY at", null)) {
            while (c.moveToNext()) out.add(contact(c));
        }
        return out;
    }

    private static Contact contact(Cursor c) {
        Contact k = new Contact();
        k.id = c.getLong(0); k.friendId = c.getLong(1); k.at = c.getLong(2); k.kind = c.getInt(3); k.note = nz(c.getString(4));
        return k;
    }

    /** Regista um contacto e atualiza a data do último contacto do amigo. */
    public void addContact(Contact k) {
        ContentValues v = new ContentValues();
        v.put("friend", k.friendId); v.put("at", k.at); v.put("kind", k.kind); v.put("note", k.note);
        k.id = getWritableDatabase().insert("contacts", null, v);
        refreshLast(k.friendId);
    }

    public void deleteContact(Contact k) {
        getWritableDatabase().delete("contacts", "id=?", new String[]{"" + k.id});
        refreshLast(k.friendId);
    }

    private void refreshLast(long friendId) {
        getWritableDatabase().execSQL("UPDATE friends SET last = COALESCE((SELECT MAX(at) FROM contacts WHERE friend=?), 0) WHERE id=?",
                new Object[]{friendId, friendId});
    }

    /** Substitui tudo (restauro de cópia de segurança). */
    public void replaceAll(List<Friend> friends, List<Contact> contacts) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("contacts", null, null);
            db.delete("friends", null, null);
            for (Friend f : friends) {
                long old = f.id;
                f.id = 0;
                long nid = save(f);
                for (Contact k : contacts) if (k.friendId == old) { k.friendId = -nid; }
            }
            for (Contact k : contacts) {
                if (k.friendId >= 0) continue;
                ContentValues v = new ContentValues();
                v.put("friend", -k.friendId); v.put("at", k.at); v.put("kind", k.kind); v.put("note", k.note);
                db.insert("contacts", null, v);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
