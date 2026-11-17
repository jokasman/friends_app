package pt.relacoes.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Fotos das pessoas: guardadas reduzidas (máx. 384 px) na pasta privada da app. */
public final class Photos {
    private Photos() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(60);

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "photos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Copia e reduz a imagem; devolve o nome do ficheiro ou null se falhar. */
    public static String save(Context c, Uri uri) {
        try {
            Bitmap b;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.Source src = ImageDecoder.createSource(c.getContentResolver(), uri);
                b = ImageDecoder.decodeBitmap(src, (dec, info, s) -> {
                    int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                    int max = Math.max(w, h);
                    if (max > 384) dec.setTargetSize(w * 384 / max, h * 384 / max);
                    dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } else {
                try (InputStream is = c.getContentResolver().openInputStream(uri)) {
                    b = BitmapFactory.decodeStream(is);
                }
                if (b == null) return null;
                int max = Math.max(b.getWidth(), b.getHeight());
                if (max > 384) b = Bitmap.createScaledBitmap(b, b.getWidth() * 384 / max, b.getHeight() * 384 / max, true);
            }
            return store(c, b);
        } catch (Exception e) {
            return null;
        }
    }

    /** Guarda bytes de imagem (ex.: foto de um contacto). */
    public static String save(Context c, byte[] data) {
        try {
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
            return b == null ? null : store(c, b);
        } catch (Exception e) {
            return null;
        }
    }

    private static String store(Context c, Bitmap b) throws Exception {
        String name = "p" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000) + ".jpg";
        try (FileOutputStream os = new FileOutputStream(new File(dir(c), name))) {
            b.compress(Bitmap.CompressFormat.JPEG, 88, os);
        }
        return name;
    }

    public static Bitmap load(Context c, String name) {
        if (name == null || name.isEmpty()) return null;
        Bitmap b = CACHE.get(name);
        if (b != null) return b;
        File f = new File(dir(c), name);
        if (!f.exists()) return null;
        b = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (b != null) CACHE.put(name, b);
        return b;
    }

    public static void delete(Context c, String name) {
        if (name == null || name.isEmpty()) return;
        CACHE.remove(name);
        new File(dir(c), name).delete();
    }
}
