package com.kanedias.dybr.exporter;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.kanedias.dybr.exporter.Constants.DIARY_URI;

public class PostParser {
    static int postCount, completedPostCount, postCounter;

    public static List<Post> getPosts(HtmlRetriever h, String shortname, File dir, List<String> ready) throws IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
        DiaryExporter.frame.printInfo("Сбор информации о записях...");
        DiaryExporter.logger.info("seek posts");
        List<String> ids = getPostIds(h, shortname);
        DiaryExporter.logger.info("seek not ready posts");
        ids.removeAll(ready);
        postCount = ready.size() + ids.size();
        completedPostCount = ready.size();
        DiaryExporter.logger.info("load posts start");
        return getPostsList(h, ids, shortname, dir);
    }

    private static List<String> getPostIds(HtmlRetriever h, String shortname) throws IOException, InterruptedException {
        Set<String> postIds = new LinkedHashSet<>();
        Queue<URI> pageRefs = new LinkedList<>();
        Set<URI> processedPages = new HashSet<>();

        pageRefs.add(DIARY_URI.resolve("/~" + shortname));
        while (!pageRefs.isEmpty()) {
            URI nextPage = pageRefs.remove();
            String body = h.get(nextPage.toString());

            if (body.equals(h.nullMessage)) {
                DiaryExporter.frame.printErrorInfo(0);
                continue;
            }
            Document doc = Jsoup.parse(body, DIARY_URI.toString());

            Elements pageBarLinks = doc.select("#pageBar a");
            for (Element ref: pageBarLinks) {
                URI pageLink = DIARY_URI.resolve(ref.attr("href"));
                if (!processedPages.contains(pageLink)) {
                    pageRefs.offer(pageLink);
                }

                processedPages.add(pageLink);
            }

            Elements postsOnPage = doc.select("#postsArea > div[id^=post]");
            for (Element p : postsOnPage) {
                postIds.add(StringUtils.substringAfter(p.attr("id"), "post"));
            }
        }
        return new ArrayList<>(postIds);
    }

    private static List<Post> getPostsList(HtmlRetriever h, List<String> ids, String shortname, File dir) throws IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
        List<Post> posts = new ArrayList<>();
        int done = 0;

        for (String p : ids) {
            Post post = new Post();
            post.postid = p;
            String bodystring = h.get(DIARY_URI.resolve("/~" + shortname + "/?editpost&postid=" + p).toString());
            if (bodystring.equals(h.nullMessage)) {
                DiaryExporter.frame.printErrorInfo(0);
                continue;
            }
            Document doc = Jsoup.parse(bodystring);
            Element title = doc.getElementById("postTitle");
            if (title == null) {
                postCount--;
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

            bodystring = h.get(DIARY_URI.resolve("/~" + shortname + "/p" + p + ".html").toString());
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
            Elements votingLink = doc.getElementsByAttributeValue("onclick", "return swapPoll(this);");
            Elements votingBlock = doc.getElementsByAttributeValue("class", "voting");
            if (votingLink.size() > 0 || votingBlock.size() > 0) {
                Element votingQuestion, votingTable;
                if (votingLink.size() > 0) {
                    String link = votingLink.last().attr("href");
                    bodystring = h.get(DIARY_URI.resolve(link).toString());
                    if (bodystring.equals(h.nullMessage)) {
                        DiaryExporter.frame.printErrorInfo(0);
                        continue;
                    }
                    Document votingDoc = Jsoup.parse(bodystring);
                    votingQuestion = votingDoc.body();
                    votingTable = votingDoc.getElementsByTag("table").first();
                } else {
                    votingQuestion = votingBlock.last();
                    votingTable = votingQuestion.getElementsByTag("table").first();
                }

                post.voting.question = votingQuestion.getElementsByTag("b").first().text();
                if (votingTable != null) {
                    Elements trs = votingTable.getElementsByTag("tr");
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

                // voting table can be null in case you haven't voted on this particular post
            }

            Elements comments = doc.getElementsByClass("singleComment");
            for (Element comment : comments) {
                Comment c = new Comment();

                c.dateline = comment.getElementsByClass("postTitle").first()
                        .getElementsByTag("span").first()
                        .childNode(0).toString();
                c.author_username = comment.getElementsByClass("authorName").text();
                String message = comment.getElementsByClass("postInner").first()
                        .child(0).child(0).toString();
                message = message.substring(message.indexOf(">") + 1, message.lastIndexOf("</"));
                c.message_html = changeCommentText(message.trim());
                post.comments.add(c);
            }
            posts.add(post);
            completedPostCount++;
            done++;
            if (done % 20 == 0 || p.equals(ids.get(ids.size() - 1))) {
                postCounter++;
                int poz = done / 20;
                DiaryExporter.logger.info("create json " + postCounter);
                DiaryExporter.createJson(posts, Math.max((poz - 1) * 20, 0), dir, postCounter);
            }
            DiaryExporter.frame.printInfo("<html>Получено " + (100 * completedPostCount / postCount) + "%<br>" + completedPostCount + " из " + postCount + "</html>");
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
        Pattern fullJ = Pattern.compile("(?:<a class=\"TagJIco\" href=\"(?:https://www.diary.ru|)/member/\\?[0-9]+\" title=\"профиль\" target=(?:'|\")?_blank(?:'|\")? ?>(?:&nbsp;|)</a>[\\s]*|)<a class=\"TagL\" href=\"[" + diarylink + "]+.diary.ru\" title=\"(?:дневник: |)[" + diaryname + "]*\" target=(?:'|\")?_blank(?:'|\")?>([" + diaryname + "]+)</a>");
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

    public static Map<String, String> loadAllImages(HtmlRetriever h, List<Post> posts, Map<String, String> image_gallery, File dir) throws IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
        for (Post post : posts) {
            PostParser.loadImages(h, post, image_gallery, dir);
        }
        return image_gallery;
    }

    public static void loadImages(HtmlRetriever h, Post post, Map<String, String> image_gallery, File dir) throws IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
        DiaryExporter.logger.log(Level.INFO, "изображения из " + post.postid);
        loadImage(h, post.message_html, image_gallery, dir);
        for (Comment com : post.comments) {
            loadImage(h, com.message_html, image_gallery, dir);
        }
    }

    protected static void loadImage(HtmlRetriever h, String message, Map<String, String> image_gallery, File dir) throws IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
        Elements imgs = Jsoup.parse(message, DIARY_URI.toString()).getElementsByTag("img");
        for (Element img : imgs) {
            String imgAddr = img.absUrl("src");
            try {
                URI imageSrc = URI.create(imgAddr);
                if (!StringUtils.equals(imageSrc.getHost(), "static.diary.ru") && !StringUtils.equals(imageSrc.getHost(), "secure.diary.ru"))
                    continue;

                if (Smile.links.stream().anyMatch(smileLink -> imageSrc.getPath().equals(smileLink)))
                    continue;

                DiaryExporter.frame.printInfo("<html>Получение изображений<br>" + imageSrc + "</html>");
                if (image_gallery.containsKey(imageSrc))
                    continue;

                String image = h.get(imageSrc.toString(), true);
                if (image.equals(h.nullMessage))
                    continue;

                image_gallery.put(imageSrc.toString(), image);
                DiaryExporter.createJson(image_gallery, dir);
            } catch (Exception ex) {
                DiaryExporter.logger.log(Level.SEVERE, "Failed to rerieve picture " + imgAddr, ex);
                continue;
            }
        }
    }
}
