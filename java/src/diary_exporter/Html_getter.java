package diary_exporter;

import java.awt.Image;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Html_getter {  
    public String directory;
    public int max_img_file = -1;
    private String user_cookie = "";
    private String cookie = "";
    private String[] useragents = {
        "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.102 Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:46.0) Gecko/20100101 Firefox/46.0",
        "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:41.0) Gecko/20100101 Firefox/41.0",
        "Mozilla/5.0 (Windows NT 6.3; Win64; x64; rv:57.0) Gecko/20100101 Firefox/57.0"
    };
    private String useragent = "";
    
    public Html_getter(String login, String pass) {
        Random rnd = new Random(System.currentTimeMillis());
        useragent = useragents[rnd.nextInt(useragents.length)];
        user_cookie = "user_login="+login+"; user_pass="+pass+"; ";
    }
    public void setDirectory(String dir) {
        directory = dir;
    }
    
    private static int normalNumber(String s) {
        Pattern pattern = Pattern.compile("\\(|\\)");
        String[] array = pattern.split(s);
        String k = "";
        for(int i=0; i < array.length; i++) {
            if(array[i].contains("[]")) {
                int x = array[i].split("\\[\\]").length;  
                if(array[i].length() >= 3 && array[i].substring(array[i].length()-3, array[i].length()).equals("+[]")) {
                    x -= 1;
                }
                k += x;
            }
        }
        return Integer.parseInt(k);
    }
    
    private static int cloudflareKey(String s) {
        Pattern p = Pattern.compile("([\\+\\-\\*\\\\\\/]?)([=:])([\\[\\]\\+!\\(\\)]+)");  
        Matcher m = p.matcher(s); 
        int key = 0;
        while(m.find()) {
            String x = s.substring(m.start(), m.end());
            switch(x.charAt(0)) {
                case ':':
                    key = normalNumber(x);
                    break;
                case '+':
                    key += normalNumber(x);
                    break;
                case '-':
                    key -= normalNumber(x);
                    break;
                case '/':
                    key /= normalNumber(x);
                    break;
                case '*':
                    key *= normalNumber(x);
                    break;    
            }            
        }
        return key;
    }
    
    private static String getAttr(String attrname, String attrvalue, String s) {
        int x = ("name=\""+attrname+"\" value=\"").length();
        Pattern p = Pattern.compile("name=\""+attrname+"\" value=\""+attrvalue+"\"");  
        Matcher m = p.matcher(s);
        m.find();
        String y = s.substring(m.start()+x, m.end()-1);
        return y;
    }
    
    private String request(String address, String data, String method, boolean image) throws MalformedURLException, IOException, InterruptedException {
        StringBuffer result = new StringBuffer();
        URL url = new URL(address);
        
        if(url.getProtocol().equals("https")) {
            return requestHTTPS(url, data, method, image);
        } else {            
            return requestHTTP(url, data, method, image);
        }
    }
    
    private String requestHTTP(URL url, String data, String method, boolean image) throws MalformedURLException, IOException, InterruptedException {
        StringBuffer result = new StringBuffer();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", useragent);
        connection.setRequestProperty("Connection", "Keep-Alive");
        if(!cookie.equals("") || !user_cookie.equals("")) {
            connection.setRequestProperty("Cookie", user_cookie+cookie);
        }
        connection.setRequestProperty("Accept", "*/*");
        if(method.equals("POST") && !data.equals("")) {
            connection.setDoInput(true);
            connection.setDoOutput(true);
            OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
            wr.write(data);
            wr.flush();
            wr.close();
        }
        connection.connect();
        BufferedReader rd;
        String line;
        if (connection.getResponseCode() == 503) {
            Diary_exporter.logger.info("cloudflare");   
            rd = new BufferedReader(new InputStreamReader(connection.getErrorStream(),"windows-1251"));

            while ((line = rd.readLine()) != null) {
                result.append(line).append("\n");
            }
            line = result.toString();
            
            return cloudflareHTTP(url.getProtocol()+"://"+url.getHost(), url.getFile(), data, method,
                    getAttr("jschl_vc", "(\\w+)", line), 
                    getAttr("pass", "(.+?)", line), 
                    (cloudflareKey(line) + url.getHost().length())+"",
                    image);            
        }
        else if (image) {
            BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
            String[] temp = url.getFile().split("/");
            temp = temp[temp.length - 1].split("\\.");            
            max_img_file += 1;
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream( directory + "/image_" + max_img_file + "." + temp[temp.length - 1]));
            int i;
            while ((i = in.read()) != -1) {
                out.write(i);
            }
            out.flush();
            out.close();
            in.close();
            return "image_" + max_img_file + "." + temp[temp.length - 1];
        }
        else {
            rd = new BufferedReader(new InputStreamReader(connection.getInputStream(), "windows-1251"));
            
            while ((line = rd.readLine()) != null) {
                result.append(line).append("\n");
            }
        }
        
        return result.toString();
    }
    
    private String requestHTTPS(URL url, String data, String method, boolean image) throws MalformedURLException, IOException, InterruptedException {
        StringBuffer result = new StringBuffer();
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", useragent);
        if(!cookie.equals("")) {
            connection.setRequestProperty("Cookie", cookie);
        }
        connection.setRequestProperty("Accept", "*/*");
        if(method.equals("POST") && !data.equals("")) {
            connection.setDoInput(true);
            connection.setDoOutput(true);
            OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
            wr.write(data);
            wr.flush();
            wr.close();
        }
        connection.connect();
        BufferedReader rd;
        if (connection.getResponseCode() == 503) {
            rd = new BufferedReader(new InputStreamReader(connection.getErrorStream(),"windows-1251"));

            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line).append("\n");
            }
            line = result.toString();
            
            return cloudflareHTTPS(url.getProtocol()+"://"+url.getHost(), url.getPath(), data, method,
                    getAttr("jschl_vc", "(\\w+)", line), 
                    getAttr("pass", "(.+?)", line), 
                    (cloudflareKey(line) + url.getHost().length())+"", 
                    image);            
        }
        else if (image) {
            BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
            String[] temp = url.getFile().split("/");
            temp = temp[temp.length - 1].split(".");            
            max_img_file += 1;
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream( directory + "/image_" + max_img_file + "." + temp[temp.length - 1]));
            int i;
            while ((i = in.read()) != -1) {
                out.write(i);
            }
            out.flush();
            out.close();
            in.close();
            return "image_" + max_img_file + "." + temp[temp.length - 1];
        }
        else {            
            rd = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }
        
    private String cloudflareHTTP(String address, String tale, String data, String method, String jschl_vc, String pass, String jschl_answer, boolean image) throws MalformedURLException, IOException, InterruptedException {
        Date d0 = new Date();
        synchronized(d0){
            d0.wait(5000);
        }
        
        StringBuffer result = new StringBuffer();
        URL url = new URL(address + "/cdn-cgi/l/chk_jschl?"+
                "jschl_vc="+URLEncoder.encode(jschl_vc, "windows-1251")+
                "&pass="+URLEncoder.encode(pass, "windows-1251")+
                "&jschl_answer="+URLEncoder.encode(jschl_answer, "windows-1251"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", useragent);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Referer", address);
        connection.connect();
        BufferedReader rd;
        
        this.cookie = connection.getHeaderField("Set-Cookie");
        
        return request(address+tale, data, method, image);
    }
    
    private String cloudflareHTTPS(String address, String tale, String data, String method, String jschl_vc, String pass, String jschl_answer, boolean image) throws MalformedURLException, IOException, InterruptedException {
        Date d0 = new Date();
        synchronized(d0){
            d0.wait(5000);
        }
        
        StringBuffer result = new StringBuffer();
        URL url = new URL(address + "/cdn-cgi/l/chk_jschl?"+
                "jschl_vc="+URLEncoder.encode(jschl_vc, "windows-1251")+
                "&pass="+URLEncoder.encode(pass, "windows-1251")+
                "&jschl_answer="+URLEncoder.encode(jschl_answer, "windows-1251"));
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", useragent);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Referer", address);
        connection.connect();
        BufferedReader rd;
        
        this.cookie = connection.getHeaderField("Set-Cookie");
        
        return request(address+tale, data, method, image);
    }
    
    public String post(String url, String data, boolean image) throws IOException, MalformedURLException, InterruptedException {
        return request(url, data, "POST", image);
    }
    public String post(String url, String data) throws IOException, MalformedURLException, InterruptedException {
        return request(url, data, "POST", false);
    }
    
    public String get(String url, boolean image) throws IOException, MalformedURLException, InterruptedException {
        return request(url, "", "GET", image);
    }
    public String get(String url) throws IOException, MalformedURLException, InterruptedException {
        return request(url, "", "GET", false);
    }
}
