package pt.amigos.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import pt.amigos.core.Contact;
import pt.amigos.core.Friend;

/** Cópia de segurança em JSON (exportar e restaurar). */
public final class Backup {
    private Backup() {}

    public static String export(Context c) throws JSONException {
        Db db = Db.get(c);
        JSONArray fs = new JSONArray();
        for (Friend f : db.all()) {
            JSONObject o = new JSONObject();
            o.put("id", f.id); o.put("name", f.name); o.put("phone", f.phone); o.put("email", f.email); o.put("notes", f.notes);
            o.put("bMonth", f.bMonth); o.put("bDay", f.bDay); o.put("bYear", f.bYear); o.put("everyDays", f.everyDays);
            o.put("lastContact", f.lastContact); o.put("created", f.created);
            fs.put(o);
        }
        JSONArray ks = new JSONArray();
        for (Contact k : db.allContacts()) {
            JSONObject o = new JSONObject();
            o.put("friend", k.friendId); o.put("at", k.at); o.put("kind", k.kind); o.put("note", k.note);
            ks.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("app", "amigos"); root.put("version", 1); root.put("friends", fs); root.put("contacts", ks);
        return root.toString(2);
    }

    /** Restaura a cópia; devolve o número de amigos importados. */
    public static int restore(Context c, String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!"amigos".equals(root.optString("app"))) throw new JSONException("Ficheiro não é uma cópia da app Amigos");
        List<Friend> friends = new ArrayList<>();
        JSONArray fs = root.getJSONArray("friends");
        for (int i = 0; i < fs.length(); i++) {
            JSONObject o = fs.getJSONObject(i);
            Friend f = new Friend();
            f.id = o.getLong("id"); f.name = o.getString("name");
            f.phone = o.optString("phone"); f.email = o.optString("email"); f.notes = o.optString("notes");
            f.bMonth = o.optInt("bMonth"); f.bDay = o.optInt("bDay"); f.bYear = o.optInt("bYear");
            f.everyDays = o.optInt("everyDays"); f.lastContact = o.optLong("lastContact"); f.created = o.optLong("created");
            friends.add(f);
        }
        List<Contact> contacts = new ArrayList<>();
        JSONArray ks = root.optJSONArray("contacts");
        for (int i = 0; ks != null && i < ks.length(); i++) {
            JSONObject o = ks.getJSONObject(i);
            Contact k = new Contact();
            k.friendId = o.getLong("friend"); k.at = o.getLong("at"); k.kind = o.optInt("kind"); k.note = o.optString("note");
            contacts.add(k);
        }
        Db.get(c).replaceAll(friends, contacts);
        return friends.size();
    }
}
