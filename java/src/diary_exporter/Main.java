package diary_exporter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Main{      
    public static void main(String[] args) throws UnsupportedEncodingException, InterruptedException, IOException, NoSuchAlgorithmException { 
        NewJFrame frame = new NewJFrame();
        Diary_exporter dex = new Diary_exporter(frame);
    }
}
