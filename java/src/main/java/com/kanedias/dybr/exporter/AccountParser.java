package com.kanedias.dybr.exporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.List;

public class AccountParser {
    public static Account getAccount(HtmlRetriever h, String login) throws IOException, InterruptedException {
        Account acc = new Account();
        acc.username = login;

        DiaryExporter.logger.info("Account_parser start");
        DiaryExporter.logger.info("ignition");
        int all_right = ignition(h, acc);
        if (all_right != 0 || acc.shortname.equals("")) {
            return acc;
        }
        DiaryExporter.logger.info("profile_list_step");
        profile_list_step(h, acc);
        DiaryExporter.logger.info("journal_list_step");
        journal_list_step(h, acc);
        DiaryExporter.logger.info("comment_list_step");
        comment_list_step(h, acc);
        DiaryExporter.logger.info("white_black_list_step");
        white_black_list_step(h, acc);
        DiaryExporter.logger.info("member_step");
        member_step(h, acc);
        DiaryExporter.logger.info("tags_step");
        tags_step(h, acc);
        DiaryExporter.logger.info("profile_step");
        profile_step(h, acc);
        DiaryExporter.logger.info("geography_step");
        geography_step(h, acc);
        DiaryExporter.logger.info("epigraph_step");
        epigraph_step(h, acc);
        DiaryExporter.logger.info("Account_parser stop");

        return acc;
    }

    private static int ignition(HtmlRetriever webClient, Account acc) throws IOException, InterruptedException {
        String html = webClient.get("https://x.diary.ru/");
        Document doc = Jsoup.parse(html);

        Element inf_menu = doc.getElementById("inf_menu");
        Element m_menu = doc.getElementById("m_menu");
        Element main_menu = doc.getElementById("main_menu");
        String link_to_member;
        Element link_to_diary;
        if (inf_menu != null && m_menu != null) {
            link_to_member = inf_menu.getElementsByTag("a").first().attr("href");
            link_to_diary = m_menu.getElementsByTag("a").first();
        } else if (main_menu != null) {
            link_to_member = main_menu.getElementsByTag("a").get(8).attr("href");
            link_to_diary = main_menu.getElementsByTag("a").first();
        } else {
            DiaryExporter.logger.info("have no access");
            return 1;
        }
        String journal = "";
        if (link_to_diary != null && link_to_diary.childNodeSize() > 0) {
            journal = link_to_diary.childNode(0).toString();
        }
        String diary = link_to_diary.attr("href");
        DiaryExporter.logger.info("find diary " + journal);
        switch (journal) {
            case "Мой дневник":
                acc.journal = "1";
                acc.shortname = diary.substring(7, diary.indexOf(".diary.ru"));
                break;
            case "Мое сообщество":
                acc.journal = "2";
                acc.shortname = diary.substring(7, diary.indexOf(".diary.ru"));
                break;
            case "Завести дневник":
                acc.journal = "0";
                break;
            default:
                DiaryExporter.logger.info("have no access");
                return 1;
        }

        acc.userid = link_to_member.substring(link_to_member.indexOf("?") + 1);
        return 0;
    }

    private static void profile_list_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get("https://x.diary.ru/options/member/?access"));

        Elements access = doc.getElementsByAttributeValue("name", "access_mode");
        for (Element el : access) {
            if (el.hasAttr("checked")) {
                acc.profile_access = el.attr("value");
            }
        }

        Element access_list = doc.getElementById("access_list");
        if (access_list != null && access_list.childNodeSize() > 0) {
            String list = access_list.childNode(0).toString();
            acc.profile_list = list.split("\n");
        }
    }

    private static void journal_list_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get("https://x.diary.ru/options/diary/?access"));

        Elements access = doc.getElementsByAttributeValue("name", "access_mode");
        for (Element el : access) {
            if (el.hasAttr("checked")) {
                acc.journal_access = el.attr("value");
            }
        }

        access = doc.getElementsByAttributeValue("name", "access_mode2");
        for (Element el : access) {
            if (el.hasAttr("checked")) {
                acc.journal_access = "" + (Integer.parseInt(acc.journal_access) + Integer.parseInt(el.attr("value")));
            }
        }

        Element access_list = doc.getElementById("access_list");
        if (access_list != null && access_list.childNodeSize() > 0) {
            String list = access_list.childNode(0).toString();
            acc.journal_list = list.split("\n");
        }
    }

    private static void comment_list_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get("https://x.diary.ru/options/diary/?commentaccess"));

        Elements access = doc.getElementsByAttributeValue("name", "comments_access_mode");
        for (Element el : access) {
            if (el.hasAttr("checked")) {
                acc.comment_access = el.attr("value");
            }
        }

        Element access_list = doc.getElementById("comments_access_list");
        if (access_list != null && access_list.childNodeSize() > 0) {
            String list = access_list.childNode(0).toString();
            acc.comment_list = list.split("\n");
        }
    }

    private static void white_black_list_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get("https://x.diary.ru/options/member/?access"));

        Element white_list = doc.getElementById("white_list");
        if (white_list != null && white_list.childNodeSize() > 0) {
            String list = white_list.childNode(0).toString();
            acc.white_list = list.split("\n");
        }

        doc = Jsoup.parse(h.get("https://x.diary.ru/options/diary/?pch"));

        Element black_list = doc.getElementById("members");
        if (black_list != null && black_list.childNodeSize() > 0) {
            String list = black_list.childNode(0).toString();
            acc.black_list = list.split("\n");
        }
    }

    private static void member_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get("http://x.diary.ru/member/?" + acc.userid + "&fullreaderslist&fullfavoriteslist&fullcommunity_membershiplist&fullcommunity_moderatorslist&fullcommunity_masterslist&fullcommunity_memberslist"));

        Element contant = doc.getElementById("contant");
        if (contant == null) {
            contant = doc.getElementById("lm_right_content");
        }

        Elements avatar = contant.child(2).getElementsByTag("img");
        if (avatar.size() > 0) {
            acc.avatar = avatar.first().attr("src");
        }

        Elements heads = doc.getElementsByTag("h6");
        for (Element el : heads) {
            String title = "";
            if (el.childNodeSize() > 0) {
                title = el.childNode(0).toString();
                if (title.contains("<noindex>")) {
                    title = el.childNode(1).toString();
                }
            }
            Elements m;
            switch (title) {
                case "Дневник: ":
                case "Сообщество: ":
                    acc.journal_title = el.childNode(1).childNode(0).childNode(0).toString();
                    break;
                case "Участник сообществ:":
                    m = el.nextElementSibling().getElementsByTag("a");
                    acc.communities = new String[m.size() - 2];
                    for (int j = 1; j < m.size() - 1; j++) {
                        Element elem = m.get(j);
                        String s;
                        if (elem.childNodeSize() > 0) {
                            s = elem.childNode(0).toString();
                            if (s.indexOf("<font color=\"red\">") == 0) {
                                s = s.substring(s.indexOf(">") + 1, s.lastIndexOf("<"));
                            }
                        } else {
                            s = elem.attr("href");
                        }
                        acc.communities[j - 1] = s;
                    }
                    break;
                case "Избранные дневники:":
                    m = el.nextElementSibling().getElementsByTag("a");
                    acc.favourites = new String[m.size() - 2];
                    for (int j = 1; j < m.size() - 1; j++) {
                        Element elem = m.get(j);
                        String s;
                        if (elem.childNodeSize() > 0) {
                            s = elem.childNode(0).toString();
                            if (s.indexOf("<font color=\"red\">") == 0) {
                                s = s.substring(s.indexOf(">") + 1, s.lastIndexOf("<"));
                            }
                        } else {
                            s = elem.attr("href");
                        }
                        acc.favourites[j - 1] = s;
                    }
                    break;
                case "Постоянные читатели:":
                    m = el.nextElementSibling().getElementsByTag("a");
                    acc.readers = new String[m.size() - 2];
                    for (int j = 1; j < m.size() - 1; j++) {
                        Element elem = m.get(j);
                        String s;
                        if (elem.childNodeSize() > 0) {
                            s = elem.childNode(0).toString();
                            if (s.indexOf("<font color=\"red\">") == 0) {
                                s = s.substring(s.indexOf(">") + 1, s.lastIndexOf("<"));
                            }
                        } else {
                            s = elem.attr("href");
                        }
                        acc.readers[j - 1] = s;
                    }
                    break;
                case "Участники сообщества:":
                    m = el.nextElementSibling().getElementsByTag("a");
                    acc.members = new String[m.size() - 2];
                    for (int j = 1; j < m.size() - 1; j++) {
                        Element elem = m.get(j);
                        String s;
                        if (elem.childNodeSize() > 0) {
                            s = elem.childNode(0).toString();
                            if (s.indexOf("<font color=\"red\">") == 0) {
                                s = s.substring(s.indexOf(">") + 1, s.lastIndexOf("<"));
                            }
                        } else {
                            s = elem.attr("href");
                        }
                        acc.members[j - 1] = s;
                    }
                    break;
                case "Владельцы сообщества:":
                    m = el.nextElementSibling().getElementsByTag("a");
                    acc.owners = new String[m.size() - 2];
                    for (int j = 1; j < m.size() - 1; j++) {
                        Element elem = m.get(j);
                        String s;
                        if (elem.childNodeSize() > 0) {
                            s = elem.childNode(0).toString();
                            if (s.indexOf("<font color=\"red\">") == 0) {
                                s = s.substring(s.indexOf(">") + 1, s.lastIndexOf("<"));
                            }
                        } else {
                            s = elem.attr("href");
                        }
                        acc.owners[j - 1] = s;
                    }
                    break;
                case "Модераторы сообщества:":
                    m = el.nextElementSibling().getElementsByTag("a");
                    acc.moderators = new String[m.size() - 2];
                    for (int j = 1; j < m.size() - 1; j++) {
                        Element elem = m.get(j);
                        String s;
                        if (elem.childNodeSize() > 0) {
                            s = elem.childNode(0).toString();
                            if (s.indexOf("<font color=\"red\">") == 0) {
                                s = s.substring(s.indexOf(">") + 1, s.lastIndexOf("<"));
                            }
                        } else {
                            s = elem.attr("href");
                        }
                        acc.moderators[j - 1] = s;
                    }
                    break;
            }
        }
    }

    private static void tags_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get("https://x.diary.ru/options/diary/?tags"));

        Element tag_list = doc.getElementById("textarea");
        if (tag_list != null && tag_list.childNodeSize() > 0) {
            String list = tag_list.childNode(0).toString();
            acc.tags = list.split("\n");
        }
    }

    private static void profile_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get("https://x.diary.ru/options/member/?profile"));

        Elements by_line = doc.getElementsByAttributeValue("name", "usertitle");
        if (by_line.size() > 0) {
            acc.by_line = by_line.first().attr("value");
        }

        String year = "00", month = "00", day = "00";
        List<Node> m = doc.getElementById("day").childNodes();
        for (Node n : m) {
            if (n.hasAttr("selected")) {
                day = n.attr("value");
                while (month.length() < 2) {
                    day = "0" + day;
                }
            }
        }
        m = doc.getElementById("month").childNodes();
        for (Node n : m) {
            if (n.hasAttr("selected")) {
                month = n.attr("value");
                while (month.length() < 2) {
                    month = "0" + month;
                }
            }
        }
        m = doc.getElementById("year").childNodes();
        for (Node n : m) {
            if (n.hasAttr("selected")) {
                if (n.childNodeSize() > 0) {
                    year = n.childNode(0).toString();
                }
                while (month.length() < 2) {
                    year = "0" + year;
                }
            }
        }
        acc.birthday = year + "-" + month + "-" + day;


        Element sex = doc.getElementById("malesex");
        if (sex.hasAttr("checked")) {
            acc.sex = "Мужской";
        }
        sex = doc.getElementById("femalesex");
        if (sex.hasAttr("checked")) {
            acc.sex = "Женский";
        }

        List<Node> edu = doc.getElementById("education").childNodes();
        for (Node n : edu) {
            if (n.hasAttr("selected")) {
                if (n.childNodeSize() > 0) {
                    acc.education = n.childNode(0).toString();
                }
            }
        }

        List<Node> sfera = doc.getElementById("sfera").childNodes();
        for (Node n : sfera) {
            if (n.hasAttr("selected")) {
                if (n.childNodeSize() > 0) {
                    acc.sfera = n.childNode(0).toString();
                }
            }
        }

        Element about = doc.getElementById("about");
        if (about != null && about.childNodeSize() > 0) {
            acc.about = about.childNode(0).toString();
        }
    }

    private static void geography_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get("https://x.diary.ru/options/member/?geography"));

        List<Node> countries = doc.getElementsByAttributeValue("name", "country").first().childNodes();
        for (Node n : countries) {
            if (n.hasAttr("selected")) {
                if (n.childNodeSize() > 0) {
                    acc.country = n.childNode(0).toString();
                }
            }
        }

        List<Node> cities = doc.getElementsByAttributeValue("name", "city").first().childNodes();
        for (Node n : cities) {
            if (n.hasAttr("selected")) {
                if (n.childNodeSize() > 0) {
                    acc.city = n.childNode(0).toString();
                }
            }
        }
        acc.city += doc.getElementsByAttributeValue("name", "other").first().attr("value");

        Elements timezone = doc.getElementsByAttributeValue("name", "timezoneoffset").first().getElementsByTag("option");
        for (Element el : timezone) {
            if (el.hasAttr("selected")) {
                acc.timezone = el.attr("value");
            }
        }
    }

    private static void epigraph_step(HtmlRetriever h, Account acc) throws IOException, InterruptedException {
        Document doc = Jsoup.parse(h.get("https://x.diary.ru/options/diary/?owner"));

        Element ep = doc.getElementById("message");
        if (ep != null && ep.childNodeSize() > 0) {
            acc.epigraph = ep.childNode(0).toString();
        }
    }
}
