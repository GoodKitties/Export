package com.kanedias.dybr.exporter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class DiaryExporter implements Runnable {
    private HtmlRetriever h;
    Account acc;
    static String shortname = "";
    List<Post> posts;
    Map<String, String> image_gallery;
    static MainJFrame frame;
    JSONArray ready;
    List<String> ready_ids;
    static Logger logger;

    public DiaryExporter(MainJFrame frame) {
        DiaryExporter.frame = frame;
        logger = Logger.getLogger("MyLog");
    }

    public static String createhash(String json) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
            md.reset();
            md.update((json + shortname).getBytes("windows-1251"));
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException ex) {
            frame.printInfo("<html>Ошибка при подготовке к аутентификации.</html>");
            DiaryExporter.logger.log(Level.SEVERE, "Ошибка при подготовке к аутентификации.", ex);
        }
        byte[] digest = md.digest();
        BigInteger bigInt = new BigInteger(1, digest);
        String hashtext = bigInt.toString(16);
        while (hashtext.length() < 32) {
            hashtext = "0" + hashtext;
        }

        return hashtext;
    }

    public static void createJson(Map<String, String> image_gallery, String dir) throws IllegalArgumentException, IllegalAccessException, IOException {
        JSONObject jsonObject = new JSONObject();
        image_gallery.entrySet().forEach((entry) -> {
            jsonObject.put(entry.getKey(), entry.getValue());
        });
        try (FileWriter file = new FileWriter(dir + "/images.json")) {
            String json = jsonObject.toJSONString().replaceAll("\\\\/", "/");
            jsonObject.put("hash", createhash(json));
            file.write(jsonObject.toJSONString().replaceAll("\\\\/", "/"));
        }
    }

    public static void createJson(Account acc, String dir) throws IllegalArgumentException, IllegalAccessException, IOException {
        JSONObject jsonObject = new JSONObject();
        for (Field field : Account.fields) {
            jsonObject.put(field.getName(), createJsonElement(field.get(acc)));
        }
        try (FileWriter file = new FileWriter(dir + "/account.json")) {
            String json = jsonObject.toJSONString().replaceAll("\\\\/", "/");
            jsonObject.put("hash", createhash(json));
            file.write(jsonObject.toJSONString().replaceAll("\\\\/", "/"));
        }
    }

    public static void createJson(List<Post> posts, int from, String dir, int filenumber) throws IllegalArgumentException, IllegalAccessException, IOException {
        JSONObject jsonObject = new JSONObject();
        JSONArray arr = new JSONArray();
        for (int i = from; i < posts.size(); i++) {
            Post post = posts.get(i);
            JSONObject jsonPostObject = new JSONObject();
            for (Field field : Post.fields) {
                jsonPostObject.put(field.getName(), createJsonElement(field.get(post)));
            }
            arr.add(jsonPostObject);
        }
        jsonObject.put("posts", arr);
        try (FileWriter file = new FileWriter(dir + "/posts_" + filenumber + ".json")) {
            String json = jsonObject.toJSONString().replaceAll("\\\\/", "/");
            jsonObject.put("hash", createhash(json));
            file.write(jsonObject.toJSONString().replaceAll("\\\\/", "/"));
        }
    }

    public static Object createJsonElement(Object obj) throws IllegalArgumentException, IllegalAccessException {
        if (obj instanceof String[]) {
            JSONArray arr = new JSONArray();
            for (Object s : (Object[]) obj) {
                arr.add(s);
            }
            return arr;
        }
        if (obj instanceof List) {
            JSONArray arr = new JSONArray();
            for (Object s : (List) obj) {
                arr.add(createJsonElement(s));
            }
            return arr;
        }
        if (obj instanceof Post.Voting) {
            JSONObject jsonObject = new JSONObject();
            for (Field field : Post.Voting.fields) {
                jsonObject.put(field.getName(), createJsonElement(field.get(obj)));
            }
            return jsonObject;
        }
        if (obj instanceof Post.Answer) {
            JSONObject jsonObject = new JSONObject();
            for (Field field : Post.Answer.fields) {
                jsonObject.put(field.getName(), createJsonElement(field.get(obj)));
            }
            return jsonObject;
        }
        if (obj instanceof Comment) {
            JSONObject jsonObject = new JSONObject();
            for (Field field : Comment.fields) {
                jsonObject.put(field.getName(), createJsonElement(field.get(obj)));
            }
            return jsonObject;
        } else {
            return obj;
        }
    }

    public void getReady(String dir) throws IOException, ParseException {
        File[] fList;
        File F = new File(dir);

        fList = F.listFiles();
        JSONParser parser = new JSONParser();
        JSONObject object;

        for (File fList1 : fList) {
            if (!fList1.isFile()) {
                continue;
            }
            String name = fList1.getName();
            if (name.equals("images.json")) {
                object = (JSONObject) parser.parse(new FileReader(dir + "/" + name));

                if (!object.containsKey("hash"))
                    frame.printErrorInfo(1);

                for (Object key : object.keySet()) {
                    //based on you key types
                    String keyStr = (String) key;
                    String keyvalue = (String) object.get(keyStr);
                    image_gallery.put(keyStr, keyvalue);
                }
                continue;
            } else if (!name.contains("posts_")) continue;
            object = (JSONObject) parser.parse(new FileReader(dir + "/" + name));

            if (!object.containsKey("hash"))
                frame.printErrorInfo(1);

            for (Object o : (JSONArray) object.get("posts")) {
                ready_ids.add((String) ((JSONObject) o).get("postid"));
                ready.add(o);
            }
            PostParser.postCounter = Math.max(PostParser.postCounter, Integer.parseInt(name.substring(6, name.length() - 5)));
        }

        if (frame.checkLoaded()) {
            F = new File(dir + "/Images");
            fList = F.listFiles();

            for (File fList1 : fList) {
                if (!fList1.isFile()) {
                    continue;
                }
                String name = fList1.getName();
                h.imgCounter = Math.max(h.imgCounter, Integer.parseInt(name.substring(6, name.length() - 5)));
            }
        }

    }

    @Override
    public void run() {
        acc = new Account();
        posts = new ArrayList<>();

        String login = frame.getLogin();
        String loginutf = login;
        String pass = frame.getPass();
        String dir = frame.getDir();

        dir = dir.replaceAll("\\\\", "/");


        if (!login.equals("") && !pass.equals("") && !dir.equals("")) {
            frame.printInfo("<html>Подключение.<br>Может потребоваться некоторое время на установку cоединения и создание лог-файла.</html>");
            try {
                FileHandler fh;
                fh = new FileHandler(dir + "/diary_exporter_log_file.log");
                logger.addHandler(fh);
                SimpleFormatter formatter = new SimpleFormatter();
                fh.setFormatter(formatter);
            } catch (Exception ex) {
                frame.printInfo("<html>Ошибка при создании лог-файла.</html>");
                frame.printErrorInfo(-1);
                DiaryExporter.logger.log(Level.SEVERE, "Ошибка при создании лог-файла.", ex);
            }

            DiaryExporter.logger.info("cookie creation");
            MessageDigest md = null;
            try {
                loginutf = URLEncoder.encode(login, "windows-1251");
                md = MessageDigest.getInstance("MD5");
                md.reset();
                md.update(pass.getBytes("windows-1251"));
            } catch (Exception ex) {
                frame.printInfo("<html>Ошибка при подготовке к аутентификации.</html>");
                frame.printErrorInfo(2);
                DiaryExporter.logger.log(Level.SEVERE, "Ошибка при подготовке к аутентификации.", ex);
            }
            byte[] digest = md.digest();
            BigInteger bigInt = new BigInteger(1, digest);
            String hashtext = bigInt.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }

            h = new HtmlRetriever(loginutf, hashtext);
            ready = new JSONArray();
            ready_ids = new ArrayList<>();

            PostParser.postCounter = -1;
            h.imgCounter = -1;

            try {
                acc = AccountParser.getAccount(h, loginutf);
                acc.username = login;
                frame.printInfo("Данные аккаунта получены");
                DiaryExporter.logger.info("account ready");
            } catch (Exception ex) {
                frame.printInfo("<html>Ошибка аутентификации.<br>Ваши логин/пароль не верны или случилось что-то непредвиденное.</html>");
                frame.printErrorInfo(2);
                DiaryExporter.logger.log(Level.SEVERE, "Ошибка аутентификации.<br>Ваши логин/пароль не верны или случилось что-то непредвиденное.", ex);
                //throw new Error();
            }

            image_gallery = new HashMap<>();

            if (acc.shortname.equals("")) {
                if (acc.journal.equals("0")) {
                    frame.printInfo("Похоже, вы не ведете ни дневник, ни сообщество.");
                    logger.info("no diary");
                } else {
                    frame.printInfo("<html>Ошибка аутентификации.</html>");
                }
                acc.journal = "0";
            } else {
                shortname = acc.shortname;
                logger.info("creation dirs");
                File myPath;
                dir += "/diary_" + acc.shortname;
                if (frame.loadImage()) {
                    myPath = new File(dir + "/Images");
                } else {
                    myPath = new File(dir);
                }
                myPath.mkdirs();
                h.setDirectory(dir + "/Images");

                DiaryExporter.logger.info("save accout");
                try {
                    createJson(acc, dir);
                } catch (Exception ex) {
                    frame.printInfo("<html>Что-то пошло не так при сохранении данных аккаунта.</html>");
                    frame.printErrorInfo(2);
                    DiaryExporter.logger.log(Level.SEVERE, "Что-то пошло не так при сохранении данных аккаунта.", ex);
                    throw new Error();
                }

                if (frame.addLoad()) {
                    frame.printInfo("Обработка скачанных ранее файлов");
                    DiaryExporter.logger.info("looking for information in old files");
                    try {
                        getReady(dir);
                    } catch (Exception ex) {
                        frame.printInfo("<html>Что-то пошло не так при обработке уже имеющихся данных.</html>");
                        frame.printErrorInfo(2);
                        DiaryExporter.logger.log(Level.SEVERE, "Что-то пошло не так при обработке уже имеющихся данных.", ex);
                        //throw new Error();
                    }
                }

                try {
                    posts = PostParser.getPosts(h, acc.shortname, dir, ready_ids);
                } catch (Exception ex) {
                    frame.printInfo("<html>Что-то пошло не так, когда выгружались посты.</html>");
                    frame.printErrorInfo(2);
                    DiaryExporter.logger.log(Level.SEVERE, "Что-то пошло не так, когда выгружались посты.", ex);
                    throw new Error();
                }

                if (frame.loadImage()) {
                    DiaryExporter.logger.info("image loading start");
                    try {
                        frame.printInfo("Начинается выгрузка изображений");
                        image_gallery = PostParser.loadAllImages(h, posts, image_gallery, dir);
                    } catch (Exception ex) {
                        frame.printInfo("<html>Что-то пошло не так, когда выгружались изображения.</html>");
                        frame.printErrorInfo(2);
                        DiaryExporter.logger.log(Level.SEVERE, "Что-то пошло не так, когда выгружались изображения.", ex);
                        throw new Error();
                    }

                    if (frame.checkLoaded()) {
                        for (Object o : ready) {
                            try {
                                PostParser.loadImage(h, (String) ((JSONObject) o).get("message_html"), image_gallery, dir);
                                for (Object oc : (JSONArray) ((JSONObject) o).get("comments")) {
                                    PostParser.loadImage(h, (String) ((JSONObject) oc).get("message_html"), image_gallery, dir);
                                }
                            } catch (Exception ex) {
                                frame.printInfo("<html>Что-то пошло не так, когда выгружались изображения.</html>");
                                frame.printErrorInfo(2);
                                DiaryExporter.logger.log(Level.SEVERE, "Что-то пошло не так, когда выгружались изображения.", ex);
                                throw new Error();
                            }
                        }
                    }

                    try {
                        if (!acc.avatar.equals("") && !image_gallery.containsKey(acc.avatar)) {
                            image_gallery.put(acc.avatar, h.get(acc.avatar, true));
                        }
                        frame.printInfo("Изображения получены");
                    } catch (Exception ex) {
                        frame.printInfo("<html>Что-то пошло не так, когда выгружалась аватарка.</html>");
                        frame.printErrorInfo(2);
                        DiaryExporter.logger.log(Level.SEVERE, "Что-то пошло не так, когда выгружалась аватарка.", ex);
                        throw new Error();
                    }
                    DiaryExporter.logger.info("image loading stop");
                }
                frame.printInfo("Готово");
            }
        }

        frame.changeEnabled();
    }
}