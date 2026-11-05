package app.aerial.tablet;

/** Standalone JVM checks, including a vector generated independently with Python hashlib. */
public final class PinCodecTest {
    private static int checks;
    private static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        check(PinCodec.valid("1234".toCharArray())&&PinCodec.valid("12345678".toCharArray()),"PIN bounds");
        check(!PinCodec.valid("123".toCharArray())&&!PinCodec.valid("123456789".toCharArray()),"short and long PIN rejected");
        check(!PinCodec.valid("12a4".toCharArray())&&!PinCodec.valid("١٢٣٤".toCharArray())&&!PinCodec.valid(null),"only ASCII digits accepted");
        String independent="1$600000$AAECAwQFBgcICQoLDA0ODw==$Gc92uHHptfWGmpSqCM6Zlnmb7RHGklqr4tuThyLugpM=";
        check(PinCodec.verify("482619".toCharArray(),independent),"independent PBKDF2-SHA256 vector");
        check(!PinCodec.verify("482618".toCharArray(),independent),"wrong PIN rejected");
        String a=PinCodec.encode("482619".toCharArray()),b=PinCodec.encode("482619".toCharArray());
        check(!a.equals(b),"unique random salt per PIN save");
        check(PinCodec.verify("482619".toCharArray(),a)&&PinCodec.verify("482619".toCharArray(),b),"salted round trips");
        check(!PinCodec.verify("1234".toCharArray(),null)&&!PinCodec.verify("1234".toCharArray(),"invalid"),"missing and malformed verifier rejected");
        check(!PinCodec.verify("482619".toCharArray(),independent.replace("600000","999999999")),"untrusted work factor rejected");
        check(!PinCodec.verify("482619".toCharArray(),"1$600000$???$???"),"malformed base64 rejected");
        check(PinCodec.delayAfterFailure(4)==0&&PinCodec.delayAfterFailure(5)==30000,"pause begins after fifth failure");
        check(PinCodec.delayAfterFailure(6)==60000&&PinCodec.delayAfterFailure(100)==300000,"increasing pause capped at five minutes");
        System.out.println(checks+"/"+checks+" checks passed");
    }
}
