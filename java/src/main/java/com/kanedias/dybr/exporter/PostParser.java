package com.kanedias.dybr.exporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostParser {
    static int all, had, postCounter;

    public static List<Post> getPosts(HtmlRetriever h, String shortname, String dir, List<String> ready) throws IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
        DiaryExporter.frame.printInfo("Сбор информации о записях...");
        DiaryExporter.logger.info("seek posts");
        List<String> ids = get_post_ids(h, shortname);
        DiaryExporter.logger.info("seek not ready posts");
        getAdditionalPosts(ready, ids);
        all = ready.size() + ids.size();
        had = ready.size();
        DiaryExporter.logger.info("load posts start");
        return getPostsList(h, ids, shortname, dir);
    }

    private static List<String> get_post_ids(HtmlRetriever h, String shortname) throws IOException, InterruptedException {
        List<String> list = new ArrayList<String>();

        String page = "/~" + shortname;
        while (!page.equals("")) {
            String bodystring = h.get("http://www.diary.ru" + page);
            page = "";
            if (bodystring.equals(h.nullMessage)) {
                DiaryExporter.frame.printErrorInfo(0);
                continue;
            }
            Document doc = Jsoup.parse(bodystring);
            Element nextPage = doc.getElementById("pageBar");
            if (nextPage != null) {
                nextPage = nextPage.getElementsByAttributeValue("class", "pages_str")
                        .first().child(1);
                if (nextPage.childNodeSize() > 0) {
                    page = nextPage.child(0).attr("href");
                }
            }

            Elements ps = doc.getElementsByClass("singlePost");
            for (Element p : ps) {
                list.add(p.attr("id").substring(4));
            }
        }
        return list;
    }

    private static void getAdditionalPosts(List<String> ready, List<String> find) {
        for (String p : ready) {
            find.remove(p);
        }
    }

    private static List<Post> getPostsList(HtmlRetriever h, List<String> ids, String shortname, String dir) throws IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
        List<Post> posts = new ArrayList<Post>();
        int done = 0;

        for (String p : ids) {
            Post post = new Post();
            post.postid = p;
            String bodystring = h.get("http://www.diary.ru/~" + shortname + "/?editpost&postid=" + p);
            if (bodystring.equals(h.nullMessage)) {
                DiaryExporter.frame.printErrorInfo(0);
                continue;
            }
            Document doc = Jsoup.parse(bodystring);
            Element title = doc.getElementById("postTitle");
            if (title == null) {
                all--;
                continue;
            } else {
                post.title = title.attr("value");
            }

            String body = doc.getElementById("message").toString();
            body = body.substring(body.indexOf(">") + 1, body.lastIndexOf("</"));
            post.message_html = changePostText(body.trim());
            title = doc.getElementById("atMusic");
            post.current_music = title.attr("value");
            title = doc.getElementById("atMood");
            post.current_mood = title.attr("value");
            Element check = doc.getElementById("nocomm");
            if (check.hasAttr("checked")) {
                post.no_comments = "1";
            }
            check = doc.getElementById("manyAnswers");
            if (check.hasAttr("checked")) {
                post.voting.multiselect = true;
            }
            check = doc.getElementById("endVoting");
            if (check.hasAttr("checked")) {
                post.voting.end = true;
            }

            Elements access = doc.getElementsByAttributeValue("name", "close_access_mode");
            for (Element el : access) {
                if (el.hasAttr("checked")) {
                    post.access = el.attr("value");
                }
            }
            access = doc.getElementsByAttributeValue("name", "close_access_mode2");
            for (Element el : access) {
                if (el.hasAttr("checked")) {
                    post.access = "" + (Integer.parseInt(post.access) + Integer.parseInt(el.attr("value")));
                }
            }

            Elements tags = doc.getElementById("my_tags").getElementsByTag("input");
            for (Element tag : tags) {
                if (tag.hasAttr("checked")) {
                    post.tags.add(tag.attr("value"));
                }
            }
            Element add_tags_field = doc.getElementById("tags");
            if (add_tags_field != null) {
                String[] add_tags = add_tags_field.attr("value").split(";");
                for (String tag : add_tags) {
                    post.tags.add(tag);
                }
            }

            bodystring = bodystring.replaceAll("\\n", "\\\\n");
            doc = Jsoup.parse(bodystring);
            Element access_list = doc.getElementById("access_list3");
            if (access_list != null) {
                post.access_list = access_list.nextSibling().toString().split("\\\\n");
            }

            bodystring = h.get("http://www.diary.ru/~" + shortname + "/p" + p + ".html");
            if (bodystring.equals(h.nullMessage)) {
                DiaryExporter.frame.printErrorInfo(0);
                continue;
            }
            doc = Jsoup.parse(bodystring);
            Element post_body = doc.getElementById("post" + p);
            Elements date = post_body.getElementsByTag("span");
            post.dateline_date = date.get(0).childNode(0).toString() +
                    ", " + date.get(1).childNode(0).toString();
            post.dateline_cdate = post.dateline_date;
            Elements author = post_body.getElementsByClass("authorName");
            if (author != null) {
                author = author.first().children();
                if (author != null) {
                    Element a = author.last();
                    if (a.childNodeSize() > 0) {
                        a = a.child(0);
                        if (a.childNodeSize() > 0) {
                            post.author_username = a.childNode(0).toString();
                        }
                    }
                }
            }

            Element ret = post_body.getElementsByAttributeValue("onclick", "return confirm(\"Вы уверены, что хотите вернуть запись на место?\");").last();
            if (ret != null) {
                String cdate = ret.child(0).toString();
                cdate = cdate.substring(cdate.indexOf(":") + 1, cdate.lastIndexOf("</"));
                post.dateline_cdate = cdate.trim();
            }
            Elements vot_link = doc.getElementsByAttributeValue("onclick", "return swapPoll(this);");
            Elements vot_block = doc.getElementsByAttributeValue("class", "voting");
            if (vot_link.size() > 0 || vot_block.size() > 0) {
                Element voting_question, voting_table;
                if (vot_link.size() > 0) {
                    String link = vot_link.last().attr("href");
                    bodystring = h.get("http://www.diary.ru" + link);
                    if (bodystring.equals(h.nullMessage)) {
                        DiaryExporter.frame.printErrorInfo(0);
                        continue;
                    }
                    Document vot_doc = Jsoup.parse(bodystring);
                    voting_question = vot_doc.body();
                    voting_table = vot_doc.getElementsByTag("table").first();
                } else {
                    voting_question = vot_block.last();
                    voting_table = voting_question.getElementsByTag("table").first();
                }

                post.voting.question = voting_question.getElementsByTag("b").first().childNode(0).toString();
                Elements trs = voting_table.getElementsByTag("tr");
                for (Element tr : trs) {
                    Elements tds = tr.getElementsByTag("td");
                    if (tds.size() < 4) continue;
                    Post.Answer a = new Post.Answer();
                    a.variant = tds.get(0).childNode(0).toString().substring(3).trim();
                    a.count = tds.get(2).childNode(0).toString().trim();
                    a.percent = tds.get(3).childNode(0).toString().trim();
                    post.voting.answers.add(a);
                }

            }

            Elements comments = doc.getElementsByClass("singleComment");
            for (Element comment : comments) {
                Comment c = new Comment();

                c.dateline = comment.getElementsByClass("postTitle").first()
                        .getElementsByTag("span").first()
                        .childNode(0).toString();
                c.author_username = comment.getElementsByClass("authorName").first()
                        .getElementsByTag("strong").first()
                        .childNode(0).toString();
                String message = comment.getElementsByClass("postInner").first()
                        .child(0).child(0).toString();
                message = message.substring(message.indexOf(">") + 1, message.lastIndexOf("</"));
                c.message_html = changeCommentText(message.trim());
                post.comments.add(c);
            }
            posts.add(post);
            had++;
            done++;
            if (done % 20 == 0 || p.equals(ids.get(ids.size() - 1))) {
                postCounter++;
                int poz = done / 20;
                DiaryExporter.logger.info("create json " + postCounter);
                DiaryExporter.createJson(posts, Math.max((poz - 1) * 20, 0), dir, postCounter);
            }
            DiaryExporter.frame.printInfo("<html>Получено " + (100 * had / all) + "%<br>" + had + " из " + all + "</html>");
        }

        return posts;
    }

    public static String changePostText(String s) {
        s = s.replaceAll("&quot;", "\"").replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("&apos;", "\\\\");
        return s;
    }

    public static String changeCommentText(String s) {
        s = s.replaceAll("&quot;", "\"").replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("&apos;", "\\\\");

        String diaryname = "0-9a-zA-Zа-яА-Я-_~!@#$&*()+?=/|\\\\.,;:<>\\[\\] ";
        String diarylink = "0-9a-zA-Z-_.!~*'\\\\()/:";
        Pattern fullJ = Pattern.compile("(?:<a class=\"TagJIco\" href=\"(?:http://www.diary.ru|)/member/\\?[0-9]+\" title=\"профиль\" target=(?:'|\")?_blank(?:'|\")? ?>(?:&nbsp;|)</a>[\\s]*|)<a class=\"TagL\" href=\"[" + diarylink + "]+.diary.ru\" title=\"(?:дневник: |)[" + diaryname + "]*\" target=(?:'|\")?_blank(?:'|\")?>([" + diaryname + "]+)</a>");
        Pattern openMORE = Pattern.compile("(<a href=\"(?:/~[" + diarylink + "]+/p[0-9]+.htm\\?oam|)#more[0-9]*\" class=\"LinkMore\" onclick=\"var e=event; if \\(swapMore\\(\"c?[0-9]+m[0-9]+\", e.ctrlKey \\|\\| e.altKey\\)\\) document.location = this.href; return false;\" id=\"linkmorec?[0-9]+m[0-9]+\">[\\s]*)");
        Pattern startMORE = Pattern.compile("(</a>[\\s]*<span id=\"morec?[0-9]+m[0-9]\"+ ondblclick=\"return swapMore\\(\"c?[0-9]+m[0-9]+\"\\);\" style=\"display:none;(?>visibility:hidden;)?\"><a name=\"morec?[0-9]+m[0-9]+start\"></a>)");
        Pattern closeMORE = Pattern.compile("<a name=\"morec?[0-9]+m[0-9]+end\"></a></span>");

        StringBuffer sb = new StringBuffer(s.length());
        Matcher m_fullJ = fullJ.matcher(s);
        while (m_fullJ.find()) {
            m_fullJ.appendReplacement(sb, Matcher.quoteReplacement("[J]" + m_fullJ.group(1) + "[/J]"));
        }
        m_fullJ.appendTail(sb);
        s = sb.toString();

        Matcher m_openMORE = openMORE.matcher(s);
        if (m_openMORE.find()) {
            s = m_openMORE.replaceAll("[MORE=");
        }
        Matcher m_startMORE = startMORE.matcher(s);
        if (m_startMORE.find()) s = m_startMORE.replaceAll("]");
        Matcher m_closeMORE = closeMORE.matcher(s);
        if (m_closeMORE.find()) s = m_closeMORE.replaceAll("[/MORE]");

        return s;
    }

    public static Map<String, String> loadAllImages(HtmlRetriever h, List<Post> posts, Map<String, String> image_gallery, String dir) throws IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
        for (Post post : posts) {
            PostParser.loadImages(h, post, image_gallery, dir);
        }
        return image_gallery;
    }

    public static void loadImages(HtmlRetriever h, Post post, Map<String, String> image_gallery, String dir) throws IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
        DiaryExporter.logger.log(Level.INFO, "изображения из " + post.postid);
        loadImage(h, post.message_html, image_gallery, dir);
        for (Comment com : post.comments) {
            loadImage(h, com.message_html, image_gallery, dir);
        }
    }

    protected static void loadImage(HtmlRetriever h, String message, Map<String, String> image_gallery, String dir) throws IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
        Elements imgs = Jsoup.parse(message).getElementsByTag("img");
        for (Element img : imgs) {
            String img_src = img.attr("src");
            if (!img_src.contains("static.diary.ru")) continue;
            img_src = img.attr("src").substring(img.attr("src").lastIndexOf("http://")).replaceAll("\"", "");
            if (Smile.links.contains(img_src)) continue;
            DiaryExporter.frame.printInfo("<html>Получение изображений<br>" + img_src + "</html>");
            if (image_gallery.containsKey(img_src)) continue;
            String image = h.get(img_src, true);
            if (image.equals(h.nullMessage)) continue;
            image_gallery.put(img_src, image);
            DiaryExporter.createJson(image_gallery, dir);
        }
    }
}
