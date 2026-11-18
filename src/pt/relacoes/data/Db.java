package pt.relacoes.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pt.relacoes.core.Gift;
import pt.relacoes.core.Group;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Person;
import pt.relacoes.core.Relation;
import pt.relacoes.core.Reminder;
import pt.relacoes.core.Template;

/** Base de dados SQLite local (funciona sem internet). */
public final class Db extends SQLiteOpenHelper {
    private static Db inst;

    public static synchronized Db get(Context c) {
        if (inst == null) inst = new Db(c.getApplicationContext());
        return inst;
    }

    private Db(Context c) { super(c, "relacoes.db", null, 5); }

    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(false); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE people (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, photo TEXT, phone TEXT, email TEXT,"
                + " notes TEXT, bmonth INTEGER DEFAULT 0, bday INTEGER DEFAULT 0, byear INTEGER DEFAULT 0, every INTEGER DEFAULT 0,"
                + " bday_on_day INTEGER DEFAULT 1, bday_before INTEGER DEFAULT 0, long_msg INTEGER DEFAULT 0, tone INTEGER DEFAULT 0,"
                + " likes TEXT, avoid TEXT, created INTEGER DEFAULT 0, last_at INTEGER DEFAULT 0, last_medium INTEGER DEFAULT -1, nickname TEXT)");
        db.execSQL("CREATE TABLE groups_ (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, color INTEGER, sort INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE person_groups (person INTEGER NOT NULL, grp INTEGER NOT NULL, PRIMARY KEY (person, grp))");
        db.execSQL("CREATE TABLE interactions (id INTEGER PRIMARY KEY AUTOINCREMENT, person INTEGER NOT NULL, at INTEGER NOT NULL,"
                + " medium INTEGER DEFAULT 8, note TEXT, message TEXT, template INTEGER DEFAULT 0, by_me INTEGER DEFAULT 1)");
        db.execSQL("CREATE INDEX interactions_person ON interactions(person, at)");
        db.execSQL("CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT, person INTEGER NOT NULL, text TEXT, kind INTEGER,"
                + " auto INTEGER DEFAULT 0, due_at INTEGER, unit INTEGER DEFAULT 3, n INTEGER DEFAULT 1, offset_days INTEGER DEFAULT 0,"
                + " nag INTEGER DEFAULT 0, next_at INTEGER, done INTEGER DEFAULT 0, done_at INTEGER DEFAULT 0, notified INTEGER DEFAULT 0, alarm INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE templates (id INTEGER PRIMARY KEY AUTOINCREMENT, type INTEGER, size INTEGER, text TEXT, builtin INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE relations (id INTEGER PRIMARY KEY AUTOINCREMENT, person INTEGER NOT NULL, other INTEGER NOT NULL, kind INTEGER)");
        db.execSQL("CREATE TABLE gifts (id INTEGER PRIMARY KEY AUTOINCREMENT, person INTEGER NOT NULL, text TEXT, done INTEGER DEFAULT 0, created INTEGER)");
        createDetections(db);
        String[] gs = {"Família", "Amigos", "Trabalho"};
        for (int i = 0; i < gs.length; i++) {
            ContentValues v = new ContentValues();
            v.put("name", gs[i]); v.put("color", Group.COLORS[i]); v.put("sort", i);
            db.insert("groups_", null, v);
        }
        seedTemplates(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) db.execSQL("ALTER TABLE interactions ADD COLUMN by_me INTEGER DEFAULT 1");
        if (o < 3) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN alarm INTEGER DEFAULT 0");
            // Aniversários e lembretes criados à mão passam a tocar como despertador.
            db.execSQL("UPDATE reminders SET alarm = 1 WHERE kind IN (0, 1, 3) AND auto <> 4");
        }
        if (o < 4) createDetections(db);
        if (o < 5) db.execSQL("ALTER TABLE people ADD COLUMN nickname TEXT");
    }

    private static void createDetections(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS detections (id INTEGER PRIMARY KEY AUTOINCREMENT, person INTEGER NOT NULL, at INTEGER NOT NULL,"
                + " medium INTEGER, app TEXT, status INTEGER DEFAULT 0)");
    }

    static void seedTemplates(SQLiteDatabase db) {
        for (Object[] t : Template.BUILTIN) {
            ContentValues v = new ContentValues();
            v.put("type", (Integer) t[0]); v.put("size", (Integer) t[1]); v.put("text", (String) t[2]); v.put("builtin", 1);
            db.insert("templates", null, v);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String[] a(long id) { return new String[]{String.valueOf(id)}; }

    // ---------------------------------------------------------------- Pessoas

    private static final String P_COLS = "id, name, photo, phone, email, notes, bmonth, bday, byear, every, bday_on_day, bday_before,"
            + " long_msg, tone, likes, avoid, created, last_at, last_medium, nickname";

    private static Person person(Cursor c) {
        Person p = new Person();
        p.id = c.getLong(0); p.name = nz(c.getString(1)); p.photo = nz(c.getString(2)); p.phone = nz(c.getString(3));
        p.email = nz(c.getString(4)); p.notes = nz(c.getString(5)); p.bMonth = c.getInt(6); p.bDay = c.getInt(7); p.bYear = c.getInt(8);
        p.everyDays = c.getInt(9); p.bdayOnDay = c.getInt(10) != 0; p.bdayBefore = c.getInt(11); p.longMessages = c.getInt(12) != 0;
        p.tone = c.getInt(13); p.likes = nz(c.getString(14)); p.avoid = nz(c.getString(15)); p.created = c.getLong(16);
        p.lastAt = c.getLong(17); p.lastMedium = c.getInt(18); p.nickname = nz(c.getString(19));
        return p;
    }

    public List<Person> people() {
        List<Person> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + P_COLS + " FROM people", null)) {
            while (c.moveToNext()) out.add(person(c));
        }
        final Collator col = Collator.getInstance(new Locale("pt", "PT"));
        col.setStrength(Collator.PRIMARY);
        Collections.sort(out, (x, y) -> col.compare(x.name, y.name));
        return out;
    }

    public Map<Long, Person> peopleById() {
        Map<Long, Person> m = new HashMap<>();
        for (Person p : people()) m.put(p.id, p);
        return m;
    }

    public Person person(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + P_COLS + " FROM people WHERE id=?", a(id))) {
            return c.moveToFirst() ? person(c) : null;
        }
    }

    public long save(Person p) {
        stamp++;
        ContentValues v = new ContentValues();
        v.put("name", p.name); v.put("photo", p.photo); v.put("phone", p.phone); v.put("email", p.email); v.put("notes", p.notes);
        v.put("bmonth", p.bMonth); v.put("bday", p.bDay); v.put("byear", p.bYear); v.put("every", p.everyDays);
        v.put("bday_on_day", p.bdayOnDay ? 1 : 0); v.put("bday_before", p.bdayBefore); v.put("long_msg", p.longMessages ? 1 : 0);
        v.put("tone", p.tone); v.put("likes", p.likes); v.put("avoid", p.avoid);
        if (p.created == 0) p.created = System.currentTimeMillis();
        v.put("created", p.created); v.put("last_at", p.lastAt); v.put("last_medium", p.lastMedium); v.put("nickname", p.nickname);
        if (p.id > 0) getWritableDatabase().update("people", v, "id=?", a(p.id));
        else p.id = getWritableDatabase().insert("people", null, v);
        return p.id;
    }

    public void deletePerson(long id) {
        stamp++;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String t : new String[]{"person_groups", "interactions", "reminders", "gifts"}) db.delete(t, "person=?", a(id));
            db.delete("relations", "person=? OR other=?", new String[]{"" + id, "" + id});
            db.delete("people", "id=?", a(id));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ---------------------------------------------------------------- Grupos

    /** Muda sempre que os grupos ou as pertenças mudam (para as caches de cores). */
    public static volatile int stamp;

    /** Cor de cada pessoa: a do seu primeiro grupo (pela ordem dos grupos). Pessoas sem grupo não aparecem. */
    public Map<Long, Integer> personColors() {
        Map<Long, Integer> m = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT pg.person, g.color FROM person_groups pg JOIN groups_ g ON g.id = pg.grp"
                + " ORDER BY g.sort DESC, g.name DESC", null)) {
            while (c.moveToNext()) m.put(c.getLong(0), c.getInt(1)); // o último a escrever é o primeiro grupo
        }
        return m;
    }

    public List<Group> groups() {
        List<Group> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, name, color, sort FROM groups_ ORDER BY sort, name", null)) {
            while (c.moveToNext()) {
                Group g = new Group();
                g.id = c.getLong(0); g.name = c.getString(1); g.color = c.getInt(2); g.sort = c.getInt(3);
                out.add(g);
            }
        }
        return out;
    }

    public long save(Group g) {
        stamp++;
        ContentValues v = new ContentValues();
        v.put("name", g.name); v.put("color", g.color); v.put("sort", g.sort);
        if (g.id > 0) getWritableDatabase().update("groups_", v, "id=?", a(g.id));
        else g.id = getWritableDatabase().insert("groups_", null, v);
        return g.id;
    }

    public void deleteGroup(long id) {
        stamp++;
        getWritableDatabase().delete("person_groups", "grp=?", a(id));
        getWritableDatabase().delete("groups_", "id=?", a(id));
    }

    public List<Long> groupsOf(long person) {
        List<Long> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT grp FROM person_groups WHERE person=?", a(person))) {
            while (c.moveToNext()) out.add(c.getLong(0));
        }
        return out;
    }

    /** Mapa pessoa → grupos. */
    public Map<Long, List<Long>> allMemberships() {
        Map<Long, List<Long>> m = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT person, grp FROM person_groups", null)) {
            while (c.moveToNext()) {
                List<Long> l = m.get(c.getLong(0));
                if (l == null) m.put(c.getLong(0), l = new ArrayList<>());
                l.add(c.getLong(1));
            }
        }
        return m;
    }

    public void setGroups(long person, List<Long> groups) {
        stamp++;
        SQLiteDatabase db = getWritableDatabase();
        db.delete("person_groups", "person=?", a(person));
        for (Long g : groups) {
            ContentValues v = new ContentValues();
            v.put("person", person); v.put("grp", g);
            db.insertWithOnConflict("person_groups", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    // ---------------------------------------------------------------- Interações

    private static final String I_COLS = "id, person, at, medium, note, message, template, by_me";

    private static Interaction interaction(Cursor c) {
        Interaction i = new Interaction();
        i.id = c.getLong(0); i.personId = c.getLong(1); i.at = c.getLong(2); i.medium = c.getInt(3);
        i.note = nz(c.getString(4)); i.message = nz(c.getString(5)); i.templateId = c.getLong(6); i.byMe = c.getInt(7) != 0;
        return i;
    }

    public List<Interaction> interactions(long person) {
        List<Interaction> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + I_COLS + " FROM interactions WHERE person=? ORDER BY at DESC", a(person))) {
            while (c.moveToNext()) out.add(interaction(c));
        }
        return out;
    }

    /** Interações entre dois instantes (todas as pessoas), da mais antiga para a mais recente. */
    public List<Interaction> interactionsBetween(long from, long to) {
        List<Interaction> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + I_COLS + " FROM interactions WHERE at>=? AND at<? ORDER BY at",
                new String[]{"" + from, "" + to})) {
            while (c.moveToNext()) out.add(interaction(c));
        }
        return out;
    }

    public void add(Interaction i) {
        ContentValues v = new ContentValues();
        v.put("person", i.personId); v.put("at", i.at); v.put("medium", i.medium); v.put("note", i.note);
        v.put("message", i.message); v.put("template", i.templateId); v.put("by_me", i.byMe ? 1 : 0);
        i.id = getWritableDatabase().insert("interactions", null, v);
        refreshLast(i.personId);
    }

    public void delete(Interaction i) {
        getWritableDatabase().delete("interactions", "id=?", a(i.id));
        refreshLast(i.personId);
    }

    private void refreshLast(long person) {
        long at = 0;
        int medium = -1;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT at, medium FROM interactions WHERE person=? ORDER BY at DESC LIMIT 1", a(person))) {
            if (c.moveToFirst()) { at = c.getLong(0); medium = c.getInt(1); }
        }
        ContentValues v = new ContentValues();
        v.put("last_at", at); v.put("last_medium", medium);
        getWritableDatabase().update("people", v, "id=?", a(person));
    }

    // ---------------------------------------------------------------- Lembretes

    private static final String R_COLS = "id, person, text, kind, auto, due_at, unit, n, offset_days, nag, next_at, done, done_at, notified, alarm";

    private static Reminder reminder(Cursor c) {
        Reminder r = new Reminder();
        r.id = c.getLong(0); r.personId = c.getLong(1); r.text = nz(c.getString(2)); r.kind = c.getInt(3); r.auto = c.getInt(4);
        r.dueAt = c.getLong(5); r.repeatUnit = c.getInt(6); r.repeatN = c.getInt(7); r.offsetDays = c.getInt(8); r.nagMin = c.getInt(9);
        r.nextAt = c.isNull(10) ? Long.MAX_VALUE : c.getLong(10); r.done = c.getInt(11) != 0; r.doneAt = c.getLong(12);
        r.notified = c.getInt(13) != 0;
        r.alarm = c.getInt(14) != 0;
        return r;
    }

    public List<Reminder> reminders() {
        List<Reminder> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + R_COLS + " FROM reminders ORDER BY due_at", null)) {
            while (c.moveToNext()) out.add(reminder(c));
        }
        return out;
    }

    public List<Reminder> reminders(long person) {
        List<Reminder> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + R_COLS + " FROM reminders WHERE person=? ORDER BY done, due_at", a(person))) {
            while (c.moveToNext()) out.add(reminder(c));
        }
        return out;
    }

    public Reminder reminder(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + R_COLS + " FROM reminders WHERE id=?", a(id))) {
            return c.moveToFirst() ? reminder(c) : null;
        }
    }

    public long save(Reminder r) {
        ContentValues v = new ContentValues();
        v.put("person", r.personId); v.put("text", r.text); v.put("kind", r.kind); v.put("auto", r.auto); v.put("due_at", r.dueAt);
        v.put("unit", r.repeatUnit); v.put("n", r.repeatN); v.put("offset_days", r.offsetDays); v.put("nag", r.nagMin);
        v.put("next_at", r.nextAt); v.put("done", r.done ? 1 : 0); v.put("done_at", r.doneAt); v.put("notified", r.notified ? 1 : 0); v.put("alarm", r.alarm ? 1 : 0);
        if (r.id > 0) getWritableDatabase().update("reminders", v, "id=?", a(r.id));
        else r.id = getWritableDatabase().insert("reminders", null, v);
        return r.id;
    }

    public void deleteReminder(long id) { getWritableDatabase().delete("reminders", "id=?", a(id)); }

    /** Apaga lembretes concluídos (não recorrentes) há mais de N dias. */
    public void purgeDone(long before) {
        getWritableDatabase().delete("reminders", "done=1 AND done_at<?", new String[]{"" + before});
    }

    // ---------------------------------------------------------------- Modelos

    public List<Template> templates() {
        List<Template> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, type, size, text, builtin FROM templates ORDER BY type, size, id", null)) {
            while (c.moveToNext()) {
                Template t = new Template();
                t.id = c.getLong(0); t.type = c.getInt(1); t.size = c.getInt(2); t.text = nz(c.getString(3)); t.builtin = c.getInt(4) != 0;
                out.add(t);
            }
        }
        return out;
    }

    public long save(Template t) {
        ContentValues v = new ContentValues();
        v.put("type", t.type); v.put("size", t.size); v.put("text", t.text); v.put("builtin", t.builtin ? 1 : 0);
        if (t.id > 0) getWritableDatabase().update("templates", v, "id=?", a(t.id));
        else t.id = getWritableDatabase().insert("templates", null, v);
        return t.id;
    }

    public void deleteTemplate(long id) { getWritableDatabase().delete("templates", "id=?", a(id)); }

    /** Volta a acrescentar os modelos de origem que tenham sido apagados. */
    public int restoreBuiltinTemplates() {
        List<String> have = new ArrayList<>();
        for (Template t : templates()) have.add(t.text);
        int n = 0;
        for (Object[] t : Template.BUILTIN) {
            if (have.contains((String) t[2])) continue;
            ContentValues v = new ContentValues();
            v.put("type", (Integer) t[0]); v.put("size", (Integer) t[1]); v.put("text", (String) t[2]); v.put("builtin", 1);
            getWritableDatabase().insert("templates", null, v);
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- Ligações

    public List<Relation> relations(long person) {
        List<Relation> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, person, other, kind FROM relations WHERE person=?", a(person))) {
            while (c.moveToNext()) {
                Relation r = new Relation();
                r.id = c.getLong(0); r.personId = c.getLong(1); r.otherId = c.getLong(2); r.kind = c.getInt(3);
                out.add(r);
            }
        }
        return out;
    }

    public List<Relation> allRelations() {
        List<Relation> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, person, other, kind FROM relations", null)) {
            while (c.moveToNext()) {
                Relation r = new Relation();
                r.id = c.getLong(0); r.personId = c.getLong(1); r.otherId = c.getLong(2); r.kind = c.getInt(3);
                out.add(r);
            }
        }
        return out;
    }

    /** Liga as duas pessoas (guarda os dois sentidos). "other" é o/a {kind} de "person". */
    public void relate(long person, long other, int kind) {
        unrelate(person, other);
        ContentValues v = new ContentValues();
        v.put("person", person); v.put("other", other); v.put("kind", kind);
        getWritableDatabase().insert("relations", null, v);
        v = new ContentValues();
        v.put("person", other); v.put("other", person); v.put("kind", Relation.inverse(kind));
        getWritableDatabase().insert("relations", null, v);
    }

    public void unrelate(long person, long other) {
        getWritableDatabase().delete("relations", "(person=? AND other=?) OR (person=? AND other=?)",
                new String[]{"" + person, "" + other, "" + other, "" + person});
    }

    // ---------------------------------------------------------------- Prendas

    public List<Gift> gifts(long person) {
        List<Gift> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, person, text, done, created FROM gifts WHERE person=? ORDER BY done, created DESC", a(person))) {
            while (c.moveToNext()) out.add(gift(c));
        }
        return out;
    }

    public List<Gift> allGifts() {
        List<Gift> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, person, text, done, created FROM gifts", null)) {
            while (c.moveToNext()) out.add(gift(c));
        }
        return out;
    }

    private static Gift gift(Cursor c) {
        Gift g = new Gift();
        g.id = c.getLong(0); g.personId = c.getLong(1); g.text = nz(c.getString(2)); g.done = c.getInt(3) != 0; g.created = c.getLong(4);
        return g;
    }

    public long save(Gift g) {
        ContentValues v = new ContentValues();
        v.put("person", g.personId); v.put("text", g.text); v.put("done", g.done ? 1 : 0);
        if (g.created == 0) g.created = System.currentTimeMillis();
        v.put("created", g.created);
        if (g.id > 0) getWritableDatabase().update("gifts", v, "id=?", a(g.id));
        else g.id = getWritableDatabase().insert("gifts", null, v);
        return g.id;
    }

    public void deleteGift(long id) { getWritableDatabase().delete("gifts", "id=?", a(id)); }

    // ---------------------------------------------------------------- Contactos detetados (notificações)

    public static final int DET_PENDING = 0, DET_ACCEPTED = 1, DET_DISMISSED = 2;

    public static final class Detection {
        public long id, personId, at;
        public int medium, status;
        public String app = "";
    }

    public long addDetection(long person, long at, int medium, String app, int status) {
        ContentValues v = new ContentValues();
        v.put("person", person); v.put("at", at); v.put("medium", medium); v.put("app", app); v.put("status", status);
        return getWritableDatabase().insert("detections", null, v);
    }

    /** Já houve deteção desta pessoa por este meio desde "since"? */
    public boolean detectedSince(long person, int medium, long since) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM detections WHERE person=? AND medium=? AND at>=? LIMIT 1",
                new String[]{"" + person, "" + medium, "" + since})) {
            return c.moveToFirst();
        }
    }

    public List<Detection> pendingDetections() {
        List<Detection> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, person, at, medium, app, status FROM detections WHERE status=0 ORDER BY at DESC", null)) {
            while (c.moveToNext()) {
                Detection d = new Detection();
                d.id = c.getLong(0); d.personId = c.getLong(1); d.at = c.getLong(2); d.medium = c.getInt(3); d.app = nz(c.getString(4)); d.status = c.getInt(5);
                out.add(d);
            }
        }
        return out;
    }

    public Detection detection(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, person, at, medium, app, status FROM detections WHERE id=?", a(id))) {
            if (!c.moveToFirst()) return null;
            Detection d = new Detection();
            d.id = c.getLong(0); d.personId = c.getLong(1); d.at = c.getLong(2); d.medium = c.getInt(3); d.app = nz(c.getString(4)); d.status = c.getInt(5);
            return d;
        }
    }

    public void setDetectionStatus(long id, int status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        getWritableDatabase().update("detections", v, "id=?", a(id));
    }

    /** Fecha as pendentes de uma pessoa (ex.: já registou o contacto à mão) e limpa as antigas. */
    public void closeDetections(long person) {
        ContentValues v = new ContentValues();
        v.put("status", DET_DISMISSED);
        getWritableDatabase().update("detections", v, "person=? AND status=0", a(person));
    }

    public void purgeDetections(long before) {
        getWritableDatabase().delete("detections", "at<?", new String[]{"" + before});
    }

    // ---------------------------------------------------------------- Cópia de segurança

    public SQLiteDatabase raw() { return getWritableDatabase(); }
}
