package dev.local.deepseekapitest;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import android.util.Base64;

/** Header validation regression fixtures. No real credentials or network. */
public final class ApiKeyTest {
    static int opened;
    static String normalize(ApiSelfTest t,String s)throws Exception{return (String)t.call(t.c("ApiSupport"),"normalizeKey",s);}
    public static void run(ApiSelfTest t)throws Exception{
        String clean="local-key-fixture-only",dirty="\r\n local-key-\nfixture-\tonly \r\n";
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){opened++;return new HttpURLConnection(u){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}};}}:null);
        try{
            t.check(normalize(t,clean).equals(clean),"clean token is preserved exactly");
            t.check(normalize(t,dirty).equals(clean),"clipboard CR LF tabs and spaces are removed");
            t.check(normalize(t,"\ufefflocal-\u200bkey-fixture\u00a0-only\u2060").equals(clean),"zero-width and Unicode clipboard spacing are removed");
            t.check(normalize(t,"Bearer "+dirty).equals(clean),"pasted Bearer prefix is not duplicated");
            boolean rejected=true;for(String value:new String[]{"", "Bearer ", "masked-***", "\"quoted\"", "bad:header", "bad\u0000token", "token中文"}){try{normalize(t,value);rejected=false;}catch(IOException expected){if(expected.getMessage().contains(value)&&!value.isEmpty())rejected=false;}}
            t.check(rejected,"invalid token characters are rejected without echoing the token");
            t.call(t.c("ApiSupport"),"saveKey",dirty);t.check(t.call(t.c("ApiSupport"),"key").equals(clean),"saved key is normalized before encryption");
            Object prefs=t.call(t.c("ApiSupport"),"prefs");String stored=(String)t.call(prefs,"getString","key","");t.check(!stored.contains(clean)&&!stored.contains(dirty),"stored preferences contain encrypted key only");
            try{t.call(t.c("ApiSupport"),"saveKey","bad:header");}catch(IOException expected){}t.check(stored.equals(t.call(prefs,"getString","key","")),"invalid replacement does not overwrite existing key");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,(javax.crypto.SecretKey)t.call(t.c("ApiSupport"),"secret"));
            String legacy=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+"."+Base64.encodeToString(cipher.doFinal(dirty.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);Object editor=t.call(prefs,"edit");t.call(editor,"putString","key",legacy);t.call(editor,"commit");
            t.check(t.call(t.c("ApiSupport"),"key").equals(clean),"legacy encrypted keys are normalized when read");
            HttpURLConnection connection=(HttpURLConnection)t.call(t.c("ApiSupport"),"connection","/models","GET",dirty);
            t.check(connection.getRequestProperty("Authorization").equals("Bearer "+clean),"outbound Authorization header contains one clean Bearer token");
            int before=opened;try{t.call(t.c("ApiSupport"),"connection","/models","GET","bad:header");}catch(IOException expected){}t.check(opened==before,"invalid key is rejected before opening HTTP transport");
            String visible=(String)t.call(t.c("ApiSupport"),"userError",new IllegalArgumentException("Unexpected header Bearer "+clean),"Safe network error");
            t.check(visible.equals("Safe network error")&&!visible.contains(clean),"untrusted transport errors cannot echo key in settings UI");
        }finally{Object editor=t.call(t.call(t.c("ApiSupport"),"prefs"),"edit");t.call(editor,"remove","key");t.call(editor,"putBoolean","enabled",false);t.call(editor,"commit");}
    }
}
