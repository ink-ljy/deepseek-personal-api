import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** Personal patch for the supplied 2.8.0 APK. No credentials in the build. */
public final class ApiSupport {
    static Context app;
    static WeakReference<Activity> activity = new WeakReference<>(null);
    static final String ORIGIN = "https://api.deepseek.com";
    static final String ALIAS = "deepseek.personal.api.key.v1";
    public static void init(Context context) {
        app = context.getApplicationContext();
        if (context instanceof Activity) activity = new WeakReference<>((Activity) context);
    }
    static SharedPreferences prefs() { return app.getSharedPreferences("personal_api_mode", Context.MODE_PRIVATE); }
    static boolean enabled() { return app != null && prefs().getBoolean("enabled", false) && !key().isEmpty(); }
    static javax.crypto.SecretKey secret() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (javax.crypto.SecretKey) store.getKey(ALIAS, null);
    }
    static void saveKey(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secret());
        String stored = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs().edit().putString("key", stored).putBoolean("enabled", true).commit()) throw new IOException("无法保存 API Key");
    }
    static String key() {
        try {
            String[] pieces = prefs().getString("key", "").split("\\.");
            if (pieces.length != 2) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
    static Object field(Object object, String name) throws Exception {
        Class<?> type = object instanceof Class ? (Class<?>) object : object.getClass();
        Field field = type.getField(name); return field.get(object instanceof Class ? null : object);
    }
    static void set(Object object, String name, Object value) throws Exception { object.getClass().getField(name).set(object, value); }
    static Class<?> cls(String name) throws Exception { return Class.forName(name); }
    static Object call(Object object, String name, Object... args) throws Exception {
        Class<?> type = object instanceof Class ? (Class<?>) object : object.getClass();
        for (Method m : type.getMethods()) if (m.getName().equals(name) && m.getParameterTypes().length == args.length) {
            try { return m.invoke(object instanceof Class ? null : object, args); }
            catch (IllegalArgumentException ignored) { }
            catch (InvocationTargetException e) { Throwable cause=e.getCause(); if(cause instanceof Exception) throw (Exception)cause; throw e; }
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    static Object make(String name, Object... args) throws Exception {
        for (Constructor<?> c : cls(name).getConstructors()) if (c.getParameterTypes().length == args.length) {
            try { return c.newInstance(args); } catch (IllegalArgumentException ignored) { }
        }
        throw new NoSuchMethodException(name+" constructor");
    }
    static Object unit() throws Exception { return field(cls("qjb"), "a"); }
    static byte[] readAll(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n; while ((n=in.read(b))!=-1) { if(out.size()+n>64*1024*1024) throw new IOException("响应过大"); out.write(b,0,n); }
            return out.toByteArray();
        }
    }
    static HttpURLConnection connection(String path, String method, String apiKey) throws Exception {
        if (!path.startsWith("/") || path.startsWith("//")) throw new IOException("API 路径无效");
        HttpURLConnection c = (HttpURLConnection) new URL(ORIGIN + path).openConnection();
        c.setInstanceFollowRedirects(false); c.setConnectTimeout(20000); c.setReadTimeout(120000);
        c.setRequestMethod(method); c.setRequestProperty("Authorization", "Bearer " + apiKey);
        c.setRequestProperty("Accept", "application/json"); return c;
    }
    static JSONObject get(String path, String apiKey) throws Exception {
        HttpURLConnection c=connection(path,"GET",apiKey); c.setReadTimeout(20000);
        try { int code=c.getResponseCode(); if(code!=200) throw new IOException(error(code));
            return new JSONObject(new String(readAll(c.getInputStream()),StandardCharsets.UTF_8));
        } finally { c.disconnect(); }
    }
    static String error(int code) {
        switch(code) { case 401: return "API Key 无效或已失效，请重新配置。"; case 402: return "API 余额不足，请到官方平台充值。";
            case 429: return "API 请求频率受限，请稍后重试。"; case 400: return "API 参数不受支持，请检查当前模型及输入。";
            default: return "DeepSeek API 返回 HTTP " + code + "，请稍后重试。"; }
    }
    static JSONObject obj(Object... entries) throws JSONException {
        JSONObject o=new JSONObject(); for(int i=0;i<entries.length;i+=2)o.put((String)entries[i], entries[i+1]==null?JSONObject.NULL:entries[i+1]); return o;
    }
    static void notice(String message) {
        Activity a=activity.get(); if(a!=null) a.runOnUiThread(()->android.widget.Toast.makeText(a,message,android.widget.Toast.LENGTH_LONG).show());
    }
}
