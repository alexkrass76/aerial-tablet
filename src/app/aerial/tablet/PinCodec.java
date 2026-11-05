package app.aerial.tablet;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted local PIN verifier; neither the PIN nor a reversible copy is persisted. */
public final class PinCodec {
    private static final int ITERATIONS=600000;
    private PinCodec() {}
    public static boolean valid(char[] pin) {
        if(pin==null||pin.length<4||pin.length>8)return false;
        for(char c:pin)if(c<'0'||c>'9')return false;return true;
    }
    public static String encode(char[] pin) throws GeneralSecurityException {
        if(!valid(pin))throw new IllegalArgumentException("PIN must contain 4 to 8 ASCII digits");
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        byte[] hash=derive(pin,salt);
        try{return "1$"+ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(hash);}
        finally{Arrays.fill(hash,(byte)0);}
    }
    public static boolean verify(char[] pin,String encoded) {
        if(!valid(pin)||encoded==null)return false;
        byte[] actual=null,expected=null;
        try {
            String[] parts=encoded.split("\\$",-1);
            if(parts.length!=4||!parts[0].equals("1")||!parts[1].equals(String.valueOf(ITERATIONS)))return false;
            byte[] salt=Base64.getDecoder().decode(parts[2]);expected=Base64.getDecoder().decode(parts[3]);
            if(salt.length!=16||expected.length!=32)return false;
            actual=derive(pin,salt);return MessageDigest.isEqual(actual,expected);
        }catch(GeneralSecurityException|IllegalArgumentException e){return false;}
        finally {if(actual!=null)Arrays.fill(actual,(byte)0);if(expected!=null)Arrays.fill(expected,(byte)0);}
    }
    private static byte[] derive(char[] pin,byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(pin,salt,ITERATIONS,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        finally{spec.clearPassword();}
    }
    public static long delayAfterFailure(int count) {return count<5?0:Math.min(300000L,30000L<<Math.min(4,count-5));}
}
